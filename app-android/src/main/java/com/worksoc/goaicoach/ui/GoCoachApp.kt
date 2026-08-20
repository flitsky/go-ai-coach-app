package com.worksoc.goaicoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.worksoc.goaicoach.application.premium.FeatureAccessPolicy
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import com.worksoc.goaicoach.application.premium.buildPremiumDeactivatedDiagnosticEvent
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.AnalysisResultCache
import com.worksoc.goaicoach.application.analysis.PositionCacheOptimizationController
import com.worksoc.goaicoach.application.analysis.UndoAnalysisRestoreCache
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import com.worksoc.goaicoach.persistence.DeviceIdentityStore
import com.worksoc.goaicoach.application.autoai.AutoAiTurnController
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.buildAutoAiTurnFailureDisplayPlan
import com.worksoc.goaicoach.application.autoai.completeAutoAiTurnRun
import com.worksoc.goaicoach.application.debugreport.DebugReportController
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import com.worksoc.goaicoach.application.engine.EngineBenchmarkController
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupRunRequest
import com.worksoc.goaicoach.application.engine.runEngineStartupApplication
import com.worksoc.goaicoach.application.engine.operation.EngineActivityIndicator
import com.worksoc.goaicoach.application.preferences.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.application.preferences.UserPreferencesAutosaveRequest
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.preferences.runUserPreferencesAutosave
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeAppStartLog
import com.worksoc.goaicoach.application.runtime.runtimeGameResetLog
import com.worksoc.goaicoach.application.runtime.runtimeScoreSnapshotsChangedLog
import com.worksoc.goaicoach.application.runtime.toRuntimeLogContext
import com.worksoc.goaicoach.application.savedgame.SavedSessionController
import com.worksoc.goaicoach.application.startgame.NewGameController
import com.worksoc.goaicoach.application.score.ScoringRuleController
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.undo.UndoController
import com.worksoc.goaicoach.application.engine.operation.EngineOperationGate
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.humanmove.HumanMoveController
import com.worksoc.goaicoach.application.savedgame.SavedGamePersistenceRunRequest
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.savedgame.buildEndedGameRestoreDisplayPlan
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.savedgame.SavedSessionPromptRunRequest
import com.worksoc.goaicoach.application.savedgame.runSavedGamePersistenceApplication
import com.worksoc.goaicoach.application.savedgame.runSavedSessionPromptApplication
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionDisplayStateApplier
import com.worksoc.goaicoach.application.session.GameSessionStateHolder
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.GameSettingsController
import com.worksoc.goaicoach.application.session.runTurnAutomationTriggerEffect
import com.worksoc.goaicoach.application.topmoves.TopMovesController
import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisDeferral
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.persistence.EngineBenchmarkStore
import com.worksoc.goaicoach.persistence.DebugReportMirrorStore
import com.worksoc.goaicoach.persistence.RuntimeEventLog
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.persistence.PremiumStateStore
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.GoCoachScreenStateAssembler
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.presentation.applyEvalActivation
import com.worksoc.goaicoach.presentation.buildGameUiEventHandlers
import com.worksoc.goaicoach.presentation.dispatchGameUiEvent
import com.worksoc.goaicoach.presentation.toKaTrainUxOptions
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import kotlinx.coroutines.CoroutineScope
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import kotlinx.coroutines.Job
import java.io.File

@Composable
internal fun GoCoachApp(
    engineClient: EngineSessionClient,
    engineName: String,
    engineDiagnostic: String,
    diagnosticEventLog: DiagnosticEventLogPort,
) {
    MaterialTheme(
        colorScheme = AppLightColorScheme,
    ) {
        ProvideUiLanguage { selectedLanguage, onLanguageChange ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                GoCoachScreen(engineClient, engineName, engineDiagnostic, diagnosticEventLog, selectedLanguage, onLanguageChange)
            }
        }
    }
}

@Composable
private fun GoCoachScreen(
    engineClient: EngineSessionClient,
    engineName: String,
    engineDiagnostic: String,
    diagnosticEventLog: DiagnosticEventLogPort,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val preferencesStore: UserPreferencesStorePort = remember(context) { UserPreferencesStore(context) }
    val initialPreferences = remember(preferencesStore) { preferencesStore.load() }
    // AndroidAuthClient는 내부 상태가 없는 얇은 래퍼라 재구성마다 새로 만들어도 비용/동작
    // 차이가 없다 — 별도로 캐시해 두지 않고 그대로 둬서 상태 훅 예산을 아낀다.
    val authClient: AuthClientPort = AndroidAuthClient()
    // DeviceIdentityStore도 authClient와 같은 이유로 캐시해 두지 않는다(내부 상태 없는 얇은 래퍼).
    val deviceIdentityStore: DeviceIdentityStorePort = DeviceIdentityStore(context)
    // Credential Manager 호출도 내부 상태가 없는 얇은 래퍼라 authClient와 동일하게 캐시하지 않는다.
    val credentialManagerClient = GoogleCredentialManagerClient()
    // 첫 화면 판단(로그인 기능 온/오프 포함)은 FeatureFlags.kt의 initialDestination으로
    // 뺐다 — 별도 훅을 새로 추가하지 않고 이 초기값 계산식만 함수 호출로 바꾼다(이 파일의
    // 상태 훅 예산이 거의 소진돼 있어, 새 컴포즈 상태 훅을 추가하지 않는 쪽을 우선한다).
    var currentDestination by remember {
        mutableStateOf(initialDestination(deviceIdentityStore, initialPreferences.hasSeenOnboarding))
    }
    var showResignConfirmFromBack by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    // PremiumStateStore도 AndroidAuthClient와 같은 이유로 remember하지 않는다(내부 상태
    // 없는 얇은 래퍼, 상태 훅 예산 절약). 백그라운드에서 시스템이 프로세스를 종료했다 재시작해도
    // (사용자의 명시적 강제 종료와 구분 불가) 활성화 상태가 사라지지 않도록 저장소에서 복원한다.
    val premiumStateStore: PremiumStateStorePort = PremiumStateStore(context)
    var premiumState by remember { mutableStateOf(premiumStateStore.load()) }
    val sessionStore: SavedGameStorePort = remember(context) { GameSessionStore(context) }
    val benchmarkStore: EngineBenchmarkStorePort = remember(context) { EngineBenchmarkStore(context) }
    val debugReportMirror: DebugReportMirrorPort = remember(context) { DebugReportMirrorStore(context) }
    val clipboardPort: ClipboardPort = remember(context) { AndroidClipboardPort(context) }
    val userNoticePort: UserNoticePort = remember(context) { AndroidUserNoticePort(context) }
    val runtimeEventLog: RuntimeEventLogPort = remember(context) {
        RuntimeEventLog(File(context.filesDir, RuntimeEventLog.FileName))
    }
    val defaultPlayLevel = remember { PlayLevelSetting() }
    val initialPlan = remember(initialPreferences, defaultPlayLevel) {
        buildInitialUserPreferencesPlan(
            preferences = initialPreferences,
            defaultPlayLevel = defaultPlayLevel,
            currentProfile = EngineProfile(),
        )
    }
    val sessionHolder = remember {
        GameSessionStateHolder(
            buildInitialSessionState(
                initialPlan = initialPlan,
                engineDiagnostic = engineDiagnostic,
                benchmarkStore = benchmarkStore,
            ),
        )
    }
    var sessionSnapshot by remember { mutableStateOf(sessionHolder.current) }

    LaunchedEffect(sessionHolder) {
        sessionHolder.state.collect { snapshot ->
            sessionSnapshot = snapshot
        }
    }

    fun mutateSession(transform: (GameSessionControllerState) -> GameSessionControllerState) {
        sessionHolder.update(transform)
        sessionSnapshot = sessionHolder.current
    }

    fun mutateCore(transform: (GameSessionCoreState) -> GameSessionCoreState) {
        sessionHolder.updateCore(transform)
        sessionSnapshot = sessionHolder.current
    }

    var gameState by HolderBackedState(
        { sessionSnapshot.gameState },
        { value -> mutateCore { it.copy(gameState = value) } },
    )
    var engineMessage by HolderBackedState(
        { sessionSnapshot.engineMessage },
        { value -> mutateCore { it.copy(engineMessage = value) } },
    )
    var analysisState by HolderBackedState(
        { sessionSnapshot.core.analysisState },
        { value -> mutateCore { it.copy(analysisState = value) } },
    )
    var scoreState by HolderBackedState(
        { sessionSnapshot.core.scoreState },
        { value ->
            val previousSnapshots = sessionSnapshot.core.scoreState.scoreSnapshots
            if (previousSnapshots != value.scoreSnapshots) {
                runtimeEventLog.append(
                    runtimeScoreSnapshotsChangedLog(
                        gameState = sessionSnapshot.gameState,
                        previous = previousSnapshots,
                        next = value.scoreSnapshots,
                    ),
                )
            }
            mutateCore { it.copy(scoreState = value) }
        },
    )
    var moveReviewState by HolderBackedState(
        { sessionSnapshot.core.moveReviewState },
        { value -> mutateCore { it.copy(moveReviewState = value) } },
    )
    var runtimeState by HolderBackedState(
        { sessionSnapshot.core.runtimeState },
        { value -> mutateCore { it.copy(runtimeState = value) } },
    )
    var isGameEnded by HolderBackedState(
        { sessionSnapshot.isGameEnded },
        { value -> mutateCore { it.copy(isGameEnded = value) } },
    )
    var settingsState by HolderBackedState(
        { sessionSnapshot.settings },
        { value -> mutateSession { it.withSettings(value) } },
    )
    var benchmarkUiState by HolderBackedState(
        { sessionSnapshot.benchmark },
        { value -> mutateSession { it.withBenchmark(value) } },
    )
    var savedSessionUiState by HolderBackedState(
        { sessionSnapshot.savedSession },
        { value -> mutateSession { it.withSavedSession(value) } },
    )
    var autoAiTurnUiState by HolderBackedState(
        { sessionSnapshot.autoAiTurn },
        { value -> mutateSession { it.withAutoAiTurn(value) } },
    )
    var positionCacheOptimizationState by HolderBackedState(
        { sessionSnapshot.positionCacheOptimization },
        { value -> mutateSession { it.withPositionCacheOptimization(value) } },
    )
    var turnTimeState by HolderBackedState(
        { sessionSnapshot.core.turnTimeState },
        { value -> mutateCore { it.copy(turnTimeState = value) } },
    )
    ObserveTimerLifecycle(turnTimeState) { turnTimeState = it }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineBlockingBusy by remember { mutableStateOf(false) }
    var engineActivityIndicator by remember { mutableStateOf<EngineActivityIndicator?>(EngineActivityIndicator.Preparing) }
    var engineTurnWaitCompletionSeq by remember { mutableStateOf(0) }
    var isEngineReady by remember { mutableStateOf(false) }
    val analysisCache = remember { AnalysisResultCache(maxEntries = 96) }
    val undoAnalysisRestoreCache = remember { UndoAnalysisRestoreCache(maxEntries = 96) }
    var uxOptions by remember { mutableStateOf(initialPreferences.toKaTrainUxOptions()) }
    var isScoreGraphExpanded by remember { mutableStateOf(false) }
    var hasCompletedEngineStartup by remember { mutableStateOf(false) }

    val playerSetup = settingsState.playerSetup
    val matchMode = settingsState.matchMode
    val searchTimeSettings = settingsState.searchTimeSettings
    val topMovesEnabled = settingsState.topMovesEnabled
    val shouldShowResumePrompt = savedSessionUiState.shouldShowResumePrompt
    var undoEngineInterventionQuietUntil by remember { mutableStateOf(0L) }
    var isPendingUndoSync by remember { mutableStateOf(false) }
    var cancelUndoSync: () -> Unit = {}
    fun clearUndoEngineInterventionQuietWindow() { undoEngineInterventionQuietUntil = 0L; cancelUndoSync() }
    fun activateEndgameJudgementReview() { uxOptions = uxOptions.copy(showOwnershipOverlay = true) }
    fun currentRuntimeLogContext(): RuntimeLogContext {
        return sessionSnapshot.toRuntimeLogContext(
            engineName = engineName,
            engineDiagnostic = engineDiagnostic,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
            turnTimeText = turnTimeState.runtimeText(),
        )
    }
    fun applyCoreSessionState(core: GameSessionCoreState) {
        mutateCore { core }
        if (!core.isGameEnded) {
            positionCacheOptimizationState = positionCacheOptimizationState.clearPrompt()
        }
    }
    // controllers.settingsController::refreshNewGamePreview가 아직 없어(controllers는 아래에서
    // 만들어짐) 일단 no-op으로 선언해 두고, controllers 생성 직후 실제 구현으로 교체한다 —
    // cancelUndoSync와 같은 전방 참조 패턴.
    var exitToHome: () -> Unit = {}
    val lifecycleController = remember {
        EngineOperationLifecycleController(
            scope = scope,
            runtimeEventLog = runtimeEventLog,
            diagnosticEventLog = diagnosticEventLog,
            currentRuntimeLogContext = { currentRuntimeLogContext() },
            currentState = { gameState },
            currentSessionGeneration = { runtimeState.sessionGeneration },
            onBusyChanged = { busy, blocking, activityIndicator, completionSeq ->
                isEngineBusy = busy
                isEngineBlockingBusy = blocking
                engineActivityIndicator = activityIndicator
                engineTurnWaitCompletionSeq = completionSeq
            },
        )
    }
    fun engineProfileTimeoutPolicy(profile: EngineProfile): EngineTimeoutPolicy =
        EngineTimeoutPolicy(
            timeoutMillis = profile.analysisLimit.timeMillis,
            label = "${profile.difficulty.label}:${profile.analysisLimit.visits}v",
        )
    val displayStateApplier = remember {
        GameSessionDisplayStateApplier(
            currentCoreState = { sessionSnapshot.core },
            applyCoreState = ::applyCoreSessionState,
            appendEngineOperationDiscardLog = lifecycleController::appendDiscardLog,
        )
    }
    fun applyFinalScoreWithJudgement(final: FinalScoreDisplayPlan) { activateEndgameJudgementReview(); displayStateApplier.applyFinalScoreDisplayPlan(final) }
    LaunchedEffect(Unit) {
        runtimeEventLog.append(runtimeAppStartLog(currentRuntimeLogContext()))
    }
    LaunchedEffect(engineClient) {
        hasCompletedEngineStartup = false
        val startup = engineClient.runEngineStartupApplication(
            EngineStartupRunRequest(
                state = gameState,
                profile = runtimeState.engineProfile,
                sessionGeneration = runtimeState.sessionGeneration,
                engineDiagnostic = engineDiagnostic,
                diagnosticEventLog = diagnosticEventLog,
                lifecycleCallbacks = lifecycleController.callbacks(),
            ),
        )
        isEngineReady = startup.isEngineReady
        displayStateApplier.applyEngineStartupDisplayPlan(startup)
        hasCompletedEngineStartup = true
    }
    LaunchedEffect(sessionStore) {
        runSavedSessionPromptApplication(
            SavedSessionPromptRunRequest(
                store = sessionStore,
                applyPrompt = { prompt ->
                    savedSessionUiState = savedSessionUiState.applyPrompt(prompt)
                    // Ended-game snapshot: skip the resume prompt, jump straight to InGame and
                    // restore its result popup so it survives the OS killing this process
                    // while the app was backgrounded (see buildEndedGameRestoreDisplayPlan).
                    val endedGameDisplay = prompt.pendingSavedSession?.let(::buildEndedGameRestoreDisplayPlan)
                    if (endedGameDisplay != null) {
                        applyFinalScoreWithJudgement(endedGameDisplay)
                        currentDestination = ScreenDestination.InGame
                    }
                },
            ),
        )
    }
    val benchmarkController = EngineBenchmarkController(
        scope = scope,
        engineClient = engineClient,
        store = benchmarkStore,
        diagnosticEventLog = diagnosticEventLog,
        lifecycleCallbacks = { lifecycleController.callbacks() },
        currentState = { gameState },
        sessionGeneration = { runtimeState.sessionGeneration },
        isEngineReady = { isEngineReady },
        isEngineBusy = { isEngineBusy },
        currentBenchmarkUiState = { benchmarkUiState },
        onBenchmarkUiState = { state -> benchmarkUiState = state },
        onEngineMessage = { message -> engineMessage = message },
        onDisplayPlan = { plan -> displayStateApplier.applyEngineBenchmarkDisplayPlan(plan) },
    )
    LaunchedEffect(
        preferencesStore,
        settingsState,
        uxOptions,
        gameState.ruleset,
        gameState.komi,
    ) {
        runUserPreferencesAutosave(
            request = UserPreferencesAutosaveRequest(
                settingsState = settingsState,
                ruleset = gameState.ruleset,
                komi = gameState.komi,
                showCoordinates = uxOptions.showCoordinates,
                showMoveNumbers = uxOptions.showMoveNumbers,
                showLastMoveRing = uxOptions.showLastMoveRing,
                showOwnershipOverlay = uxOptions.showOwnershipOverlay,
                isDirectPlayEnabled = uxOptions.isDirectPlayEnabled,
                showMoveReview = uxOptions.showMoveReview,
            ),
            store = preferencesStore,
        )
    }
    LaunchedEffect(
        savedSessionUiState,
        isGameEnded,
        gameState.moves.size,
        gameState.ruleset,
        playerSetup,
        runtimeState.playLevel,
        topMovesEnabled,
        scoreState.scoreSnapshots,
    ) {
        runSavedGamePersistenceApplication(
            SavedGamePersistenceRunRequest(
                savedSessionUiState = savedSessionUiState,
                isGameEnded = isGameEnded,
                gameState = gameState,
                playerSetup = playerSetup,
                playLevel = runtimeState.playLevel,
                topMovesEnabled = topMovesEnabled,
                scoreSnapshots = scoreState.scoreSnapshots,
                nowMillis = System.currentTimeMillis(),
                store = sessionStore,
                finalScoreJudgement = scoreState.finalScoreJudgement,
            ),
        )
    }
    val deferredTopMoveAnalysis = remember { TopMoveAnalysisDeferral() }
    // gameState/settingsState/scoreState/moveReviewState/runtimeState/autoAiTurnUiState/
    // positionCacheOptimizationState/benchmarkUiState/savedSessionUiState/turnTimeState/
    // isGameEnded are all HolderBackedState reads of sessionSnapshot (see their declarations
    // above), so sessionSnapshot changing is already necessary and sufficient to catch every
    // change in them — listing them separately here would be redundant.
    val wiringContext = remember(
        sessionSnapshot,
        undoEngineInterventionQuietUntil,
        isPendingUndoSync,
        isEngineReady,
        isEngineBusy,
        isEngineBlockingBusy,
        uxOptions
    ) {
        object : GoCoachAppWiringContext {
            override val scope: CoroutineScope = scope
            override val engineClient: EngineSessionClient = engineClient
            override val diagnosticEventLog: DiagnosticEventLogPort = diagnosticEventLog
            override val runtimeEventLog: RuntimeEventLogPort = runtimeEventLog
            override val sessionStore: SavedGameStorePort = sessionStore
            override val preferencesStore: UserPreferencesStorePort = preferencesStore
            override val benchmarkStore: EngineBenchmarkStorePort = benchmarkStore
            override val debugReportMirror: DebugReportMirrorPort = debugReportMirror
            override val clipboardPort: ClipboardPort = clipboardPort
            override val userNoticePort: UserNoticePort = userNoticePort
            override val lifecycleController: EngineOperationLifecycleController = lifecycleController
            override val displayStateApplier: GameSessionDisplayStateApplier = displayStateApplier
            override val defaultPlayLevel: PlayLevelSetting = defaultPlayLevel
            override val analysisCache: AnalysisResultCache = analysisCache
            override val undoAnalysisRestoreCache: UndoAnalysisRestoreCache = undoAnalysisRestoreCache
            override val deferredTopMoveAnalysis: TopMoveAnalysisDeferral = deferredTopMoveAnalysis

            override fun sessionSnapshot(): GameSessionControllerState = sessionSnapshot
            override fun gameState(): GameState = gameState
            override fun playerSetup(): PlayerSetup = playerSetup
            override fun analysisState(): GameSessionAnalysisState = analysisState
            override fun scoreState(): GameSessionScoreState = scoreState
            override fun moveReviewState(): GameSessionMoveReviewState = moveReviewState
            override fun runtimeState(): GameSessionRuntimeState = runtimeState
            override fun settingsState(): GameSessionSettingsState = settingsState
            override fun autoAiTurnUiState(): AutoAiTurnUiState = autoAiTurnUiState
            override fun positionCacheOptimizationState(): PositionAnalysisCacheOptimizationUiState = positionCacheOptimizationState
            override fun benchmarkUiState(): EngineBenchmarkUiState = benchmarkUiState
            override fun savedSessionUiState(): SavedSessionUiState = savedSessionUiState
            override fun turnTimeState(): GameSessionTurnTimeState = turnTimeState
            override fun undoEngineInterventionQuietUntil(): Long = undoEngineInterventionQuietUntil
            override fun isPendingUndoSync(): Boolean = isPendingUndoSync
            override fun isEngineReady(): Boolean = isEngineReady
            override fun isEngineBusy(): Boolean = isEngineBusy
            override fun isEngineBlockingBusy(): Boolean = isEngineBlockingBusy
            override fun isGameEnded(): Boolean = isGameEnded
            override fun shouldShowResumePrompt(): Boolean = shouldShowResumePrompt
            override fun matchMode(): MatchMode = matchMode
            override fun topMovesEnabled(): Boolean = topMovesEnabled
            override fun currentRuntimeLogContext(): RuntimeLogContext = currentRuntimeLogContext()
            override fun engineName(): String = engineName
            override fun engineDiagnostic(): String = engineDiagnostic

            override fun setGameState(value: GameState) { gameState = value }
            override fun setEngineMessage(value: String) { engineMessage = value }
            override fun setAnalysisState(value: GameSessionAnalysisState) { analysisState = value }
            override fun setScoreState(value: GameSessionScoreState) { scoreState = value }
            override fun setMoveReviewState(value: GameSessionMoveReviewState) { moveReviewState = value }
            override fun setRuntimeState(value: GameSessionRuntimeState) { runtimeState = value }
            override fun setSettingsState(value: GameSessionSettingsState) { settingsState = value }
            override fun setAutoAiTurnUiState(value: AutoAiTurnUiState) { autoAiTurnUiState = value }
            override fun setPositionCacheOptimizationState(value: PositionAnalysisCacheOptimizationUiState) { positionCacheOptimizationState = value }
            override fun setBenchmarkUiState(value: EngineBenchmarkUiState) { benchmarkUiState = value }
            override fun setSavedSessionUiState(value: SavedSessionUiState) { savedSessionUiState = value }
            override fun setTurnTimeState(value: GameSessionTurnTimeState) { turnTimeState = value }
            override fun setUndoEngineInterventionQuietUntil(value: Long) { undoEngineInterventionQuietUntil = value }
            override fun setPendingUndoSync(value: Boolean) { isPendingUndoSync = value }
            override fun setEngineReady(value: Boolean) { isEngineReady = value }
            override fun setIsGameEnded(value: Boolean) { isGameEnded = value }

            override fun applyCoreSessionState(next: GameSessionCoreState) = applyCoreSessionState(next)
            override fun activateEndgameJudgementReview() = activateEndgameJudgementReview()
            override fun clearUndoEngineInterventionQuietWindow() = clearUndoEngineInterventionQuietWindow()
            override fun engineProfileTimeoutPolicy(profile: EngineProfile): EngineTimeoutPolicy = engineProfileTimeoutPolicy(profile)
            override fun applyFinalScoreWithJudgement(final: FinalScoreDisplayPlan) = applyFinalScoreWithJudgement(final)
        }
    }

    val controllers = remember(wiringContext) { wireGoCoachControllers(wiringContext) }
    cancelUndoSync = controllers.undoController::cancelPendingSync
    exitToHome = {
        isGameEnded = true
        controllers.settingsController.refreshNewGamePreview()
        currentDestination = ScreenDestination.Home
    }
    fun dispatch(event: GameUiEvent) {
        dispatchGameUiEvent(
            event = event,
            handlers = buildGameUiEventHandlers(
                currentPlayer = { gameState.nextPlayer },
                isTopMovesEnabled = { topMovesEnabled },
                startConfiguredGame = {
                    sessionStore.clear()
                    savedSessionUiState = savedSessionUiState.dismiss()
                    controllers.newGameController::startConfiguredGame.invoke()
                },
                copyDebugReport = controllers.debugReportController::copy,
                showEngineBenchmark = controllers.benchmarkController::showResult,
                requestScoreEstimate = controllers.scoreEstimateController::request,
                toggleEvalWithGradient = { uxOptions = uxOptions.applyEvalActivation(onEvalGradientActivated = controllers.scoreEstimateController::request) },
                showTopMoves = controllers.topMovesController::showForCurrentState,
                hideTopMoves = controllers.topMovesController::hide,
                undoLastTurn = controllers.undoController::undoLastTurn,
                submitMove = controllers.humanMoveController::submitMove,
                resignCurrentGame = { resignCurrentGameIfAllowed(isGameEnded, isEngineBusy, gameState.nextPlayer, controllers.humanMoveController::submitMove) { isGameEnded = true } },
                dismissResumePrompt = { sessionStore.clear(); savedSessionUiState = savedSessionUiState.dismiss() },
                acceptCacheOptimizationPrompt = controllers.cacheOptController::accept,
                dismissCacheOptimizationPrompt = controllers.cacheOptController::dismiss,
                restoreSavedSession = { snap ->
                    controllers.savedSessionController.restore(snap)
                    savedSessionUiState = savedSessionUiState.dismiss()
                    currentDestination = ScreenDestination.InGame
                },
                changePlayerSetup = controllers.settingsController::changePlayerSetup, changeAutoPlayDelay = controllers.settingsController::changeAutoPlayDelay,
                changeSearchTimeSettings = controllers.settingsController::changeSearchTimeSettings,
                changeBoardSize = controllers.settingsController::changeBoardSize,
                changeScoringRule = controllers.scoringRuleController::change,
                changeKomi = controllers.settingsController::changeKomi,
                changeUxOptions = { options -> uxOptions = options },
                changeHandicapCount = controllers.settingsController::changeHandicapCount,
                reportEngineTurnWatchdogTriggered = { elapsedMillis, thresholdMillis ->
                    diagnosticEventLog.append(
                        DiagnosticEvent(
                            severity = DiagnosticSeverity.Warning,
                            code = "engine_turn_watchdog_triggered",
                            message = "AI turn exceeded the watchdog threshold without responding.",
                            context = mapOf(
                                "elapsedMillis" to elapsedMillis.toString(),
                                "thresholdMillis" to thresholdMillis.toString(),
                                "player" to gameState.nextPlayer.name,
                            ),
                        ),
                    )
                },
                forceResetEngine = {
                    diagnosticEventLog.append(
                        DiagnosticEvent(
                            severity = DiagnosticSeverity.Warning,
                            code = "engine_force_reset_requested",
                            message = "User manually requested engine reset after watchdog warning.",
                        ),
                    )
                    engineClient.forceResetEngine()
                },
            ),
        )
    }

    LaunchedEffect(
        isEngineReady,
        isEngineBusy,
        playerSetup,
        searchTimeSettings,
        isGameEnded,
        shouldShowResumePrompt,
        undoEngineInterventionQuietUntil,
        gameState.nextPlayer,
        gameState.moves.size,
    ) {
        controllers.topMovesController.resumeDeferredAnalysisIfIdle()
        runTurnAutomationTriggerEffect(
            quietUntilMillis = undoEngineInterventionQuietUntil,
            topMoveTargetState = gameState,
            requestAiTurn = controllers.autoAiTurnController::requestAiTurn,
            requestTopMoveAnalysis = { targetState -> controllers.topMovesController.requestAnalysis(targetState, automatic = true) },
        )
    }

    LaunchedEffect(
        isGameEnded,
        isEngineReady,
        isEngineBusy,
        positionCacheOptimizationState.isRunning,
        playerSetup,
        searchTimeSettings,
        gameState.moves.size,
    ) {
        controllers.cacheOptController.refreshPromptIfNeeded(
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
        )
    }

    val screenState = GoCoachScreenStateAssembler.assemble(
        GoCoachScreenStateAssembler.Input(
            controller = sessionSnapshot,
            uxOptions = uxOptions,
            engineRuntime = GoCoachScreenStateAssembler.EngineRuntime(
                name = engineName,
                diagnostic = engineDiagnostic,
                isReady = isEngineReady,
                isBusy = isEngineBusy,
                isBlockingBusy = isEngineBlockingBusy,
                activityIndicator = engineActivityIndicator,
                engineTurnWaitCompletionSeq = engineTurnWaitCompletionSeq,
                hasCompletedStartup = hasCompletedEngineStartup,
            ),
            displayRuntime = GoCoachScreenStateAssembler.DisplayRuntime(
                analysisCacheStats = "${analysisCache.statsText()}, ${undoAnalysisRestoreCache.statsText()}",
                isScoreGraphExpanded = isScoreGraphExpanded,
                turnTimeText = turnTimeState.summaryText(),
            ),
        ),
    )

    val savedSessionToPrompt = if (benchmarkUiState.progress == null && benchmarkUiState.resultToConfirm == null) {
        screenState.resumePrompt?.snapshot
    } else {
        null
    }

    if (showResumeDialog && savedSessionToPrompt != null) {
        ResumeSavedSessionDialog(
            snapshot = savedSessionToPrompt,
            engineName = screenState.engine.name,
            strings = LocalUiStrings.current,
            onResume = {
                showResumeDialog = false
                dispatch(GameUiEvent.ResumeSavedSession(savedSessionToPrompt))
            },
            onDismiss = {
                showResumeDialog = false
                dispatch(GameUiEvent.DismissResumePrompt)
            },
        )
    }

    BackHandler(enabled = currentDestination != ScreenDestination.Home && currentDestination != ScreenDestination.Onboarding) {
        if (currentDestination == ScreenDestination.InGame && !isGameEnded) {
            showResignConfirmFromBack = true
        } else {
            exitToHome()
        }
    }

    if (showResignConfirmFromBack) {
        val strings = LocalUiStrings.current
        AlertDialog(
            onDismissRequest = { showResignConfirmFromBack = false },
            title = { Text(strings.resignConfirmTitle) },
            text = { Text(strings.resignConfirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResignConfirmFromBack = false
                        dispatch(GameUiEvent.ResignCurrentGame)
                        exitToHome()
                    },
                ) {
                    Text(strings.resign)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirmFromBack = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    DirectPlayRecommendationDialog(
        boardSize = settingsState.boardSize,
        isDirectPlayEnabled = uxOptions.isDirectPlayEnabled,
        onConfirm = { enabled -> uxOptions = uxOptions.copy(isDirectPlayEnabled = enabled) }
    )

    val premiumUiState = PremiumUiState(
        // 재구성 시점의 현재 시각으로 매번 평가한다(별도 tick 없이, 대국 중 재구성이 충분히 잦아 문제없음).
        isActive = premiumState.isActive(nowMillis = System.currentTimeMillis()),
        isPurchased = premiumState.source == PremiumSource.Purchase,
        adGrantExpiresAtMillis = premiumState.adGrantStartedAtMillis?.plus(PremiumState.AdGrantDurationMillis),
        resolve = { featureId -> FeatureAccessPolicy.resolve(featureId, premiumState, System.currentTimeMillis()) },
        // claimedFeatures를 이어붙인다 — 안 그러면 클레임이 조용히 사라진다.
        setPurchased = { purchased ->
            premiumState = (if (purchased) PremiumState.purchased() else PremiumState()).copy(claimedFeatures = premiumState.claimedFeatures)
            premiumStateStore.save(premiumState)
        },
        purchasePremium = {
            val (outcome, nextState) = performPremiumPurchase(context, diagnosticEventLog)
            nextState?.let { premiumState = it.copy(claimedFeatures = premiumState.claimedFeatures); premiumStateStore.save(premiumState) }
            outcome
        },
        activateAdGrant = {
            val (outcome, nextState) = performPremiumAdGrant(context, diagnosticEventLog)
            nextState?.let { premiumState = it.copy(claimedFeatures = premiumState.claimedFeatures); premiumStateStore.save(premiumState) }
            outcome
        },
        claim = { featureId -> premiumState = premiumState.copy(claimedFeatures = premiumState.claimedFeatures + featureId); premiumStateStore.save(premiumState) },
    )

    PremiumPurchaseRestoreEffect(context, diagnosticEventLog) { nextState ->
        premiumState = nextState
        premiumStateStore.save(nextState)
    }

    // 프리미엄이 비활성 상태가 될 때(활성화 안 함 선택, 만료 등) 형세보기/추천수의
    // "켜짐" 상태값 자체를 꺼서, 버튼만 잠기고 기능은 이전 값대로 계속 동작하는 걸 방지한다.
    // 진단 로그 내용 자체는 순수 함수(buildPremiumDeactivatedDiagnosticEvent)가 판정한다.
    LaunchedEffect(premiumUiState.isActive) {
        if (!premiumUiState.isActive) {
            uxOptions = uxOptions.copy(showOwnershipOverlay = false)
            controllers.topMovesController.hide()
            buildPremiumDeactivatedDiagnosticEvent(premiumState, System.currentTimeMillis())
                ?.let(diagnosticEventLog::append)
        }
    }

    CompositionLocalProvider(LocalPremiumUiState provides premiumUiState) {
    when (currentDestination) {
        ScreenDestination.Onboarding -> {
            OnboardingScreen(
                authClient = authClient,
                deviceIdentityStore = deviceIdentityStore,
                credentialManagerClient = credentialManagerClient,
                diagnosticEventLog = diagnosticEventLog,
                onOnboardingComplete = {
                    preferencesStore.save(initialPreferences.copy(hasSeenOnboarding = true))
                    currentDestination = ScreenDestination.Home
                },
            )
        }
        ScreenDestination.Home -> {
            GoCoachHomeScreen(
                onStartMatchClick = {
                    dispatch(GameUiEvent.DismissResumePrompt)
                    currentDestination = ScreenDestination.GameSetup
                },
                onSettingsClick = { currentDestination = ScreenDestination.Settings },
                onStudyClick = { currentDestination = ScreenDestination.Study },
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                hasResumableSession = savedSessionToPrompt != null,
                onResumeClick = { showResumeDialog = true },
            )
        }
        ScreenDestination.Study -> {
            StudyScreen(onBackClick = { currentDestination = ScreenDestination.Home })
        }
        ScreenDestination.Settings -> {
            SettingsScreen(
                authClient = authClient,
                credentialManagerClient = credentialManagerClient,
                diagnosticEventLog = diagnosticEventLog,
                screenState = screenState,
                onEvent = ::dispatch,
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                onBackClick = { currentDestination = ScreenDestination.Home },
            )
        }
        ScreenDestination.GameSetup -> {
            GameSetupLobby(
                screenState = screenState,
                onEvent = ::dispatch,
                onBackClick = exitToHome,
                onStartMatch = { currentDestination = ScreenDestination.InGame }
            )
        }
        ScreenDestination.InGame -> {
            GoCoachContent(
                screenState = screenState,
                benchmarkProgress = benchmarkUiState.progress,
                benchmarkResult = benchmarkUiState.resultToConfirm,
                onBenchmarkResultConfirmed = { benchmarkUiState = benchmarkUiState.clearConfirmedResult() },
                onBenchmarkRerun = controllers.benchmarkController::rerun,
                onScoreGraphExpandedChange = { expanded -> isScoreGraphExpanded = expanded },
                onFinalJudgementReview = ::activateEndgameJudgementReview,
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                turnTimeState = turnTimeState,
                onEvent = ::dispatch,
            )
        }
    }
    }
}

internal enum class ScreenDestination {
    Onboarding,
    Home,
    Settings,
    Study,
    GameSetup,
    InGame
}
