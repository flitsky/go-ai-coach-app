package com.worksoc.goaicoach.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.application.AnalysisCacheKey
import com.worksoc.goaicoach.application.AnalysisResultCache
import com.worksoc.goaicoach.application.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.MoveReviewMarker
import com.worksoc.goaicoach.application.AutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.buildDebugReport
import com.worksoc.goaicoach.application.buildAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.buildEndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.buildEngineEstimateDisplayPlan
import com.worksoc.goaicoach.application.buildEngineStartupFailureDisplayPlan
import com.worksoc.goaicoach.application.buildEngineStartupSuccessDisplayPlan
import com.worksoc.goaicoach.application.buildEngineUndoPlan
import com.worksoc.goaicoach.application.buildHumanEngineSyncFailurePlan
import com.worksoc.goaicoach.application.buildHumanEngineSyncSuccessPlan
import com.worksoc.goaicoach.application.buildLocalFinalScoreDisplayPlan
import com.worksoc.goaicoach.application.buildLocalTwoPlayerUndoPlan
import com.worksoc.goaicoach.application.buildUndoRequestPlan
import com.worksoc.goaicoach.application.buildCachedTopMoveAnalysisUpdate
import com.worksoc.goaicoach.application.buildNewLocalGameSessionPlan
import com.worksoc.goaicoach.application.buildResolvedEndgameDisplayPlan
import com.worksoc.goaicoach.application.buildSavedGameRestoreRequestPlan
import com.worksoc.goaicoach.application.buildSavedSessionCheckPlan
import com.worksoc.goaicoach.application.buildSavedSessionDismissPlan
import com.worksoc.goaicoach.application.buildScoreEstimateRequestPlan
import com.worksoc.goaicoach.application.buildScoringRuleChangePlan
import com.worksoc.goaicoach.application.buildStartConfiguredGamePlan
import com.worksoc.goaicoach.application.buildTopMoveAnalysisPlan
import com.worksoc.goaicoach.application.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.application.buildPlayerSetupChangePlan
import com.worksoc.goaicoach.application.buildUserPreferencesSnapshot
import com.worksoc.goaicoach.application.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.EngineBenchmarkDefaultSamplesPerVisit
import com.worksoc.goaicoach.application.EngineBenchmarkDefaultTimeCapMs
import com.worksoc.goaicoach.application.EngineBenchmarkDefaultVisits
import com.worksoc.goaicoach.application.EngineBenchmarkMeasurementVersion
import com.worksoc.goaicoach.application.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.EngineSessionClient
import com.worksoc.goaicoach.application.applyHumanMoveLocally
import com.worksoc.goaicoach.application.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.GameSessionResetPlan
import com.worksoc.goaicoach.application.HumanEngineSyncFailurePlan
import com.worksoc.goaicoach.application.HumanEngineSyncDisplayPlan
import com.worksoc.goaicoach.application.EngineStartupDisplayPlan
import com.worksoc.goaicoach.application.PlayerSetupChangePlan
import com.worksoc.goaicoach.application.localScoreSnapshot
import com.worksoc.goaicoach.application.planShowTopMoves
import com.worksoc.goaicoach.application.selectRuntimePlayLevel
import com.worksoc.goaicoach.application.shouldRequestAiTurn
import com.worksoc.goaicoach.application.shouldRequestTopMoveAnalysis
import com.worksoc.goaicoach.application.planSavedGamePersistence
import com.worksoc.goaicoach.application.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.runTopMoveAnalysis
import com.worksoc.goaicoach.application.ScoringRuleChangePlan
import com.worksoc.goaicoach.application.runRestoredGameSyncDisplayPlan
import com.worksoc.goaicoach.application.runAutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.runScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.runScoringRuleSyncDisplayPlan
import com.worksoc.goaicoach.application.runtimeAiTurnBeginLog
import com.worksoc.goaicoach.application.runtimeAiTurnCompleteLog
import com.worksoc.goaicoach.application.runtimeAiTurnEndgameDetectedLog
import com.worksoc.goaicoach.application.runtimeAiTurnEndgameFailureLog
import com.worksoc.goaicoach.application.runtimeAiTurnEndgameSuccessLog
import com.worksoc.goaicoach.application.runtimeAiTurnFailureLog
import com.worksoc.goaicoach.application.runtimeAiTurnScheduleCancelledLog
import com.worksoc.goaicoach.application.runtimeAiTurnScheduleLog
import com.worksoc.goaicoach.application.runtimeAiTurnSuccessLog
import com.worksoc.goaicoach.application.runtimeAppStartLog
import com.worksoc.goaicoach.application.runtimeAutoPlayDelayChangeLog
import com.worksoc.goaicoach.application.runtimeEngineGameStartFailureLog
import com.worksoc.goaicoach.application.runtimeEngineGameStartRequestLog
import com.worksoc.goaicoach.application.runtimeEngineGameStartSuccessLog
import com.worksoc.goaicoach.application.runtimeGameResetLog
import com.worksoc.goaicoach.application.ShowTopMovesPlan
import com.worksoc.goaicoach.application.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.ScoreEstimateRequestPlan
import com.worksoc.goaicoach.application.SavedGameRestorePlan
import com.worksoc.goaicoach.application.SavedGameRestoreRequestPlan
import com.worksoc.goaicoach.application.SavedGamePersistencePlan
import com.worksoc.goaicoach.application.SavedSessionPromptPlan
import com.worksoc.goaicoach.application.StartConfiguredGamePlan
import com.worksoc.goaicoach.application.TopMoveAnalysisUpdate
import com.worksoc.goaicoach.application.UndoRequestPlan
import com.worksoc.goaicoach.application.UndoLocalStatePlan
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.persistence.EngineBenchmarkStore
import com.worksoc.goaicoach.persistence.RuntimeEventLog
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.persistence.UserPreferencesSnapshot
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.presentation.GameScreenStateInput
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.GameUiEventHandlers
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.presentation.buildGameScreenState
import com.worksoc.goaicoach.presentation.dispatchGameUiEvent
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun GoCoachApp(
    engineClient: EngineSessionClient,
    engineName: String,
    engineDiagnostic: String,
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2F6B4F),
            secondary = Color(0xFF546E7A),
            background = Color(0xFFF7F4EC),
            surface = Color(0xFFFFFCF4),
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            GoCoachScreen(
                engineClient = engineClient,
                engineName = engineName,
                engineDiagnostic = engineDiagnostic,
            )
        }
    }
}

@Composable
private fun GoCoachScreen(
    engineClient: EngineSessionClient,
    engineName: String,
    engineDiagnostic: String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionStore = remember(context) { GameSessionStore(context) }
    val preferencesStore = remember(context) { UserPreferencesStore(context) }
    val benchmarkStore = remember(context) { EngineBenchmarkStore(context) }
    val runtimeEventLog = remember(context) {
        RuntimeEventLog(File(context.filesDir, RuntimeEventLog.FileName))
    }
    val initialPreferences = remember(preferencesStore) { preferencesStore.load() }
    val defaultPlayLevel = remember { PlayLevelSetting() }
    val initialPlan = remember(initialPreferences, defaultPlayLevel) {
        buildInitialUserPreferencesPlan(
            preferences = initialPreferences,
            defaultPlayLevel = defaultPlayLevel,
            currentProfile = EngineProfile(),
        )
    }
    var gameState by remember { mutableStateOf(initialPlan.gameState) }
    var engineMessage by remember { mutableStateOf("Engine not initialized.") }
    var candidateText by remember { mutableStateOf(engineDiagnostic) }
    var candidateMoves by remember { mutableStateOf(emptyList<CandidateMove>()) }
    var reviewCandidateMoves by remember { mutableStateOf(emptyList<CandidateMove>()) }
    var reviewAnalysis by remember { mutableStateOf(MoveAnalysisSnapshot.empty(gameState)) }
    var scoreText by remember { mutableStateOf("No score estimate yet.") }
    var scoreEstimate by remember { mutableStateOf<ScoreEstimate?>(null) }
    var scoreSnapshots by remember {
        mutableStateOf(listOf(localScoreSnapshot(initialPlan.gameState)))
    }
    var moveReviewText by remember { mutableStateOf("No move review yet.") }
    var moveReviews by remember { mutableStateOf(emptyList<MoveReviewMarker>()) }
    var lastMoveText by remember { mutableStateOf("None") }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }
    var playLevel by remember { mutableStateOf(initialPlan.runtime.playLevel) }
    var engineProfile by remember { mutableStateOf(initialPlan.runtime.engineProfile) }
    var playerSetup by remember { mutableStateOf(initialPlan.playerSetup) }
    val matchMode = playerSetup.matchMode()
    var autoPlayDelaySetting by remember { mutableStateOf(initialPlan.autoPlayDelaySetting) }
    var searchTimeSettings by remember { mutableStateOf(initialPreferences.searchTimeSettings.normalized()) }
    var topMovesEnabled by remember { mutableStateOf(initialPlan.topMovesEnabled) }
    var analysisPreset by remember { mutableStateOf(initialPlan.runtime.analysisPreset) }
    val analysisCache = remember { AnalysisResultCache(maxEntries = 96) }
    var uxOptions by remember { mutableStateOf(initialPreferences.toKaTrainUxOptions()) }
    var isDisplayMenuExpanded by remember { mutableStateOf(false) }
    var isScoreGraphExpanded by remember { mutableStateOf(false) }
    var lastAnalysisKey by remember { mutableStateOf<AnalysisCacheKey?>(null) }
    var isGameEnded by remember { mutableStateOf(false) }
    var endgameLog by remember { mutableStateOf("No endgame result recorded.") }
    var hasCompletedEngineStartup by remember { mutableStateOf(false) }
    var hasCheckedSavedSession by remember { mutableStateOf(false) }
    var hasCheckedEngineBenchmark by remember {
        mutableStateOf(
            benchmarkStore.hasUsableProfile(
                samplesPerVisit = EngineBenchmarkDefaultSamplesPerVisit,
                timeCapMs = EngineBenchmarkDefaultTimeCapMs,
                measurementVersion = EngineBenchmarkMeasurementVersion,
                visitsTargets = EngineBenchmarkDefaultVisits,
            ),
        )
    }
    var engineBenchmarkText by remember { mutableStateOf(benchmarkStore.loadText()) }
    var searchTimeBenchmarkAverages by remember {
        mutableStateOf(benchmarkStore.load()?.averageMillisByVisits().orEmpty())
    }
    var benchmarkProgress by remember { mutableStateOf<EngineBenchmarkProgress?>(null) }
    var benchmarkResultToConfirm by remember { mutableStateOf<EngineBenchmarkProfile?>(null) }
    var pendingSavedSession by remember { mutableStateOf<SavedGameSnapshot?>(null) }
    var shouldShowResumePrompt by remember { mutableStateOf(false) }
    var isAutoAiTurnPending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runtimeEventLog.append(runtimeAppStartLog(engineName, engineDiagnostic))
    }

    fun applyEngineStartupDisplayPlan(startup: EngineStartupDisplayPlan) {
        isEngineReady = startup.isEngineReady
        if (startup.scoreSnapshots.isNotEmpty()) {
            scoreSnapshots = startup.scoreSnapshots
        }
        engineMessage = startup.engineMessage
        startup.candidateText?.let { text -> candidateText = text }
    }

    fun applySavedSessionPromptPlan(prompt: SavedSessionPromptPlan) {
        pendingSavedSession = prompt.pendingSavedSession
        shouldShowResumePrompt = prompt.shouldShowResumePrompt
        hasCheckedSavedSession = prompt.hasCheckedSavedSession
    }

    LaunchedEffect(engineClient) {
        hasCompletedEngineStartup = false
        isEngineBusy = true
        runCatching {
            withContext(Dispatchers.IO) {
                engineClient.startSession(engineProfile, gameState)
            }
        }.onSuccess { result ->
            applyEngineStartupDisplayPlan(
                buildEngineStartupSuccessDisplayPlan(
                    state = gameState,
                    result = result,
                ),
            )
        }.onFailure { error ->
            applyEngineStartupDisplayPlan(
                buildEngineStartupFailureDisplayPlan(
                    errorMessage = error.message,
                    engineDiagnostic = engineDiagnostic,
                ),
            )
        }
        isEngineBusy = false
        hasCompletedEngineStartup = true
    }

    LaunchedEffect(sessionStore) {
        applySavedSessionPromptPlan(buildSavedSessionCheckPlan(sessionStore.load()))
    }

    suspend fun runEngineBenchmark() {
        if (!isEngineReady) {
            engineMessage = "Engine benchmark requires a ready local engine."
            return
        }
        if (!engineClient.capabilities.supportsDeviceBenchmark) {
            engineMessage = "Engine benchmark is available only for the local KataGo process engine."
            return
        }
        if (isEngineBusy || benchmarkProgress != null) {
            engineMessage = "Engine is busy. Run benchmark after the current response."
            return
        }

        benchmarkResultToConfirm = null
        isEngineBusy = true
        engineMessage = "엔진 벤치마크 시작 전 안정화 대기 중입니다."
        benchmarkProgress = EngineBenchmarkProgress(
            currentVisits = EngineBenchmarkDefaultVisits.first(),
            currentSample = 1,
            samplesPerVisit = EngineBenchmarkDefaultSamplesPerVisit,
            completedCalls = 0,
            totalCalls = EngineBenchmarkDefaultVisits.size * EngineBenchmarkDefaultSamplesPerVisit,
            stageOverride = "엔진 안정화 대기 중...",
        )
        candidateText = "Engine benchmark waiting for startup settle delay."
        delay(EngineBenchmarkStartupSettleDelayMillis)

        engineMessage = "최초 실행환경에서 최적 플레이를 위해 벤치마크 테스트가 진행중입니다."
        candidateText = "Engine benchmark running: B16/B32/B64, ${EngineBenchmarkDefaultSamplesPerVisit} samples each."
        runCatching {
            withContext(Dispatchers.IO) {
                engineClient
                    .runStartupBenchmark(
                        restoreState = gameState,
                        nowMillis = System.currentTimeMillis(),
                        onProgress = { progress ->
                            withContext(Dispatchers.Main) {
                                benchmarkProgress = progress
                                engineMessage = progress.stageText
                                candidateText = "Engine benchmark running: ${progress.progressText}, ${progress.sampleText}."
                            }
                        },
                    )
                    .also { profile -> benchmarkStore.save(profile) }
            }
        }.onSuccess { profile ->
            engineBenchmarkText = benchmarkStore.loadText()
            searchTimeBenchmarkAverages = profile.averageMillisByVisits()
            engineMessage = "Engine benchmark saved to ${benchmarkStore.path()}."
            candidateText = "Engine benchmark complete.\n${profile.toSummaryText()}"
            benchmarkResultToConfirm = profile
        }.onFailure { error ->
            engineMessage = "Engine benchmark failed: ${error.message ?: "unknown error"}"
            candidateText = "Engine benchmark failed. The app will continue with built-in defaults."
        }
        benchmarkProgress = null
        isEngineBusy = false
    }

    fun showEngineBenchmarkResult() {
        benchmarkStore.load()?.let { profile ->
            benchmarkResultToConfirm = profile
            return
        }
        scope.launch {
            runEngineBenchmark()
        }
    }

    fun rerunEngineBenchmark() {
        benchmarkResultToConfirm = null
        scope.launch {
            runEngineBenchmark()
        }
    }

    LaunchedEffect(
        hasCompletedEngineStartup,
        isEngineReady,
    ) {
        if (
            !hasCompletedEngineStartup ||
            !isEngineReady ||
            !engineClient.capabilities.supportsDeviceBenchmark ||
            hasCheckedEngineBenchmark
        ) {
            return@LaunchedEffect
        }
        if (
            benchmarkStore.hasUsableProfile(
                samplesPerVisit = EngineBenchmarkDefaultSamplesPerVisit,
                timeCapMs = EngineBenchmarkDefaultTimeCapMs,
                measurementVersion = EngineBenchmarkMeasurementVersion,
                visitsTargets = EngineBenchmarkDefaultVisits,
            )
        ) {
            hasCheckedEngineBenchmark = true
            engineBenchmarkText = benchmarkStore.loadText()
            searchTimeBenchmarkAverages = benchmarkStore.load()?.averageMillisByVisits().orEmpty()
            return@LaunchedEffect
        }

        hasCheckedEngineBenchmark = true
        runEngineBenchmark()
    }

    LaunchedEffect(
        preferencesStore,
        playerSetup,
        autoPlayDelaySetting,
        searchTimeSettings,
        topMovesEnabled,
        uxOptions,
        gameState.ruleset,
    ) {
        preferencesStore.save(
            buildUserPreferencesSnapshot(
                playerSetup = playerSetup,
                ruleset = gameState.ruleset,
                topMovesEnabled = topMovesEnabled,
                showCoordinates = uxOptions.showCoordinates,
                showMoveNumbers = uxOptions.showMoveNumbers,
                showLastMoveRing = uxOptions.showLastMoveRing,
                showOwnershipOverlay = uxOptions.showOwnershipOverlay,
                autoPlayDelaySetting = autoPlayDelaySetting,
                searchTimeSettings = searchTimeSettings,
            ),
        )
    }

    LaunchedEffect(
        hasCheckedSavedSession,
        shouldShowResumePrompt,
        isGameEnded,
        gameState.moves.size,
        gameState.ruleset,
        playerSetup,
        playLevel,
        topMovesEnabled,
    ) {
        when (
            val plan = planSavedGamePersistence(
                hasCheckedSavedSession = hasCheckedSavedSession,
                shouldShowResumePrompt = shouldShowResumePrompt,
                isGameEnded = isGameEnded,
                gameState = gameState,
                playerSetup = playerSetup,
                playLevel = playLevel,
                topMovesEnabled = topMovesEnabled,
                nowMillis = System.currentTimeMillis(),
            )
        ) {
            SavedGamePersistencePlan.Skip -> Unit
            SavedGamePersistencePlan.Clear -> sessionStore.clear()
            is SavedGamePersistencePlan.Save -> sessionStore.save(plan.snapshot)
        }
    }

    fun clearTopMoveSpots(message: String? = null) {
        candidateMoves = emptyList()
        if (message != null) {
            candidateText = message
        }
    }

    fun clearReviewAnalysis(state: GameState = gameState) {
        reviewAnalysis = MoveAnalysisSnapshot.empty(state)
        reviewCandidateMoves = emptyList()
    }

    fun applyTopMoveAnalysisUpdate(
        update: TopMoveAnalysisUpdate,
        analysisKey: AnalysisCacheKey,
    ) {
        reviewAnalysis = update.snapshot
        reviewCandidateMoves = update.reviewCandidateMoves
        candidateText = update.candidateText
        candidateMoves = update.candidateMoves
        engineMessage = update.engineMessage
        lastAnalysisKey = analysisKey
    }

    fun applyScoreEstimateDisplayPlan(score: ScoreEstimateDisplayPlan) {
        scoreText = score.scoreText
        scoreEstimate = score.scoreEstimate
        scoreSnapshots = score.scoreSnapshots
        engineMessage = score.engineMessage
    }

    fun applyFinalScoreDisplayPlan(final: FinalScoreDisplayPlan) {
        isGameEnded = true
        gameState = final.gameState
        scoreText = final.scoreText
        scoreEstimate = final.scoreEstimate
        scoreSnapshots = final.scoreSnapshots
        endgameLog = final.endgameLog
        engineMessage = final.engineMessage
        candidateText = final.candidateText
    }

    fun applyEndgameFailureDisplayPlan(failure: EndgameFailureDisplayPlan) {
        endgameLog = failure.endgameLog
        engineMessage = failure.engineMessage
        candidateText = failure.candidateText
    }

    fun applyRuntimePlayLevelSelection(runtime: RuntimePlayLevelSelection) {
        playLevel = runtime.playLevel
        engineProfile = runtime.engineProfile
        analysisPreset = runtime.analysisPreset
    }

    fun applyAutoAiTurnDisplayPlan(display: AutoAiTurnDisplayPlan): GameState? {
        playLevel = display.playLevel
        engineProfile = display.profile
        analysisPreset = display.analysisPreset
        gameState = display.gameState
        clearTopMoveSpots()
        clearReviewAnalysis(display.gameState)
        lastAnalysisKey = null
        engineMessage = display.turnEngineMessage
        candidateText = display.candidateText
        lastMoveText = display.lastMoveText
        applyScoreEstimateDisplayPlan(display.scoreDisplay)
        return display.nextAnalysisState
    }

    fun applyHumanEngineSyncDisplayPlan(sync: HumanEngineSyncDisplayPlan): GameState? =
        when (sync) {
            is HumanEngineSyncDisplayPlan.FinalScore -> {
                applyFinalScoreDisplayPlan(sync.display)
                null
            }
            is HumanEngineSyncDisplayPlan.ScoreEstimate -> {
                applyScoreEstimateDisplayPlan(sync.display)
                candidateText = sync.candidateText
                sync.nextAnalysisState
            }
            HumanEngineSyncDisplayPlan.NoUpdate -> null
        }

    fun applyHumanEngineSyncFailurePlan(failure: HumanEngineSyncFailurePlan) {
        scoreSnapshots = failure.scoreSnapshots
        candidateText = failure.candidateText
        engineMessage = failure.engineMessage
    }

    fun applyGameSessionResetPlan(reset: GameSessionResetPlan) {
        gameState = reset.gameState
        isGameEnded = false
        clearTopMoveSpots(reset.candidateText)
        reviewAnalysis = reset.reviewAnalysis
        reviewCandidateMoves = emptyList()
        lastAnalysisKey = null
        scoreText = reset.scoreText
        scoreEstimate = null
        scoreSnapshots = reset.scoreSnapshots
        moveReviewText = reset.moveReviewText
        moveReviews = emptyList()
        lastMoveText = reset.lastMoveText
        endgameLog = reset.endgameLog
        engineMessage = reset.engineMessage
        runtimeEventLog.append(
            runtimeGameResetLog(
                reset = reset,
                playerSetup = playerSetup,
                engineName = engineName,
                autoPlayDelaySetting = autoPlayDelaySetting,
                searchTimeSettings = searchTimeSettings,
            ),
        )
    }

    fun applySavedGameRestorePlan(restore: SavedGameRestorePlan) {
        playerSetup = restore.playerSetup
        applyRuntimePlayLevelSelection(restore.runtime)
        topMovesEnabled = restore.topMovesEnabled
        gameState = restore.gameState
        isGameEnded = false
        clearTopMoveSpots(restore.candidateText)
        reviewAnalysis = restore.reviewAnalysis
        reviewCandidateMoves = emptyList()
        lastAnalysisKey = null
        scoreText = restore.scoreText
        scoreEstimate = null
        scoreSnapshots = restore.scoreSnapshots
        moveReviewText = restore.moveReviewText
        moveReviews = emptyList()
        lastMoveText = restore.lastMoveText
        endgameLog = restore.endgameLog
        engineMessage = restore.engineMessage
    }

    fun applyUndoLocalStatePlan(undo: UndoLocalStatePlan) {
        gameState = undo.gameState
        isGameEnded = false
        clearTopMoveSpots()
        reviewAnalysis = undo.reviewAnalysis
        reviewCandidateMoves = emptyList()
        lastAnalysisKey = null
        moveReviews = undo.moveReviews
        moveReviewText = undo.moveReviewText
        lastMoveText = undo.lastMoveText
        candidateText = undo.candidateText
        scoreText = undo.scoreText
        scoreEstimate = null
        scoreSnapshots = undo.scoreSnapshots
        endgameLog = undo.endgameLog
    }

    fun applyScoringRuleChangePlan(ruleChange: ScoringRuleChangePlan) {
        gameState = ruleChange.gameState
        clearTopMoveSpots(ruleChange.candidateText)
        reviewAnalysis = ruleChange.reviewAnalysis
        reviewCandidateMoves = emptyList()
        lastAnalysisKey = null
        scoreEstimate = null
        scoreText = ruleChange.scoreText
        scoreSnapshots = ruleChange.scoreSnapshots
        endgameLog = ruleChange.endgameLog
        ruleChange.engineMessage?.let { message -> engineMessage = message }
    }

    fun changePlayerSetup(nextSetup: PlayerSetup) {
        when (
            val plan = buildPlayerSetupChangePlan(
                nextSetup = nextSetup,
                currentState = gameState,
                currentProfile = engineProfile,
                defaultPlayLevel = defaultPlayLevel,
                isEngineBusy = isEngineBusy,
                searchTimeSettings = searchTimeSettings,
            )
        ) {
            is PlayerSetupChangePlan.ShowMessage -> {
                engineMessage = plan.message
            }
            is PlayerSetupChangePlan.Apply -> {
                playerSetup = plan.playerSetup
                applyRuntimePlayLevelSelection(plan.runtime)
                clearTopMoveSpots(plan.topMoveClearMessage)
                reviewAnalysis = plan.reviewAnalysis
                reviewCandidateMoves = emptyList()
                lastAnalysisKey = null
            }
        }
    }

    fun changeSearchTimeSettings(nextSettings: SearchTimeSettings) {
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Change search time after the current action."
            return
        }
        val normalized = nextSettings.normalized()
        searchTimeSettings = normalized
        applyRuntimePlayLevelSelection(
            selectRuntimePlayLevel(
                setup = playerSetup,
                nextPlayer = gameState.nextPlayer,
                currentProfile = engineProfile,
                defaultPlayLevel = defaultPlayLevel,
                searchTimeSettings = normalized,
            ),
        )
        clearTopMoveSpots("Search time changed. Analysis cache will rebuild with the new time cap.")
        clearReviewAnalysis(gameState)
        lastAnalysisKey = null
    }

    fun requestTopMoveAnalysisForState(
        targetState: GameState,
        automatic: Boolean,
        deep: Boolean = false,
    ) {
        if (
            !shouldRequestTopMoveAnalysis(
                isGameEnded = isGameEnded,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                shouldShowResumePrompt = shouldShowResumePrompt,
                playerSetup = playerSetup,
                targetState = targetState,
            )
        ) {
            return
        }

        val plan = buildTopMoveAnalysisPlan(
            targetState = targetState,
            engineProfile = engineProfile,
            analysisPreset = analysisPreset,
            deep = deep,
        )
        if (automatic && plan.analysisKey == lastAnalysisKey) {
            if (topMovesEnabled && candidateMoves.isEmpty() && reviewAnalysis.scoredPlayCount > 0) {
                candidateMoves = reviewAnalysis.candidatesForDisplay()
            }
            return
        }
        analysisCache.get(plan.analysisKey)?.let { cached ->
            val update = buildCachedTopMoveAnalysisUpdate(
                targetState = targetState,
                cacheKey = plan.analysisKey,
                cached = cached,
                topMovesEnabled = topMovesEnabled,
            )
            applyTopMoveAnalysisUpdate(update, plan.analysisKey)
            return
        }

        lastAnalysisKey = plan.analysisKey
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runTopMoveAnalysis(
                        targetState = targetState,
                        engineProfile = engineProfile,
                        analysisPreset = analysisPreset,
                        plan = plan,
                        deep = deep,
                        topMovesEnabled = topMovesEnabled,
                        cacheEnabled = analysisCache.isEnabled,
                    )
                }
            }.onSuccess { update ->
                applyTopMoveAnalysisUpdate(update, plan.analysisKey)
                update.cachedResult?.let { cached ->
                    analysisCache.put(plan.analysisKey, cached)
                }
            }.onFailure { error ->
                engineMessage = error.message ?: "Top Moves analysis failed."
                clearReviewAnalysis(targetState)
                lastAnalysisKey = null
                if (topMovesEnabled) {
                    clearTopMoveSpots("Top Moves analysis failed.")
                }
            }
            isEngineBusy = false
        }
    }

    fun showTopMovesForCurrentState() {
        if (
            !shouldRequestTopMoveAnalysis(
                isGameEnded = isGameEnded,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                shouldShowResumePrompt = shouldShowResumePrompt,
                playerSetup = playerSetup,
                targetState = gameState,
            )
        ) {
            topMovesEnabled = false
            clearTopMoveSpots()
            engineMessage = "Top Moves is available only on human turns."
            return
        }
        topMovesEnabled = true
        when (
            val plan = planShowTopMoves(
                reviewAnalysis = reviewAnalysis,
                lastAnalysisKey = lastAnalysisKey,
                currentPlan = buildTopMoveAnalysisPlan(
                    targetState = gameState,
                    engineProfile = engineProfile,
                    analysisPreset = analysisPreset,
                    deep = false,
                ),
                analysisPreset = analysisPreset,
                isEngineBusy = isEngineBusy,
            )
        ) {
            is ShowTopMovesPlan.ShowCached -> {
                candidateMoves = plan.candidateMoves
                engineMessage = plan.engineMessage
            }
            is ShowTopMovesPlan.RequestAnalysis -> {
                candidateMoves = plan.candidateMoves
                plan.engineMessage?.let { message -> engineMessage = message }
                requestTopMoveAnalysisForState(
                    targetState = gameState,
                    automatic = false,
                    deep = plan.deep,
                )
            }
        }
    }

    fun hideTopMoves() {
        topMovesEnabled = false
        clearTopMoveSpots()
        engineMessage = "Top Moves hidden. Background move review keeps using fast best-1 analysis."
    }

    fun changeScoringRule(nextRuleset: Ruleset) {
        if (nextRuleset == gameState.ruleset) {
            return
        }
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Change scoring rule after the current response."
            return
        }

        val ruleChange = buildScoringRuleChangePlan(
            currentState = gameState,
            nextRuleset = nextRuleset,
            isGameEnded = isGameEnded,
            matchMode = matchMode,
            isEngineReady = isEngineReady,
            previousSnapshots = scoreSnapshots,
        )
        val nextState = ruleChange.gameState
        applyScoringRuleChangePlan(ruleChange)

        if (!ruleChange.requiresEngineSync) {
            return
        }

        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runScoringRuleSyncDisplayPlan(
                        state = nextState,
                        profile = engineProfile,
                        previousSnapshots = scoreSnapshots,
                        engineMessage = "Scoring rule changed to ${nextRuleset.scoringLabel}; engine rules synchronized.",
                    )
                }
            }.onSuccess { score ->
                applyScoreEstimateDisplayPlan(score)
            }.onFailure { error ->
                engineMessage = error.message ?: "Scoring rule changed, but engine rule sync failed."
            }
            isEngineBusy = false
            requestTopMoveAnalysisForState(
                targetState = gameState,
                automatic = true,
            )
        }
    }

    fun resetLocalGame(
        message: String,
        ruleset: Ruleset = gameState.ruleset,
    ) {
        applyGameSessionResetPlan(buildNewLocalGameSessionPlan(message, ruleset))
    }

    fun restoreSavedSession(snapshot: SavedGameSnapshot) {
        val request = buildSavedGameRestoreRequestPlan(
            snapshot = snapshot,
            currentProfile = engineProfile,
            defaultPlayLevel = defaultPlayLevel,
            isEngineBusy = isEngineBusy,
            isEngineReady = isEngineReady,
            searchTimeSettings = searchTimeSettings,
        )
        if (request is SavedGameRestoreRequestPlan.ShowMessage) {
            engineMessage = request.message
            return
        }

        val restoreRequest = request as SavedGameRestoreRequestPlan.Restore
        val restore = restoreRequest.restore
        val restoredState = restore.gameState
        val restoredProfile = restore.runtime.engineProfile
        if (restoreRequest.syncEngineAfterRestore) {
            isEngineBusy = true
        }

        applySavedGameRestorePlan(restore)

        if (!restoreRequest.syncEngineAfterRestore) {
            return
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runRestoredGameSyncDisplayPlan(
                        state = restoredState,
                        profile = restoredProfile,
                    )
                }
            }.onSuccess { score ->
                applyScoreEstimateDisplayPlan(score)
            }.onFailure { error ->
                engineMessage = error.message ?: "Saved game restored locally, but engine sync failed."
            }
            isEngineBusy = false
            requestTopMoveAnalysisForState(
                targetState = restoredState,
                automatic = true,
            )
        }
    }

    fun requestEngineScoreEstimate(plan: ScoreEstimateRequestPlan.RequestEngineEstimate) {
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.runScoreEstimateDisplayPlan(
                        request = plan,
                        previousSnapshots = scoreSnapshots,
                    )
                }
            }.onSuccess { score ->
                applyScoreEstimateDisplayPlan(score)
            }.onFailure { error ->
                engineMessage = error.message ?: "Score estimate failed."
                scoreEstimate = null
            }
            isEngineBusy = false
        }
    }

    fun requestScoreEstimate() {
        val plan = buildScoreEstimateRequestPlan(
            state = gameState,
            previousSnapshots = scoreSnapshots,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            matchMode = matchMode,
            engineProfile = engineProfile,
        )
        when (plan) {
            is ScoreEstimateRequestPlan.ShowMessage -> {
                engineMessage = plan.message
            }
            is ScoreEstimateRequestPlan.ShowLocalEstimate -> {
                applyScoreEstimateDisplayPlan(plan.display)
            }
            is ScoreEstimateRequestPlan.RequestEngineEstimate -> {
                requestEngineScoreEstimate(plan)
            }
        }
    }

    fun startEngineBackedNewGame(plan: StartConfiguredGamePlan.StartEngineGame) {
        val targetRuleset = plan.ruleset
        val runtime = plan.runtime
        applyRuntimePlayLevelSelection(runtime)
        runtimeEventLog.append(
            runtimeEngineGameStartRequestLog(
                ruleset = targetRuleset,
                playerSetup = playerSetup,
                engineName = engineName,
                runtime = runtime,
                autoPlayDelaySetting = autoPlayDelaySetting,
                searchTimeSettings = searchTimeSettings,
            ),
        )
        scope.launch {
            isEngineBusy = true
            var nextAnalysisState: GameState? = null
            val startMillis = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.startNewGame(runtime.engineProfile, BoardSize.Nine, targetRuleset)
                }
            }.onSuccess { result ->
                runtimeEventLog.append(
                    runtimeEngineGameStartSuccessLog(
                        elapsedMs = System.currentTimeMillis() - startMillis,
                        message = result.message,
                    ),
                )
                resetLocalGame(result.message, targetRuleset)
                scoreSnapshots = listOf(result.scoreSnapshot ?: localScoreSnapshot(gameState))
                nextAnalysisState = gameState
            }.onFailure { error ->
                runtimeEventLog.append(
                    runtimeEngineGameStartFailureLog(
                        elapsedMs = System.currentTimeMillis() - startMillis,
                        error = error,
                    ),
                )
                resetLocalGame(error.message ?: "New AI game failed.", targetRuleset)
            }
            isEngineBusy = false
            requestTopMoveAnalysisForState(
                targetState = nextAnalysisState ?: gameState,
                automatic = true,
            )
        }
    }

    fun startConfiguredGame() {
        val targetRuleset = gameState.ruleset
        val targetSetup = playerSetup
        when (
            val plan = buildStartConfiguredGamePlan(
                setup = targetSetup,
                ruleset = targetRuleset,
                nextPlayer = gameState.nextPlayer,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                currentProfile = engineProfile,
                defaultPlayLevel = defaultPlayLevel,
                searchTimeSettings = searchTimeSettings,
            )
        ) {
            is StartConfiguredGamePlan.ShowMessage -> {
                engineMessage = plan.message
                return
            }
            is StartConfiguredGamePlan.ResetLocalGame -> {
                resetLocalGame(plan.message, plan.ruleset)
                return
            }
            is StartConfiguredGamePlan.StartEngineGame -> {
                startEngineBackedNewGame(plan)
            }
        }
    }

    fun changeAutoPlayDelay(setting: AutoPlayDelaySetting) {
        runtimeEventLog.append(
            runtimeAutoPlayDelayChangeLog(
                from = autoPlayDelaySetting,
                to = setting,
            ),
        )
        autoPlayDelaySetting = setting
    }

    fun requestAiTurnForCurrentState() {
        when (
            val request = buildAutoAiTurnRequestPlan(
                isGameEnded = isGameEnded,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                isAutoAiTurnPending = isAutoAiTurnPending,
                shouldShowResumePrompt = shouldShowResumePrompt,
                playerSetup = playerSetup,
                gameState = gameState,
                autoPlayDelaySetting = autoPlayDelaySetting,
            )
        ) {
            AutoAiTurnRequestPlan.Skip -> return
            is AutoAiTurnRequestPlan.Schedule -> {
                isAutoAiTurnPending = true
                runtimeEventLog.append(
                    runtimeAiTurnScheduleLog(
                        gameState = gameState,
                        delayMillis = request.delayMillis,
                        autoPlayDelaySetting = autoPlayDelaySetting,
                        isEngineBusy = isEngineBusy,
                    ),
                )
                scope.launch {
                    if (request.delayMillis > 0L) {
                        delay(request.delayMillis)
                    }
                    if (
                        !shouldRequestAiTurn(
                            isGameEnded = isGameEnded,
                            isEngineReady = isEngineReady,
                            isEngineBusy = isEngineBusy,
                            shouldShowResumePrompt = shouldShowResumePrompt,
                            playerSetup = playerSetup,
                            gameState = gameState,
                        )
                    ) {
                        runtimeEventLog.append(
                            runtimeAiTurnScheduleCancelledLog(
                                gameState = gameState,
                                isEngineReady = isEngineReady,
                                isEngineBusy = isEngineBusy,
                                isGameEnded = isGameEnded,
                                shouldShowResumePrompt = shouldShowResumePrompt,
                            ),
                        )
                        isAutoAiTurnPending = false
                        return@launch
                    }

                    val turnState = gameState
                    val aiPlayer = turnState.nextPlayer
                    val side = playerSetup.sideFor(aiPlayer)
                    val aiPlayLevel = side.playLevel
                    val aiLimit = aiPlayLevel.analysisLimitWith(searchTimeSettings)
                    val isolateSearchCache = playerSetup.isAutoPlay()
                    val previousReviewCandidates = reviewCandidateMoves
                    val turnStartMillis = System.currentTimeMillis()
                    runtimeEventLog.append(
                        runtimeAiTurnBeginLog(
                            turnState = turnState,
                            aiPlayer = aiPlayer,
                            playLevel = aiPlayLevel,
                            analysisLimit = aiLimit,
                            delayMillis = request.delayMillis,
                            isolateSearchCache = isolateSearchCache,
                        ),
                    )
                    isEngineBusy = true
                    var nextAnalysisState: GameState? = null
                    runCatching {
                        withContext(Dispatchers.IO) {
                            engineClient.runAutoAiTurnDisplayPlan(
                                currentState = turnState,
                                playLevel = aiPlayLevel,
                                currentProfile = engineProfile,
                                searchTimeSettings = searchTimeSettings,
                                isolateSearchCache = isolateSearchCache,
                                previousSnapshots = scoreSnapshots,
                                previousReviewCandidates = previousReviewCandidates,
                            )
                        }
                    }.onSuccess { display ->
                        runtimeEventLog.append(
                            runtimeAiTurnSuccessLog(
                                turnState = turnState,
                                aiPlayer = aiPlayer,
                                display = display,
                                turnElapsedMs = System.currentTimeMillis() - turnStartMillis,
                            ),
                        )
                        nextAnalysisState = applyAutoAiTurnDisplayPlan(display)
                        if (display.shouldResolveEndgame) {
                            isGameEnded = true
                            runtimeEventLog.append(
                                runtimeAiTurnEndgameDetectedLog(display.gameState),
                            )
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    engineClient.resolveEndgameForState(
                                        state = display.gameState,
                                        profile = display.profile,
                                        prePassCandidates = display.endgamePrePassCandidates,
                                    )
                                }
                            }.onSuccess { endgame ->
                                runtimeEventLog.append(
                                    runtimeAiTurnEndgameSuccessLog(
                                        state = display.gameState,
                                        endgame = endgame,
                                    ),
                                )
                                val final = buildResolvedEndgameDisplayPlan(
                                    source = "auto-ai-engine-dead-stone-cleanup",
                                    originalState = display.gameState,
                                    resolution = endgame,
                                    previousSnapshots = scoreSnapshots,
                                    engineMessagePrefix = display.turnEngineMessage,
                                )
                                applyFinalScoreDisplayPlan(final)
                            }.onFailure { error ->
                                runtimeEventLog.append(
                                    runtimeAiTurnEndgameFailureLog(
                                        state = display.gameState,
                                        error = error,
                                    ),
                                )
                                val failure = buildEndgameFailureDisplayPlan(
                                    source = "auto-ai-engine-final-score-failed",
                                    state = display.gameState,
                                    errorMessage = error.message ?: "Unknown error",
                                    engineMessagePrefix = display.turnEngineMessage,
                                )
                                applyEndgameFailureDisplayPlan(failure)
                            }
                        }
                    }.onFailure { error ->
                        runtimeEventLog.append(
                            runtimeAiTurnFailureLog(
                                turnState = turnState,
                                aiPlayer = aiPlayer,
                                turnElapsedMs = System.currentTimeMillis() - turnStartMillis,
                                error = error,
                            ),
                        )
                        engineMessage = error.message ?: "AI turn failed."
                        candidateText = "AI turn failed. Current board state was not changed."
                    }
                    isEngineBusy = false
                    isAutoAiTurnPending = false
                    runtimeEventLog.append(
                        runtimeAiTurnCompleteLog(
                            gameState = gameState,
                            isEngineBusy = isEngineBusy,
                            isAutoAiTurnPending = isAutoAiTurnPending,
                        ),
                    )
                    nextAnalysisState?.let { state ->
                        requestTopMoveAnalysisForState(
                            targetState = state,
                            automatic = true,
                        )
                    }
                }
            }
        }
    }

    fun submitHumanMove(move: Move) {
        if (playerSetup.sideFor(gameState.nextPlayer).controller != SeatController.Human) {
            engineMessage = "It is not a human player's turn."
            return
        }
        if (move.player != gameState.nextPlayer) {
            engineMessage = "Move player does not match the current turn."
            return
        }
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Wait for the current analysis."
            return
        }

        val beforeMove = gameState
        val previousReviewCandidates = reviewCandidateMoves
        val localMove = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = move,
            reviewAnalysis = reviewAnalysis,
            previousMoveReviews = moveReviews,
        )
            .onFailure { error ->
                engineMessage = error.message ?: "Illegal move."
            }
            .getOrNull()
            ?: return
        val afterMove = localMove.afterMove

        gameState = afterMove
        clearTopMoveSpots()
        moveReviewText = localMove.moveReview.text
        moveReviews = localMove.moveReviews
        clearReviewAnalysis(afterMove)
        lastAnalysisKey = null
        lastMoveText = localMove.lastMoveText
        scoreText = "Score estimate not current."
        scoreEstimate = null

        if (!isEngineReady) {
            scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localMove.localScoreSnapshot)
            val localFinalScore = localMove.localFinalScore
            if (localFinalScore != null) {
                val final = buildLocalFinalScoreDisplayPlan(
                    source = "local-human-consecutive-pass",
                    state = afterMove,
                    finalScore = localFinalScore,
                    previousSnapshots = scoreSnapshots,
                    detail = "triggerMove=${move.describe(beforeMove.boardSize)}",
                    engineMessage = "Local game ended after two passes. ${localFinalScore.status.message}",
                    candidateText = "Game ended after two passes.",
                )
                applyFinalScoreDisplayPlan(final)
            } else {
                candidateText = localMove.capturedText
                engineMessage = "Local move accepted without engine sync: ${move.describe(beforeMove.boardSize)}."
            }
            return
        }

        isEngineBusy = true
        scope.launch {
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.syncAfterHumanMove(
                        afterMove = afterMove,
                        profile = engineProfile,
                        move = move,
                        previousReviewCandidates = previousReviewCandidates,
                    )
                }
            }.onSuccess { result ->
                nextAnalysisState = applyHumanEngineSyncDisplayPlan(
                    buildHumanEngineSyncSuccessPlan(
                        afterMove = afterMove,
                        moveDescription = move.describe(beforeMove.boardSize),
                        result = result,
                        localMove = localMove,
                        previousSnapshots = scoreSnapshots,
                    ),
                )
            }.onFailure { error ->
                applyHumanEngineSyncFailurePlan(
                    buildHumanEngineSyncFailurePlan(
                        localMove = localMove,
                        previousSnapshots = scoreSnapshots,
                        errorMessage = error.message,
                    ),
                )
            }
            isEngineBusy = false
            nextAnalysisState?.let { state ->
                requestTopMoveAnalysisForState(
                    targetState = state,
                    automatic = true,
                )
            }
        }
    }

    fun undoLocalTwoPlayerTurn(plan: UndoRequestPlan.LocalTwoPlayerUndo) {
        val undo = buildLocalTwoPlayerUndoPlan(
            currentState = gameState,
            scoreSnapshots = scoreSnapshots,
        )
        val nextState = undo.gameState
        applyUndoLocalStatePlan(undo)
        if (!plan.syncEngineAfterUndo) {
            engineMessage = "Local undo completed without engine sync."
            return
        }
        isEngineBusy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    engineClient.syncAndEstimateGraphScore(nextState, engineProfile)
                }
            }.onSuccess { estimate ->
                val score = buildEngineEstimateDisplayPlan(
                    state = nextState,
                    estimate = estimate,
                    previousSnapshots = scoreSnapshots,
                    engineMessage = "Local undo completed and engine analysis synced.",
                )
                applyScoreEstimateDisplayPlan(score)
            }.onFailure { error ->
                engineMessage = error.message ?: "Local undo engine sync failed."
            }
            isEngineBusy = false
            requestTopMoveAnalysisForState(
                targetState = nextState,
                automatic = true,
            )
        }
    }

    fun undoEngineBackedTurn(plan: UndoRequestPlan.EngineUndo) {
        val undoCount = plan.undoCount
        scope.launch {
            isEngineBusy = true
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    repeat(undoCount) {
                        engineClient.undoMove()
                    }
                }
            }.onSuccess {
                val undo = buildEngineUndoPlan(
                    currentState = gameState,
                    undoCount = undoCount,
                    previousMoveReviews = moveReviews,
                    scoreSnapshots = scoreSnapshots,
                )
                val nextState = undo.gameState
                applyUndoLocalStatePlan(undo)
                engineMessage = "Undid $undoCount move(s) in local state and engine state."
                nextAnalysisState = nextState
            }.onFailure { error ->
                engineMessage = error.message ?: "Undo failed."
            }
            isEngineBusy = false
            nextAnalysisState?.let { state ->
                requestTopMoveAnalysisForState(
                    targetState = state,
                    automatic = true,
                )
            }
        }
    }

    fun undoLastTurn() {
        when (
            val plan = buildUndoRequestPlan(
                currentState = gameState,
                matchMode = matchMode,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                humanSeatCount = playerSetup.humanSeatCount(),
            )
        ) {
            is UndoRequestPlan.ShowMessage -> {
                engineMessage = plan.message
                return
            }
            is UndoRequestPlan.LocalTwoPlayerUndo -> {
                undoLocalTwoPlayerTurn(plan)
                return
            }
            is UndoRequestPlan.EngineUndo -> {
                undoEngineBackedTurn(plan)
            }
        }
    }

    fun copyDebugReport() {
        val report = buildDebugReport(
            mode = matchMode,
            playerSetup = playerSetup,
            engineName = engineName,
            engineDiagnostic = engineDiagnostic,
            engineProfile = engineProfile,
            playLevel = playLevel,
            analysisPreset = analysisPreset,
            analysisCacheStats = analysisCache.statsText(),
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            isGameEnded = isGameEnded,
            topMovesEnabled = topMovesEnabled,
            topMoveCandidateCount = reviewAnalysis.legalPlayCount,
            moveAnalysisCoverage = reviewAnalysis.coverageSummary(),
            gameState = gameState,
            engineMessage = engineMessage,
            candidateText = candidateText,
            scoreText = scoreText,
            scoreSnapshots = scoreSnapshots,
            moveReviewText = moveReviewText,
            lastMoveText = lastMoveText,
            endgameLog = endgameLog,
            engineBenchmarkText = engineBenchmarkText,
            runtimeEventLogText = runtimeEventLog.readText(),
            searchTimeSettings = searchTimeSettings,
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Go AI Coach debug report", report))
        runCatching {
            context.openFileOutput(DebugReportMirrorFileName, Context.MODE_PRIVATE).use { output ->
                output.write(report.toByteArray(Charsets.UTF_8))
            }
        }
        engineMessage = "Debug report copied to clipboard. Paste it into chat for review."
        Toast.makeText(context, "Debug report copied", Toast.LENGTH_SHORT).show()
    }

    fun dispatch(event: GameUiEvent) {
        dispatchGameUiEvent(
            event = event,
            handlers = GameUiEventHandlers(
                currentPlayer = { gameState.nextPlayer },
                isTopMovesEnabled = { topMovesEnabled },
                startConfiguredGame = ::startConfiguredGame,
                copyDebugReport = ::copyDebugReport,
                showEngineBenchmark = ::showEngineBenchmarkResult,
                requestScoreEstimate = ::requestScoreEstimate,
                showTopMoves = ::showTopMovesForCurrentState,
                hideTopMoves = ::hideTopMoves,
                undoLastTurn = ::undoLastTurn,
                submitMove = ::submitHumanMove,
                dismissResumePrompt = {
                    sessionStore.clear()
                    applySavedSessionPromptPlan(buildSavedSessionDismissPlan())
                },
                restoreSavedSession = { snapshot ->
                    applySavedSessionPromptPlan(buildSavedSessionDismissPlan())
                    restoreSavedSession(snapshot)
                },
                changePlayerSetup = ::changePlayerSetup,
                changeAutoPlayDelay = ::changeAutoPlayDelay,
                changeSearchTimeSettings = ::changeSearchTimeSettings,
                changeScoringRule = ::changeScoringRule,
                changeUxOptions = { options -> uxOptions = options },
            ),
        )
    }

    LaunchedEffect(
        isEngineReady,
        isEngineBusy,
        isGameEnded,
        shouldShowResumePrompt,
        playerSetup,
        autoPlayDelaySetting,
        searchTimeSettings,
        gameState.nextPlayer,
        gameState.moves.size,
    ) {
        requestAiTurnForCurrentState()
    }

    LaunchedEffect(
        isEngineReady,
        isEngineBusy,
        playerSetup,
        searchTimeSettings,
        isGameEnded,
        shouldShowResumePrompt,
        gameState.nextPlayer,
        gameState.moves.size,
    ) {
        requestTopMoveAnalysisForState(
            targetState = gameState,
            automatic = true,
        )
    }

    val screenState = buildGameScreenState(
        GameScreenStateInput(
            gameState = gameState,
            matchMode = matchMode,
            playerSetup = playerSetup,
            autoPlayDelaySetting = autoPlayDelaySetting,
            searchTimeSettings = searchTimeSettings,
            searchTimeBenchmarkAverages = searchTimeBenchmarkAverages,
            playLevel = playLevel,
            uxOptions = uxOptions,
            engineName = engineName,
            engineDiagnostic = engineDiagnostic,
            engineProfile = engineProfile,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            engineMessage = engineMessage,
            analysisPreset = analysisPreset,
            analysisCacheStats = analysisCache.statsText(),
            topMovesEnabled = topMovesEnabled,
            candidateMoves = candidateMoves,
            candidateText = candidateText,
            reviewAnalysis = reviewAnalysis,
            reviewCandidateMoves = reviewCandidateMoves,
            moveReviews = moveReviews,
            moveReviewText = moveReviewText,
            lastMoveText = lastMoveText,
            scoreText = scoreText,
            scoreEstimate = scoreEstimate,
            scoreSnapshots = scoreSnapshots,
            isScoreGraphExpanded = isScoreGraphExpanded,
            pendingSavedSession = pendingSavedSession,
            shouldShowResumePrompt = shouldShowResumePrompt,
            hasCompletedEngineStartup = hasCompletedEngineStartup,
            isGameEnded = isGameEnded,
            endgameLog = endgameLog,
        ),
    )

    GoCoachContent(
        screenState = screenState,
        benchmarkProgress = benchmarkProgress,
        benchmarkResult = benchmarkResultToConfirm,
        onBenchmarkResultConfirmed = { benchmarkResultToConfirm = null },
        onBenchmarkRerun = ::rerunEngineBenchmark,
        isDisplayMenuExpanded = isDisplayMenuExpanded,
        onDisplayMenuExpandedChange = { expanded -> isDisplayMenuExpanded = expanded },
        onScoreGraphExpandedChange = { expanded -> isScoreGraphExpanded = expanded },
        onEvent = ::dispatch,
    )
}

private fun UserPreferencesSnapshot.toKaTrainUxOptions(): KaTrainUxOptions =
    KaTrainUxOptions(
        showCoordinates = showCoordinates,
        showMoveNumbers = showMoveNumbers,
        showLastMoveRing = showLastMoveRing,
        showOwnershipOverlay = showOwnershipOverlay,
    )

private fun EngineBenchmarkProfile.averageMillisByVisits(): Map<Int, Double> =
    metrics.associate { metric -> metric.visits to metric.avgMs }

private const val EngineBenchmarkStartupSettleDelayMillis = 1_500L
private const val DebugReportMirrorFileName = "last_debug_report.txt"
