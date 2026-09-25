package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KataGoJsonAnalysisQueryFactoryTest {
    /**
     * 쿼리 id는 **절대 겹치면 안 된다**(refactor backlog #16ⓑ). 응답을 id로 짝지어 찾기
     * 때문에, 같은 id 둘은 한쪽의 응답을 다른 쪽이 받아 가는 형태로 조용히 틀린다.
     * 여기서는 동시성까지 밀어 넣어 확인한다 — 예전의 비원자적 `Int++`가 지던 자리다.
     */
    @Test
    fun queryIdsNeverRepeatEvenWhenGeneratedFromManyThreadsAtOnce() {
        val threads = 8
        val perThread = 500
        val ids = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val start = java.util.concurrent.CountDownLatch(1)
        val workers = (1..threads).map {
            Thread {
                start.await()
                repeat(perThread) {
                    ids += KataGoJsonAnalysisQueryFactory.nextQueryId(
                        boardSize = BoardSize.Nine,
                        playedMoves = emptyList(),
                    )
                }
            }.also { worker -> worker.start() }
        }
        start.countDown()
        workers.forEach { worker -> worker.join() }

        assertEquals(threads * perThread, ids.size)
    }

    /** 로그에서 **어느 국면의 쿼리였는지** 읽혀야 한다 — 예전 id는 일련번호뿐이었다. */
    @Test
    fun queryIdReadsBackThePositionItWasBuiltFor() {
        val playedMoves = listOf(
            Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            Move.Pass(StoneColor.White),
        )

        val plain = KataGoJsonAnalysisQueryFactory.nextQueryId(
            boardSize = BoardSize.Nine,
            playedMoves = playedMoves,
        )
        val refined = KataGoJsonAnalysisQueryFactory.nextQueryId(
            boardSize = BoardSize.Nine,
            playedMoves = playedMoves,
            refineMove = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("C3", BoardSize.Nine)),
        )

        assertTrue(plain.endsWith("-9x9-m2"), plain)
        assertTrue(refined.endsWith("-9x9-m3-rC3"), refined)
        assertTrue(plain.startsWith("go-ai-coach-analysis-"))
    }

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

    @Test
    fun buildsPositionAnalysisQueryForStaticPositionWithInitialStones() {
        val initialStones = listOf(
            StoneColor.Black to BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            StoneColor.White to BoardCoordinate.fromLabel("D4", BoardSize.Nine),
        )

        val query = KataGoJsonAnalysisQueryFactory.build(
            id = "query-static",
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            playedMoves = emptyList(),
            limit = AnalysisLimit(
                visits = 64,
                includePolicy = true,
            ),
            initialStones = initialStones,
            initialPlayer = StoneColor.White,
        )

        assertEquals("query-static", query.getString("id"))
        assertEquals("W", query.getString("initialPlayer"))
        val stonesJson = query.getJSONArray("initialStones")
        assertEquals(2, stonesJson.length())
        assertEquals("B", stonesJson.getJSONArray(0).getString(0))
        assertEquals("E5", stonesJson.getJSONArray(0).getString(1))
        assertEquals("W", stonesJson.getJSONArray(1).getString(0))
        assertEquals("D4", stonesJson.getJSONArray(1).getString(1))
        assertEquals(0, query.getJSONArray("moves").length())
        assertEquals(0, query.getJSONArray("analyzeTurns").getInt(0))
    }
}
