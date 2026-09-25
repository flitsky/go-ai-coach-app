package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.contract.*
import com.worksoc.goaicoach.application.orchestration.*
import com.worksoc.goaicoach.application.preferences.*
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.*
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.HandicapKomi
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.testsupport.CannedRuntimeEventLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            runtimeEventLog = CannedRuntimeEventLog(),
            currentRuntimeLogContext = {
                RuntimeLogContext(
                    engineName = "KataGo",
                    engineDiagnostic = "ok",
                    playerSetup = PlayerSetup(),
                    gameState = GameState.empty(),
                    runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset.Lite),
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

    /**
     * 회귀 방지(2026-08-30): 예전에는 엔진이 바쁘면 이 설정 변경을 **막았고**, 그 게이트가
     * AI 대 AI 대국에서 최대 탐색 시간을 영영 못 바꾸게 했다 — 그 모드에서는 엔진이 사실상
     * 항상 바쁘다. 이 값은 다음 엔진 호출부터 적용되므로 막을 이유가 없다.
     */
    @Test
    fun changeSearchTimeSettingsAppliesEvenWhileEngineIsBusy() {
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
            runtimeEventLog = CannedRuntimeEventLog(),
            currentRuntimeLogContext = {
                RuntimeLogContext(
                    engineName = "KataGo",
                    engineDiagnostic = "ok",
                    playerSetup = PlayerSetup(),
                    gameState = GameState.empty(),
                    runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset.Lite),
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

        assertEquals(nextSettings, appliedSettings, "엔진이 바빠도 설정은 적용돼야 한다")
        assertNull(engineMessage, "막혔다는 안내가 뜨면 안 된다")
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
            runtimeEventLog = CannedRuntimeEventLog(),
            currentRuntimeLogContext = {
                RuntimeLogContext(
                    engineName = "KataGo",
                    engineDiagnostic = "ok",
                    playerSetup = PlayerSetup(),
                    gameState = GameState.empty(),
                    runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset.Lite),
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
            runtimeEventLog = CannedRuntimeEventLog(),
            currentRuntimeLogContext = {
                RuntimeLogContext(
                    engineName = "KataGo",
                    engineDiagnostic = "ok",
                    playerSetup = PlayerSetup(),
                    gameState = GameState.empty(),
                    runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset.Lite),
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
        assertNotNull(appliedCore, "refreshNewGamePreview should push an updated core state")
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
        assertNotNull(appliedCore, "refreshNewGamePreview should push an updated core state")
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
     * **재시작 직후 판 크기를 바꿔도 저장된 덤이 남아야 한다**(refactor backlog #93이 찾음).
     *
     * 재시작하면 화면의 덤(`gameState.komi`)은 저장값으로 오르는데, 미리보기를 다시 그릴 때 쓰는
     * 설정 상태의 덤(`settings.komi`)은 **기본값 6.5로 비어 있었다** — 그래서 덤을 7.5로 두고 앱을
     * 다시 켠 뒤 판 크기나 접바둑을 한 번 바꾸면 덤이 소리 없이 6.5로 돌아갔다. #93의 자동 전환은
     * *"접바둑 점수만 바꾸면 덤을 건드리지 않는다"* 를 약속하므로, 이 구멍이 있으면 재시작 뒤에
     * 그 약속이 깨진다.
     */
    @Test
    fun boardSizeChangeAfterRestartKeepsTheSavedKomi() {
        val live = LiveSettingsWiring(restartFrom(UserPreferencesSnapshot(boardSize = BoardSize.Thirteen, komi = 7.5)))
        val controller = liveWiredController(live)
        assertEquals(7.5, live.displayedKomi, "재시작 직후 화면의 덤이 저장값이 아니다")

        controller.changeBoardSize(BoardSize.Nineteen)

        assertEquals(
            7.5,
            live.displayedKomi,
            "재시작 뒤 판 크기를 바꾸자 덤이 기본값으로 돌아갔다 — 설정 상태가 저장된 덤을 싣지 않았다",
        )
    }

    /**
     * **접바둑을 고르면 드롭다운의 덤이 곧바로 0.5가 된다**(refactor backlog #93, 2026-09-24 사용자 결정).
     * 로비·설정 화면의 드롭다운은 `gameState.komi`(미리보기)를 그리므로, 설정 상태만 바뀌고 미리보기가
     * 다시 그려지지 않으면 저장은 0.5인데 화면은 6.5로 남는다.
     */
    @Test
    fun choosingAHandicapShowsHandicapKomiImmediately() {
        val live = LiveSettingsWiring(restartFrom(UserPreferencesSnapshot()))
        val controller = liveWiredController(live)
        assertEquals(DefaultKomi, live.displayedKomi)

        controller.changeHandicapCount(3)

        assertEquals(3, live.settings.handicapCount)
        assertEquals(3, live.core.gameState.handicapCount, "미리보기 판에 접바둑 돌이 안 놓였다")
        assertEquals(HandicapKomi, live.displayedKomi, "접바둑을 골랐는데 드롭다운의 덤이 0.5가 아니다")
    }

    /**
     * **함정 2(자동저장 배선)** — 자동 전환한 0.5가 자동저장을 지나 재시작 뒤에도 남는가.
     * 자동저장은 스냅샷을 처음부터 다시 만들고, 덤은 `GoCoachApp`이 미리보기의 `gameState.komi`로
     * 넘긴다. 재시작 뒤 점수만 바꾸면(3→5) 0.5가 그대로여야 하고, 호선으로 돌아오면 6.5다.
     */
    @Test
    fun handicapKomiSurvivesAutosaveAndRestart() {
        val store = InMemoryPreferencesStore()
        val first = LiveSettingsWiring(restartFrom(store.load()))
        liveWiredController(first).changeHandicapCount(3)
        autosaveLikeTheApp(first, store)
        assertEquals(HandicapKomi, store.load().komi, "자동저장이 0.5를 적지 않았다")

        val second = LiveSettingsWiring(restartFrom(store.load()))
        val controller = liveWiredController(second)
        assertEquals(3, second.settings.handicapCount)
        assertEquals(HandicapKomi, second.displayedKomi, "재시작 뒤 덤이 0.5가 아니다")

        controller.changeHandicapCount(5)
        assertEquals(HandicapKomi, second.displayedKomi, "재시작 뒤 점수만 바꿨는데(3→5) 덤이 바뀌었다")

        controller.changeHandicapCount(0)
        assertEquals(DefaultKomi, second.displayedKomi, "0.5 그대로 호선으로 돌아왔는데 6.5가 아니다")
        autosaveLikeTheApp(second, store)

        val third = LiveSettingsWiring(restartFrom(store.load()))
        assertEquals(0, third.settings.handicapCount)
        assertEquals(DefaultKomi, third.displayedKomi)
    }

    /** 사용자가 접바둑에서 덤을 7.5로 고쳐 두면, 재시작한 뒤 호선으로 돌아와도 7.5가 남는다. */
    @Test
    fun userEditedKomiSurvivesLeavingTheHandicapAfterRestart() {
        val store = InMemoryPreferencesStore()
        val first = LiveSettingsWiring(restartFrom(store.load()))
        val firstController = liveWiredController(first)
        firstController.changeHandicapCount(3)
        firstController.changeKomi(7.5)
        assertEquals(7.5, first.displayedKomi)
        autosaveLikeTheApp(first, store)

        val second = LiveSettingsWiring(restartFrom(store.load()))
        liveWiredController(second).changeHandicapCount(0)

        assertEquals(7.5, second.displayedKomi, "사용자가 고친 덤 7.5가 호선으로 돌아오며 사라졌다")
        autosaveLikeTheApp(second, store)
        assertEquals(7.5, store.load().komi)
    }

    /**
     * **이관 없음**(사용자 결정) — 이미 *"접바둑 + 덤 6.5"* 로 저장된 설정은 그 사용자가 고른 값이다.
     * 재시작해도, 점수만 바꿔도 6.5로 남는다.
     */
    @Test
    fun savedHandicapWithDefaultKomiIsNotMigrated() {
        val live = LiveSettingsWiring(
            restartFrom(UserPreferencesSnapshot(boardSize = BoardSize.Nineteen, handicapCount = 3, komi = DefaultKomi)),
        )
        assertEquals(DefaultKomi, live.displayedKomi, "저장된 접바둑 + 6.5가 재시작에서 바뀌었다")

        liveWiredController(live).changeHandicapCount(4)

        assertEquals(DefaultKomi, live.displayedKomi, "저장된 접바둑의 점수만 바꿨는데 덤이 바뀌었다")
    }

    /**
     * 앱 배선(`SettingsAndDiagnosticsControllerWiring`)과 **같은 모양**으로 설정·코어 상태를 실제로
     * 갈아끼우는 컨트롤러. 위 테스트들처럼 람다가 값을 받아 적기만 하면 *"설정 상태에서 미리보기로"*
     * 흐르는 경로가 보이지 않는다.
     */
    private fun liveWiredController(live: LiveSettingsWiring): GameSettingsController =
        boardSettingsTestController(
            isGameEnded = { live.core.isGameEnded },
            currentSettingsState = { live.settings },
            applySettingsBoardSize = { size -> live.settings = live.settings.applyBoardSize(size) },
            applySettingsHandicapCount = { count -> live.settings = live.settings.applyHandicap(count) },
            applySettingsKomi = { komi -> live.settings = live.settings.applyKomi(komi) },
            applyCoreSessionState = { core -> live.core = core },
            currentGameState = { live.core.gameState },
            currentCoreSessionState = { live.core },
        )

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
        currentGameState: () -> GameState = { GameState.empty() },
        currentCoreSessionState: () -> GameSessionCoreState = { defaultTestCoreState() },
    ): GameSettingsController = GameSettingsController(
        currentGameState = currentGameState,
        currentPlayerSetup = { PlayerSetup() },
        currentEngineProfile = { EngineProfile() },
        currentSearchTimeSettings = { SearchTimeSettings() },
        currentAnalysisState = { GameSessionAnalysisState.empty(GameState.empty()) },
        currentAutoPlayDelaySetting = { AutoPlayDelaySetting.Default },
        currentSettingsState = currentSettingsState,
        isGameEnded = isGameEnded,
        defaultPlayLevel = PlayLevelSetting(),
        isEngineBusy = { false },
        runtimeEventLog = CannedRuntimeEventLog(),
        currentRuntimeLogContext = {
            RuntimeLogContext(
                engineName = "KataGo",
                engineDiagnostic = "ok",
                playerSetup = PlayerSetup(),
                gameState = GameState.empty(),
                runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset.Lite),
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
        currentCoreSessionState = currentCoreSessionState,
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
        runtimeState = GameSessionRuntimeState(PlayLevelSetting(), EngineProfile(), com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset.Lite),
        moveReviewState = GameSessionMoveReviewState.reset("", ""),
        engineMessage = ""
    )

/**
 * 앱을 켤 때와 같은 경로로 세션을 세운다 — `GoCoachApp`이 `buildInitialUserPreferencesPlan`으로
 * 초기 계획을 만들고, `buildInitialSessionState`가 그것을 **대국 전 미리보기**(`isGameEnded = true`)로
 * 올린다.
 */
private fun restartFrom(saved: UserPreferencesSnapshot): InitialUserPreferencesPlan =
    buildInitialUserPreferencesPlan(
        preferences = saved,
        defaultPlayLevel = PlayLevelSetting(),
        currentProfile = EngineProfile(),
    )

/** 화면이 읽는 두 상태 — 드롭다운의 덤은 `core.gameState.komi`, 접바둑은 `settings.handicapCount`다. */
private class LiveSettingsWiring(plan: InitialUserPreferencesPlan) {
    var settings: GameSessionSettingsState = plan.toGameSessionSettingsState()
    var core: GameSessionCoreState = defaultTestCoreState().copy(gameState = plan.gameState, isGameEnded = true)
    val displayedKomi: Double get() = core.gameState.komi
}

/**
 * `GoCoachApp`의 자동저장 `LaunchedEffect`와 같은 요청 — 덤은 **미리보기의** `gameState.komi`,
 * 대국 설정은 설정 상태다. 표시 옵션은 이 테스트와 무관해 스냅샷 기본값을 넘긴다.
 */
private fun autosaveLikeTheApp(live: LiveSettingsWiring, store: UserPreferencesStorePort) {
    val defaults = UserPreferencesSnapshot()
    runUserPreferencesAutosave(
        request = UserPreferencesAutosaveRequest(
            settingsState = live.settings,
            ruleset = live.core.gameState.ruleset,
            komi = live.core.gameState.komi,
            showCoordinates = defaults.showCoordinates,
            showMoveNumbers = defaults.showMoveNumbers,
            showLastMoveRing = defaults.showLastMoveRing,
            showOwnershipOverlay = defaults.showOwnershipOverlay,
            isDirectPlayEnabled = defaults.isDirectPlayEnabled,
        ),
        store = store,
    )
}

private class InMemoryPreferencesStore : UserPreferencesStorePort {
    private var saved: UserPreferencesSnapshot = UserPreferencesSnapshot()

    override fun save(snapshot: UserPreferencesSnapshot) {
        saved = snapshot
    }

    override fun load(): UserPreferencesSnapshot = saved
}
