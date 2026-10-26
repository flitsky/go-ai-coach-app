package com.worksoc.goaicoach.kv

// 안드로이드 `persistence/GameHistoryStore.kt`의 공용 사본(백로그 #294 — P3) — 저장소는 아래 [KvGameHistoryStore](파일 이음매 위), import만 바꿨다(org.json → kv.json). 고칠 때는 둘을 함께 고친다(`KvStoreParityTest`).

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameHistoryResult
import com.worksoc.goaicoach.application.gamehistory.GameHistoryRetentionPolicy
import com.worksoc.goaicoach.application.gamehistory.GameHistoryStorePort
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.kv.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.kv.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.kv.json.JSONArray
import com.worksoc.goaicoach.kv.json.JSONObject
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * 대국 기록이 사는 `filesDir` 하위 디렉터리.
 *
 * ⚠️ **초기화 경로가 이 이름을 알아야 한다.** `wipeToFreshInstall`은 `shared_prefs`만 훑으므로,
 * 파일로 옮긴 순간부터 **여기를 따로 지워 주지 않으면 대국 기록이 초기화를 살아남는다**(백로그 #151).
 */
internal const val GameHistoryDirName = "game_history"

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
            .also { json -> GameSetupJsonCodec.write(json, entry.setup, GameSetupJsonCodec.Order.KomiThenHandicap) }
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
                // 판 정체성 네 키는 이어하기와 같은 한 곳이 읽는다(refactor backlog #22) — 기본값도 같다(판 크기 9·일본·
                // 호선·덤 [DefaultKomi], 백로그 #23이 맞춘 것). `encodeEntry`는 이 파일이 생긴 첫 커밋(cc84f13d)부터 네 키를
                // 전부 실어 왔으므로 기본값은 사실 도달 불가다. ⚠️ 지원하지 않는 판 크기의 줄은 이제 버려진다(`BoardSize`
                // 검사) — 기록은 전부 실제로 둔 판에서 왔으므로 그런 줄은 저장된 적이 없고, 있었다면 다시보기(`loadReplay`의
                // `BoardSize(entry.boardSize)`)가 이미 던졌다.
                setup = GameSetupJsonCodec.read(json),
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

/**
 * 대국 기록 저장소의 공용 구현(P3) — 안드로이드 `GameHistoryStore`와 같은 배치: `game_history/index.json`(목록) +
 * `game_history/replay/<id>.json`(본문), 쓰기는 임시 파일 → fsync → 이름 바꾸기, 보존 상한은 [GameHistoryRetentionPolicy].
 * 옛 SharedPreferences 목록의 이주는 없다(iOS에는 옛 판이 없다).
 */
internal class KvGameHistoryStore(private val files: AppFileStore) : GameHistoryStorePort {
    private val root = GameHistoryDirName
    private val indexFile = "$root/index.json"
    private val replayDir = "$root/replay"

    override fun appendCompletedGame(entry: GameHistoryEntry, replay: GameReplayData?) {
        files.mkdirs(root)
        files.mkdirs(replayDir)
        val next = loadAll() + entry
        if (replay != null && !replay.isEmpty) {
            files.writeAtomically(replayFile(entry.id), GameReplayCodec.encode(replay, BoardSize(entry.boardSize)))
        }
        writeIndex(applyRetention(next))
    }

    override fun loadAll(): List<GameHistoryEntry> {
        if (!files.exists(indexFile)) {
            sweepOrphanedReplays(emptyList())
            return emptyList()
        }
        val raw = files.readText(indexFile) ?: return emptyList()
        val entries = GameHistoryIndexCodec.decodeAll(raw)
        sweepOrphanedReplays(entries)
        return entries
    }

    override fun loadReplay(id: String): GameReplayData? {
        val entry = loadAll().firstOrNull { it.id == id } ?: return null
        val raw = files.readText(replayFile(id)) ?: return null
        return GameReplayCodec.decode(raw, BoardSize(entry.boardSize))
    }

    override fun updateNote(id: String, note: String?) {
        val current = loadAll()
        if (current.none { it.id == id }) return
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() }
        writeIndex(current.map { entry -> if (entry.id == id) entry.copy(note = trimmed) else entry })
    }

    override fun updateReplay(id: String, replay: GameReplayData) {
        if (replay.isEmpty) return
        val entry = loadAll().firstOrNull { it.id == id } ?: return
        files.writeAtomically(replayFile(id), GameReplayCodec.encode(replay, BoardSize(entry.boardSize)))
    }

    private fun applyRetention(entries: List<GameHistoryEntry>): List<GameHistoryEntry> {
        val evicted = GameHistoryRetentionPolicy.idsToEvict(entries) { entry -> 512L + files.length(replayFile(entry.id)) }
        if (evicted.isEmpty()) return entries
        val evictedSet = evicted.toSet()
        evictedSet.forEach { id -> files.delete(replayFile(id)) }
        return entries.filterNot { it.id in evictedSet }
    }

    private fun replayFile(id: String): String = "$replayDir/${id.sanitizedFileName()}.json"

    private fun sweepOrphanedReplays(entries: List<GameHistoryEntry>) {
        val expectedNames = entries.mapTo(HashSet()) { replayFile(it.id).substringAfterLast('/') }
        val actual = files.list(replayDir) ?: return
        for (name in actual) {
            if (name.endsWith(".tmp")) continue
            if (name !in expectedNames) files.delete("$replayDir/$name")
        }
    }

    private fun writeIndex(entries: List<GameHistoryEntry>): Boolean = files.writeAtomically(indexFile, GameHistoryIndexCodec.encodeAll(entries))
}
