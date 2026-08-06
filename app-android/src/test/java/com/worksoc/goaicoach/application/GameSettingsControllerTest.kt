package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.*
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.SearchTimeLimit
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

        val nextSettings = SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds)
        controller.changeSearchTimeSettings(nextSettings)

        // Verify applied search time settings
        assertNotNull(appliedSettings)
        assertEquals(SearchTimeLimit.WithinThreeSeconds, appliedSettings?.limit)
        assertTrue(clearedQuietWindow)
        assertNotNull(playLevelSelection)
        assertTrue(analysisStateUpdated)
        assertNull(engineMessage)
    }

    @Test
    fun changeSearchTimeSettingsBlocksWhileEngineIsBusy() {
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

        val nextSettings = SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds)
        controller.changeSearchTimeSettings(nextSettings)

        assertNull(appliedSettings)
        assertEquals("Engine is busy. Change search time after the current action.", engineMessage)
    }

    @Test
    fun changePlayerSetupAppliesSetupWhenEngineIsNotBusy() {
        var appliedSetup: PlayerSetup? = null
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
            applyPlayerSetup = { setup -> appliedSetup = setup },
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
            applySettingsSearchTimeSettings = {},
            clearUndoEngineInterventionQuietWindow = {}
        )

        val nextSetup = PlayerSetup()
        controller.changePlayerSetup(nextSetup)

        assertEquals(nextSetup, appliedSetup)
        assertNull(engineMessage)
    }

    @Test
    fun changePlayerSetupBlocksWhileEngineIsBusy() {
        var appliedSetup: PlayerSetup? = null
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
            applyPlayerSetup = { setup -> appliedSetup = setup },
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
            applySettingsSearchTimeSettings = {},
            clearUndoEngineInterventionQuietWindow = {}
        )

        controller.changePlayerSetup(PlayerSetup())

        assertNull(appliedSetup)
        assertEquals("Engine is busy. Change Player Setup after the current action.", engineMessage)
    }
}

private class ControllerFakeRuntimeEventLogPort : RuntimeEventLogPort {
    override fun append(event: String, nowMillis: Long) = Unit
    override fun readText(): String = ""
    override fun clear() = Unit
}
