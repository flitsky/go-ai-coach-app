package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GameHistoryStore`의 **쓰기 내구성** 재현(refactor backlog #21).
 *
 * 대국이 하나 끝날 때마다 `index.json` **전체**를 다시 쓴다. 제자리에서 덮어쓰면(`writeText`) 파일이
 * 먼저 0바이트로 잘린 뒤 채워지므로, 그 사이에 프로세스가 죽거나 쓰기가 실패하면 **깨진 JSON**이 남는다.
 * 코덱은 깨진 index를 빈 목록으로 읽고, 다음 대국의 기록이 그 빈 목록에 한 판을 얹어 쓰므로
 * **그때까지의 기록 전부가 영구히 사라진다.**
 *
 * 쓰기 도중의 죽음은 [openOutput] 자리에 **정해진 바이트만 쓰고 터지는 스트림**을 넣어 흉내 낸다 —
 * 잘린 파일이 남는다는 점에서 프로세스 강제 종료와 같다.
 */
class GameHistoryStoreDurabilityTest {
    private val root: File = createTempDirectory("go-coach-game-history").toFile().resolve(GameHistoryDirName)
    private val legacyPrefs = InMemorySharedPreferences()
    private val indexFile = root.resolve("index.json")

    private fun store(openOutput: (File) -> OutputStream = ::FileOutputStream) =
        GameHistoryStore(root = root, legacyPrefs = { legacyPrefs }, openOutput = openOutput)

    /** 파일마다 [budgetFor]바이트까지만 쓰고 그다음 바이트에서 터진다. */
    private fun dyingOutput(budgetFor: (Int) -> Int): (File) -> OutputStream = { file ->
        DyingOutputStream(FileOutputStream(file), budgetFor)
    }

    @Test
    fun indexWriteThatDiesMidwayLeavesThePreviousHistoryReadable() {
        val earlier = listOf(entry("1-a"), entry("2-b"))
        earlier.forEach { store().appendCompletedGame(it) }

        // 0바이트(자르자마자 죽음)와 절반(쓰다 죽음) 두 지점을 다 본다.
        for (budget in listOf<(Int) -> Int>({ 0 }, { total -> total / 2 })) {
            store(dyingOutput(budget)).appendCompletedGame(entry("3-c"))

            assertEquals("쓰기가 죽은 뒤에도 이전 기록은 그대로 읽혀야 한다", earlier, store().loadAll())
        }
    }

    @Test
    fun nextGameAfterAFailedWriteKeepsEveryEarlierGame() {
        // 사용자가 겪는 결말 — 실패 자체보다 **그다음 대국이 빈 목록 위에 쓰는 것**이 기록을 지운다.
        store().appendCompletedGame(entry("1-a"))
        store().appendCompletedGame(entry("2-b"))
        store(dyingOutput { total -> total / 2 }).appendCompletedGame(entry("3-c"))

        store().appendCompletedGame(entry("4-d"))

        assertEquals(listOf("1-a", "2-b", "4-d"), store().loadAll().map { it.id })
    }

    @Test
    fun legacyBlobIsKeptWhenItsMigrationWriteFails() {
        // 옛 `SharedPreferences` 기록의 이관도 같은 쓰기를 탄다. 쓰기가 실패했는데 옛 키를 지우면
        // **이관할 원본까지 사라진다.** 실패하면 옛 키를 남겨 다음 실행에서 다시 이관해야 한다.
        legacyPrefs.edit().putString("entries", LegacyBlob).apply()

        store(dyingOutput { total -> total / 2 }).loadAll()

        assertEquals(listOf("legacy-1"), store().loadAll().map { it.id })
        assertEquals(null, legacyPrefs.rawString("entries"))
    }

    @Test
    fun writtenFilesAreByteForByteTheUnchangedCodecOutputAndNoTempFileRemains() {
        // ⚠️ 포맷 불변(함정 69) — 쓰는 방식만 바뀌고 **바이트는 한 개도 안 바뀐다.** 고치기 전의
        // `File.writeText`는 UTF-8 바이트를 그대로 썼으므로, 코덱 출력의 UTF-8 바이트와 같아야 한다.
        val first = entry("1-a").copy(note = "한 줄 평 — 유니코드")
        val second = entry("2-b", hasReplay = true)
        store().appendCompletedGame(first)
        store().appendCompletedGame(second, Replay)

        assertArrayEquals(
            GameHistoryIndexCodec.encodeAll(listOf(first, second)).toByteArray(Charsets.UTF_8),
            indexFile.readBytes(),
        )
        assertArrayEquals(
            GameReplayCodec.encode(Replay, BoardSize(9)).toByteArray(Charsets.UTF_8),
            root.resolve("replay/2-b.json").readBytes(),
        )
        assertEquals(setOf("index.json", "replay"), root.list()!!.toSet())
        assertEquals(setOf("2-b.json"), root.resolve("replay").list()!!.toSet())
        assertEquals(Replay, store().loadReplay("2-b"))
    }

    @Test
    fun indexWrittenByThePreviousCodeIsReadAndExtendedAsIs() {
        // 고치기 전 코드가 남긴 파일을 손으로 적어 둔다 — 새 코드가 **그대로** 읽고 이어 써야 한다.
        root.mkdirs()
        indexFile.writeText(OldIndexJson)

        val loaded = store().loadAll().single()
        assertEquals("1000-1", loaded.id)
        assertEquals(1_000L, loaded.playedAtMillis)
        assertEquals(Ruleset.Chinese, loaded.ruleset)
        assertEquals(6.5, loaded.komi, 0.0)
        assertEquals(StoneColor.Black, loaded.winner)
        assertEquals(3.5, loaded.margin!!, 0.0)
        assertEquals("좋은 판", loaded.note)

        store().appendCompletedGame(entry("2-b"))

        assertEquals(listOf(loaded, entry("2-b")), store().loadAll())
    }

    private fun entry(id: String, hasReplay: Boolean = false) = GameHistoryEntry(
        id = id,
        playedAtMillis = id.substringBefore('-').toLong(),
        boardSize = 9,
        ruleset = Ruleset.Japanese,
        komi = 6.5,
        handicapCount = 0,
        playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        ),
        moveCount = 3,
        humanColor = StoneColor.Black,
        winner = StoneColor.White,
        isResign = true,
        margin = null,
        hasReplay = hasReplay,
    )

    private class DyingOutputStream(
        private val delegate: OutputStream,
        private val budgetFor: (Int) -> Int,
    ) : OutputStream() {
        private var written = 0

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            val allowed = (budgetFor(len) - written).coerceIn(0, len)
            delegate.write(b, off, allowed)
            written += allowed
            if (allowed < len) throw IOException("simulated death after $written bytes")
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }

    private companion object {
        val Replay = GameReplayData(
            moves = listOf(
                Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 3)),
                Move.Pass(StoneColor.White),
                Move.Resign(StoneColor.White),
            ),
        )

        val LegacyBlob = """
            {"schema":1,"entries":[{"id":"legacy-1","playedAtMillis":5,"boardSize":9,"ruleset":"Japanese",
            "komi":6.5,"handicapCount":0,"moveCount":10,"humanColor":"Black","result":"Win","margin":2.5}]}
        """.trimIndent()

        val OldIndexJson = """
            {"schema":1,"entries":[{"id":"1000-1","playedAtMillis":1000,"boardSize":9,"ruleset":"Chinese",
            "komi":6.5,"handicapCount":2,"playerSetup":{"black":{"controller":"Human","humanGameType":"Normal",
            "playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal",
            "playLevel":{"group":"FastBeginner","level":1}}},"moveCount":84,"humanColor":"Black",
            "winner":"Black","isResign":false,"margin":3.5,"hasReplay":false,"note":"좋은 판"}]}
        """.trimIndent()
    }
}
