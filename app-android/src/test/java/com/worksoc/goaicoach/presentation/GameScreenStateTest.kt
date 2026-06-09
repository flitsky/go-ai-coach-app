package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameScreenStateTest {
    @Test
    fun buildGameScreenStateExposesCurrentTurnAndDefaultUxOptions() {
        val gameState = GameState.empty(nextPlayer = StoneColor.White)
        val screenState = buildGameScreenState(defaultInput(gameState = gameState))

        assertEquals(StoneColor.White, screenState.nextPlayer)
        assertTrue(screenState.uxOptions.showCoordinates)
        assertTrue(screenState.uxOptions.showLastMoveRing)
        assertTrue(screenState.uxOptions.showOwnershipOverlay)
        assertFalse(screenState.uxOptions.showMoveNumbers)
        assertEquals(AutoPlayDelaySetting.Default, screenState.autoPlayDelaySetting)
        assertNull(screenState.resumePrompt)
    }

    @Test
    fun buildGameScreenStateShowsResumePromptOnlyAfterStartupAndIdle() {
        val resumableState = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val snapshot = SavedGameSnapshot(
            gameState = resumableState,
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 123L,
        )

        assertNull(
            buildGameScreenState(
                defaultInput(
                    pendingSavedSession = snapshot,
                    shouldShowResumePrompt = true,
                    hasCompletedEngineStartup = false,
                    isEngineBusy = false,
                ),
            ).resumePrompt,
        )
        assertNull(
            buildGameScreenState(
                defaultInput(
                    pendingSavedSession = snapshot,
                    shouldShowResumePrompt = true,
                    hasCompletedEngineStartup = true,
                    isEngineBusy = true,
                ),
            ).resumePrompt,
        )

        val visible = buildGameScreenState(
            defaultInput(
                pendingSavedSession = snapshot,
                shouldShowResumePrompt = true,
                hasCompletedEngineStartup = true,
                isEngineBusy = false,
            ),
        )

        assertEquals(snapshot, visible.resumePrompt?.snapshot)
    }

    @Test
    fun buildGameScreenStateBuildsDefaultActionButtonStates() {
        val screenState = buildGameScreenState(defaultInput())

        assertEquals(
            listOf(
                GameActionButtonRole.Pass,
                GameActionButtonRole.Undo,
                GameActionButtonRole.TopMoves,
                GameActionButtonRole.Eval,
            ),
            screenState.actionButtons.map { it.role },
        )
        assertTrue(screenState.actionButtons.first { it.role == GameActionButtonRole.Pass }.enabled)
        assertTrue(screenState.actionButtons.first { it.role == GameActionButtonRole.Pass }.isFilled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.Undo }.enabled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.TopMoves }.isFilled)
        assertTrue(screenState.actionButtons.first { it.role == GameActionButtonRole.Eval }.enabled)
    }

    @Test
    fun buildGameScreenStateKeepsTopMovesButtonActiveWhileBusyWhenToggleIsOn() {
        val screenState = buildGameScreenState(
            defaultInput(
                isEngineBusy = true,
                topMovesEnabled = true,
            ),
        )

        val topMoves = screenState.actionButtons.first { it.role == GameActionButtonRole.TopMoves }
        assertTrue(topMoves.enabled)
        assertTrue(topMoves.isFilled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.Pass }.enabled)
        assertFalse(screenState.actionButtons.first { it.role == GameActionButtonRole.Eval }.enabled)
    }

    private fun defaultInput(
        gameState: GameState = GameState.empty(),
        pendingSavedSession: SavedGameSnapshot? = null,
        shouldShowResumePrompt: Boolean = false,
        hasCompletedEngineStartup: Boolean = true,
        isEngineBusy: Boolean = false,
        topMovesEnabled: Boolean = false,
    ): GameScreenStateInput =
        GameScreenStateInput(
            gameState = gameState,
            matchMode = MatchMode.HumanVsAi,
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            playLevel = PlayLevelSetting(),
            uxOptions = KaTrainUxOptions(),
            engineName = "KataGo",
            engineDiagnostic = "ready",
            engineProfile = EngineProfile(),
            isEngineReady = true,
            isEngineBusy = isEngineBusy,
            engineMessage = "ready",
            analysisPreset = AnalysisPreset.Lite,
            analysisCacheStats = "entries=0, hits=0, misses=0",
            topMovesEnabled = topMovesEnabled,
            candidateMoves = emptyList(),
            candidateText = "none",
            reviewAnalysis = MoveAnalysisSnapshot.empty(gameState),
            reviewCandidateMoves = emptyList(),
            moveReviews = emptyList(),
            moveReviewText = "none",
            lastMoveText = "None",
            scoreText = "No score estimate yet.",
            scoreEstimate = null,
            scoreSnapshots = emptyList(),
            isScoreGraphExpanded = false,
            pendingSavedSession = pendingSavedSession,
            shouldShowResumePrompt = shouldShowResumePrompt,
            hasCompletedEngineStartup = hasCompletedEngineStartup,
            isGameEnded = false,
            endgameLog = "No endgame result recorded.",
        )
}
