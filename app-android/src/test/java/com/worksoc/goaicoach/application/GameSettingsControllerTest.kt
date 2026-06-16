package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.*
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.EngineProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsControllerTest {
    @Test
    fun changeSearchTimeSettingsAppliesSettingsWhenEngineIsNotBusy() {
        var appliedSettings: SearchTimeSettings? = null
        var playLevelSelection: RuntimePlayLevelSelection? = null
        var analysisStateUpdated = false
        var clearedQuietWindow = false
        var engineMessage: String? = null

        val controller = GameSettingsController(
            currentGameState = { GameState.empty() },
            currentPlayerSetup = { PlayerSetup() },
            currentEngineProfile = { EngineProfile() },
            currentSearchTimeSettings = { SearchTimeSettings() },
            currentAnalysisState = { GameSessionAnalysisState.empty(GameState.empty()) },
            currentAutoPlayDelaySetting = { AutoPlayDelaySetting.Default },
            defaultPlayLevel = PlayLevelSetting(),
            isEngineBusy = { false },
            runtimeEventLog = ControllerFakeRuntimeEventLogPort(),
            currentRuntimeLogContext = {
                RuntimeLogContext(
                    engineName = "KataGo",
                    engineDiagnostic = "ok",
                    playerSetup = PlayerSetup(),
                    gameState = GameState.empty(),
                    runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.AnalysisPreset.Lite),
                    autoPlayDelaySetting = AutoPlayDelaySetting.Default,
                    searchTimeSettings = SearchTimeSettings(),
                    topMovesEnabled = true,
                    isEngineReady = true,
                    isEngineBusy = false,
                    isGameEnded = false,
                    isAutoAiTurnPending = false,
                    shouldShowResumePrompt = false,
                    analysisCacheStats = "entries=0",
                    moveAnalysisCoverage = "none",
                    scoreText = "0",
                )
            },
            onEngineMessage = { msg -> engineMessage = msg },
            applyPlayerSetup = {},
            applyCoreSessionState = {},
            currentCoreSessionState = {
                GameSessionCoreState(
                    gameState = GameState.empty(),
                    isGameEnded = false,
                    analysisState = GameSessionAnalysisState.empty(GameState.empty()),
                    scoreState = GameSessionScoreState.reset("0", emptyList(), ""),
                    runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.AnalysisPreset.Lite),
                    moveReviewState = GameSessionMoveReviewState.reset("", ""),
                    engineMessage = ""
                )
            },
            applyRuntimePlayLevelSelection = { selection -> playLevelSelection = selection },
            applyAnalysisState = { analysisStateUpdated = true },
            applySettingsAutoPlayDelay = {},
            applySettingsSearchTimeSettings = { settings -> appliedSettings = settings },
            clearUndoEngineInterventionQuietWindow = { clearedQuietWindow = true }
        )

        val nextSettings = SearchTimeSettings(b16Millis = 2000L)
        controller.changeSearchTimeSettings(nextSettings)

        // Verify applied search time settings
        assertNotNull(appliedSettings)
        assertEquals(2000L, appliedSettings?.b16Millis)
        assertTrue(clearedQuietWindow)
        assertNotNull(playLevelSelection)
        assertTrue(analysisStateUpdated)
        assertNull(engineMessage)
    }

    @Test
    fun changeSearchTimeSettingsAppliesEvenWhenEngineIsBusy() {
        // Engine-busy gate is intentionally disabled so settings apply immediately.
        // To restore the block-when-busy behaviour, re-enable the gate in GameSettingsController
        // and revert this test to assert assertNull(appliedSettings) + busy message.
        var appliedSettings: SearchTimeSettings? = null
        var engineMessage: String? = null

        val controller = GameSettingsController(
            currentGameState = { GameState.empty() },
            currentPlayerSetup = { PlayerSetup() },
            currentEngineProfile = { EngineProfile() },
            currentSearchTimeSettings = { SearchTimeSettings() },
            currentAnalysisState = { GameSessionAnalysisState.empty(GameState.empty()) },
            currentAutoPlayDelaySetting = { AutoPlayDelaySetting.Default },
            defaultPlayLevel = PlayLevelSetting(),
            isEngineBusy = { true },
            runtimeEventLog = ControllerFakeRuntimeEventLogPort(),
            currentRuntimeLogContext = {
                RuntimeLogContext(
                    engineName = "KataGo",
                    engineDiagnostic = "ok",
                    playerSetup = PlayerSetup(),
                    gameState = GameState.empty(),
                    runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.AnalysisPreset.Lite),
                    autoPlayDelaySetting = AutoPlayDelaySetting.Default,
                    searchTimeSettings = SearchTimeSettings(),
                    topMovesEnabled = true,
                    isEngineReady = true,
                    isEngineBusy = true,
                    isGameEnded = false,
                    isAutoAiTurnPending = false,
                    shouldShowResumePrompt = false,
                    analysisCacheStats = "entries=0",
                    moveAnalysisCoverage = "none",
                    scoreText = "0",
                )
            },
            onEngineMessage = { msg -> engineMessage = msg },
            applyPlayerSetup = {},
            applyCoreSessionState = {},
            currentCoreSessionState = {
                GameSessionCoreState(
                    gameState = GameState.empty(),
                    isGameEnded = false,
                    analysisState = GameSessionAnalysisState.empty(GameState.empty()),
                    scoreState = GameSessionScoreState.reset("0", emptyList(), ""),
                    runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.AnalysisPreset.Lite),
                    moveReviewState = GameSessionMoveReviewState.reset("", ""),
                    engineMessage = ""
                )
            },
            applyRuntimePlayLevelSelection = {},
            applyAnalysisState = {},
            applySettingsAutoPlayDelay = {},
            applySettingsSearchTimeSettings = { settings -> appliedSettings = settings },
            clearUndoEngineInterventionQuietWindow = {}
        )

        val nextSettings = SearchTimeSettings(b16Millis = 2000L)
        controller.changeSearchTimeSettings(nextSettings)

        assertNotNull(appliedSettings)
        assertNull(engineMessage)
    }
}

private class ControllerFakeRuntimeEventLogPort : RuntimeEventLogPort {
    override fun append(event: String, nowMillis: Long) = Unit
    override fun readText(): String = ""
    override fun clear() = Unit
}
