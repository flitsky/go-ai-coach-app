package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMoveSource
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정적 국면 **위에 수를 둔 뒤**에도 분석이 서는가(refactor backlog #91).
 *
 * ## 무엇이 틀려 있었나
 * `syncStaticPosition` 뒤 다시 동기화하지 않고 `playMove`/`genMove`가 홀수 번 불리면,
 * [KataGoAnalysisContext.replayState]가 정적 국면의 돌 위에 수순을 **지금 둘 차례**(`nextPlayer`)부터
 * 접어 쌓았다. 첫 수를 둔 쪽은 그 반대 색이라 `BoardRules`가 던졌다 — 흑 차례 국면이면 *"Expected White,
 * got Black"*, 백 차례 국면이면 *"Expected Black, got White"*.
 * JSON 경로는 그 예외로 GTP로 폴백하지만, GTP 쪽 `fillFromPolicyIfNeeded`가 `replayState`를 또 불러
 * `analyze()`가 강등 없이 예외로 끝났다. JSON 쿼리의 `initialPlayer`도 같은 이유로 "지금 차례"였다 —
 * KataGo에게 이 값은 **`initialStones` 위에서 첫 수를 둘 차례**다.
 *
 * ## 지금 닿는가
 * 닿지 않는다. 앱의 동기화(`syncToGameState`)는 매번 국면을 새로 세우고, 정적 국면은 수순이 없을
 * 때만 보낸다. 오늘의 명령·쿼리가 그대로라는 것은 [KataGoProcessEngineAdapterSyncToGameStateGoldenTest]가
 * 고정한다. 이 파일은 정적 국면에 수를 두는 기능이 생기는 날의 함정을 막는다.
 */
class KataGoProcessEngineAdapterStaticPositionMovesTest {
    private val fake = FakeKataGoExecutable.create()
    private val adapter = KataGoProcessEngineAdapter(fake.processConfig)

    @After
    fun tearDown() {
        runBlocking { adapter.stop() }
        fake.close()
    }

    @Test
    fun oneMoveOnAWhiteToMoveStaticPositionStaysOnTheJsonPath() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncStaticPosition(WhiteToMove)
        adapter.playMove(play(StoneColor.White, "C7"))

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "static W + 1 move")
        assertEquals(WhiteToMove.initialStonePairs(), query.initialStones())
        assertEquals("the side to move on the static stones", "W", query.getString("initialPlayer"))
        assertEquals(listOf("W" to "C7"), query.moves())
        assertEquals(1, query.getJSONArray("analyzeTurns").getInt(0))
    }

    /** 백로그가 적은 메시지(*"Expected White, got Black"*)가 그대로 나오던 모양 — 흑 차례 정적 국면. */
    @Test
    fun oneMoveOnABlackToMoveStaticPositionStaysOnTheJsonPath() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncStaticPosition(BlackToMove)
        adapter.playMove(play(StoneColor.Black, "C7"))

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "static B + 1 move")
        assertEquals("B", query.getString("initialPlayer"))
        assertEquals(listOf("B" to "C7"), query.moves())
    }

    /**
     * GTP 경로(`includePolicy=false`)는 폴백이 아니라 **처음부터** `fillFromPolicyIfNeeded`를 탄다.
     * 거기서 `replayState`가 서야 합법 착점 보충 후보가 나온다 — 지금 둘 차례(백)의 것으로, 정적 국면의
     * 돌과 그 뒤에 둔 수 자리를 빼고.
     */
    @Test
    fun oneMoveOnAStaticPositionStillFillsLegalCandidatesOnTheGtpPath() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncStaticPosition(BlackToMove)
        adapter.playMove(play(StoneColor.Black, "A9"))

        val result = adapter.analyze(GtpPathLimit)

        val occupied = BlackToMove.stones.keys + coordinate("A9")
        assertEquals(GtpPathLimit.candidateCount, result.candidates.size)
        result.candidates.forEach { candidate ->
            val move = candidate.move as Move.Play
            assertEquals(CandidateMoveSource.LegalFallback, candidate.source)
            assertEquals(StoneColor.White, move.player)
            assertTrue("${move.coordinate.label(BoardSize.Nine)} is occupied", move.coordinate !in occupied)
        }
    }

    /** `genMove`도 같은 길이다 — 가짜 KataGo는 `genmove`에 패스로 답한다. */
    @Test
    fun aGeneratedMoveOnAStaticPositionStaysOnTheJsonPath() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncStaticPosition(WhiteToMove)
        adapter.genMove(StoneColor.White)

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "static W + genmove")
        assertEquals("W", query.getString("initialPlayer"))
        assertEquals(listOf("W" to "pass"), query.moves())
    }

    /** 홀수가 하나뿐인 게 아니다 — 세 수 뒤에도, 네 수에서 하나 물린 뒤에도 시작 차례는 정적 국면의 차례다. */
    @Test
    fun threeMovesAndAnUndoBackToThreeKeepTheStaticStartTurn() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncStaticPosition(WhiteToMove)
        val moves = listOf(
            play(StoneColor.White, "C7"),
            play(StoneColor.Black, "G3"),
            play(StoneColor.White, "C3"),
        )
        moves.forEach { move -> adapter.playMove(move) }

        val afterThree = adapter.analyze(JsonPathLimit)
        assertJsonAnalysisSucceeded(afterThree, "static W + 3 moves")
        assertEquals("W", fake.lastQuery().getString("initialPlayer"))

        adapter.playMove(play(StoneColor.Black, "G7"))
        adapter.undoMove()

        val afterUndo = adapter.analyze(JsonPathLimit)
        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(afterUndo, "static W + 4 moves - 1 undo")
        assertEquals("W", query.getString("initialPlayer"))
        assertEquals(moves.map { it.player.toGtpColor() to it.coordinate.label(BoardSize.Nine) }, query.moves())
    }

    /**
     * 짝수 수(패스 하나, 착수 하나) 뒤 — 지금 차례와 시작 차례가 같아 예전 코드도 여기서는 맞았다. 고친 뒤에도
     * 같은 쿼리여야 한다(홀수만 고치고 짝수를 흔들지 않았다는 반례).
     */
    @Test
    fun aPassAndAMoveOnAStaticPositionKeepTheStaticStartTurn() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncStaticPosition(WhiteToMove)
        adapter.playMove(Move.Pass(StoneColor.White))
        adapter.playMove(play(StoneColor.Black, "C7"))

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "static W + pass + 1 move")
        assertEquals("W", query.getString("initialPlayer"))
        assertEquals(listOf("W" to "pass", "B" to "C7"), query.moves())
        assertEquals(2, query.getJSONArray("analyzeTurns").getInt(0))
    }

    /**
     * 다시 동기화하면 시작 차례도 **새 국면의 것**이다 — 앞 국면(백 차례)에 둔 수와 시작 차례가 남지 않는다.
     * 그 위에 착수·패스·착수(홀수, 가운데 패스)를 둔다.
     */
    @Test
    fun aResyncedStaticPositionStartsFromItsOwnTurnThroughAMovePassMove() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncStaticPosition(WhiteToMove)
        adapter.playMove(play(StoneColor.White, "C7"))
        adapter.syncStaticPosition(BlackToMove)
        adapter.playMove(play(StoneColor.Black, "C7"))
        adapter.playMove(Move.Pass(StoneColor.White))
        adapter.playMove(play(StoneColor.Black, "G3"))

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "re-synced static B + move, pass, move")
        assertEquals(BlackToMove.initialStonePairs(), query.initialStones())
        assertEquals("B", query.getString("initialPlayer"))
        assertEquals(listOf("B" to "C7", "W" to "pass", "B" to "G3"), query.moves())
    }

    private fun assertJsonAnalysisSucceeded(result: AnalysisResult, label: String) {
        // 폴백이 붙었다면 JSON 경로가 중간에 무너졌다는 뜻이다(예: replayState 예외).
        assertNull("$label fell back to GTP: ${result.fallback}", result.fallback)
    }

    private companion object {
        val JsonPathLimit = AnalysisLimit(
            visits = 16,
            candidateCount = 5,
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        val GtpPathLimit = JsonPathLimit.copy(includePolicy = false)

        val WhiteToMove: GameState = GameState(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            stones = mapOf(
                coordinate("E5") to StoneColor.Black,
                coordinate("D4") to StoneColor.White,
                coordinate("F6") to StoneColor.Black,
            ),
            moves = emptyList(),
        )

        val BlackToMove: GameState = WhiteToMove.copy(nextPlayer = StoneColor.Black)

        fun coordinate(label: String): BoardCoordinate = BoardCoordinate.fromLabel(label, BoardSize.Nine)

        fun play(player: StoneColor, label: String): Move.Play = Move.Play(player, coordinate(label))

        fun GameState.initialStonePairs(): List<Pair<String, String>> =
            stones.map { (coordinate, color) -> color.toGtpColor() to coordinate.label(boardSize) }

        fun JSONObject.initialStones(): List<Pair<String, String>> = getJSONArray("initialStones").pairs()

        fun JSONObject.moves(): List<Pair<String, String>> = getJSONArray("moves").pairs()

        fun JSONArray.pairs(): List<Pair<String, String>> =
            (0 until length()).map { index ->
                val pair = getJSONArray(index)
                pair.getString(0) to pair.getString(1)
            }
    }
}
