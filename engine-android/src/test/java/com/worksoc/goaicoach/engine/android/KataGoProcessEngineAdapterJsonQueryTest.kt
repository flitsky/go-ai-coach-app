package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 로컬 JSON 분석 쿼리가 **접바둑 돌을 싣는가**(refactor backlog #65-B).
 *
 * ## 무엇이 틀려 있었나
 * 접바둑 대국은 `newGame(handicapCount = N)`으로 시작한다. GTP 쪽은 `set_free_handicap`으로
 * 돌을 놓지만, JSON 분석 쿼리의 `initialStones`는 `syncStaticPosition`만 채우는 필드라
 * **빈 채로** 나갔다. KataGo는 흑돌 N개가 빠진 판(빈 판 + `initialPlayer=W`)을 분석했고,
 * 실측으로 백 기준 +21~+67점이 어긋났다. 누락은 접바둑이 처음 생긴 aace70da(2026-07-16)부터다.
 *
 * ## 무엇을 고정하는가
 * 어댑터가 **실제로 KataGo analysis 프로세스에 써 넣은 쿼리**를 [FakeKataGoExecutable]로 가로채
 * 본다. 쿼리 팩토리만 따로 부르는 테스트로는 이 버그가 안 잡힌다 — 팩토리는 받은 대로 싣고,
 * 빠뜨린 쪽은 어댑터였다.
 *
 * ## 고친 자리가 `newGame`이 아닌 이유
 * `initialStones`는 "밖에서 받은 정적 국면"의 표지다 — 채워져 있으면 [KataGoAnalysisContext.replayState]와
 * 쿼리의 `initialPlayer`가 그 판을 정적 국면으로 읽고, 시작 차례로 `syncStaticPosition`이 적어 둔 차례를
 * 쓴다(refactor backlog #91 — 그 전에는 **지금 둘 차례**를 써서 홀수 수 뒤에 예외가 났다). `newGame`에서
 * 접바둑 돌을 `initialStones`에 넣으면 그 시작 차례가 접바둑의 백 차례라는 보장이 없어(기본값은 흑),
 * 첫 수(백)에서 `BoardRules`가 예외를 던진다. 접바둑 국면은 `GameStateReplayer`가 `handicapCount`로
 * 이미 제대로 복원한다. 그래서 돌은 **쿼리 끝단에서만** 보충한다.
 * [capturedHandicapStoneStaysInTheQueryAndTheReplayStillHolds]가 7수(홀수) 뒤를 보는 것도
 * 그 대안이 다시 들어오면 빨개지게 하려는 것이다.
 */
class KataGoProcessEngineAdapterJsonQueryTest {
    private val fake = FakeKataGoExecutable.create()
    private val adapter = KataGoProcessEngineAdapter(fake.processConfig)

    @After
    fun tearDown() {
        runBlocking { adapter.stop() }
        fake.close()
    }

    @Test
    fun handicapOpeningQueryCarriesEveryHandicapStoneAsBlackAndAsksWhiteToMove() = runBlocking {
        adapter.initialize(EngineProfile())
        val cases = listOf(
            Triple(BoardSize.Nine, 2, Ruleset.Chinese),
            Triple(BoardSize.Nine, 4, Ruleset.Japanese),
            Triple(BoardSize.Thirteen, 5, Ruleset.Japanese),
            Triple(BoardSize.Nineteen, 2, Ruleset.Japanese),
            Triple(BoardSize.Nineteen, 4, Ruleset.Chinese),
            Triple(BoardSize.Nineteen, 9, Ruleset.Chinese),
        )
        for ((boardSize, handicapCount, ruleset) in cases) {
            val label = "${boardSize.value}x${boardSize.value} H$handicapCount ${ruleset.name}"
            adapter.newGame(boardSize, ruleset, handicapCount, komi = 0.5)

            val result = adapter.analyze(JsonPathLimit)

            val query = fake.lastQuery()
            assertJsonAnalysisSucceeded(result, label)
            assertEquals(label, handicapStones(boardSize, handicapCount), query.initialStones())
            assertEquals(label, "W", query.getString("initialPlayer"))
            assertEquals(label, emptyList<Pair<String, String>>(), query.moves())
            assertEquals(label, 0, query.getJSONArray("analyzeTurns").getInt(0))
            assertEquals(label, ruleset.katagoName, query.getString("rules"))
        }
    }

    @Test
    fun handicapQueryKeepsTheHandicapStonesUnderThePlayedMoves() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 2, komi = 0.5)
        adapter.playMove(play(StoneColor.White, "E5"))
        adapter.playMove(play(StoneColor.Black, "E4"))

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "9x9 H2 after 2 moves")
        assertEquals(handicapStones(BoardSize.Nine, 2), query.initialStones())
        assertEquals("W", query.getString("initialPlayer"))
        assertEquals(listOf("W" to "E5", "B" to "E4"), query.moves())
        assertEquals(2, query.getJSONArray("analyzeTurns").getInt(0))
    }

    /**
     * 백이 접바둑 돌(G7)을 따낸 뒤. 쿼리의 `initialStones`에는 **따낸 돌까지 둘 다** 남아야 한다 —
     * KataGo가 수순을 다시 두며 스스로 따내고, 접바둑 보정 N은 **시작판**의 흑돌 수로 센다.
     * 지금 판에 남은 돌만 실으면 N이 1로 떨어져 중국식 보정이 0이 된다(원격 서버가 그렇게 틀린다).
     */
    @Test
    fun capturedHandicapStoneStaysInTheQueryAndTheReplayStillHolds() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.newGame(BoardSize.Nine, Ruleset.Chinese, handicapCount = 2, komi = 0.5)
        val moves = listOf(
            play(StoneColor.White, "G8"),
            play(StoneColor.Black, "E3"),
            play(StoneColor.White, "F7"),
            play(StoneColor.Black, "C5"),
            play(StoneColor.White, "H7"),
            play(StoneColor.Black, "D6"),
            play(StoneColor.White, "G6"), // G7을 따낸다
        )
        moves.forEach { move -> adapter.playMove(move) }

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "9x9 H2 after capturing G7")
        assertEquals(listOf("B" to "G7", "B" to "C3"), query.initialStones())
        assertEquals("W", query.getString("initialPlayer"))
        assertEquals(moves.map { it.player.toGtpColor() to it.coordinate.label(BoardSize.Nine) }, query.moves())
        assertEquals(7, query.getJSONArray("analyzeTurns").getInt(0))
    }

    @Test
    fun evenGameQueryStaysEmptyAndBlackToMove() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.newGame(BoardSize.Nineteen, Ruleset.Japanese, handicapCount = 0, komi = 6.5)

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "19x19 even")
        assertEquals(emptyList<Pair<String, String>>(), query.initialStones())
        assertEquals("B", query.getString("initialPlayer"))
    }

    /** 앞 대국의 접바둑 돌이 정적 국면(보드 스캔)으로 새어 들어가면 안 된다. */
    @Test
    fun staticPositionAfterAHandicapGameCarriesOnlyTheScannedStones() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 2, komi = 0.5)
        adapter.syncStaticPosition(ScannedPosition)

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "static after H2")
        assertEquals(ScannedPosition.initialStonePairs(), query.initialStones())
        assertEquals("W", query.getString("initialPlayer"))
    }

    /** 정적 국면 뒤에 새 접바둑 대국을 열면, 스캔한 돌은 사라지고 접바둑 돌만 남는다. */
    @Test
    fun handicapGameAfterAStaticPositionDropsTheScannedStones() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncStaticPosition(ScannedPosition)
        adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 2, komi = 0.5)

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "H2 after static")
        assertEquals(handicapStones(BoardSize.Nine, 2), query.initialStones())
        assertEquals("W", query.getString("initialPlayer"))
    }

    /**
     * `initialStones`가 이미 채워져 있으면 그것이 판 전체다 — `handicapCount`가 붙어 있어도
     * 접바둑 돌을 **다시 보태지 않는다**. 오늘 `syncToGameState`는 `handicapCount == 0`인 정적
     * 국면만 보내지만, 보충 조건이 "비어 있을 때만"이라는 것을 여기서 고정한다(보태면 따낸 자리에
     * 돌이 되살아난다).
     */
    @Test
    fun staticPositionThatCarriesAHandicapCountIsSentAsIs() = runBlocking {
        adapter.initialize(EngineProfile())
        val position = GameState(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Chinese,
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                BoardCoordinate.fromLabel("C3", BoardSize.Nine) to StoneColor.Black,
                BoardCoordinate.fromLabel("E5", BoardSize.Nine) to StoneColor.White,
            ),
            moves = emptyList(),
            handicapCount = 2,
        )
        adapter.syncStaticPosition(position)

        val result = adapter.analyze(JsonPathLimit)

        val query = fake.lastQuery()
        assertJsonAnalysisSucceeded(result, "static with handicapCount")
        assertEquals(position.initialStonePairs(), query.initialStones())
        assertEquals("B", query.getString("initialPlayer"))
    }

    private fun assertJsonAnalysisSucceeded(result: AnalysisResult, label: String) {
        // 폴백이 붙었다면 JSON 경로가 중간에 무너졌다는 뜻이다(예: replayState 예외).
        assertNull("$label fell back to GTP: ${result.fallback}", result.fallback)
    }

    private companion object {
        /** `analyze()`가 JSON 경로를 타게 하는 한도 — `includePolicy=true`. */
        val JsonPathLimit = AnalysisLimit(
            visits = 16,
            candidateCount = 5,
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        val ScannedPosition: GameState = GameState(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            stones = mapOf(
                BoardCoordinate.fromLabel("E5", BoardSize.Nine) to StoneColor.Black,
                BoardCoordinate.fromLabel("D4", BoardSize.Nine) to StoneColor.White,
                BoardCoordinate.fromLabel("F6", BoardSize.Nine) to StoneColor.Black,
            ),
            moves = emptyList(),
        )

        fun play(player: StoneColor, label: String): Move.Play =
            Move.Play(player, BoardCoordinate.fromLabel(label, BoardSize.Nine))

        fun handicapStones(boardSize: BoardSize, count: Int): List<Pair<String, String>> =
            boardSize.handicapStonePositions(count).map { coordinate -> "B" to coordinate.label(boardSize) }

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
