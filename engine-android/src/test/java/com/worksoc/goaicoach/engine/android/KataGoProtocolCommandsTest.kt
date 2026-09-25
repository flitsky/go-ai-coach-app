package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KataGoProtocolCommandsTest {
    @Test
    fun buildsGtpSessionCommands() {
        assertEquals("boardsize 9", KataGoProtocolCommands.boardSize(BoardSize.Nine))
        assertEquals("komi 6.5", KataGoProtocolCommands.komi())
        assertEquals("kata-set-rules japanese", KataGoProtocolCommands.rules(Ruleset.Japanese))
        assertEquals("clear_board", KataGoProtocolCommands.clearBoard())
    }

    @Test
    fun buildsMoveCommands() {
        assertEquals(
            "play B E5",
            KataGoProtocolCommands.play(
                Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
                BoardSize.Nine,
            ),
        )
        assertEquals("play W pass", KataGoProtocolCommands.play(Move.Pass(StoneColor.White), BoardSize.Nine))
        assertEquals("genmove B", KataGoProtocolCommands.genMove(StoneColor.Black))
        assertEquals("undo", KataGoProtocolCommands.undo())
    }

    @Test
    fun buildsSearchCommandsWithAndWithoutTimeCap() {
        assertEquals(
            "kata-search_analyze B",
            KataGoProtocolCommands.searchAnalyze(
                StoneColor.Black,
                AnalysisLimit(visits = 16, timeMillis = null),
            ),
        )
        assertEquals(
            "kata-search_analyze W 25",
            KataGoProtocolCommands.searchAnalyze(
                StoneColor.White,
                AnalysisLimit(visits = 16, timeMillis = 250),
            ),
        )
        assertEquals("kata-set-param maxVisits 32", KataGoProtocolCommands.setMaxVisits(32))
        assertEquals("kata-set-param maxTime 0.5", KataGoProtocolCommands.setMaxTime(500))
        assertEquals("kata-set-param maxTime", KataGoProtocolCommands.clearMaxTime())
    }

    @Test
    fun clearsPreviousProcessGlobalTimeCapWhenLimitTurnsOff() {
        assertEquals(
            listOf(
                "kata-set-param maxVisits 32",
                "kata-set-param maxTime 3.0",
            ),
            KataGoProtocolCommands.searchLimitCommands(AnalysisLimit(visits = 32, timeMillis = 3_000L)),
        )
        assertEquals(
            listOf(
                "kata-set-param maxVisits 32",
                "kata-set-param maxTime",
            ),
            KataGoProtocolCommands.searchLimitCommands(AnalysisLimit(visits = 32, timeMillis = null)),
        )
    }

    /**
     * [toGtpMove]와 [toGtpMoveOrNull]은 같은 판정의 두 실패 의미다(refactor backlog #36) — 읽히는 토큰에서는
     * 같은 수, 못 읽는 토큰에서는 `null` 대 `BoardCoordinate.fromLabel`과 **같은 타입·같은 문구**의 예외.
     */
    @Test
    fun throwingAndNullableGtpMoveReadsAgreeAndTheThrowingOneKeepsFromLabelsException() {
        val tokens = listOf(
            "pass", "PASS", "Resign", "D4", "q16", "E05", "T19", "A1",
            "I5", "U1", "T0", "T20", "Q", "Qx", "passs", "", " pass", "resign ",
        )
        val boardSize = BoardSize.Nineteen

        for (token in tokens) {
            val nullable = token.toGtpMoveOrNull(StoneColor.Black, boardSize)
            if (nullable != null) {
                assertEquals(nullable, token.toGtpMove(StoneColor.Black, boardSize), "\"$token\"")
                continue
            }
            val expected = assertFailsWith<IllegalArgumentException>("\"$token\"") { BoardCoordinate.fromLabel(token, boardSize) }
            val thrown = assertFailsWith<IllegalArgumentException>("\"$token\"") { token.toGtpMove(StoneColor.Black, boardSize) }
            assertEquals(expected::class, thrown::class, "\"$token\"")
            assertEquals(expected.message, thrown.message, "\"$token\"")
        }
    }

    @Test
    fun buildsFinalAndMaintenanceCommands() {
        assertEquals("kata-raw-nn 0", KataGoProtocolCommands.rawNn())
        assertEquals("final_score", KataGoProtocolCommands.finalScore())
        assertEquals("final_status_list dead", KataGoProtocolCommands.finalStatusList("dead"))
        assertEquals("clear_cache", KataGoProtocolCommands.clearSearchCache())
        assertEquals("quit", KataGoProtocolCommands.quit())
    }
}
