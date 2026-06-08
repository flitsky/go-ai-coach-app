package com.worksoc.goaicoach.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.AiEndgameResolution
import com.worksoc.goaicoach.application.AnalysisCacheKey
import com.worksoc.goaicoach.application.AnalysisResultCache
import com.worksoc.goaicoach.application.CachedAnalysisResult
import com.worksoc.goaicoach.application.MinScoredTopMovesForDisplay
import com.worksoc.goaicoach.application.MoveReviewMarker
import com.worksoc.goaicoach.application.analysisKeyFor
import com.worksoc.goaicoach.application.buildDebugReport
import com.worksoc.goaicoach.application.buildMoveReview
import com.worksoc.goaicoach.application.buildEndgameLog
import com.worksoc.goaicoach.application.deepTopMovesAnalysisLimitFor
import com.worksoc.goaicoach.application.resolveAiEndgame
import com.worksoc.goaicoach.application.topMoveCandidateCountFor
import com.worksoc.goaicoach.application.topMovesAnalysisLimitFor
import com.worksoc.goaicoach.application.withReviewMarker
import com.worksoc.goaicoach.application.withAnalysisCoverage
import com.worksoc.goaicoach.application.withTopMovesStrengthHeader
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.match.applyAiTurn
import com.worksoc.goaicoach.match.boardInputEnabled
import com.worksoc.goaicoach.match.modeSummary
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import com.worksoc.goaicoach.shared.replayWithoutLastMoves
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
            val init = withContext(Dispatchers.IO) { engineAdapter.initialize(engineProfile) }
            val newGame = withContext(Dispatchers.IO) {
                engineAdapter.newGame(gameState.boardSize, gameState.ruleset)
            }
            val estimate = withContext(Dispatchers.IO) {
                runCatching { engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile)) }.getOrNull()
            }
            EngineStartupResult(
                message = "Ready for 9x9 match.\n${init.message}\n${newGame.message}",
                scoreSnapshot = estimate?.let { ScoreTimeline.fromEstimate(gameState.moves.size, it) },
            )
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

    fun List<CandidateMove>.scoredCandidateCount(): Int =
        count { it.pointLoss != null }

    fun currentTopMoveAnalysisLimit(state: GameState): AnalysisLimit =
        topMovesAnalysisLimitFor(
            profile = engineProfile,
            preset = analysisPreset,
            candidateCount = topMoveCandidateCountFor(state, analysisPreset),
        )

    fun primaryPlayLevelForSetup(
        setup: PlayerSetup = playerSetup,
        state: GameState = gameState,
    ): PlayLevelSetting =
        setup.sideFor(state.nextPlayer)
            .takeIf { side -> side.controller == SeatController.Ai }
            ?.playLevel
            ?: setup.black.takeIf { side -> side.controller == SeatController.Ai }?.playLevel
            ?: setup.white.takeIf { side -> side.controller == SeatController.Ai }?.playLevel
            ?: defaultPlayLevel

    fun applyRuntimePlayLevel(nextPlayLevel: PlayLevelSetting) {
        playLevel = nextPlayLevel
        engineProfile = nextPlayLevel.toEngineProfile(engineProfile)
        analysisPreset = nextPlayLevel.analysisPreset
    }

    fun changePlayerSetup(nextSetup: PlayerSetup) {
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Change Player Setup after the current action."
            return
        }
        playerSetup = nextSetup
        applyRuntimePlayLevel(primaryPlayLevelForSetup(nextSetup))
        clearTopMoveSpots("Player Setup changed. Press New to restart with this setup, or continue from the current position.")
        clearReviewAnalysis(gameState)
        lastAnalysisKey = null
    }

    fun applyCachedAnalysis(
        targetState: GameState,
        cacheKey: AnalysisCacheKey,
        cached: CachedAnalysisResult,
    ) {
        reviewAnalysis = cached.snapshot
        reviewCandidateMoves = cached.snapshot.candidatesForReview()
        candidateText = "Analysis cache hit: ${cacheKey.preset.label}.\n${cached.candidateText}"
        if (topMovesEnabled) {
            engineMessage = "Top Moves cache hit for ${targetState.nextPlayer.label}: ${cached.snapshot.scoredPlayCount}/${cached.snapshot.legalPlayCount} legal spot(s) scored."
            candidateMoves = cached.snapshot.candidatesForDisplay()
        } else {
            engineMessage = "Pre-move analysis cache hit for ${targetState.nextPlayer.label}: ${cached.snapshot.scoredPlayCount}/${cached.snapshot.legalPlayCount} legal spot(s) scored."
        }
        lastAnalysisKey = cacheKey
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

        val candidateCount = topMoveCandidateCountFor(targetState, analysisPreset)
        val analysisLimit = if (deep) {
            deepTopMovesAnalysisLimitFor(engineProfile, candidateCount)
        } else {
            topMovesAnalysisLimitFor(engineProfile, analysisPreset, candidateCount)
        }
        val analysisKey = analysisKeyFor(targetState, analysisPreset, analysisLimit, deep)
        analysisCache.get(analysisKey)?.let { cached ->
            applyCachedAnalysis(targetState, analysisKey, cached)
            return
        }
        if (automatic && analysisKey == lastAnalysisKey) {
            if (topMovesEnabled && candidateMoves.isEmpty() && reviewAnalysis.scoredPlayCount > 0) {
                candidateMoves = reviewAnalysis.candidatesForDisplay()
            }
            return
        }

        lastAnalysisKey = analysisKey
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.analyze(analysisLimit)
                }
            }.onSuccess { result ->
                val snapshot = MoveAnalysisSnapshot.from(targetState, result.candidates)
                val analysisText = result.toCandidateText(targetState.boardSize)
                    .withAnalysisCoverage(snapshot)
                    .withTopMovesStrengthHeader(engineProfile, analysisPreset, analysisLimit, candidateCount, deep)
                reviewAnalysis = snapshot
                reviewCandidateMoves = snapshot.candidatesForReview()
                candidateText = "Analysis cache miss: stored ${analysisPreset.label} result.\n$analysisText"
                analysisCache.put(
                    analysisKey,
                    CachedAnalysisResult(
                        snapshot = snapshot,
                        candidateText = analysisText,
                    ),
                )
                if (topMovesEnabled) {
                    engineMessage = result.status.message
                    candidateMoves = snapshot.candidatesForDisplay()
                } else {
                    engineMessage =
                        "Top Moves analysis ready for ${targetState.nextPlayer.label}: ${snapshot.scoredPlayCount}/${snapshot.legalPlayCount} legal spot(s) scored."
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
        if (
            reviewAnalysis.hasEngineCandidates &&
            lastAnalysisKey == analysisKeyFor(gameState, analysisPreset, currentTopMoveAnalysisLimit(gameState), deep = false)
        ) {
            val scoredCount = reviewAnalysis.scoredPlayCount
            if (
                scoredCount >= MinScoredTopMovesForDisplay ||
                isEngineBusy ||
                !analysisPreset.allowManualDeepFallback
            ) {
                candidateMoves = reviewAnalysis.candidatesForDisplay()
                engineMessage = "Showing ${candidateMoves.scoredCandidateCount()} scored Top Moves from cached ${analysisPreset.label} analysis."
                return
            }
            candidateMoves = emptyList()
            engineMessage = "Cached Top Moves has only $scoredCount scored candidate(s). Running deeper analysis."
            requestTopMoveAnalysisForState(
                targetState = gameState,
                automatic = false,
                deep = true,
            )
            return
        }

        candidateMoves = emptyList()
        requestTopMoveAnalysisForState(
            targetState = gameState,
            automatic = false,
        )
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
            BoardScorer.score(nextState).toDisplayText()
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
                    engineAdapter.syncToGameState(nextState)
                    engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile))
                }
            }.onSuccess { estimate ->
                scoreText = estimate.toDisplayText()
                scoreEstimate = estimate
                scoreSnapshots = ScoreTimeline.record(
                    ScoreTimeline.trimAfter(scoreSnapshots, nextState.moves.size),
                    ScoreTimeline.fromEstimate(nextState.moves.size, estimate),
                )
                engineMessage = "Scoring rule changed to ${nextRuleset.scoringLabel}; engine rules synchronized."
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
        gameState = GameState.empty(BoardSize.Nine, ruleset)
        isGameEnded = false
        clearTopMoveSpots("No analysis yet.")
        clearReviewAnalysis()
        lastAnalysisKey = null
        scoreText = "No score estimate yet."
        scoreEstimate = null
        scoreSnapshots = listOf(localScoreSnapshot(gameState))
        moveReviewText = "No move review yet."
        moveReviews = emptyList()
        lastMoveText = "None"
        endgameLog = "No endgame result recorded."
        engineMessage = message
    }

    fun restoreSavedSession(snapshot: SavedGameSnapshot) {
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Restore the saved game after the current action."
            return
        }

        val restoredState = snapshot.gameState
        val restoredPlayLevel = primaryPlayLevelForSetup(snapshot.playerSetup, restoredState)
        val restoredProfile = restoredPlayLevel.toEngineProfile(engineProfile)
        if (isEngineReady) {
            isEngineBusy = true
        }

        playerSetup = snapshot.playerSetup
        playLevel = restoredPlayLevel
        engineProfile = restoredProfile
        analysisPreset = restoredPlayLevel.analysisPreset
        topMovesEnabled = snapshot.topMovesEnabled
        gameState = restoredState
        isGameEnded = false
        clearTopMoveSpots("Restored previous game. Analysis cache will rebuild.")
        clearReviewAnalysis(restoredState)
        lastAnalysisKey = null
        scoreText = "Score estimate not current."
        scoreEstimate = null
        scoreSnapshots = listOf(localScoreSnapshot(restoredState))
        moveReviewText = "Move review restored after app restart; pre-move analysis cache will rebuild."
        moveReviews = emptyList()
        lastMoveText = restoredState.moves.lastOrNull()?.describe(restoredState.boardSize) ?: "None"
        endgameLog = "No endgame result recorded after restore."
        engineMessage = "Previous game restored at move ${restoredState.moves.size}."

        if (!isEngineReady) {
            return
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.configure(restoredProfile)
                    engineAdapter.syncToGameState(restoredState)
                    engineAdapter.estimateScore(scoreGraphAnalysisLimit(restoredProfile))
                }
            }.onSuccess { estimate ->
                scoreText = estimate.toDisplayText()
                scoreEstimate = estimate
                scoreSnapshots = listOf(ScoreTimeline.fromEstimate(restoredState.moves.size, estimate))
                engineMessage = "Previous game restored and engine state synchronized."
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
            val score = BoardScorer.score(gameState)
            scoreText = score.toDisplayText()
            scoreEstimate = null
            scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localScoreSnapshot(gameState))
            engineMessage = "Local ${gameState.ruleset.scoringLabel} estimate refreshed."
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
                    if (matchMode == MatchMode.LocalTwoPlayer) {
                        engineAdapter.syncToGameState(estimateState)
                    }
                    engineAdapter.estimateScore(engineProfile.analysisLimit)
                }
            }.onSuccess { estimate ->
                engineMessage = estimate.status.message
                scoreText = estimate.toDisplayText()
                scoreEstimate = estimate
                scoreSnapshots = ScoreTimeline.record(
                    scoreSnapshots,
                    ScoreTimeline.fromEstimate(gameState.moves.size, estimate),
                )
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

        val targetPlayLevel = primaryPlayLevelForSetup(targetSetup)
        val targetProfile = targetPlayLevel.toEngineProfile(engineProfile)
        applyRuntimePlayLevel(targetPlayLevel)

        scope.launch {
            isEngineBusy = true
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.configure(targetProfile)
                    val status = engineAdapter.newGame(BoardSize.Nine, targetRuleset)
                    val estimate = runCatching {
                        engineAdapter.estimateScore(scoreGraphAnalysisLimit(targetProfile))
                    }.getOrNull()
                    EngineStartupResult(
                        message = status.message,
                        scoreSnapshot = estimate?.let { ScoreTimeline.fromEstimate(0, it) },
                    )
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

    fun startAiGame() {
        playerSetup = PlayerSetup()
        startConfiguredGame()
    }

    fun startLocalTwoPlayerGame() {
        val targetRuleset = gameState.ruleset
        playerSetup = PlayerSetup(
            black = playerSetup.black.copy(controller = SeatController.Human),
            white = playerSetup.white.copy(controller = SeatController.Human),
        )
        resetLocalGame("2P test mode. Local shared rules handle captures, suicide, and simple ko.", targetRuleset)
        if (!isEngineReady) {
            engineMessage = "2P test mode without engine analysis. Local rules are active."
            return
        }
        if (isEngineBusy) {
            engineMessage = "Engine is busy. Change mode after the current response."
            return
        }

        val nextState = gameState
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.syncToGameState(nextState)
                    engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile))
                }
            }.onSuccess { estimate ->
                scoreText = estimate.toDisplayText()
                scoreEstimate = estimate
                scoreSnapshots = listOf(ScoreTimeline.fromEstimate(0, estimate))
                engineMessage = "2P test mode connected to engine analysis."
            }.onFailure { error ->
                engineMessage = error.message ?: "2P engine sync failed."
            }
            isEngineBusy = false
            requestTopMoveAnalysisForState(
                targetState = gameState,
                automatic = true,
            )
        }
    }

    fun changePlayLevel(nextSetting: PlayLevelSetting) {
        val normalized = nextSetting.normalized()
        if (normalized == playLevel) {
            return
        }
        if (!isEngineReady) {
            engineMessage = "Engine is not ready."
            return
        }
        if (isEngineBusy) {
            engineMessage = "AI is busy. Change level after the current response."
            return
        }

        val nextProfile = normalized.toEngineProfile(engineProfile)
        playLevel = normalized
        engineProfile = nextProfile
        analysisPreset = normalized.analysisPreset
        clearTopMoveSpots("Engine level changed to ${normalized.displayLabel}.")
        clearReviewAnalysis(gameState)
        lastAnalysisKey = null
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) { engineAdapter.configure(nextProfile) }
            }.onSuccess { status ->
                engineMessage = "${status.message}\nLevel: ${normalized.displayLabel} (${normalized.selectionPolicy.description})."
            }.onFailure { error ->
                engineMessage = error.message ?: "Engine configuration failed."
            }
            isEngineBusy = false
            requestTopMoveAnalysisForState(
                targetState = gameState,
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
        val turnProfile = aiPlayLevel.toEngineProfile(engineProfile)
        val previousReviewCandidates = reviewCandidateMoves

        scope.launch {
            isEngineBusy = true
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.configure(turnProfile)
                    engineAdapter.syncToGameState(turnState)
                    val outcome = applyAiTurn(
                        engineAdapter = engineAdapter,
                        currentState = turnState,
                        aiPlayer = aiPlayer,
                        playLevel = aiPlayLevel,
                    )
                    val estimate = runCatching {
                        engineAdapter.estimateScore(scoreGraphAnalysisLimit(turnProfile))
                    }.getOrNull()
                    AutoAiTurnResult(
                        turnOutcome = outcome,
                        scoreEstimate = estimate,
                        profile = turnProfile,
                        playLevel = aiPlayLevel,
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
                    scoreText = estimate.toDisplayText()
                    scoreEstimate = estimate
                    scoreSnapshots = ScoreTimeline.record(
                        scoreSnapshots,
                        ScoreTimeline.fromEstimate(outcome.gameState.moves.size, estimate),
                    )
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
                            resolveAiEndgame(
                                engineAdapter = engineAdapter,
                                originalState = outcome.gameState,
                                estimateLimit = scoreGraphAnalysisLimit(result.profile),
                                prePassCandidates = if (outcome.gameState.moves.lastOrNull() is Move.Pass) {
                                    previousReviewCandidates
                                } else {
                                    emptyList()
                                },
                            )
                        }
                    }.onSuccess { endgame ->
                        gameState = endgame.cleanup.state
                        val finalScoreText = endgame.finalScore.toDisplayText()
                        scoreText = finalScoreText
                        scoreEstimate = null
                        scoreSnapshots = ScoreTimeline.record(
                            scoreSnapshots,
                            ScoreTimeline.fromFinalScore(
                                moveNumber = endgame.cleanup.state.moves.size,
                                finalScore = endgame.finalScore,
                                source = ScoreSnapshotSource.FinalScore,
                            ),
                        )
                        endgameLog = buildEndgameLog(
                            source = "auto-ai-engine-dead-stone-cleanup",
                            state = endgame.cleanup.state,
                            finalScoreText = finalScoreText,
                            detail = endgame.toLogDetail(outcome.gameState),
                        )
                        engineMessage = "${outcome.engineMessage}\n${endgame.toEngineMessage()}"
                        candidateText = endgame.toCandidateText()
                    }.onFailure { error ->
                        val finalScoreText = "Final score failed: ${error.message ?: "Unknown error"}"
                        endgameLog = buildEndgameLog(
                            source = "auto-ai-engine-final-score-failed",
                            state = outcome.gameState,
                            finalScoreText = finalScoreText,
                            detail = "lastMove=${outcome.gameState.moves.lastOrNull()?.describe(outcome.gameState.boardSize) ?: "None"}",
                        )
                        engineMessage = "${outcome.engineMessage}\nFinal score failed: ${error.message ?: "Unknown error"}"
                        candidateText = "Game ended after two passes, but final score failed."
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
        val previousMoveReviews = moveReviews
        val afterMove = runCatching { beforeMove.play(move) }
            .onFailure { error ->
                engineMessage = error.message ?: "Illegal move."
            }
            .getOrNull()
            ?: return

        gameState = afterMove
        val moveReview = buildMoveReview(
            move = move,
            analysis = reviewAnalysis,
            boardSize = beforeMove.boardSize,
            moveNumber = afterMove.moves.size,
        )
        clearTopMoveSpots()
        moveReviewText = moveReview.text
        moveReviews = previousMoveReviews.withReviewMarker(moveReview.marker)
        clearReviewAnalysis(afterMove)
        lastAnalysisKey = null
        lastMoveText = move.describe(beforeMove.boardSize)
        scoreText = "Score estimate not current."
        scoreEstimate = null

        if (!isEngineReady) {
            scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localScoreSnapshot(afterMove))
            if (afterMove.hasConsecutivePasses()) {
                val finalScore = BoardScorer.score(afterMove)
                val finalScoreText = finalScore.toDisplayText()
                isGameEnded = true
                scoreText = finalScoreText
                scoreSnapshots = ScoreTimeline.record(
                    scoreSnapshots,
                    ScoreTimeline.fromFinalScore(
                        moveNumber = afterMove.moves.size,
                        finalScore = finalScore,
                        source = ScoreSnapshotSource.FinalScore,
                    ),
                )
                candidateText = "Game ended after two passes."
                endgameLog = buildEndgameLog(
                    source = "local-human-consecutive-pass",
                    state = afterMove,
                    finalScoreText = finalScoreText,
                    detail = "triggerMove=${move.describe(beforeMove.boardSize)}",
                )
                engineMessage = "Local game ended after two passes. ${finalScore.status.message}"
            } else {
                candidateText = "Captured: Black ${afterMove.capturedBy(StoneColor.Black)} / White ${afterMove.capturedBy(StoneColor.White)}"
                engineMessage = "Local move accepted without engine sync: ${move.describe(beforeMove.boardSize)}."
            }
            return
        }

        isEngineBusy = true
        scope.launch {
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.syncToGameState(afterMove)
                    if (afterMove.hasConsecutivePasses() || afterMove.isBoardFull()) {
                        LocalEngineMoveResult(
                            endgame = resolveAiEndgame(
                                engineAdapter = engineAdapter,
                                originalState = afterMove,
                                estimateLimit = scoreGraphAnalysisLimit(engineProfile),
                                prePassCandidates = if (move is Move.Pass) {
                                    previousReviewCandidates
                                } else {
                                    emptyList()
                                },
                            ),
                        )
                    } else {
                        LocalEngineMoveResult(
                            estimate = engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile)),
                        )
                    }
                }
            }.onSuccess { result ->
                val endgame = result.endgame
                val estimate = result.estimate
                if (endgame != null) {
                    isGameEnded = true
                    gameState = endgame.cleanup.state
                    val finalScoreText = endgame.finalScore.toDisplayText()
                    scoreText = finalScoreText
                    scoreEstimate = null
                    scoreSnapshots = ScoreTimeline.record(
                        scoreSnapshots,
                        ScoreTimeline.fromFinalScore(
                            moveNumber = endgame.cleanup.state.moves.size,
                            finalScore = endgame.finalScore,
                            source = ScoreSnapshotSource.FinalScore,
                        ),
                    )
                    endgameLog = buildEndgameLog(
                        source = "human-engine-dead-stone-cleanup",
                        state = endgame.cleanup.state,
                        finalScoreText = finalScoreText,
                        detail = endgame.toLogDetail(afterMove),
                    )
                    engineMessage = "Game ended after two passes.\n${endgame.toEngineMessage()}"
                    candidateText = endgame.toCandidateText()
                } else if (estimate != null) {
                    scoreText = estimate.toDisplayText()
                    scoreEstimate = estimate
                    scoreSnapshots = ScoreTimeline.record(
                        scoreSnapshots,
                        ScoreTimeline.fromEstimate(afterMove.moves.size, estimate),
                    )
                    candidateText = "Captured: Black ${afterMove.capturedBy(StoneColor.Black)} / White ${afterMove.capturedBy(StoneColor.White)}"
                    engineMessage = "Human move accepted and engine analysis synced: ${move.describe(beforeMove.boardSize)}."
                    nextAnalysisState = afterMove
                }
            }.onFailure { error ->
                scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localScoreSnapshot(afterMove))
                candidateText = "Captured: Black ${afterMove.capturedBy(StoneColor.Black)} / White ${afterMove.capturedBy(StoneColor.White)}"
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
            val nextState = gameState.replayWithoutLastMoves(1)
            gameState = nextState
            isGameEnded = false
            clearTopMoveSpots()
            clearReviewAnalysis(nextState)
            lastAnalysisKey = null
            moveReviews = emptyList()
            moveReviewText = "Move review cleared by undo."
            lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None"
            candidateText = "Captured: Black ${nextState.capturedBy(StoneColor.Black)} / White ${nextState.capturedBy(StoneColor.White)}"
            scoreText = "Score estimate not current."
            scoreEstimate = null
            scoreSnapshots = ScoreTimeline.record(
                ScoreTimeline.trimAfter(scoreSnapshots, nextState.moves.size),
                localScoreSnapshot(nextState),
            )
            endgameLog = "Endgame log cleared by undo."
            if (!isEngineReady) {
                engineMessage = "Local undo completed without engine sync."
                return
            }
            isEngineBusy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        engineAdapter.syncToGameState(nextState)
                        engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile))
                    }
                }.onSuccess { estimate ->
                    scoreText = estimate.toDisplayText()
                    scoreEstimate = estimate
                    scoreSnapshots = ScoreTimeline.record(
                        scoreSnapshots,
                        ScoreTimeline.fromEstimate(nextState.moves.size, estimate),
                    )
                    engineMessage = "Local undo completed and engine analysis synced."
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
                val nextState = gameState.replayWithoutLastMoves(undoCount)
                gameState = nextState
                isGameEnded = false
                clearTopMoveSpots()
                clearReviewAnalysis(nextState)
                lastAnalysisKey = null
                moveReviews = moveReviews.filter { marker -> marker.moveNumber <= nextState.moves.size }
                moveReviewText = "Move review cleared by undo."
                lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None"
                candidateText = "Undo cleared current Top Moves."
                scoreText = "Score estimate not current."
                scoreEstimate = null
                scoreSnapshots = ScoreTimeline.trimAfter(scoreSnapshots, nextState.moves.size)
                endgameLog = "Endgame log cleared by undo."
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

    val savedSessionToPrompt = pendingSavedSession
    if (
        shouldShowResumePrompt &&
        savedSessionToPrompt != null &&
        hasCompletedEngineStartup &&
        !isEngineBusy
    ) {
        AlertDialog(
            onDismissRequest = {
                sessionStore.clear()
                pendingSavedSession = null
                shouldShowResumePrompt = false
            },
            title = { Text("이전 대국 이어하기") },
            text = {
                Text(
                    text = buildString {
                        appendLine("진행 중이던 ${savedSessionToPrompt.gameState.moves.size}수 대국이 있습니다.")
                        appendLine("이어 진행하시겠습니까?")
                        appendLine()
                        append("마지막 수: ")
                        append(
                            savedSessionToPrompt.gameState.moves
                                .lastOrNull()
                                ?.describe(savedSessionToPrompt.gameState.boardSize)
                                ?: "None",
                        )
                        appendLine()
                        append(savedSessionToPrompt.playerSetup.summary(engineName))
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSavedSession = null
                        shouldShowResumePrompt = false
                        restoreSavedSession(savedSessionToPrompt)
                    },
                ) {
                    Text("예")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        sessionStore.clear()
                        pendingSavedSession = null
                        shouldShowResumePrompt = false
                    },
                ) {
                    Text("아니오")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Go AI Coach POC",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = modeSummary(playerSetup, engineName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            KaTrainUxMenuButton(
                menuExpanded = isDisplayMenuExpanded,
                onMenuExpandedChange = { expanded -> isDisplayMenuExpanded = expanded },
            )
        }

        if (isDisplayMenuExpanded) {
            PlayerSetupPanel(
                playerSetup = playerSetup,
                engineName = engineName,
                enabled = !isEngineBusy,
                onPlayerSetupChange = ::changePlayerSetup,
            )

            GameMenuActionsPanel(
                mode = matchMode,
                ruleset = gameState.ruleset,
                canStartNew = !isEngineBusy && (matchMode == MatchMode.LocalTwoPlayer || isEngineReady),
                canChangeRuleset = !isEngineBusy,
                onNewGame = ::startConfiguredGame,
                onCopyLog = ::copyDebugReport,
                onRulesetChange = ::changeScoringRule,
            )

            KaTrainUxMenuPanel(
                options = uxOptions,
                onOptionsChange = { nextOptions -> uxOptions = nextOptions },
            )

        }

        ScoreGraphPanel(
            snapshots = scoreSnapshots,
            capturedByBlack = gameState.capturedBy(StoneColor.Black),
            capturedByWhite = gameState.capturedBy(StoneColor.White),
            isExpanded = isScoreGraphExpanded,
            onExpandedChange = { expanded -> isScoreGraphExpanded = expanded },
        )

        GoBoard(
            gameState = gameState,
            candidateMoves = candidateMoves,
            moveReviews = moveReviews,
            ownershipEstimate = scoreEstimate?.ownership,
            uxOptions = uxOptions,
            inputEnabled = !isGameEnded && boardInputEnabled(playerSetup, isEngineReady, isEngineBusy, gameState.nextPlayer),
            engineBusy = isEngineBusy,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            onCoordinateTap = { coordinate ->
                submitHumanMove(Move.Play(gameState.nextPlayer, coordinate))
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    submitHumanMove(Move.Pass(gameState.nextPlayer))
                },
                enabled = !isGameEnded && boardInputEnabled(playerSetup, isEngineReady, isEngineBusy, gameState.nextPlayer),
                modifier = Modifier.weight(1f),
                contentPadding = ActionButtonContentPadding,
            ) {
                ActionButtonText("Pass")
            }

            OutlinedButton(
                onClick = ::undoLastTurn,
                enabled = !isEngineBusy &&
                    gameState.moves.isNotEmpty() &&
                    (isEngineReady || matchMode == MatchMode.LocalTwoPlayer),
                modifier = Modifier.weight(1f),
                contentPadding = ActionButtonContentPadding,
            ) {
                ActionButtonText("Undo")
            }

            val topMovesButtonEnabled = !isGameEnded &&
                isEngineReady &&
                (!isEngineBusy || topMovesEnabled)
            if (topMovesEnabled) {
                Button(
                    onClick = ::hideTopMoves,
                    enabled = topMovesButtonEnabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = ActionButtonContentPadding,
                ) {
                    ActionButtonText("Top Moves")
                }
            } else {
                OutlinedButton(
                    onClick = ::showTopMovesForCurrentState,
                    enabled = topMovesButtonEnabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = ActionButtonContentPadding,
                ) {
                    ActionButtonText("Top Moves")
                }
            }

            OutlinedButton(
                onClick = ::requestScoreEstimate,
                enabled = !isEngineBusy && (isEngineReady || matchMode == MatchMode.LocalTwoPlayer),
                modifier = Modifier.weight(1f),
                contentPadding = ActionButtonContentPadding,
            ) {
                ActionButtonText("Eval")
            }
        }

        EngineResponsePanel(
            nextPlayer = gameState.nextPlayer,
            moveCount = gameState.moves.size,
            capturedByBlack = gameState.capturedBy(StoneColor.Black),
            capturedByWhite = gameState.capturedBy(StoneColor.White),
            lastMoveText = lastMoveText,
            isEngineBusy = isEngineBusy,
            playerSetup = playerSetup,
            engineMessage = engineMessage,
            candidateText = candidateText,
            scoreText = scoreText,
            moveReviewText = moveReviewText,
        )
    }
}

@Composable
private fun ActionButtonText(label: String) {
    Text(
        text = label,
        maxLines = 1,
        softWrap = false,
        style = MaterialTheme.typography.labelSmall,
    )
}

private val ActionButtonContentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

private data class EngineStartupResult(
    val message: String,
    val scoreSnapshot: ScoreSnapshot?,
)

private data class AutoAiTurnResult(
    val turnOutcome: TurnOutcome,
    val scoreEstimate: ScoreEstimate?,
    val profile: EngineProfile,
    val playLevel: PlayLevelSetting,
)

private data class LocalEngineMoveResult(
    val estimate: ScoreEstimate? = null,
    val endgame: AiEndgameResolution? = null,
)

private suspend fun EngineAdapter.syncToGameState(state: GameState): EngineStatus {
    val status = newGame(state.boardSize, state.ruleset)
    state.moves.forEach { move ->
        playMove(move)
    }
    return status
}

private fun scoreGraphAnalysisLimit(profile: EngineProfile): AnalysisLimit =
    profile.analysisLimit.copy(candidateCount = 1)

private fun localScoreSnapshot(state: GameState): ScoreSnapshot =
    ScoreTimeline.fromFinalScore(
        moveNumber = state.moves.size,
        finalScore = BoardScorer.score(state),
        source = ScoreSnapshotSource.LocalAreaEstimate,
    )
