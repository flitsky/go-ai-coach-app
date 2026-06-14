package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KataGoJsonAnalysisQueryFactoryTest {
    @Test
    fun buildsPositionAnalysisQueryForCurrentTurn() {
        val query = KataGoJsonAnalysisQueryFactory.build(
            id = "query-1",
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            playedMoves = listOf(
                Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
                Move.Pass(StoneColor.White),
            ),
            limit = AnalysisLimit(
                visits = 32,
                timeMillis = 2_000,
                includePolicy = true,
            ),
        )

        assertEquals("query-1", query.getString("id"))
        assertEquals("japanese", query.getString("rules"))
        assertEquals(9, query.getInt("boardXSize"))
        assertEquals(32, query.getInt("maxVisits"))
        assertTrue(query.getBoolean("includePolicy"))
        assertEquals(2.0, query.getJSONObject("overrideSettings").getDouble("maxTime"))

        val moves = query.getJSONArray("moves")
        assertEquals("B", moves.getJSONArray(0).getString(0))
        assertEquals("E5", moves.getJSONArray(0).getString(1))
        assertEquals("W", moves.getJSONArray(1).getString(0))
        assertEquals("pass", moves.getJSONArray(1).getString(1))
        assertEquals(2, query.getJSONArray("analyzeTurns").getInt(0))
    }

    @Test
    fun appendsRefineMoveAndCanDisablePolicy() {
        val refineMove = Move.Play(
            StoneColor.Black,
            BoardCoordinate.fromLabel("D4", BoardSize.Nine),
        )

        val query = KataGoJsonAnalysisQueryFactory.build(
            id = "query-2",
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Chinese,
            playedMoves = emptyList(),
            limit = AnalysisLimit(
                visits = 8,
                timeMillis = null,
                includePolicy = true,
            ),
            refineMove = refineMove,
            includePolicyOverride = false,
        )

        assertEquals("chinese", query.getString("rules"))
        assertFalse(query.getBoolean("includePolicy"))
        assertFalse(query.getJSONObject("overrideSettings").has("maxTime"))
        assertEquals("B", query.getJSONArray("moves").getJSONArray(0).getString(0))
        assertEquals("D4", query.getJSONArray("moves").getJSONArray(0).getString(1))
        assertEquals(1, query.getJSONArray("analyzeTurns").getInt(0))
    }
}
