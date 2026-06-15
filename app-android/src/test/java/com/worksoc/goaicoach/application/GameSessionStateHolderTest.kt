package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.session.GameSessionStateHolder
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GameSessionStateHolderTest {
    @Test
    fun exposesInitialStateSynchronouslyAndAsFlow() {
        val initial = controllerState()
        val holder = GameSessionStateHolder(initial)

        assertSame(initial, holder.current)
        assertSame(initial, holder.state.value)
    }

    @Test
    fun updateReplacesWholeStateAndIsVisibleSynchronously() {
        val holder = GameSessionStateHolder(controllerState())

        holder.update { it.withSettings(it.settings.copy(topMovesEnabled = true)) }

        // Read-after-write: current must reflect the update immediately.
        assertEquals(true, holder.current.settings.topMovesEnabled)
        assertEquals(true, holder.state.value.settings.topMovesEnabled)
    }

    @Test
    fun updateCoreChangesOnlyCoreSliceAndPreservesSiblings() {
        val initial = controllerState()
        val holder = GameSessionStateHolder(initial)

        holder.updateCore { it.copy(engineMessage = "Engine ready.") }

        val next = holder.current
        assertEquals("Engine ready.", next.engineMessage)
        // Sibling slices are untouched (same references).
        assertSame(initial.settings, next.settings)
        assertSame(initial.benchmark, next.benchmark)
        assertSame(initial.savedSession, next.savedSession)
        assertSame(initial.autoAiTurn, next.autoAiTurn)
        assertSame(initial.positionCacheOptimization, next.positionCacheOptimization)
    }

    @Test
    fun successiveUpdatesComposeOnLatestValue() {
        val holder = GameSessionStateHolder(controllerState())

        holder.updateCore { it.copy(engineMessage = "first") }
        holder.updateCore { it.copy(engineMessage = it.engineMessage + "-second") }

        assertEquals("first-second", holder.current.engineMessage)
    }

    private fun controllerState(): GameSessionControllerState =
        GameSessionControllerState(
            core = coreState(),
            settings = GameSessionSettingsState(
                playerSetup = PlayerSetup(),
                autoPlayDelaySetting = AutoPlayDelaySetting.Default,
                searchTimeSettings = SearchTimeSettings(),
                topMovesEnabled = false,
            ),
            benchmark = EngineBenchmarkUiState.initial(
                benchmarkText = "No benchmark yet.",
                profile = null,
            ),
            savedSession = SavedSessionUiState(),
            autoAiTurn = AutoAiTurnUiState(),
            positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
        )

    private fun coreState(): GameSessionCoreState {
        val gameState = GameState.empty()
        return GameSessionCoreState(
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
}
