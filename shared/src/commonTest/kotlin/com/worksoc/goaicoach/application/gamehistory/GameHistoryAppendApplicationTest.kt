package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeGameHistoryStore : GameHistoryStorePort {
    private val entries = mutableListOf<GameHistoryEntry>()

    override fun appendCompletedGame(entry: GameHistoryEntry) {
        entries += entry
    }

    override fun loadAll(): List<GameHistoryEntry> = entries.toList()
}

private fun gameStateWithMoveCount(moveCount: Int) =
    GameState.empty(boardSize = BoardSize.Nine, ruleset = Ruleset.Chinese)
        .copy(moves = List(moveCount) { Move.Pass(StoneColor.Black) })

private fun judgement(winner: StoneColor? = StoneColor.Black, margin: Double? = 3.5) =
    FinalScoreJudgement(
        winner = winner,
        margin = margin,
        ruleset = Ruleset.Chinese,
        isEstimatedDisplay = false,
        removedBlack = 0,
        removedWhite = 0,
        blackArea = null,
        whiteAreaWithKomi = null,
        capturedByBlack = 0,
        capturedByWhite = 0,
        komi = 6.5,
    )

class GameHistoryAppendApplicationTest {
    @Test
    fun completedGameGetsAppended() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(),
            gameState = gameStateWithMoveCount(42),
            playerSetup = PlayerSetup(),
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(listOf(entry), store.loadAll())
    }

    @Test
    fun unendedGameIsNotAppended() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = false,
            finalScoreJudgement = null,
            gameState = gameStateWithMoveCount(42),
            playerSetup = PlayerSetup(),
            nowMillis = 1_000L,
            store = store,
        )

        assertNull(entry)
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun endedGameWithoutAJudgementIsNotAppended() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = null,
            gameState = gameStateWithMoveCount(42),
            playerSetup = PlayerSetup(),
            nowMillis = 1_000L,
            store = store,
        )

        assertNull(entry)
    }

    @Test
    fun sameGameIsNotRecordedTwiceAcrossRepeatedEffectFirings() {
        // LaunchedEffect가 관련 없는 이유로 재구성마다 여러 번 실행돼도, 저장소 자체를 근거로
        // 삼아 같은 대국을 중복 기록하지 않는다.
        val store = FakeGameHistoryStore()
        val gameState = gameStateWithMoveCount(42)
        runGameHistoryAppendIfCompleted(true, judgement(), gameState, PlayerSetup(), 1_000L, store)

        val second = runGameHistoryAppendIfCompleted(true, judgement(), gameState, PlayerSetup(), 1_050L, store)

        assertNull(second)
        assertEquals(1, store.loadAll().size)
    }

    @Test
    fun aDifferentSubsequentGameIsRecordedAsANewEntry() {
        val store = FakeGameHistoryStore()
        runGameHistoryAppendIfCompleted(true, judgement(), gameStateWithMoveCount(42), PlayerSetup(), 1_000L, store)

        val second = runGameHistoryAppendIfCompleted(
            true,
            judgement(winner = StoneColor.White, margin = 1.5),
            gameStateWithMoveCount(80),
            PlayerSetup(),
            2_000L,
            store,
        )

        assertTrue(second != null)
        assertEquals(2, store.loadAll().size)
    }
}
