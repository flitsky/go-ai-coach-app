package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.humanmove.*

import com.worksoc.goaicoach.application.undo.*

import com.worksoc.goaicoach.application.startgame.*

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.score.*

import com.worksoc.goaicoach.application.topmoves.*
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GameSessionCoreStateTest {
    @Test
    fun applyGameSessionResetPlanRebuildsCoreDisplayState() {
        val reset = GameSessionResetPlan(
            gameState = GameState.empty(),
            candidateText = "Captured: Black 0 / White 0",
            reviewAnalysis = MoveAnalysisSnapshot.empty(GameState.empty()),
            scoreText = "No score estimate yet.",
            scoreSnapshots = listOf(localScoreSnapshot(GameState.empty())),
            moveReviewText = "No move review yet.",
            lastMoveText = "None",
            endgameLog = "No endgame result recorded.",
            engineMessage = "New game started.",
        )

        val next = baseCoreState(isGameEnded = true)
            .applyGameSessionResetPlan(reset)

        assertEquals(reset.gameState, next.gameState)
        assertFalse(next.isGameEnded)
        assertEquals("Captured: Black 0 / White 0", next.analysisState.candidateText)
        assertEquals("No score estimate yet.", next.scoreState.scoreText)
        assertNull(next.scoreState.scoreEstimate)
        assertEquals("No move review yet.", next.moveReviewState.moveReviewText)
        assertEquals("None", next.moveReviewState.lastMoveText)
        assertEquals(emptyList<MoveReviewMarker>(), next.moveReviewState.moveReviews)
        assertEquals(1L, next.runtimeState.sessionGeneration)
        assertEquals("New game started.", next.engineMessage)
    }

    @Test
    fun applyAutoAiTurnDisplayPlanUpdatesRuntimeAnalysisScoreAndMoveReviewTogether() {
        val afterMove = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val playLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 7)
        val profile = playLevel.toEngineProfile(EngineProfile(), SearchTimeSettings(b32Millis = 2_000L))
        val display = AutoAiTurnDisplayPlan(
            playLevel = playLevel,
            profile = profile,
            analysisPreset = playLevel.analysisPreset,
            gameState = afterMove,
            turnEngineMessage = "AI selected Black E5.",
            candidateText = "candidate reset",
            lastMoveText = "Black E5",
            scoreDisplay = ScoreEstimateDisplayPlan(
                scoreText = "Score updated.",
                scoreEstimate = null,
                scoreSnapshots = listOf(localScoreSnapshot(afterMove)),
                engineMessage = "Score estimate complete.",
            ),
            shouldResolveEndgame = false,
            endgamePrePassCandidates = emptyList(),
            nextAnalysisState = afterMove,
        )

        val next = baseCoreState().applyAutoAiTurnDisplayPlan(display)

        assertEquals(afterMove, next.gameState)
        assertEquals(playLevel, next.runtimeState.playLevel)
        assertEquals(profile, next.runtimeState.engineProfile)
        assertEquals(playLevel.analysisPreset, next.runtimeState.analysisPreset)
        assertEquals("candidate reset", next.analysisState.candidateText)
        assertFalse(next.analysisState.reviewAnalysis.hasEngineCandidates)
        assertEquals("Score updated.", next.scoreState.scoreText)
        assertEquals("Black E5", next.moveReviewState.lastMoveText)
        assertEquals("Score estimate complete.", next.engineMessage)
    }

    @Test
    fun applyAutoAiTurnFailureDisplayPlanKeepsBoardAndScoreState() {
        val before = baseCoreState(
            analysisState = GameSessionAnalysisState.empty(
                state = GameState.empty(),
                candidateText = "previous candidates",
            ),
            engineMessage = "previous engine message",
        )
        val failure = buildAutoAiTurnFailureDisplayPlan(IllegalStateException("engine unavailable"))

        val next = before.applyAutoAiTurnFailureDisplayPlan(failure)

        assertEquals(before.gameState, next.gameState)
        assertEquals(before.scoreState, next.scoreState)
        assertEquals("engine unavailable", next.engineMessage)
        assertEquals(
            "AI turn failed. Current board state was not changed.",
            next.analysisState.candidateText,
        )
    }

    @Test
    fun applyScoreEstimateFailureDisplayPlanClearsEstimateAndKeepsBoard() {
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 0.5,
            whiteWinRate = 0.55,
            summary = "estimate",
        )
        val snapshot = ScoreSnapshot(moveNumber = 2, source = ScoreSnapshotSource.EngineEstimate)
        val before = baseCoreState(
            scoreState = GameSessionScoreState(
                scoreText = "existing score",
                scoreEstimate = estimate,
                scoreSnapshots = listOf(snapshot),
                endgameLog = "existing log",
            ),
            engineMessage = "previous message",
        )

        val next = before.applyScoreEstimateFailureDisplayPlan(
            ScoreEstimateFailureDisplayPlan(engineMessage = "score failed"),
        )

        assertEquals(before.gameState, next.gameState)
        assertEquals("existing score", next.scoreState.scoreText)
        assertNull(next.scoreState.scoreEstimate)
        assertEquals(listOf(snapshot), next.scoreState.scoreSnapshots)
        assertEquals("existing log", next.scoreState.endgameLog)
        assertEquals("score failed", next.engineMessage)
    }

    @Test
    fun applyTopMoveAnalysisFailureDisplayPlanClearsReviewAndMessage() {
        val beforeState = GameState.empty()
        val targetState = beforeState.play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("D4", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val before = baseCoreState(
            gameState = beforeState,
            analysisState = GameSessionAnalysisState(
                candidateMoves = listOf(candidate),
                candidateText = "previous candidates",
                reviewAnalysis = MoveAnalysisSnapshot.from(beforeState, listOf(candidate)),
                reviewCandidateMoves = listOf(candidate),
                lastAnalysisKey = analysisKeyFor(
                    state = beforeState,
                    preset = AnalysisPreset.Lite,
                    limit = EngineProfile().analysisLimit,
                    deep = false,
                ),
            ),
            engineMessage = "previous message",
        )

        val next = before.applyTopMoveAnalysisFailureDisplayPlan(
            TopMoveAnalysisFailureDisplayPlan(
                targetState = targetState,
                engineMessage = "top moves failed",
                clearDisplayedTopMoves = true,
                candidateText = "Top Moves analysis failed.",
            ),
        )

        assertEquals(before.gameState, next.gameState)
        assertEquals(emptyList<CandidateMove>(), next.analysisState.candidateMoves)
        assertEquals("Top Moves analysis failed.", next.analysisState.candidateText)
        assertEquals(emptyList<CandidateMove>(), next.analysisState.reviewCandidateMoves)
        assertFalse(next.analysisState.reviewAnalysis.hasEngineCandidates)
        assertNull(next.analysisState.lastAnalysisKey)
        assertEquals("top moves failed", next.engineMessage)
    }

    @Test
    fun applyHumanMoveLocalResultClearsDisplayedTopMovesAndRefreshesReview() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val beforeMove = GameState.empty()
        val afterMove = beforeMove.play(Move.Play(StoneColor.Black, coordinate))
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.Black, coordinate),
            pointLoss = 0.0,
        )
        val marker = MoveReviewMarker(
            coordinate = coordinate,
            moveNumber = 1,
            tone = MoveReviewTone.Excellent,
        )
        val core = baseCoreState(
            gameState = beforeMove,
            analysisState = GameSessionAnalysisState(
                candidateMoves = listOf(candidate),
                candidateText = "candidate text",
                reviewAnalysis = MoveAnalysisSnapshot.from(beforeMove, listOf(candidate)),
                reviewCandidateMoves = listOf(candidate),
                lastAnalysisKey = analysisKeyFor(
                    state = beforeMove,
                    preset = AnalysisPreset.Lite,
                    limit = EngineProfile().analysisLimit,
                    deep = false,
                ),
            ),
        )
        val result = HumanMoveLocalResult(
            afterMove = afterMove,
            moveReview = MoveReviewResult(marker = marker, text = "Move review: E5 excellent."),
            moveReviews = listOf(marker),
            lastMoveText = "Black E5",
            capturedText = "Captured: Black 0 / White 0",
            localScoreSnapshot = localScoreSnapshot(afterMove),
            localFinalScore = null,
        )

        val next = core.applyHumanMoveLocalResult(result)

        assertEquals(afterMove, next.gameState)
        assertEquals(emptyList<CandidateMove>(), next.analysisState.candidateMoves)
        assertEquals("candidate text", next.analysisState.candidateText)
        assertFalse(next.analysisState.reviewAnalysis.hasEngineCandidates)
        assertNull(next.analysisState.lastAnalysisKey)
        assertEquals("Score estimate not current.", next.scoreState.scoreText)
        assertNull(next.scoreState.scoreEstimate)
        assertEquals("Move review: E5 excellent.", next.moveReviewState.moveReviewText)
        assertEquals(listOf(marker), next.moveReviewState.moveReviews)
        assertEquals("Black E5", next.moveReviewState.lastMoveText)
    }

    @Test
    fun applyUndoLocalStatePlanRewindsStateAndKeepsEngineMessage() {
        val marker = MoveReviewMarker(
            coordinate = BoardCoordinate.fromLabel("D4", BoardSize.Nine),
            moveNumber = 1,
            tone = MoveReviewTone.Good,
        )
        val undo = UndoLocalStatePlan(
            gameState = GameState.empty(),
            candidateText = "Undo cleared current Top Moves.",
            reviewAnalysis = MoveAnalysisSnapshot.empty(GameState.empty()),
            scoreText = "Score estimate not current.",
            scoreSnapshots = emptyList(),
            moveReviewText = "Move review cleared by undo.",
            moveReviews = listOf(marker),
            lastMoveText = "Black D4",
            endgameLog = "Endgame log cleared by undo.",
        )

        val next = baseCoreState(isGameEnded = true, engineMessage = "previous message")
            .applyUndoLocalStatePlan(undo)

        assertEquals(GameState.empty(), next.gameState)
        assertFalse(next.isGameEnded)
        assertEquals("Undo cleared current Top Moves.", next.analysisState.candidateText)
        assertEquals("Score estimate not current.", next.scoreState.scoreText)
        assertEquals("Move review cleared by undo.", next.moveReviewState.moveReviewText)
        assertEquals(listOf(marker), next.moveReviewState.moveReviews)
        assertEquals(1L, next.runtimeState.sessionGeneration)
        assertEquals("previous message", next.engineMessage)
    }

    @Test
    fun applyPlayerSetupChangePlanUpdatesRuntimeAndAnalysisTogether() {
        val playLevel = PlayLevelSetting(group = PlayLevelGroup.Intermediate, level = 5)
        val searchTimeSettings = SearchTimeSettings(b64Millis = 3_000L)
        val profile = playLevel.toEngineProfile(EngineProfile(), searchTimeSettings)
        val runtime = RuntimePlayLevelSelection(
            playLevel = playLevel,
            engineProfile = profile,
            analysisPreset = playLevel.analysisPreset,
            searchTimeSettings = searchTimeSettings,
        )
        val change = PlayerSetupChangePlan.Apply(
            playerSetup = PlayerSetup(),
            runtime = runtime,
            reviewAnalysis = MoveAnalysisSnapshot.empty(GameState.empty()),
            topMoveClearMessage = "Player setup changed. Analysis cache will rebuild.",
        )

        val next = baseCoreState().applyPlayerSetupChangePlan(change)

        assertEquals(playLevel, next.runtimeState.playLevel)
        assertEquals(profile, next.runtimeState.engineProfile)
        assertEquals(playLevel.analysisPreset, next.runtimeState.analysisPreset)
        assertEquals("Player setup changed. Analysis cache will rebuild.", next.analysisState.candidateText)
        assertFalse(next.analysisState.reviewAnalysis.hasEngineCandidates)
    }

    @Test
    fun applyHumanEngineSyncFailurePlanUpdatesScoreAnalysisAndMessage() {
        val snapshots = listOf(localScoreSnapshot(GameState.empty()))
        val failure = HumanEngineSyncFailurePlan(
            scoreSnapshots = snapshots,
            candidateText = "Captured: Black 0 / White 0",
            engineMessage = "Human move accepted, but engine sync failed.",
        )

        val next = baseCoreState().applyHumanEngineSyncFailurePlan(failure)

        assertEquals(snapshots, next.scoreState.scoreSnapshots)
        assertEquals("Captured: Black 0 / White 0", next.analysisState.candidateText)
        assertEquals("Human move accepted, but engine sync failed.", next.engineMessage)
    }

    private fun baseCoreState(
        gameState: GameState = GameState.empty(),
        isGameEnded: Boolean = false,
        analysisState: GameSessionAnalysisState = GameSessionAnalysisState.empty(gameState),
        scoreState: GameSessionScoreState = GameSessionScoreState.reset(
            scoreText = "No score estimate yet.",
            scoreSnapshots = listOf(localScoreSnapshot(gameState)),
            endgameLog = "No endgame result recorded.",
        ),
        runtimeState: GameSessionRuntimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
        ),
        moveReviewState: GameSessionMoveReviewState = GameSessionMoveReviewState.reset(
            moveReviewText = "No move review yet.",
            lastMoveText = "None",
        ),
        engineMessage: String = "Engine not initialized.",
    ): GameSessionCoreState =
        GameSessionCoreState(
            gameState = gameState,
            isGameEnded = isGameEnded,
            analysisState = analysisState,
            scoreState = scoreState,
            runtimeState = runtimeState,
            moveReviewState = moveReviewState,
            engineMessage = engineMessage,
        )
}
