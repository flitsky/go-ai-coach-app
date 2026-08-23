package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
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

private val HumanBlackVsAiWhite = PlayerSetup(
    black = SidePlayerSetup(controller = SeatController.Human),
    white = SidePlayerSetup(controller = SeatController.Ai),
)

private val HumanWhiteVsAiBlack = PlayerSetup(
    black = SidePlayerSetup(controller = SeatController.Ai),
    white = SidePlayerSetup(controller = SeatController.Human),
)

private fun gameStateWithMoves(moves: List<Move>) =
    GameState.empty(boardSize = BoardSize.Nine, ruleset = Ruleset.Chinese).copy(moves = moves)

private fun passMoves(count: Int) = List(count) { Move.Pass(StoneColor.Black) }

private fun judgement(winner: StoneColor?, margin: Double? = 3.5) =
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
    fun humanWinIsRecordedAsWin() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.Black),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(GameHistoryResult.Win, entry.result)
        assertEquals(StoneColor.Black, entry.humanColor)
        assertEquals(3.5, entry.margin)
    }

    @Test
    fun humanLossIsRecordedAsLoss() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.White),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(GameHistoryResult.Loss, entry.result)
    }

    @Test
    fun nullWinnerIsRecordedAsDraw() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = null, margin = null),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(GameHistoryResult.Draw, entry.result)
    }

    @Test
    fun resignationIsRecordedRegardlessOfFinalScoreJudgement() {
        // resignCurrentGameIfAllowed never sets finalScoreJudgement — this is the bug this
        // test guards against (game history staying empty after a resign).
        val store = FakeGameHistoryStore()
        val gameState = gameStateWithMoves(passMoves(10) + Move.Resign(StoneColor.White))

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = null,
            gameState = gameState,
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(GameHistoryResult.Resign, entry.result)
        assertNull(entry.margin)
    }

    @Test
    fun resignationByTheHumanIsAlsoJustRecordedAsResign() {
        // Whoever resigned, the result type is Resign — not distinguished (user request).
        val store = FakeGameHistoryStore()
        val gameState = gameStateWithMoves(passMoves(10) + Move.Resign(StoneColor.Black))

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = null,
            gameState = gameState,
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(GameHistoryResult.Resign, entry.result)
    }

    @Test
    fun humanPlayingWhiteIsRecordedFromWhitesPerspective() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.White),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanWhiteVsAiBlack,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(StoneColor.White, entry.humanColor)
        assertEquals(GameHistoryResult.Win, entry.result)
    }

    @Test
    fun humanVsHumanIsNotRecorded() {
        val store = FakeGameHistoryStore()
        val bothHuman = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.Black),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = bothHuman,
            nowMillis = 1_000L,
            store = store,
        )

        assertNull(entry)
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun aiVsAiIsNotRecorded() {
        val store = FakeGameHistoryStore()
        val bothAi = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.Black),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = bothAi,
            nowMillis = 1_000L,
            store = store,
        )

        assertNull(entry)
    }

    @Test
    fun unendedGameIsNotAppended() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = false,
            finalScoreJudgement = null,
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertNull(entry)
    }

    @Test
    fun endedGameWithoutAJudgementOrResignationIsNotAppended() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = null,
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertNull(entry)
    }

    @Test
    fun sameGameIsNotRecordedTwiceAcrossRepeatedEffectFirings() {
        val store = FakeGameHistoryStore()
        val gameState = gameStateWithMoves(passMoves(42))
        runGameHistoryAppendIfCompleted(true, judgement(StoneColor.Black), gameState, HumanBlackVsAiWhite, 1_000L, store)

        val second = runGameHistoryAppendIfCompleted(
            true, judgement(StoneColor.Black), gameState, HumanBlackVsAiWhite, 1_050L, store,
        )

        assertNull(second)
        assertEquals(1, store.loadAll().size)
    }

    @Test
    fun aDifferentSubsequentGameIsRecordedAsANewEntry() {
        val store = FakeGameHistoryStore()
        runGameHistoryAppendIfCompleted(
            true, judgement(StoneColor.Black), gameStateWithMoves(passMoves(42)), HumanBlackVsAiWhite, 1_000L, store,
        )

        val second = runGameHistoryAppendIfCompleted(
            true, judgement(StoneColor.White), gameStateWithMoves(passMoves(80)), HumanBlackVsAiWhite, 2_000L, store,
        )

        assertTrue(second != null)
        assertEquals(2, store.loadAll().size)
    }
}
