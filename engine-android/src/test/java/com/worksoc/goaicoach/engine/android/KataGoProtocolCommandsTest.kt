package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals

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
