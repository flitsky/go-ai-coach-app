package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchPolicyTest {
    @Test
    fun playerSetupDerivesAllMatchModes() {
        assertEquals(MatchMode.HumanVsAi, PlayerSetup().matchMode())
        assertEquals(
            MatchMode.AiVsHuman,
            PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Ai),
                white = SidePlayerSetup(controller = SeatController.Human),
            ).matchMode(),
        )
        assertEquals(
            MatchMode.AiVsAi,
            PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Ai),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ).matchMode(),
        )
        assertEquals(
            MatchMode.LocalTwoPlayer,
            PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(controller = SeatController.Human),
            ).matchMode(),
        )
    }

    @Test
    fun boardInputIsEnabledOnlyForCurrentHumanSeat() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        assertFalse(
            boardInputEnabled(
                playerSetup = setup,
                isEngineReady = true,
                isEngineBusy = false,
                nextPlayer = StoneColor.Black,
            ),
        )
        assertTrue(
            boardInputEnabled(
                playerSetup = setup,
                isEngineReady = true,
                isEngineBusy = false,
                nextPlayer = StoneColor.White,
            ),
        )
        assertFalse(
            boardInputEnabled(
                playerSetup = setup,
                isEngineReady = true,
                isEngineBusy = true,
                nextPlayer = StoneColor.White,
            ),
        )
    }

    @Test
    fun applyAiTurnSelectsCandidateForRequestedAiColor() = runBlocking {
        val blackMove = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))
        val adapter = FakeEngineAdapter(
            analysisCandidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D4", BoardSize.Nine)),
                    pointLoss = 0.0,
                ),
                CandidateMove(
                    move = blackMove,
                    pointLoss = 0.2,
                ),
            ),
        )

        val outcome = applyAiTurn(
            engineAdapter = adapter,
            currentState = GameState.empty(),
            aiPlayer = StoneColor.Black,
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3),
        )

        assertEquals(blackMove, outcome.gameState.moves.last())
        assertEquals(blackMove, adapter.playedMoves.last())
        assertFalse(adapter.genMoveCalled)
    }

    @Test
    fun passBestCandidateOverridesLowLevelRandomPlaySelection() = runBlocking {
        val humanMove = Move.Play(StoneColor.Black, BoardCoordinate(row = 4, column = 4))
        val stateAfterHuman = GameState.empty().play(humanMove)
        val pass = Move.Pass(StoneColor.White)
        val adapter = FakeEngineAdapter(
            analysisCandidates = listOf(
                CandidateMove(
                    move = pass,
                    pointLoss = 0.0,
                ),
                CandidateMove(
                    move = Move.Play(StoneColor.White, BoardCoordinate(row = 0, column = 0)),
                    pointLoss = 8.0,
                ),
            ),
        )

        val outcome = applyAiResponseAfterHumanTurn(
            engineAdapter = adapter,
            stateAfterHuman = stateAfterHuman,
            humanMove = humanMove,
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1),
        )

        assertEquals(pass, outcome.gameState.moves.last())
        assertEquals(pass, adapter.playedMoves.last())
        assertFalse(adapter.genMoveCalled)
        assertTrue(outcome.candidateText.contains("endgame pass override"))
    }

    @Test
    fun passIsNotSelectedWhenAPlayMoveIsBest() = runBlocking {
        val humanMove = Move.Play(StoneColor.Black, BoardCoordinate(row = 4, column = 4))
        val stateAfterHuman = GameState.empty().play(humanMove)
        val bestPlay = Move.Play(StoneColor.White, BoardCoordinate(row = 0, column = 0))
        val adapter = FakeEngineAdapter(
            analysisCandidates = listOf(
                CandidateMove(
                    move = bestPlay,
                    pointLoss = 0.0,
                ),
                CandidateMove(
                    move = Move.Pass(StoneColor.White),
                    pointLoss = 5.0,
                ),
            ),
        )

        val outcome = applyAiResponseAfterHumanTurn(
            engineAdapter = adapter,
            stateAfterHuman = stateAfterHuman,
            humanMove = humanMove,
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1),
        )

        assertEquals(bestPlay, outcome.gameState.moves.last())
        assertEquals(bestPlay, adapter.playedMoves.last())
        assertFalse(adapter.genMoveCalled)
    }

    @Test
    fun bestOnlySelectionRespectsEngineOrderBeforePointLoss() = runBlocking {
        val humanMove = Move.Play(StoneColor.Black, BoardCoordinate(row = 4, column = 4))
        val stateAfterHuman = GameState.empty().play(humanMove)
        val engineFirst = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("F5", BoardSize.Nine))
        val lowerPointLoss = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("B3", BoardSize.Nine))
        val adapter = FakeEngineAdapter(
            analysisCandidates = listOf(
                CandidateMove(
                    move = engineFirst,
                    pointLoss = 0.3,
                ),
                CandidateMove(
                    move = lowerPointLoss,
                    pointLoss = 0.0,
                ),
            ),
        )

        val outcome = applyAiResponseAfterHumanTurn(
            engineAdapter = adapter,
            stateAfterHuman = stateAfterHuman,
            humanMove = humanMove,
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3),
        )

        assertEquals(engineFirst, outcome.gameState.moves.last())
        assertEquals(engineFirst, adapter.playedMoves.last())
        assertFalse(adapter.genMoveCalled)
        assertTrue(outcome.candidateText.contains("rank 1/2"))
    }

    @Test
    fun aiTurnUsesPlayLevelSpecificAnalysisBudget() = runBlocking {
        val blackMove = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))
        val adapter = FakeEngineAdapter(
            analysisCandidates = listOf(
                CandidateMove(
                    move = blackMove,
                    pointLoss = 0.0,
                ),
            ),
        )

        applyAiTurn(
            engineAdapter = adapter,
            currentState = GameState.empty(),
            aiPlayer = StoneColor.Black,
            playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7),
        )

        assertEquals(32, adapter.analysisLimits.single().visits)
        assertEquals(500L, adapter.analysisLimits.single().timeMillis)
        assertEquals(16, adapter.analysisLimits.single().candidateCount)
        assertFalse(adapter.analysisLimits.single().includePolicy)
        assertEquals(0, adapter.analysisLimits.single().refinePolicyMoves)
        assertEquals(0, adapter.analysisLimits.single().minVisitsPerCandidate)
        assertEquals(null, adapter.analysisLimits.single().minTimeMillis)
    }

    @Test
    fun intermediateBestOnlyAiTurnUsesFastPlayBudgetWithoutTopMovesRefinement() = runBlocking {
        val blackMove = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))
        val adapter = FakeEngineAdapter(
            analysisCandidates = listOf(
                CandidateMove(
                    move = blackMove,
                    pointLoss = 0.0,
                ),
            ),
        )

        applyAiTurn(
            engineAdapter = adapter,
            currentState = GameState.empty(),
            aiPlayer = StoneColor.Black,
            playLevel = PlayLevelSetting(PlayLevelGroup.Intermediate, level = 5),
        )

        assertEquals(64, adapter.analysisLimits.single().visits)
        assertEquals(500L, adapter.analysisLimits.single().timeMillis)
        assertEquals(20, adapter.analysisLimits.single().candidateCount)
        assertFalse(adapter.analysisLimits.single().includePolicy)
        assertEquals(0, adapter.analysisLimits.single().refinePolicyMoves)
        assertEquals(0, adapter.analysisLimits.single().minVisitsPerCandidate)
        assertEquals(null, adapter.analysisLimits.single().minTimeMillis)
    }
}

private class FakeEngineAdapter(
    private val analysisCandidates: List<CandidateMove>,
) : EngineAdapter {
    val playedMoves = mutableListOf<Move>()
    val analysisLimits = mutableListOf<AnalysisLimit>()
    var genMoveCalled = false

    override suspend fun initialize(profile: EngineProfile): EngineStatus =
        EngineStatus.ready("initialized")

    override suspend fun configure(profile: EngineProfile): EngineStatus =
        EngineStatus.ready("configured")

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStatus =
        EngineStatus.ready("new game")

    override suspend fun playMove(move: Move): EngineStatus {
        playedMoves += move
        return EngineStatus.ready("played")
    }

    override suspend fun genMove(player: StoneColor): MoveResult {
        genMoveCalled = true
        val move = Move.Pass(player)
        return MoveResult(
            status = EngineStatus.ready("generated"),
            move = move,
            summary = "fallback generated ${player.label} pass",
        )
    }

    override suspend fun undoMove(): EngineStatus =
        EngineStatus.ready("undone")

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        analysisLimits += limit
        return AnalysisResult(
            status = EngineStatus.ready("analyzed"),
            candidates = analysisCandidates,
            summary = "fake analysis",
        )
    }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate =
        ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            summary = "fake estimate",
        )

    override suspend fun deadStones(): DeadStonesResult =
        DeadStonesResult(
            status = EngineStatus.ready("dead stones"),
            coordinates = emptyList(),
            summary = "fake dead stones",
        )

    override suspend fun scoreFinal(): FinalScoreResult =
        FinalScoreResult(
            status = EngineStatus.ready("final"),
            rawScore = "0",
            summary = "fake final",
        )

    override suspend fun stop(): EngineStatus =
        EngineStatus.stopped("stopped")
}
