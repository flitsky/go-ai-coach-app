package com.worksoc.goaicoach.persistence

import android.content.Context
import android.content.SharedPreferences
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameHistoryResult
import com.worksoc.goaicoach.application.gamehistory.GameHistoryRetentionPolicy
import com.worksoc.goaicoach.application.gamehistory.GameHistoryStorePort
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * 대국 기록이 사는 `filesDir` 하위 디렉터리.
 *
 * ⚠️ **초기화 경로가 이 이름을 알아야 한다.** `wipeToFreshInstall`은 `shared_prefs`만 훑으므로,
 * 파일로 옮긴 순간부터 **여기를 따로 지워 주지 않으면 대국 기록이 초기화를 살아남는다**(백로그 #151).
 */
internal const val GameHistoryDirName = "game_history"

/**
 * `SavedGameStorePort`/[GameSessionStore]와는 별개 Port다 — 저건 "진행 중인 대국 1개
 * 이어하기" 전용, 이건 끝난 대국들의 누적 목록이다(킥오프 플랜 6장).
 *
 * ## ⚠️ 2026-09-18에 `SharedPreferences`에서 **파일**로 옮겼다 (백로그 #151)
 * U-4가 상한을 **100MB**로 정했는데, `SharedPreferences`는 **파일 전체를 앱 시작 시 메모리로
 * 읽어 들이고 계속 들고 있는다.** 거기에 100MB를 담는 것은 곧 OOM이다. 게다가 옛 구조는
 * 모든 기록을 **JSON 한 덩어리**로 묶어, 목록 한 줄을 그리려 해도 전부를 파싱해야 했다.
 *
 * 그래서 둘로 갈랐다:
 * - `game_history/index.json` — **목록용 메타데이터만.** 화면이 읽는 것은 이것뿐이다.
 * - `game_history/replay/<id>.json` — 수순·형세·착수 평가. **다시보기가 열릴 때 그 한 판만** 읽는다.
 *
 * ⚠️ **옛 `SharedPreferences` 기록은 첫 읽기에서 자동 이관된다**([migrateLegacyPrefsIfNeeded]).
 * 이관 후 prefs 키를 지우므로 두 번 일어나지 않는다. 옛 기록에는 리플레이 본문이 없다 —
 * 애초에 저장된 적이 없기 때문이고, 이것이 #151이 *"늦출수록 영구 손실"* 인 이유다.
 *
 * ## ⚠️ 파일은 제자리에서 덮어쓰지 않는다 — 옆의 임시 파일에 다 쓴 뒤 이름을 바꾼다 (refactor backlog #21)
 * 대국이 끝날 때마다 `index.json` **전체**를 다시 쓴다. 제자리 덮어쓰기(`writeText`)는 파일을 먼저 0바이트로
 * 자르고 채우므로, 그 사이 프로세스가 죽거나 쓰기가 실패하면 깨진 JSON이 남고 코덱은 그것을 **빈 목록**으로
 * 읽는다. 다음 대국이 그 빈 목록에 한 판을 얹어 쓰는 순간 **그때까지의 기록 전부가 영구히 사라진다.**
 * [writeAtomically]는 같은 디렉터리의 `<이름>.tmp`에 쓰고 `fsync`한 뒤 `renameTo`로 바꿔 끼운다 —
 * 같은 파일시스템 안의 rename은 원자적이라, 읽는 쪽은 **옛 파일 전체 아니면 새 파일 전체**만 본다.
 * 실패하면 임시 파일만 지우고 옛 파일은 그대로 둔다. ⚠️ 쓰는 **바이트는 예전과 같다**(코덱 출력의 UTF-8,
 * `File.writeText`와 동일) — 저장 포맷·파일 이름·스키마 번호는 하나도 바뀌지 않았다(함정 69).
 * 남은 `.tmp`는 아무도 읽지 않고, 다음 쓰기가 덮어쓰며, 초기화는 디렉터리째 지운다.
 *
 * ⚠️ **`fsync`는 대국 하나당 최대 두 번 돈다** — 리플레이가 있으면 `replay/<id>.json`에 한 번,
 * `index.json`에 한 번(한 줄 평을 나중에 고치면 `updateNote`가 한 번 더). 둘 다 **메인 스레드**에서
 * 부른다(`GoCoachApp.kt`의 `LaunchedEffect`, `GameExitRecording.kt`) — 대국은 세션당 한 번만 끝나고
 * 파일 하나(index.json)는 보통 수 KB~수백 KB라 `fsync` 자체는 수 ms대로 보지만, 이건 추정이다.
 * 기기 스모크로 대국 종료·나가기 직후에 눈에 띄는 끊김이 없는지 확인할 것.
 *
 * ## ⚠️ replay를 쓴 뒤 index 쓰기만 실패하면 참조 없는 파일이 남는다 (refactor backlog #87, `#21` 검수가 찾음)
 * `appendCompletedGame`은 replay 본문을 먼저 쓰고 그 위에 index를 다시 쓰는데, 위의 원자 쓰기 덕분에
 * *둘 다* 반쪽짜리로 남는 일은 없어졌지만 **replay는 성공하고 index만 실패하는** 조합은 여전히
 * 가능하다 — 그러면 `replay/<id>.json`은 온전히 있는데 그 id를 아는 index가 없다. 앱 서비스
 * (`runGameHistoryAppendIfCompleted`)의 멱등성 판정은 `loadAll().lastOrNull()`만 보므로 이 실패를
 * 모르고, 재시도마다 **새 id**로 또 replay를 쓴다 — 고치지 않으면 재시도가 쌓일수록 고아가 는다.
 * [loadAll]이 부를 때마다 [sweepOrphanedReplays]로 "지금 index가 아는 replay 파일 이름"만 남기고
 * 나머지를 지운다. **읽기 쪽에 두고 쓰기 실패 시점에 직접 지우지 않은 이유**는 이 수정이 배포되기
 * *전*에 이미 쌓인 고아(과거 버전이 남긴 것)까지 한 경로로 청소하기 위해서다 — 실패 시점에만 지우면
 * 그 재현조차 못 한다. 비용은 `replayDir` 목록 한 번(디렉터리 엔트리 수만큼, `fsync` 아님)뿐이고
 * `loadAll`은 화면 진입·대국 종료 시점에만 불려(위 참고) 자주 돌지 않는다.
 * ⚠️ **`appendCompletedGame`이 [loadAll]을 replay 쓰기보다 먼저 부른다** — 순서를 반대로 두면
 * 방금 쓴 그 판의 replay가 (아직 index에 없으니) 제 손으로 쓰자마자 고아로 오인돼 지워진다.
 */
internal class GameHistoryStore internal constructor(
    private val root: File,
    private val legacyPrefs: () -> SharedPreferences,
    private val openOutput: (File) -> OutputStream = ::FileOutputStream,
) : GameHistoryStorePort {
    constructor(context: Context) : this(
        root = File(context.applicationContext.filesDir, GameHistoryDirName),
        legacyPrefs = {
            context.applicationContext.getSharedPreferences(LegacyPrefsName, Context.MODE_PRIVATE)
        },
    )

    private val indexFile = File(root, IndexFileName)
    private val replayDir = File(root, ReplayDirName)

    override fun appendCompletedGame(entry: GameHistoryEntry, replay: GameReplayData?) {
        root.mkdirs()
        replayDir.mkdirs()

        // ⚠️ loadAll()을 replay 쓰기보다 먼저 부른다 — sweepOrphanedReplays가 훑는 "index가
        // 아는 replay 파일" 집합에 이 판의 replay가 아직 없어야, 쓰자마자 고아로 오인해
        // 지워버리는 사고가 없다(클래스 KDoc, refactor backlog #87).
        val next = loadAll() + entry
        if (replay != null && !replay.isEmpty) {
            writeAtomically(
                replayFile(entry.id),
                GameReplayCodec.encode(replay, BoardSize(entry.boardSize)),
            )
        }
        writeIndex(applyRetention(next))
    }

    override fun loadAll(): List<GameHistoryEntry> {
        val migrated = migrateLegacyPrefsIfNeeded()
        if (migrated != null) {
            sweepOrphanedReplays(migrated)
            return migrated
        }
        if (!indexFile.exists()) {
            // 색인이 아예 없다 — 무엇이 진짜인지 이미 확실하므로(파일이 없다는 것 자체가 답)
            // replay가 남아 있다면 전부 고아다.
            sweepOrphanedReplays(emptyList())
            return emptyList()
        }
        // ⚠️ 여기서 읽기 자체가 실패하면(권한·I/O 오류 등) 청소하지 않는다 — index가 있는데
        // 못 읽은 것과 "진짜로 비어 있다"는 다르고, 후자로 오인해 쓸어버리면 멀쩡한 replay까지
        // 잃는다. 위 `!indexFile.exists()` 분기와 달리 이 실패는 "확인된 진실"이 아니다.
        val raw = runCatching { indexFile.readText() }.getOrNull() ?: return emptyList()
        val entries = GameHistoryIndexCodec.decodeAll(raw)
        sweepOrphanedReplays(entries)
        return entries
    }

    override fun loadReplay(id: String): GameReplayData? {
        val entry = loadAll().firstOrNull { it.id == id } ?: return null
        val raw = runCatching { replayFile(id).readText() }.getOrNull() ?: return null
        return GameReplayCodec.decode(raw, BoardSize(entry.boardSize))
    }

    override fun updateNote(id: String, note: String?) {
        val current = loadAll()
        if (current.none { it.id == id }) return
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() }
        writeIndex(current.map { entry -> if (entry.id == id) entry.copy(note = trimmed) else entry })
    }

    /**
     * U-4의 두 겹 상한을 건다. ⚠️ **저장소 안에서 건다** — 호출부에 맡기면 경로마다 달라진다.
     */
    private fun applyRetention(entries: List<GameHistoryEntry>): List<GameHistoryEntry> {
        val evicted = GameHistoryRetentionPolicy.idsToEvict(entries) { entry ->
            EstimatedIndexBytesPerEntry + replayBytes(entry.id)
        }
        if (evicted.isEmpty()) return entries
        val evictedSet = evicted.toSet()
        evictedSet.forEach { id -> runCatching { replayFile(id).delete() } }
        return entries.filterNot { it.id in evictedSet }
    }

    private fun replayFile(id: String): File = File(replayDir, "${id.sanitizedFileName()}.json")

    private fun replayBytes(id: String): Long =
        runCatching { replayFile(id).length() }.getOrDefault(0L)

    /**
     * [entries]가 아는 replay 파일 이름만 남기고 `replayDir`의 나머지를 지운다(refactor backlog #87).
     * 클래스 KDoc의 "언제·왜"를 참고 — 여기는 "어떻게"만 담당한다.
     *
     * ⚠️ **`.tmp`는 건드리지 않는다** — [writeAtomically]가 남긴 실패 잔여물은 다음 쓰기가
     * 스스로 덮어쓰므로(클래스 KDoc) 이 청소의 대상이 아니다. 범위를 늘리지 않는다.
     */
    private fun sweepOrphanedReplays(entries: List<GameHistoryEntry>) {
        val expectedNames = entries.mapTo(HashSet()) { replayFile(it.id).name }
        val actual = replayDir.listFiles() ?: return
        for (file in actual) {
            if (file.name.endsWith(TempSuffix)) continue
            if (file.name !in expectedNames) runCatching { file.delete() }
        }
    }

    /** @return 새 index가 실제로 자리를 잡았는가. 실패하면 옛 index가 그대로 남아 있다. */
    private fun writeIndex(entries: List<GameHistoryEntry>): Boolean =
        writeAtomically(indexFile, GameHistoryIndexCodec.encodeAll(entries))

    /**
     * [target]을 [text]로 **통째로** 바꾸거나, 실패하면 **손대지 않는다**(클래스 KDoc 참고).
     * 예전처럼 실패를 삼키지만(`runCatching`), 삼킨 뒤에도 옛 파일이 멀쩡하다는 점이 다르다.
     */
    private fun writeAtomically(target: File, text: String): Boolean {
        val temp = File(target.parentFile, target.name + TempSuffix)
        val replaced = runCatching {
            openOutput(temp).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
                // 이름을 바꾸기 전에 내용을 디스크에 내린다 — 안 그러면 전원이 나갔을 때 이름만 바뀌고
                // 내용은 비어 있는 파일이 남을 수 있다(`android.util.AtomicFile`도 같은 순서다).
                (output as? FileOutputStream)?.fd?.sync()
            }
            check(temp.renameTo(target)) { "rename ${temp.name} -> ${target.name} failed" }
        }.isSuccess
        if (!replaced) runCatching { temp.delete() }
        return replaced
    }

    /**
     * 옛 `SharedPreferences` blob을 index 파일로 한 번 옮기고 prefs를 비운다.
     *
     * ⚠️ **index 파일이 이미 있으면 아무것도 하지 않는다**(`null` 반환 — 이관할 게 없었다는 뜻) —
     * 안 그러면 이관이 매번 일어나 새 기록을 옛 기록으로 덮어쓴다.
     *
     * ⚠️ **index가 실제로 써졌을 때만 옛 키를 지운다**(refactor backlog #21). 쓰기가 실패했는데 지우면
     * 이관할 원본까지 사라진다. 실패해도 여기서 디코드한 옛 기록은 [loadAll]에 그대로 돌려준다(아래
     * `@return` 참고) — 그래야 **같은 호출 안에서** `appendCompletedGame`처럼 그 뒤를 잇는 다른 쓰기가,
     * 방금 실패한 이관을 모르는 채로 옛 기록 없이 index를 새로 만들어 버리지 않는다. 그렇게 새로
     * 만들어지면 `indexFile.exists()`가 참이 되어 이관이 다시는 돌지 않고, 옛 기록은 prefs에 남아
     * 있어도 영영 안 보이게 된다 — 실패 자체가 아니라 "실패 직후의 성공한 다른 쓰기"가 문제다.
     *
     * @return 이관할 옛 기록이 있어서 시도했다면(쓰기 성공 여부와 무관) 그 디코드 결과, 이관할 게
     *   없었다면(index가 이미 있거나 옛 키가 비어 있다면) `null`.
     */
    private fun migrateLegacyPrefsIfNeeded(): List<GameHistoryEntry>? {
        if (indexFile.exists()) return null
        val prefs = legacyPrefs()
        val raw = prefs.getString(LegacyEntriesKey, null) ?: return null
        root.mkdirs()
        val decoded = GameHistoryIndexCodec.decodeLegacyAll(raw)
        if (writeIndex(decoded)) {
            prefs.edit().remove(LegacyEntriesKey).apply()
        }
        return decoded
    }

    private companion object {
        const val IndexFileName = "index.json"
        const val ReplayDirName = "replay"
        const val LegacyPrefsName = "go_ai_coach_game_history"
        const val LegacyEntriesKey = "entries"

        /** [writeAtomically]가 같은 디렉터리에 두는 임시 파일의 꼬리. 읽는 쪽은 이 파일을 보지 않는다. */
        const val TempSuffix = ".tmp"

        /**
         * 한 기록이 index에서 차지하는 대략의 바이트. 상한 판정에만 쓰므로 정확할 필요는 없고,
         * **넉넉히 잡아** 상한을 넘기지 않는 쪽으로 틀리게 한다.
         */
        const val EstimatedIndexBytesPerEntry = 512L
    }
}

/** id는 우리가 만든 `"<millis>-<난수>"` 형태지만, 파일 이름으로 쓰기 전에 한 번 더 좁힌다. */
private fun String.sanitizedFileName(): String = filter { it.isLetterOrDigit() || it == '-' }

internal object GameHistoryIndexCodec {
    private const val CurrentSchemaVersion = 1

    fun encodeAll(entries: List<GameHistoryEntry>): String =
        JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put("entries", JSONArray(entries.map(::encodeEntry)))
            .toString()

    fun decodeAll(raw: String): List<GameHistoryEntry> = decode(raw, legacy = false)

    /** 옛 `SharedPreferences` blob 전용 — `humanColor`+`result`에서 승자를 역산한다. */
    fun decodeLegacyAll(raw: String): List<GameHistoryEntry> = decode(raw, legacy = true)

    private fun decode(raw: String, legacy: Boolean): List<GameHistoryEntry> =
        runCatching {
            val json = JSONObject(raw)
            // ⚠️ **등호 검사다 — 번호를 올리면 기록이 통째로 빈 목록이 된다.** 새 키는
            // 선택적으로 더하고 번호는 1을 유지할 것(백로그 #151).
            if (json.optInt("schema", -1) != CurrentSchemaVersion) return@runCatching emptyList()
            val array = json.optJSONArray("entries") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { i -> decodeEntry(array.optJSONObject(i), legacy) }
        }.getOrDefault(emptyList())

    private fun encodeEntry(entry: GameHistoryEntry): JSONObject =
        JSONObject()
            .put("id", entry.id)
            .put("playedAtMillis", entry.playedAtMillis)
            .put("boardSize", entry.boardSize)
            .put("ruleset", entry.ruleset.name)
            .put("komi", entry.komi)
            .put("handicapCount", entry.handicapCount)
            .put("playerSetup", encodePlayerSetup(entry.playerSetup))
            .put("moveCount", entry.moveCount)
            .put("humanColor", entry.humanColor?.name ?: JSONObject.NULL)
            .put("winner", entry.winner?.name ?: JSONObject.NULL)
            .put("isResign", entry.isResign)
            .put("margin", entry.margin ?: JSONObject.NULL)
            .put("hasReplay", entry.hasReplay)
            .put("note", entry.note ?: JSONObject.NULL)

    private fun decodeEntry(json: JSONObject?, legacy: Boolean): GameHistoryEntry? {
        if (json == null) return null
        return runCatching {
            val humanColor =
                if (json.isNull("humanColor")) null
                else enumOrNull<StoneColor>(json.optString("humanColor"))
            val legacyResult =
                if (legacy) enumOrNull<GameHistoryResult>(json.optString("result")) else null
            GameHistoryEntry(
                id = json.getString("id"),
                playedAtMillis = json.optLong("playedAtMillis", 0L),
                boardSize = json.optInt("boardSize", 9),
                ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese),
                // ⚠️ 백로그 #23 — 기본값은 [DefaultKomi]다, `GameSessionStore`/`UserPreferencesStore`와
                // 맞춘다. `encodeEntry`는 이 파일이 생긴 첫 커밋(cc84f13d, backlog #6)부터 계속
                // `komi`를 실어 왔으므로 이 폴백은 사실 **도달 불가**다 — komi 키가 없는 기록이
                // 저장된 적이 없다. 그래도 `0.0`으로 두면 "폴백이 실제로 쓰인다면 무슨 값이어야
                // 하는가"를 읽는 사람이 다른 스토어와 다르게 오해하게 만들어, 정합성을 위해 맞춘다.
                komi = json.optDouble("komi", DefaultKomi),
                handicapCount = json.optInt("handicapCount", 0),
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                moveCount = json.optInt("moveCount", 0),
                humanColor = humanColor,
                winner = legacyResult?.let { winnerOf(it, humanColor) }
                    ?: if (json.isNull("winner")) null else enumOrNull(json.optString("winner")),
                isResign = legacyResult?.let { it == GameHistoryResult.Resign }
                    ?: json.optBoolean("isResign", false),
                margin = if (json.isNull("margin")) null else json.optDouble("margin"),
                hasReplay = json.optBoolean("hasReplay", false),
                note = if (json.isNull("note")) null else json.optString("note").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    /**
     * 옛 기록의 승자 역산.
     *
     * ⚠️ **기권은 복원할 수 없다** — 옛 결정이 *"어느 쪽이 기권했는지 구분하지 않는다"* 였기 때문에
     * 저장된 적이 없다. `null`을 돌려주고, 화면은 `winner == null && isResign`을 "기권"으로만 쓴다.
     */
    private fun winnerOf(result: GameHistoryResult, humanColor: StoneColor?): StoneColor? =
        when (result) {
            GameHistoryResult.Win -> humanColor
            GameHistoryResult.Loss -> humanColor?.opponent
            GameHistoryResult.Draw, GameHistoryResult.Resign -> null
        }
}

internal object GameReplayCodec {
    private const val CurrentSchemaVersion = 1

    fun encode(replay: GameReplayData, boardSize: BoardSize): String =
        JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put("moves", ReplayJsonCodec.encodeMoves(replay.moves, boardSize))
            .put("scoreSnapshots", ReplayJsonCodec.encodeScoreSnapshots(replay.scoreSnapshots))
            .put(
                "moveEvaluations",
                ReplayJsonCodec.encodeMoveEvaluations(replay.moveEvaluations, boardSize),
            )
            .toString()

    fun decode(raw: String, boardSize: BoardSize): GameReplayData? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", -1) != CurrentSchemaVersion) return@runCatching null
            // ⚠️ 셋을 **따로** 감싼다 — 한 조각이 깨졌다고 수순까지 버리면 다시보기가 통째로 죽는다.
            val moves = runCatching {
                ReplayJsonCodec.decodeMoves(json.optJSONArray("moves") ?: JSONArray(), boardSize)
            }.getOrDefault(emptyList())
            if (moves.isEmpty()) return@runCatching null
            GameReplayData(
                moves = moves,
                scoreSnapshots = runCatching {
                    ReplayJsonCodec.decodeScoreSnapshots(
                        json.optJSONArray("scoreSnapshots") ?: JSONArray(),
                    )
                }.getOrDefault(emptyList()),
                moveEvaluations = runCatching {
                    ReplayJsonCodec.decodeMoveEvaluations(
                        json.optJSONArray("moveEvaluations") ?: JSONArray(),
                        boardSize,
                    )
                }.getOrDefault(emptyList()),
            )
        }.getOrNull()
}
