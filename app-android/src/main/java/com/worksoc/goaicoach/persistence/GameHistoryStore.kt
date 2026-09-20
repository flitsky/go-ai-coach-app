package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameHistoryResult
import com.worksoc.goaicoach.application.gamehistory.GameHistoryRetentionPolicy
import com.worksoc.goaicoach.application.gamehistory.GameHistoryStorePort
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import java.io.File
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
 */
internal class GameHistoryStore(context: Context) : GameHistoryStorePort {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, GameHistoryDirName)
    private val indexFile = File(root, IndexFileName)
    private val replayDir = File(root, ReplayDirName)

    override fun appendCompletedGame(entry: GameHistoryEntry, replay: GameReplayData?) {
        root.mkdirs()
        replayDir.mkdirs()

        if (replay != null && !replay.isEmpty) {
            runCatching {
                replayFile(entry.id).writeText(
                    GameReplayCodec.encode(replay, BoardSize(entry.boardSize)),
                )
            }
        }
        val next = loadAll() + entry
        writeIndex(applyRetention(next))
    }

    override fun loadAll(): List<GameHistoryEntry> {
        migrateLegacyPrefsIfNeeded()
        val raw = runCatching { indexFile.readText() }.getOrNull() ?: return emptyList()
        return GameHistoryIndexCodec.decodeAll(raw)
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

    private fun writeIndex(entries: List<GameHistoryEntry>) {
        runCatching { indexFile.writeText(GameHistoryIndexCodec.encodeAll(entries)) }
    }

    /**
     * 옛 `SharedPreferences` blob을 index 파일로 한 번 옮기고 prefs를 비운다.
     *
     * ⚠️ **index 파일이 이미 있으면 아무것도 하지 않는다** — 안 그러면 이관이 매번 일어나
     * 새 기록을 옛 기록으로 덮어쓴다.
     */
    private fun migrateLegacyPrefsIfNeeded() {
        if (indexFile.exists()) return
        val prefs = appContext.getSharedPreferences(LegacyPrefsName, Context.MODE_PRIVATE)
        val raw = prefs.getString(LegacyEntriesKey, null) ?: return
        root.mkdirs()
        writeIndex(GameHistoryIndexCodec.decodeLegacyAll(raw))
        prefs.edit().remove(LegacyEntriesKey).apply()
    }

    private companion object {
        const val IndexFileName = "index.json"
        const val ReplayDirName = "replay"
        const val LegacyPrefsName = "go_ai_coach_game_history"
        const val LegacyEntriesKey = "entries"

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
                komi = json.optDouble("komi", 0.0),
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
