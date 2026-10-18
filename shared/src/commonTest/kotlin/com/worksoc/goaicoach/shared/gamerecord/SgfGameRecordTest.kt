package com.worksoc.goaicoach.shared.gamerecord

import com.worksoc.goaicoach.application.gamehistory.buildGameReplayTimeline
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** 백로그 #269 — 「기보 보기」가 여는 SGF를 본 수순 한 줄로 읽는다. */
class SgfGameRecordTest {
    private fun read(sgf: String): GameRecord = when (val result = readSgfGameRecord(sgf)) {
        is GameRecordRead.Read -> result.record
        is GameRecordRead.Unreadable -> fail("읽지 못했다: ${result.problem}")
    }

    private fun problemOf(sgf: String): GameRecordProblem? = (readSgfGameRecord(sgf) as? GameRecordRead.Unreadable)?.problem

    private fun play(color: StoneColor, row: Int, column: Int) = Move.Play(color, BoardCoordinate(row = row, column = column))

    @Test
    fun readsTheHeaderAndMovesWithTheDefaultsOfAnOldRecord() {
        // 1933 오청원–슈사이(CWI Shusai-681)의 첫 다섯 수 — 3·3, 화점, 천원.
        val record = read("(;GM[1]FF[4]PB[Go Seigen]BR[5d]PW[Honinbo Shusai]RE[W+2]DT[1933-10-16..1934-01-29];B[qc];W[cd];B[dp];W[pq];B[jj])")

        assertEquals(19, record.setup.boardSize.value, "SZ가 없으면 19줄")
        assertEquals(0.0, record.setup.komi, "덤이 없으면 0(옛 기보)")
        assertEquals(Ruleset.Japanese, record.setup.ruleset, "RU가 없으면 일본식")
        assertTrue(record.setup.setupStones.isEmpty())
        assertEquals(StoneColor.Black, record.setup.firstPlayer)
        assertEquals("Go Seigen", record.info.blackName)
        assertEquals("5d", record.info.blackRank)
        assertEquals("Honinbo Shusai", record.info.whiteName)
        assertEquals("W+2", record.info.result)
        // `qc` = 17번째 열(q), 3번째 행(c) — 오른쪽 위 3·3.
        assertEquals(play(StoneColor.Black, row = 2, column = 16), record.moves.first())
        assertEquals(play(StoneColor.Black, row = 9, column = 9), record.moves.last())
        assertEquals(5, record.moves.size)
    }

    @Test
    fun emptyAndTtAreAPass() {
        val record = read("(;SZ[19];B[pd];W[];B[tt])")
        assertEquals(listOf<Move>(play(StoneColor.Black, 3, 15), Move.Pass(StoneColor.White), Move.Pass(StoneColor.Black)), record.moves)
    }

    @Test
    fun onlyTheMainLineIsRead() {
        val record = read("(;SZ[9];B[ee](;W[cc];B[gg](;W[gc])(;W[cg]))(;W[gc]))")
        assertEquals(
            listOf<Move>(play(StoneColor.Black, 4, 4), play(StoneColor.White, 2, 2), play(StoneColor.Black, 6, 6), play(StoneColor.White, 2, 6)),
            record.moves,
        )
    }

    @Test
    fun setupStonesCarryTheHandicapAndTheRectangleShorthand() {
        val record = read("(;SZ[9]HA[2]AB[cc][gg]AW[aa:bb]PL[W];W[ee])")
        assertEquals(0, record.setup.handicapCount, "배치가 있으면 접바둑 돌도 배치로 싣는다")
        assertEquals(2, record.info.handicap)
        assertEquals(StoneColor.White, record.setup.firstPlayer)
        assertEquals(
            mapOf(
                BoardCoordinate(2, 2) to StoneColor.Black,
                BoardCoordinate(6, 6) to StoneColor.Black,
                BoardCoordinate(0, 0) to StoneColor.White,
                BoardCoordinate(0, 1) to StoneColor.White,
                BoardCoordinate(1, 0) to StoneColor.White,
                BoardCoordinate(1, 1) to StoneColor.White,
            ),
            record.setup.setupStones,
        )
    }

    @Test
    fun aHandicapWithoutSetupStonesUsesTheStarPoints() {
        val record = read("(;SZ[19]HA[3]KM[0.5];W[dd])")
        assertEquals(3, record.setup.handicapCount)
        assertEquals(StoneColor.White, record.setup.firstPlayer)
        assertEquals(0.5, record.setup.komi)
        val timeline = buildGameReplayTimeline(record.setup, record.moves)
        assertEquals(3 + 1, timeline.stateAt(timeline.lastMoveNumber).stones.size, "화점 셋 + 백의 첫 수")
    }

    @Test
    fun aSetupRecordReplaysFromItsStones() {
        // 도사쿠 「평생의 명국」(1684, 2점)처럼 접바둑 돌이 AB로 적힌 기보 — 시작 배치(#266) 위에서 판이 선다.
        val record = read("(;SZ[19]HA[2]AB[dp][pd];W[qp];B[oq];W[pn])")
        val timeline = buildGameReplayTimeline(record.setup, record.moves)
        assertFalse(timeline.isTruncated)
        assertEquals(2 + 3, timeline.stateAt(timeline.lastMoveNumber).stones.size)
    }

    @Test
    fun escapesAndSoftLineBreaksInText() {
        val record = read("(;PB[Lee \\] Changho]GN[first\\\nhalf];B[pd])")
        assertEquals("Lee ] Changho", record.info.blackName)
        assertEquals("firsthalf", record.info.gameName)
    }

    @Test
    fun oldLowercaseIdentifiersKeepOnlyTheirCapitals() {
        val record = read("(;SiZe[9]AddBlack[cc];White[ee])")
        assertEquals(9, record.setup.boardSize.value)
        assertEquals(StoneColor.Black, record.setup.setupStones[BoardCoordinate(2, 2)])
        assertEquals(listOf<Move>(play(StoneColor.White, 4, 4)), record.moves)
    }

    @Test
    fun rulesAndKomi() {
        assertEquals(Ruleset.Chinese, read("(;RU[Chinese]KM[7.5];B[pd])").setup.ruleset)
        assertEquals(Ruleset.Chinese, read("(;RU[Ing];B[pd])").setup.ruleset)
        assertEquals(Ruleset.Japanese, read("(;RU[Japanese];B[pd])").setup.ruleset)
        assertEquals(7.5, read("(;RU[Chinese]KM[7.5];B[pd])").setup.komi)
        assertNull(read("(;B[pd])").info.event)
    }

    @Test
    fun whatItCannotRead() {
        assertEquals(GameRecordProblem.NotSgf, problemOf("not a record"))
        assertEquals(GameRecordProblem.NotSgf, problemOf("(B[pd])"))
        assertEquals(GameRecordProblem.NotGo, problemOf("(;GM[2];B[pd])"))
        assertEquals(GameRecordProblem.UnsupportedBoardSize, problemOf("(;SZ[15];B[aa])"))
        assertEquals(GameRecordProblem.SetupAfterStart, problemOf("(;SZ[9];B[ee];AB[aa];W[cc])"))
        assertEquals(GameRecordProblem.BadPoint, problemOf("(;SZ[9];B[zz])"))
    }
}
