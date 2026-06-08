package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
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
    fun gameScreenStateExposesCurrentTurnAndDefaultUxOptions() {
        val gameState = GameState.empty(nextPlayer = StoneColor.White)
        val screenState = GameScreenState(
            gameState = gameState,
            matchMode = MatchMode.HumanVsAi,
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            uxOptions = KaTrainUxOptions(),
            engine = EngineUiState(
                name = "KataGo",
                diagnostic = "ready",
                profile = EngineProfile(),
                isReady = true,
                isBusy = false,
                message = "ready",
            ),
            analysis = AnalysisUiState(
                preset = AnalysisPreset.Lite,
                cacheStats = "entries=0, hits=0, misses=0",
                topMovesEnabled = false,
                candidateMoves = emptyList(),
                candidateText = "none",
                reviewAnalysis = MoveAnalysisSnapshot.empty(gameState),
                reviewCandidateMoves = emptyList(),
                moveReviews = emptyList(),
                moveReviewText = "none",
                lastMoveText = "None",
            ),
            score = ScoreUiState(
                text = "No score estimate yet.",
                estimate = null,
                snapshots = emptyList(),
                isGraphExpanded = false,
            ),
            resumePrompt = null,
            isGameEnded = false,
            endgameLog = "No endgame result recorded.",
        )

        assertEquals(StoneColor.White, screenState.nextPlayer)
        assertTrue(screenState.uxOptions.showCoordinates)
        assertTrue(screenState.uxOptions.showLastMoveRing)
        assertFalse(screenState.uxOptions.showMoveNumbers)
        assertNull(screenState.resumePrompt)
    }
}
