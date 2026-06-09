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
import com.worksoc.goaicoach.application.MoveReviewMarker
import com.worksoc.goaicoach.application.buildDebugReport
import com.worksoc.goaicoach.application.buildEndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.buildEngineEstimateDisplayPlan
import com.worksoc.goaicoach.application.buildEngineUndoPlan
import com.worksoc.goaicoach.application.buildLocalFinalScoreDisplayPlan
import com.worksoc.goaicoach.application.buildLocalTwoPlayerUndoPlan
import com.worksoc.goaicoach.application.buildCachedTopMoveAnalysisUpdate
import com.worksoc.goaicoach.application.buildCompletedTopMoveAnalysisUpdate
import com.worksoc.goaicoach.application.buildLocalScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.buildNewLocalGameSessionPlan
import com.worksoc.goaicoach.application.buildResolvedEndgameDisplayPlan
import com.worksoc.goaicoach.application.buildSavedGameRestorePlan
import com.worksoc.goaicoach.application.buildTopMoveAnalysisPlan
import com.worksoc.goaicoach.application.configureSyncAndEstimateGraphScore
import com.worksoc.goaicoach.application.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.applyHumanMoveLocally
import com.worksoc.goaicoach.application.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.GameSessionResetPlan
import com.worksoc.goaicoach.application.localScoreSnapshot
import com.worksoc.goaicoach.application.planShowTopMoves
import com.worksoc.goaicoach.application.estimateScoreForState
import com.worksoc.goaicoach.application.resolveEndgameForState
import com.worksoc.goaicoach.application.runAutoAiTurn
import com.worksoc.goaicoach.application.selectRuntimePlayLevel
import com.worksoc.goaicoach.application.startEngineSession
import com.worksoc.goaicoach.application.startNewEngineGame
import com.worksoc.goaicoach.application.syncAndEstimateGraphScore
import com.worksoc.goaicoach.application.syncAfterHumanMove
import com.worksoc.goaicoach.application.ShowTopMovesPlan
import com.worksoc.goaicoach.application.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.SavedGameRestorePlan
import com.worksoc.goaicoach.application.TopMoveAnalysisUpdate
import com.worksoc.goaicoach.application.toCandidateText
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
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
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GoCoachApp(
    engineAdapter: EngineAdapter,
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
                engineAdapter = engineAdapter,
                engineName = engineName,
                engineDiagnostic = engineDiagnostic,
            )
        }
    }
}

@Composable
private fun GoCoachScreen(
    engineAdapter: EngineAdapter,
    engineName: String,
    engineDiagnostic: String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionStore = remember(context) { GameSessionStore(context) }
    var gameState by remember { mutableStateOf(GameState.empty(BoardSize.Nine, Ruleset.Japanese)) }
    var engineMessage by remember { mutableStateOf("Engine not initialized.") }
    var candidateText by remember { mutableStateOf(engineDiagnostic) }
    var candidateMoves by remember { mutableStateOf(emptyList<CandidateMove>()) }
    var reviewCandidateMoves by remember { mutableStateOf(emptyList<CandidateMove>()) }
    var reviewAnalysis by remember { mutableStateOf(MoveAnalysisSnapshot.empty(gameState)) }
    var scoreText by remember { mutableStateOf("No score estimate yet.") }
    var scoreEstimate by remember { mutableStateOf<ScoreEstimate?>(null) }
    var scoreSnapshots by remember {
        mutableStateOf(listOf(localScoreSnapshot(GameState.empty(BoardSize.Nine, Ruleset.Japanese))))
    }
    var moveReviewText by remember { mutableStateOf("No move review yet.") }
    var moveReviews by remember { mutableStateOf(emptyList<MoveReviewMarker>()) }
    var lastMoveText by remember { mutableStateOf("None") }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }
    val defaultPlayLevel = remember { PlayLevelSetting() }
    var playLevel by remember { mutableStateOf(defaultPlayLevel) }
    var engineProfile by remember { mutableStateOf(defaultPlayLevel.toEngineProfile(EngineProfile())) }
    var playerSetup by remember { mutableStateOf(PlayerSetup()) }
    val matchMode = playerSetup.matchMode()
    var topMovesEnabled by remember { mutableStateOf(false) }
    var analysisPreset by remember { mutableStateOf(defaultPlayLevel.analysisPreset) }
    val analysisCache = remember { AnalysisResultCache(maxEntries = 96) }
    var uxOptions by remember { mutableStateOf(KaTrainUxOptions()) }
    var isDisplayMenuExpanded by remember { mutableStateOf(false) }
    var isScoreGraphExpanded by remember { mutableStateOf(false) }
    var lastAnalysisKey by remember { mutableStateOf<AnalysisCacheKey?>(null) }
    var isGameEnded by remember { mutableStateOf(false) }
    var endgameLog by remember { mutableStateOf("No endgame result recorded.") }
    var hasCompletedEngineStartup by remember { mutableStateOf(false) }
    var hasCheckedSavedSession by remember { mutableStateOf(false) }
    var pendingSavedSession by remember { mutableStateOf<SavedGameSnapshot?>(null) }
    var shouldShowResumePrompt by remember { mutableStateOf(false) }

    LaunchedEffect(engineAdapter) {
        hasCompletedEngineStartup = false
        isEngineBusy = true
        runCatching {
            withContext(Dispatchers.IO) {
                engineAdapter.startEngineSession(engineProfile, gameState)
            }
        }.onSuccess { result ->
            isEngineReady = true
            scoreSnapshots = listOf(result.scoreSnapshot ?: localScoreSnapshot(gameState))
            engineMessage = result.message
        }.onFailure { error ->
            isEngineReady = false
            engineMessage = "Engine initialization failed.\n${error.message ?: "Unknown error"}"
            candidateText = "2P test mode is still available.\n$engineDiagnostic"
        }
        isEngineBusy = false
        hasCompletedEngineStartup = true
    }

    LaunchedEffect(sessionStore) {
        val savedSession = sessionStore.load()
        pendingSavedSession = savedSession
        shouldShowResumePrompt = savedSession != null
        hasCheckedSavedSession = true
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
        if (!hasCheckedSavedSession || shouldShowResumePrompt) {
            return@LaunchedEffect
        }
        val snapshot = SavedGameSnapshot(
            gameState = gameState,
            playerSetup = playerSetup,
            playLevel = playLevel,
            topMovesEnabled = topMovesEnabled,
            savedAtMillis = System.currentTimeMillis(),
        )
        if (isGameEnded || !snapshot.isResumable) {
            sessionStore.clear()
        } else {
            sessionStore.save(snapshot)
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
    }

    fun applySavedGameRestorePlan(restore: SavedGameRestorePlan) {
        playerSetup = restore.playerSetup
        playLevel = restore.runtime.playLevel
        engineProfile = restore.runtime.engineProfile
        analysisPreset = restore.runtime.analysisPreset
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

    fun changePlayerSetup(nextSetup: PlayerSetup) {
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Change Player Setup after the current action."
            return
        }
        playerSetup = nextSetup
        val runtime = selectRuntimePlayLevel(
            setup = nextSetup,
            nextPlayer = gameState.nextPlayer,
            currentProfile = engineProfile,
            defaultPlayLevel = defaultPlayLevel,
        )
        playLevel = runtime.playLevel
        engineProfile = runtime.engineProfile
        analysisPreset = runtime.analysisPreset
        clearTopMoveSpots("Player Setup changed. Press New to restart with this setup, or continue from the current position.")
        clearReviewAnalysis(gameState)
        lastAnalysisKey = null
    }

    fun requestTopMoveAnalysisForState(
        targetState: GameState,
        automatic: Boolean,
        deep: Boolean = false,
    ) {
        if (
            isGameEnded ||
            !isEngineReady ||
            isEngineBusy ||
            shouldShowResumePrompt
        ) {
            return
        }
        if (playerSetup.sideFor(targetState.nextPlayer).controller == SeatController.Ai) {
            return
        }

        val plan = buildTopMoveAnalysisPlan(
            targetState = targetState,
            engineProfile = engineProfile,
            analysisPreset = analysisPreset,
            deep = deep,
        )
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
        if (automatic && plan.analysisKey == lastAnalysisKey) {
            if (topMovesEnabled && candidateMoves.isEmpty() && reviewAnalysis.scoredPlayCount > 0) {
                candidateMoves = reviewAnalysis.candidatesForDisplay()
            }
            return
        }

        lastAnalysisKey = plan.analysisKey
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.analyze(plan.analysisLimit)
                }
            }.onSuccess { result ->
                val update = buildCompletedTopMoveAnalysisUpdate(
                    targetState = targetState,
                    result = result,
                    rawCandidateText = result.toCandidateText(targetState.boardSize),
                    engineProfile = engineProfile,
                    analysisPreset = analysisPreset,
                    plan = plan,
                    deep = deep,
                    topMovesEnabled = topMovesEnabled,
                )
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
        engineMessage = "Top Moves hidden. Pre-move analysis cache still runs for move review."
    }

    fun changeScoringRule(nextRuleset: Ruleset) {
        if (nextRuleset == gameState.ruleset) {
            return
        }
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Change scoring rule after the current response."
            return
        }

        val nextState = gameState.copy(ruleset = nextRuleset)
        gameState = nextState
        clearTopMoveSpots("Scoring rule changed.")
        clearReviewAnalysis(nextState)
        lastAnalysisKey = null
        scoreEstimate = null
        scoreText = if (isGameEnded || (matchMode == MatchMode.LocalTwoPlayer && !isEngineReady)) {
            buildLocalScoreEstimateDisplayPlan(
                state = nextState,
                previousSnapshots = scoreSnapshots,
                engineMessage = "",
            ).scoreText
        } else {
            "Score estimate not current."
        }
        scoreSnapshots = ScoreTimeline.record(
            ScoreTimeline.trimAfter(scoreSnapshots, nextState.moves.size),
            localScoreSnapshot(nextState),
        )
        endgameLog = "Scoring rule changed to ${nextRuleset.scoringLabel}. No endgame result recorded for the new scoring rule yet."

        if (!isEngineReady) {
            engineMessage = "Scoring rule changed to ${nextRuleset.scoringLabel}. Local scoring is active."
            return
        }

        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.syncAndEstimateGraphScore(nextState, engineProfile)
                }
            }.onSuccess { estimate ->
                val score = buildEngineEstimateDisplayPlan(
                    state = nextState,
                    estimate = estimate,
                    previousSnapshots = scoreSnapshots,
                    engineMessage = "Scoring rule changed to ${nextRuleset.scoringLabel}; engine rules synchronized.",
                    trimAfterMove = true,
                )
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
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Restore the saved game after the current action."
            return
        }

        val restore = buildSavedGameRestorePlan(
            snapshot = snapshot,
            currentProfile = engineProfile,
            defaultPlayLevel = defaultPlayLevel,
        )
        val restoredState = restore.gameState
        val restoredProfile = restore.runtime.engineProfile
        if (isEngineReady) {
            isEngineBusy = true
        }

        applySavedGameRestorePlan(restore)

        if (!isEngineReady) {
            return
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.configureSyncAndEstimateGraphScore(restoredState, restoredProfile)
                }
            }.onSuccess { estimate ->
                val score = buildEngineEstimateDisplayPlan(
                    state = restoredState,
                    estimate = estimate,
                    previousSnapshots = emptyList(),
                    engineMessage = "Previous game restored and engine state synchronized.",
                )
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

    fun requestScoreEstimate() {
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Estimate after the current response."
            return
        }

        if (matchMode == MatchMode.LocalTwoPlayer && !isEngineReady) {
            val score = buildLocalScoreEstimateDisplayPlan(
                state = gameState,
                previousSnapshots = scoreSnapshots,
                engineMessage = "Local ${gameState.ruleset.scoringLabel} estimate refreshed.",
            )
            applyScoreEstimateDisplayPlan(score)
            return
        }

        if (!isEngineReady) {
            engineMessage = "Engine is not ready."
            return
        }

        val estimateState = gameState
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.estimateScoreForState(
                        state = estimateState,
                        profile = engineProfile,
                        syncFirst = matchMode == MatchMode.LocalTwoPlayer,
                    )
                }
            }.onSuccess { estimate ->
                val score = buildEngineEstimateDisplayPlan(
                    state = gameState,
                    estimate = estimate,
                    previousSnapshots = scoreSnapshots,
                )
                applyScoreEstimateDisplayPlan(score)
            }.onFailure { error ->
                engineMessage = error.message ?: "Score estimate failed."
                scoreEstimate = null
            }
            isEngineBusy = false
        }
    }

    fun startConfiguredGame() {
        val targetRuleset = gameState.ruleset
        val targetSetup = playerSetup
        val targetMode = targetSetup.matchMode()
        if (!isEngineReady && targetMode != MatchMode.LocalTwoPlayer) {
            resetLocalGame("Player Setup includes AI, but engine is not ready.", targetRuleset)
            return
        }
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Start a new game after the current action."
            return
        }
        if (!isEngineReady && targetMode == MatchMode.LocalTwoPlayer) {
            resetLocalGame("Local two-player game. Engine analysis is not connected.", targetRuleset)
            return
        }

        val runtime = selectRuntimePlayLevel(
            setup = targetSetup,
            nextPlayer = gameState.nextPlayer,
            currentProfile = engineProfile,
            defaultPlayLevel = defaultPlayLevel,
        )
        playLevel = runtime.playLevel
        engineProfile = runtime.engineProfile
        analysisPreset = runtime.analysisPreset

        scope.launch {
            isEngineBusy = true
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.startNewEngineGame(runtime.engineProfile, BoardSize.Nine, targetRuleset)
                }
            }.onSuccess { result ->
                resetLocalGame(result.message, targetRuleset)
                scoreSnapshots = listOf(result.scoreSnapshot ?: localScoreSnapshot(gameState))
                nextAnalysisState = gameState
            }.onFailure { error ->
                resetLocalGame(error.message ?: "New AI game failed.", targetRuleset)
            }
            isEngineBusy = false
            requestTopMoveAnalysisForState(
                targetState = nextAnalysisState ?: gameState,
                automatic = true,
            )
        }
    }

    fun requestAiTurnForCurrentState() {
        if (
            isGameEnded ||
            !isEngineReady ||
            isEngineBusy ||
            shouldShowResumePrompt ||
            playerSetup.sideFor(gameState.nextPlayer).controller != SeatController.Ai
        ) {
            return
        }

        val turnState = gameState
        val aiPlayer = turnState.nextPlayer
        val side = playerSetup.sideFor(aiPlayer)
        val aiPlayLevel = side.playLevel
        val previousReviewCandidates = reviewCandidateMoves

        scope.launch {
            isEngineBusy = true
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.runAutoAiTurn(
                        currentState = turnState,
                        playLevel = aiPlayLevel,
                        currentProfile = engineProfile,
                    )
                }
            }.onSuccess { result ->
                playLevel = result.playLevel
                engineProfile = result.profile
                analysisPreset = result.playLevel.analysisPreset

                val outcome = result.turnOutcome
                gameState = outcome.gameState
                clearTopMoveSpots()
                clearReviewAnalysis(outcome.gameState)
                lastAnalysisKey = null
                engineMessage = outcome.engineMessage
                candidateText = outcome.candidateText
                lastMoveText = outcome.lastMoveText

                result.scoreEstimate?.let { estimate ->
                    val score = buildEngineEstimateDisplayPlan(
                        state = outcome.gameState,
                        estimate = estimate,
                        previousSnapshots = scoreSnapshots,
                    )
                    applyScoreEstimateDisplayPlan(score)
                } ?: run {
                    scoreText = "Score estimate not current."
                    scoreEstimate = null
                    scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localScoreSnapshot(outcome.gameState))
                }

                nextAnalysisState = outcome.gameState
                if (outcome.gameState.hasConsecutivePasses() || outcome.gameState.isBoardFull()) {
                    isGameEnded = true
                    nextAnalysisState = null
                    runCatching {
                        withContext(Dispatchers.IO) {
                            engineAdapter.resolveEndgameForState(
                                state = outcome.gameState,
                                profile = result.profile,
                                prePassCandidates = if (outcome.gameState.moves.lastOrNull() is Move.Pass) {
                                    previousReviewCandidates
                                } else {
                                    emptyList()
                                },
                            )
                        }
                    }.onSuccess { endgame ->
                        val final = buildResolvedEndgameDisplayPlan(
                            source = "auto-ai-engine-dead-stone-cleanup",
                            originalState = outcome.gameState,
                            resolution = endgame,
                            previousSnapshots = scoreSnapshots,
                            engineMessagePrefix = outcome.engineMessage,
                        )
                        applyFinalScoreDisplayPlan(final)
                    }.onFailure { error ->
                        val failure = buildEndgameFailureDisplayPlan(
                            source = "auto-ai-engine-final-score-failed",
                            state = outcome.gameState,
                            errorMessage = error.message ?: "Unknown error",
                            engineMessagePrefix = outcome.engineMessage,
                        )
                        applyEndgameFailureDisplayPlan(failure)
                    }
                }
            }.onFailure { error ->
                engineMessage = error.message ?: "AI turn failed."
                candidateText = "AI turn failed. Current board state was not changed."
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
                    engineAdapter.syncAfterHumanMove(
                        afterMove = afterMove,
                        profile = engineProfile,
                        move = move,
                        previousReviewCandidates = previousReviewCandidates,
                    )
                }
            }.onSuccess { result ->
                val endgame = result.endgame
                val estimate = result.estimate
                if (endgame != null) {
                    val final = buildResolvedEndgameDisplayPlan(
                        source = "human-engine-dead-stone-cleanup",
                        originalState = afterMove,
                        resolution = endgame,
                        previousSnapshots = scoreSnapshots,
                        engineMessagePrefix = "Game ended after two passes.",
                    )
                    applyFinalScoreDisplayPlan(final)
                } else if (estimate != null) {
                    val score = buildEngineEstimateDisplayPlan(
                        state = afterMove,
                        estimate = estimate,
                        previousSnapshots = scoreSnapshots,
                        engineMessage = "Human move accepted and engine analysis synced: ${move.describe(beforeMove.boardSize)}.",
                    )
                    applyScoreEstimateDisplayPlan(score)
                    candidateText = localMove.capturedText
                    nextAnalysisState = afterMove
                }
            }.onFailure { error ->
                scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localMove.localScoreSnapshot)
                candidateText = localMove.capturedText
                engineMessage = error.message ?: "Human move accepted, but engine sync failed."
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
        if (gameState.moves.isEmpty()) {
            engineMessage = "No move to undo."
            return
        }

        if (matchMode == MatchMode.LocalTwoPlayer) {
            if (isEngineBusy) {
                engineMessage = "Engine is busy. Undo after the current analysis."
                return
            }
            val undo = buildLocalTwoPlayerUndoPlan(
                currentState = gameState,
                scoreSnapshots = scoreSnapshots,
            )
            val nextState = undo.gameState
            gameState = nextState
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
            if (!isEngineReady) {
                engineMessage = "Local undo completed without engine sync."
                return
            }
            isEngineBusy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        engineAdapter.syncAndEstimateGraphScore(nextState, engineProfile)
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
            return
        }

        if (!isEngineReady) {
            engineMessage = "Engine is not ready."
            return
        }
        if (isEngineBusy) {
            engineMessage = "AI is busy. Undo after the current response."
            return
        }

        val undoCount = if (playerSetup.humanSeatCount() == 1) {
            minOf(2, gameState.moves.size)
        } else {
            1
        }
        scope.launch {
            isEngineBusy = true
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    repeat(undoCount) {
                        engineAdapter.undoMove()
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
                gameState = nextState
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
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Go AI Coach debug report", report))
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
                requestScoreEstimate = ::requestScoreEstimate,
                showTopMoves = ::showTopMovesForCurrentState,
                hideTopMoves = ::hideTopMoves,
                undoLastTurn = ::undoLastTurn,
                submitMove = ::submitHumanMove,
                dismissResumePrompt = {
                    sessionStore.clear()
                    pendingSavedSession = null
                    shouldShowResumePrompt = false
                },
                restoreSavedSession = { snapshot ->
                    pendingSavedSession = null
                    shouldShowResumePrompt = false
                    restoreSavedSession(snapshot)
                },
                changePlayerSetup = ::changePlayerSetup,
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
        gameState.nextPlayer,
        gameState.moves.size,
    ) {
        requestAiTurnForCurrentState()
    }

    LaunchedEffect(
        isEngineReady,
        playerSetup,
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
        isDisplayMenuExpanded = isDisplayMenuExpanded,
        onDisplayMenuExpandedChange = { expanded -> isDisplayMenuExpanded = expanded },
        onScoreGraphExpandedChange = { expanded -> isScoreGraphExpanded = expanded },
        onEvent = ::dispatch,
    )
}
