package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.*
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
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
            currentSettingsState = { defaultTestSettingsState() },
            isGameEnded = { false },
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
            currentCoreSessionState = { defaultTestCoreState() },
            applyRuntimePlayLevelSelection = { selection -> playLevelSelection = selection },
            applyAnalysisState = { analysisStateUpdated = true },
            applySettingsAutoPlayDelay = {},
            applySettingsSearchTimeSettings = { settings -> appliedSettings = settings },
            applySettingsBoardSize = {},
            applySettingsHandicapCount = {},
            applySettingsKomi = {},
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
            currentSettingsState = { defaultTestSettingsState() },
            isGameEnded = { false },
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
            currentCoreSessionState = { defaultTestCoreState() },
            applyRuntimePlayLevelSelection = {},
            applyAnalysisState = {},
            applySettingsAutoPlayDelay = {},
            applySettingsSearchTimeSettings = { settings -> appliedSettings = settings },
            applySettingsBoardSize = {},
            applySettingsHandicapCount = {},
            applySettingsKomi = {},
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
            currentSettingsState = { defaultTestSettingsState() },
            isGameEnded = { false },
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
            currentCoreSessionState = { defaultTestCoreState() },
            applyRuntimePlayLevelSelection = {},
            applyAnalysisState = {},
            applySettingsAutoPlayDelay = {},
            applySettingsSearchTimeSettings = {},
            applySettingsBoardSize = {},
            applySettingsHandicapCount = {},
            applySettingsKomi = {},
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
            currentSettingsState = { defaultTestSettingsState() },
            isGameEnded = { false },
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
            currentCoreSessionState = { defaultTestCoreState() },
            applyRuntimePlayLevelSelection = {},
            applyAnalysisState = {},
            applySettingsAutoPlayDelay = {},
            applySettingsSearchTimeSettings = {},
            applySettingsBoardSize = {},
            applySettingsHandicapCount = {},
            applySettingsKomi = {},
            clearUndoEngineInterventionQuietWindow = {}
        )

        controller.changePlayerSetup(PlayerSetup())

        assertNull(appliedSetup)
        assertEquals("Engine is busy. Change Player Setup after the current action.", engineMessage)
    }

    @Test
    fun changeBoardSizeAppliesAndRefreshesPreviewWhenGameEnded() {
        var appliedSize: BoardSize? = null
        var appliedCore: GameSessionCoreState? = null

        val controller = boardSettingsTestController(
            isGameEnded = { true },
            applySettingsBoardSize = { size -> appliedSize = size },
            applyCoreSessionState = { core -> appliedCore = core },
        )

        controller.changeBoardSize(BoardSize.Nineteen)

        assertEquals(BoardSize.Nineteen, appliedSize)
        assertNotNull("refreshNewGamePreview should push an updated core state", appliedCore)
    }

    @Test
    fun changeBoardSizeIsNoOpWhileGameInProgress() {
        var appliedSize: BoardSize? = null
        var appliedCore: GameSessionCoreState? = null

        val controller = boardSettingsTestController(
            isGameEnded = { false },
            applySettingsBoardSize = { size -> appliedSize = size },
            applyCoreSessionState = { core -> appliedCore = core },
        )

        controller.changeBoardSize(BoardSize.Nineteen)

        assertNull(appliedSize)
        assertNull(appliedCore)
    }

    @Test
    fun changeHandicapCountAppliesAndRefreshesPreviewWhenGameEnded() {
        var appliedCount: Int? = null
        var appliedCore: GameSessionCoreState? = null

        val controller = boardSettingsTestController(
            isGameEnded = { true },
            applySettingsHandicapCount = { count -> appliedCount = count },
            applyCoreSessionState = { core -> appliedCore = core },
        )

        controller.changeHandicapCount(2)

        assertEquals(2, appliedCount)
        assertNotNull("refreshNewGamePreview should push an updated core state", appliedCore)
    }

    @Test
    fun changeHandicapCountIsNoOpWhileGameInProgress() {
        var appliedCount: Int? = null
        var appliedCore: GameSessionCoreState? = null

        val controller = boardSettingsTestController(
            isGameEnded = { false },
            applySettingsHandicapCount = { count -> appliedCount = count },
            applyCoreSessionState = { core -> appliedCore = core },
        )

        controller.changeHandicapCount(2)

        assertNull(appliedCount)
        assertNull(appliedCore)
    }

    @Test
    fun changeKomiUpdatesLiveGameStateAndScoreWhileGameInProgress() {
        var appliedKomi: Double? = null
        var appliedCore: GameSessionCoreState? = null

        val controller = boardSettingsTestController(
            isGameEnded = { false },
            applySettingsKomi = { komi -> appliedKomi = komi },
            applyCoreSessionState = { core -> appliedCore = core },
        )

        controller.changeKomi(7.5)

        assertEquals(7.5, appliedKomi)
        assertEquals(7.5, appliedCore?.gameState?.komi)
        assertNotNull(appliedCore?.scoreState?.scoreText)
    }

    @Test
    fun changeKomiRefreshesPreviewInsteadWhenGameEnded() {
        var appliedKomi: Double? = null
        var refreshedPreviewCore: GameSessionCoreState? = null

        val controller = boardSettingsTestController(
            isGameEnded = { true },
            currentSettingsState = { defaultTestSettingsState().copy(komi = 7.5) },
            applySettingsKomi = { komi -> appliedKomi = komi },
            applyCoreSessionState = { core -> refreshedPreviewCore = core },
        )

        controller.changeKomi(7.5)

        assertEquals(7.5, appliedKomi)
        // applyGameSetupPreview resets to a brand-new local game — komi mid-game update
        // (gameState.copy(komi=...)) is NOT what should have run here.
        assertNotNull(refreshedPreviewCore)
    }

    /**
     * Minimal controller wired only for the board-size/handicap/komi tests above — the
     * other constructor params are exercised by the search-time/player-setup tests further
     * up and are irrelevant here (never called by changeBoardSize/changeHandicapCount/changeKomi).
     */
    private fun boardSettingsTestController(
        isGameEnded: () -> Boolean,
        currentSettingsState: () -> GameSessionSettingsState = { defaultTestSettingsState() },
        applySettingsBoardSize: (BoardSize) -> Unit = {},
        applySettingsHandicapCount: (Int) -> Unit = {},
        applySettingsKomi: (Double) -> Unit = {},
        applyCoreSessionState: (GameSessionCoreState) -> Unit = {},
    ): GameSettingsController = GameSettingsController(
        currentGameState = { GameState.empty() },
        currentPlayerSetup = { PlayerSetup() },
        currentEngineProfile = { EngineProfile() },
        currentSearchTimeSettings = { SearchTimeSettings() },
        currentAnalysisState = { GameSessionAnalysisState.empty(GameState.empty()) },
        currentAutoPlayDelaySetting = { AutoPlayDelaySetting.Default },
        currentSettingsState = currentSettingsState,
        isGameEnded = isGameEnded,
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
                isGameEnded = isGameEnded(),
                isAutoAiTurnPending = false,
                shouldShowResumePrompt = false,
                analysisCacheStats = "entries=0",
                moveAnalysisCoverage = "none",
                scoreText = "0",
            )
        },
        onEngineMessage = {},
        applyPlayerSetup = {},
        applyCoreSessionState = applyCoreSessionState,
        currentCoreSessionState = { defaultTestCoreState() },
        applyRuntimePlayLevelSelection = {},
        applyAnalysisState = {},
        applySettingsAutoPlayDelay = {},
        applySettingsSearchTimeSettings = {},
        applySettingsBoardSize = applySettingsBoardSize,
        applySettingsHandicapCount = applySettingsHandicapCount,
        applySettingsKomi = applySettingsKomi,
        clearUndoEngineInterventionQuietWindow = {},
    )
}

private fun defaultTestSettingsState(): GameSessionSettingsState =
    GameSessionSettingsState(
        boardSize = BoardSize.Thirteen,
        playerSetup = PlayerSetup(),
        autoPlayDelaySetting = AutoPlayDelaySetting.Default,
        searchTimeSettings = SearchTimeSettings(),
        topMovesEnabled = false,
    )

private fun defaultTestCoreState(): GameSessionCoreState =
    GameSessionCoreState(
        gameState = GameState.empty(),
        isGameEnded = false,
        analysisState = GameSessionAnalysisState.empty(GameState.empty()),
        scoreState = GameSessionScoreState.reset("0", emptyList(), ""),
        runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.AnalysisPreset.Lite),
        moveReviewState = GameSessionMoveReviewState.reset("", ""),
        engineMessage = ""
    )

private class ControllerFakeRuntimeEventLogPort : RuntimeEventLogPort {
    override fun append(event: String, nowMillis: Long) = Unit
    override fun readText(): String = ""
    override fun clear() = Unit
}
