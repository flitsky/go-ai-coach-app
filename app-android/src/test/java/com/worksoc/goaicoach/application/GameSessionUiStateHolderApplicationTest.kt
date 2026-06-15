package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.score.*

import com.worksoc.goaicoach.application.topmoves.*
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionUiStateHolderApplicationTest {
    @Test
    fun holderAppliesScoreEstimateWithoutOwningPlatformState() {
        var core = baseCoreState()
        val holder = GameSessionUiStateHolder(
            currentCoreState = { core },
            applyCoreState = { next -> core = next },
        )
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = -1.5,
            whiteWinRate = 0.25,
            summary = "estimate",
        )
        val display = buildEngineEstimateDisplayPlan(
            state = core.gameState,
            estimate = estimate,
            previousSnapshots = core.scoreState.scoreSnapshots,
        )

        holder.applyScoreEstimateDisplayPlan(display)

        assertEquals(estimate, core.scoreState.scoreEstimate)
        assertEquals("estimated", core.engineMessage)
        assertEquals(ScoreSnapshotSource.EngineEstimate, core.scoreState.scoreSnapshots.last().source)
    }

    @Test
    fun holderAppliesFinalScoreAndUndoPlans() {
        val blackMove = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))
        val whiteMove = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine))
        val state = GameState.empty()
            .play(blackMove)
            .play(whiteMove)
        var core = baseCoreState(gameState = state)
        val holder = GameSessionUiStateHolder(
            currentCoreState = { core },
            applyCoreState = { next -> core = next },
        )
        val final = buildLocalFinalScoreDisplayPlan(
            source = "test-final",
            state = state,
            finalScore = FinalScoreResult(
                status = EngineStatus.ready("B+0.5"),
                rawScore = "B+0.5",
                winner = StoneColor.Black,
                margin = 0.5,
                summary = "final",
            ),
            previousSnapshots = core.scoreState.scoreSnapshots,
            detail = "test",
            engineMessage = "final complete",
            candidateText = "ended",
        )

        holder.applyFinalScoreDisplayPlan(final)

        assertTrue(core.isGameEnded)
        assertEquals("final complete", core.engineMessage)

        val undo = buildLocalTwoPlayerUndoPlan(
            currentState = core.gameState,
            scoreSnapshots = core.scoreState.scoreSnapshots,
        )
        holder.applyUndoLocalStatePlan(undo)

        assertFalse(core.isGameEnded)
        assertEquals(1, core.gameState.moves.size)
        assertEquals("Black E5", core.moveReviewState.lastMoveText)
        assertNull(core.scoreState.scoreEstimate)
    }

    @Test
    fun holderAppliesAnalysisAndAiFailurePlans() {
        val state = GameState.empty()
        var core = baseCoreState(gameState = state)
        val holder = GameSessionUiStateHolder(
            currentCoreState = { core },
            applyCoreState = { next -> core = next },
        )

        holder.applyTopMoveAnalysisFailureDisplayPlan(
            TopMoveAnalysisFailureDisplayPlan(
                targetState = state,
                engineMessage = "top moves failed",
                candidateText = "analysis failed",
                clearDisplayedTopMoves = true,
            ),
        )
        holder.applyAutoAiTurnFailureDisplayPlan(
            AutoAiTurnFailureDisplayPlan(
                engineMessage = "ai failed",
                candidateText = "ai candidate failed",
            ),
        )
        holder.applyHumanEngineSyncFailurePlan(
            HumanEngineSyncFailurePlan(
                scoreSnapshots = core.scoreState.scoreSnapshots,
                candidateText = "human sync failed",
                engineMessage = "human failed",
            ),
        )

        assertEquals("human failed", core.engineMessage)
        assertEquals("human sync failed", core.analysisState.candidateText)
    }

    @Test
    fun holderAppliesEngineStartupDisplayPlan() {
        val state = GameState.empty()
        var core = baseCoreState(gameState = state)
        val holder = GameSessionUiStateHolder(
            currentCoreState = { core },
            applyCoreState = { next -> core = next },
        )
        val snapshot = localScoreSnapshot(state)

        holder.applyEngineStartupDisplayPlan(
            EngineStartupDisplayPlan(
                isEngineReady = true,
                scoreSnapshots = listOf(snapshot),
                engineMessage = "Engine ready.",
                candidateText = "Startup candidate text.",
            ),
        )

        assertEquals("Engine ready.", core.engineMessage)
        assertEquals("Startup candidate text.", core.analysisState.candidateText)
        assertEquals(listOf(snapshot), core.scoreState.scoreSnapshots)
    }

    @Test
    fun holderAppliesHumanMoveLocalResult() {
        val move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))
        var core = baseCoreState()
        val holder = GameSessionUiStateHolder(
            currentCoreState = { core },
            applyCoreState = { next -> core = next },
        )
        val localMove = applyHumanMoveLocally(
            beforeMove = core.gameState,
            move = move,
            reviewAnalysis = MoveAnalysisSnapshot.empty(core.gameState),
            previousMoveReviews = emptyList(),
        ).getOrThrow()

        holder.applyHumanMoveLocalResult(localMove)

        assertEquals(1, core.gameState.moves.size)
        assertEquals("Black E5", core.moveReviewState.lastMoveText)
        assertEquals("Score estimate not current.", core.scoreState.scoreText)
        assertNull(core.analysisState.lastAnalysisKey)
    }

    private fun baseCoreState(
        gameState: GameState = GameState.empty(),
    ): GameSessionCoreState =
        GameSessionCoreState(
            gameState = gameState,
            isGameEnded = false,
            analysisState = GameSessionAnalysisState.empty(gameState),
            scoreState = GameSessionScoreState.reset(
                scoreText = "No score estimate yet.",
                scoreSnapshots = listOf(localScoreSnapshot(gameState)),
                endgameLog = "No endgame result recorded.",
            ),
            runtimeState = GameSessionRuntimeState(
                playLevel = PlayLevelSetting(),
                engineProfile = EngineProfile(),
                analysisPreset = AnalysisPreset.Lite,
            ),
            moveReviewState = GameSessionMoveReviewState.reset(
                moveReviewText = "No move review yet.",
                lastMoveText = "None",
            ),
            engineMessage = "Engine not initialized.",
        )
}
