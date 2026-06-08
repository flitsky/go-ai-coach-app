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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.match.applyAiTurn
import com.worksoc.goaicoach.match.boardInputEnabled
import com.worksoc.goaicoach.match.modeSummary
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStoneCleaner
import com.worksoc.goaicoach.shared.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.DeadStoneDetector
import com.worksoc.goaicoach.shared.DeadStoneRemoval
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.EndgameScoreSelector
import com.worksoc.goaicoach.shared.EndgameScoreSource
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.LegalMoveGenerator
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.describe
import com.worksoc.goaicoach.shared.replayWithoutLastMoves
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

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

    LaunchedEffect(engineAdapter) {
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
    }

    fun analysisKeyFor(
        state: GameState,
        limit: AnalysisLimit,
        deep: Boolean,
    ): AnalysisCacheKey =
        AnalysisCacheKey(
            positionFingerprint = state.analysisFingerprint(),
            preset = analysisPreset,
            limit = limit,
            deep = deep,
        )

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

    fun topMoveCandidateCountFor(state: GameState): Int =
        LegalMoveGenerator
            .legalPlayCount(state)
            .coerceAtLeast(1)
            .coerceAtMost(analysisPreset.candidateCap)

    fun List<CandidateMove>.scoredCandidateCount(): Int =
        count { it.pointLoss != null }

    fun currentTopMoveAnalysisLimit(state: GameState): AnalysisLimit =
        topMovesAnalysisLimitFor(
            profile = engineProfile,
            preset = analysisPreset,
            candidateCount = topMoveCandidateCountFor(state),
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
            isEngineBusy
        ) {
            return
        }
        if (playerSetup.sideFor(targetState.nextPlayer).controller == SeatController.Ai) {
            return
        }

        val candidateCount = topMoveCandidateCountFor(targetState)
        val analysisLimit = if (deep) {
            deepTopMovesAnalysisLimitFor(engineProfile, candidateCount)
        } else {
            topMovesAnalysisLimitFor(engineProfile, analysisPreset, candidateCount)
        }
        val analysisKey = analysisKeyFor(targetState, analysisLimit, deep)
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
            lastAnalysisKey == analysisKeyFor(gameState, currentTopMoveAnalysisLimit(gameState), deep = false)
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
        gameState.nextPlayer,
        gameState.moves.size,
    ) {
        requestTopMoveAnalysisForState(
            targetState = gameState,
            automatic = true,
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

private data class AiEndgameResolution(
    val cleanup: DeadStoneCleanupResult,
    val finalScore: FinalScoreResult,
    val scoreSource: EndgameScoreSource,
    val localFinalScore: FinalScoreResult,
    val deadStonesResult: DeadStonesResult?,
    val deadStonesError: String?,
    val locallyInferredDeadStones: List<BoardCoordinate>,
    val engineScoreEstimate: ScoreEstimate?,
    val engineScoreEstimateError: String?,
    val engineFinalScore: FinalScoreResult?,
    val engineFinalScoreError: String?,
    val prePassCandidates: List<CandidateMove>,
) {
    fun toEngineMessage(): String =
        buildString {
            if (cleanup.removedCount > 0) {
                append("Dead-stone cleanup removed ${cleanup.removedCount} stone(s). ")
            } else {
                append("Dead-stone cleanup found no stones to remove. ")
            }
            append(finalScore.status.message)
            if (scoreSource == EndgameScoreSource.UnsettledEngineEstimate) {
                append("\nShowing uncertain KataGo estimate because local area final disagrees with engine evaluation.")
            }
            if (scoreSource == EndgameScoreSource.UnsettledPrePassTopMoveEstimate) {
                append("\nShowing uncertain pre-pass Top Moves estimate because pass/pass final disagrees with the best pre-pass continuation.")
            }
            engineFinalScore?.let { append("\nDiagnostic KataGo final_score: ${it.rawScore}") }
            deadStonesError?.let { append("\nDead-stone status failed: $it") }
            engineScoreEstimateError?.let { append("\nEndgame estimate failed: $it") }
            engineFinalScoreError?.let { append("\nDiagnostic final_score failed: $it") }
        }

    fun toCandidateText(): String =
        when {
            cleanup.removedCount > 0 ->
                "Game ended after pass/pass. Removed ${cleanup.removedCount} engine-marked dead stone(s)."

            scoreSource == EndgameScoreSource.UnsettledEngineEstimate ->
                "Game ended after pass/pass, but the board looks unsettled. Showing KataGo estimate instead of raw local area."

            scoreSource == EndgameScoreSource.UnsettledPrePassTopMoveEstimate ->
                "Game ended after pass/pass, but pre-pass Top Moves indicated a better cleanup/continuation. Showing uncertain pre-pass estimate."

            else ->
                "Game ended after pass/pass. KataGo did not mark dead stones for removal."
        }

    fun toLogDetail(originalState: GameState): String =
        buildString {
            appendLine("lastMove=${originalState.moves.lastOrNull()?.describe(originalState.boardSize) ?: "None"}")
            appendLine("originalStoneCount=${originalState.stones.size}")
            appendLine("cleanedStoneCount=${cleanup.state.stones.size}")
            appendLine("deadStoneStatus=${deadStonesResult?.summary ?: "failed"}")
            appendLine("deadStoneError=${deadStonesError ?: "none"}")
            appendLine("locallyInferredDeadStones=${locallyInferredDeadStones.toCoordinateLogText(originalState.boardSize)}")
            appendLine("removedStones=${cleanup.removedStones.toLogText(originalState.boardSize)}")
            appendLine("displayScoreSource=$scoreSource")
            appendLine("localAreaAfterCleanup=${localFinalScore.rawScore}")
            appendLine("prePassTopMoves=${prePassCandidates.take(8).toCandidateLogText(originalState.boardSize)}")
            appendLine("engineEstimateWhiteLead=${engineScoreEstimate?.whiteScoreLead ?: "none"}")
            appendLine("engineEstimateWhiteWinRate=${engineScoreEstimate?.whiteWinRate ?: "none"}")
            appendLine("engineEstimateError=${engineScoreEstimateError ?: "none"}")
            appendLine("diagnosticKataGoFinalScore=${engineFinalScore?.rawScore ?: "none"}")
            appendLine("diagnosticKataGoFinalScoreError=${engineFinalScoreError ?: "none"}")
        }.trim()
}

private suspend fun resolveAiEndgame(
    engineAdapter: EngineAdapter,
    originalState: GameState,
    estimateLimit: AnalysisLimit,
    prePassCandidates: List<CandidateMove> = emptyList(),
): AiEndgameResolution {
    var deadStonesResult: DeadStonesResult? = null
    var deadStonesError: String? = null
    runCatching { engineAdapter.deadStones() }
        .onSuccess { deadStonesResult = it }
        .onFailure { deadStonesError = it.message ?: "Unknown error" }

    val locallyInferredDeadStones = DeadStoneDetector.capturableDeadStones(originalState)
    val cleanup = DeadStoneCleaner.apply(
        state = originalState,
        deadStoneCoordinates = deadStonesResult?.coordinates.orEmpty() + locallyInferredDeadStones,
    )
    val localFinalScore = BoardScorer.score(cleanup.state)

    var engineScoreEstimate: ScoreEstimate? = null
    var engineScoreEstimateError: String? = null
    runCatching { engineAdapter.estimateScore(estimateLimit) }
        .onSuccess { engineScoreEstimate = it }
        .onFailure { engineScoreEstimateError = it.message ?: "Unknown error" }
    val scoreSelection = EndgameScoreSelector.selectDisplayScore(
        cleanup = cleanup,
        localScore = localFinalScore,
        engineEstimate = engineScoreEstimate,
        prePassCandidates = prePassCandidates,
    )

    var engineFinalScore: FinalScoreResult? = null
    var engineFinalScoreError: String? = null
    runCatching { engineAdapter.scoreFinal() }
        .onSuccess { engineFinalScore = it }
        .onFailure { engineFinalScoreError = it.message ?: "Unknown error" }

    return AiEndgameResolution(
        cleanup = cleanup,
        finalScore = scoreSelection.displayScore,
        scoreSource = scoreSelection.source,
        localFinalScore = localFinalScore,
        deadStonesResult = deadStonesResult,
        deadStonesError = deadStonesError,
        locallyInferredDeadStones = locallyInferredDeadStones,
        engineScoreEstimate = engineScoreEstimate,
        engineScoreEstimateError = engineScoreEstimateError,
        engineFinalScore = engineFinalScore,
        engineFinalScoreError = engineFinalScoreError,
        prePassCandidates = prePassCandidates,
    )
}

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

private fun buildDebugReport(
    mode: MatchMode,
    playerSetup: PlayerSetup,
    engineName: String,
    engineDiagnostic: String,
    engineProfile: EngineProfile,
    playLevel: PlayLevelSetting,
    analysisPreset: AnalysisPreset,
    analysisCacheStats: String,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isGameEnded: Boolean,
    topMovesEnabled: Boolean,
    topMoveCandidateCount: Int,
    moveAnalysisCoverage: String,
    gameState: GameState,
    engineMessage: String,
    candidateText: String,
    scoreText: String,
    scoreSnapshots: List<ScoreSnapshot>,
    moveReviewText: String,
    lastMoveText: String,
    endgameLog: String,
): String {
    val localScoreText = BoardScorer.score(gameState).toDisplayText()

    return buildString {
        appendLine("Go AI Coach debug report")
        appendLine("createdAtMillis=${System.currentTimeMillis()}")
        appendLine()
        appendLine("[Runtime]")
        appendLine("mode=$mode")
        appendLine("playerSetup=${playerSetup.summary(engineName)}")
        appendLine("blackSeat=${playerSetup.black.summary(engineName)}")
        appendLine("whiteSeat=${playerSetup.white.summary(engineName)}")
        appendLine("engineName=$engineName")
        appendLine("engineReady=$isEngineReady")
        appendLine("engineBusy=$isEngineBusy")
        appendLine("gameEnded=$isGameEnded")
        appendLine("engineProfile=${engineProfile.name}/${engineProfile.mode}/${engineProfile.difficulty.label}")
        appendLine("playLevel=${playLevel.displayLabel} (${playLevel.selectionPolicy.description})")
        appendLine("analysisLimit=visits:${engineProfile.analysisLimit.visits}, timeMillis:${engineProfile.analysisLimit.timeMillis}, candidates:${engineProfile.analysisLimit.candidateCount}")
        appendLine("analysisPreset=${analysisPreset.label}")
        appendLine("analysisCache=$analysisCacheStats")
        appendLine("topMovesEnabled=$topMovesEnabled")
        appendLine("topMoveCandidateCount=$topMoveCandidateCount")
        appendLine("moveAnalysisCoverage=$moveAnalysisCoverage")
        appendLine()
        appendLine("[GameState]")
        appendLine("boardSize=${gameState.boardSize.value}")
        appendLine("ruleset=${gameState.ruleset}")
        appendLine("nextPlayer=${gameState.nextPlayer.label}")
        appendLine("moves=${gameState.moves.size}")
        appendLine("capturedByBlack=${gameState.capturedBy(StoneColor.Black)}")
        appendLine("capturedByWhite=${gameState.capturedBy(StoneColor.White)}")
        appendLine("consecutivePasses=${gameState.hasConsecutivePasses()}")
        appendLine("boardFull=${gameState.isBoardFull()}")
        appendLine("koPoint=${gameState.koPoint?.label(gameState.boardSize) ?: "none"}")
        appendLine("koForbiddenFor=${gameState.koForbiddenFor?.label ?: "none"}")
        appendLine()
        appendLine("[Board]")
        appendLine(gameState.toBoardText())
        appendLine()
        appendLine("[Stones]")
        appendLine(gameState.toStonesText())
        appendLine()
        appendLine("[Moves]")
        appendLine(gameState.toMovesText())
        appendLine()
        appendLine("[EndgameLog]")
        appendLine(endgameLog)
        appendLine()
        appendLine("[LocalRulesetScoreNow]")
        appendLine(localScoreText)
        appendLine()
        appendLine("[ScoreTimeline]")
        if (scoreSnapshots.isEmpty()) {
            appendLine("none")
        } else {
            scoreSnapshots.forEach { snapshot ->
                appendLine(
                    "${snapshot.moveNumber}. source=${snapshot.source}, whiteScoreLead=${snapshot.whiteScoreLead ?: "none"}, whiteWinRate=${snapshot.whiteWinRate ?: "none"}",
                )
            }
        }
        appendLine()
        appendLine("[DisplayedTexts]")
        appendLine("lastMove=$lastMoveText")
        appendLine("engineMessage:")
        appendLine(engineMessage)
        appendLine("scoreText:")
        appendLine(scoreText)
        appendLine("moveReviewText:")
        appendLine(moveReviewText)
        appendLine("candidateText:")
        appendLine(candidateText)
        appendLine()
        appendLine("[EngineDiagnostic]")
        appendLine(engineDiagnostic)
    }.trim()
}

private fun buildEndgameLog(
    source: String,
    state: GameState,
    finalScoreText: String,
    detail: String,
): String =
    buildString {
        appendLine("source=$source")
        appendLine("recordedAtMillis=${System.currentTimeMillis()}")
        appendLine("detail=$detail")
        appendLine("moveCount=${state.moves.size}")
        appendLine("lastTwoMoves=${state.moves.takeLast(2).joinToString { it.describe(state.boardSize) }}")
        appendLine("consecutivePasses=${state.hasConsecutivePasses()}")
        appendLine("boardFull=${state.isBoardFull()}")
        appendLine("capturedByBlack=${state.capturedBy(StoneColor.Black)}")
        appendLine("capturedByWhite=${state.capturedBy(StoneColor.White)}")
        appendLine("finalScoreText:")
        appendLine(finalScoreText)
    }.trim()

private fun List<DeadStoneRemoval>.toLogText(boardSize: BoardSize): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString { removal ->
            "${removal.coordinate.label(boardSize)}=${removal.color.label}"
        }
    }

private fun List<BoardCoordinate>.toCoordinateLogText(boardSize: BoardSize): String =
    if (isEmpty()) {
        "none"
    } else {
        distinct().joinToString { it.label(boardSize) }
    }

private fun List<CandidateMove>.toCandidateLogText(boardSize: BoardSize): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString(separator = " | ") { candidate ->
            buildString {
                append(candidate.move.describe(boardSize))
                candidate.scoreLead?.let { append(" scoreLead=$it") }
                candidate.pointLoss?.let { append(" pointLoss=$it") }
                candidate.winRate?.let { append(" winRate=$it") }
            }
        }
    }

private fun GameState.toBoardText(): String =
    buildString {
        val columns = boardColumnLabels(boardSize)
        append("   ")
        columns.forEach { column -> append(column).append(' ') }
        appendLine()

        for (row in 0 until boardSize.value) {
            val rowLabel = boardSize.value - row
            append(rowLabel.toString().padStart(2, ' ')).append(' ')
            for (column in 0 until boardSize.value) {
                val coordinate = BoardCoordinate(row, column)
                val marker = when (stoneAt(coordinate)) {
                    StoneColor.Black -> "X"
                    StoneColor.White -> "O"
                    null -> "."
                }
                append(marker).append(' ')
            }
            append(rowLabel)
            appendLine()
        }

        append("   ")
        columns.forEach { column -> append(column).append(' ') }
    }

private fun GameState.toStonesText(): String {
    if (stones.isEmpty()) {
        return "(none)"
    }

    return stones.entries
        .sortedWith(compareBy({ it.key.row }, { it.key.column }))
        .joinToString(separator = "\n") { (coordinate, color) ->
            "${coordinate.label(boardSize)}=${color.label}"
        }
}

private fun GameState.toMovesText(): String {
    if (moves.isEmpty()) {
        return "(none)"
    }

    return moves
        .mapIndexed { index, move -> "${index + 1}. ${move.describe(boardSize)}" }
        .joinToString(separator = "\n")
}

private fun boardColumnLabels(boardSize: BoardSize): List<Char> {
    val columns = "ABCDEFGHJKLMNOPQRSTUVWXYZ"
    return columns.take(boardSize.value).toList()
}

private data class MoveReviewResult(
    val marker: MoveReviewMarker?,
    val text: String,
)

private fun buildMoveReview(
    move: Move,
    analysis: MoveAnalysisSnapshot,
    boardSize: BoardSize,
    moveNumber: Int,
): MoveReviewResult {
    val play = move as? Move.Play
        ?: return MoveReviewResult(
            marker = null,
            text = "Move review: pass/resign has no board spot evaluation.",
        )

    if (!analysis.hasEngineCandidates) {
        return MoveReviewResult(
            marker = null,
            text = "Move review: no pre-move analysis cache was ready.",
        )
    }

    val matchedCandidate = analysis.candidateAt(play.coordinate)
    if (matchedCandidate == null) {
        return MoveReviewResult(
            marker = MoveReviewMarker(
                coordinate = play.coordinate,
                moveNumber = moveNumber,
                tone = MoveReviewTone.Unknown,
            ),
            text = "Move review: ${play.coordinate.label(boardSize)} was not legal in the pre-move analysis snapshot.",
        )
    }

    val pointLoss = matchedCandidate.pointLoss
    val tone = moveReviewToneFor(pointLoss)
    val lossText = matchedCandidate.pointLossLabel()
        ?.let { "loss $it point(s)" }
        ?: "score loss pending"
    val priorText = matchedCandidate.policyPrior
        ?.let { ", policy ${(it * 100).toInt()}%" }
        .orEmpty()

    return MoveReviewResult(
        marker = MoveReviewMarker(
            coordinate = play.coordinate,
            moveNumber = moveNumber,
            tone = tone,
        ),
        text = "Move review: ${play.coordinate.label(boardSize)} ${moveReviewTextFor(pointLoss)} ($lossText$priorText).",
    )
}

private fun List<MoveReviewMarker>.withReviewMarker(
    marker: MoveReviewMarker?,
): List<MoveReviewMarker> =
    if (marker == null) {
        this
    } else {
        filterNot { existing -> existing.moveNumber == marker.moveNumber } + marker
    }

private fun topMovesAnalysisLimitFor(
    profile: EngineProfile,
    preset: AnalysisPreset,
    candidateCount: Int,
): AnalysisLimit {
    val promoted = if (preset.promoteTopMovesDifficulty) {
        profile.difficulty.next().defaultAnalysisLimit()
    } else {
        profile.analysisLimit
    }
    val promotedTimeMillis = promoted.timeMillis ?: profile.analysisLimit.timeMillis
    return profile.analysisLimit.copy(
        visits = maxOf(profile.analysisLimit.visits, promoted.visits),
        timeMillis = promotedTimeMillis?.let {
            strongerTopMovesTimeMillis(profile.analysisLimit.timeMillis, it)
        } ?: profile.analysisLimit.timeMillis,
        candidateCount = candidateCount,
        includePolicy = preset.includePolicy,
        refinePolicyMoves = preset.refinePolicyMoves,
        minVisitsPerCandidate = preset.minVisitsPerCandidate,
        minTimeMillis = preset.minTimeMillis,
    )
}

private fun deepTopMovesAnalysisLimitFor(
    profile: EngineProfile,
    candidateCount: Int,
): AnalysisLimit {
    val full = DifficultyProfile.FullAnalysis.defaultAnalysisLimit()
    val fullTimeMillis = full.timeMillis
        ?: profile.analysisLimit.timeMillis
        ?: 5_000L
    return profile.analysisLimit.copy(
        visits = maxOf(profile.analysisLimit.visits, full.visits),
        timeMillis = strongerTopMovesTimeMillis(profile.analysisLimit.timeMillis, fullTimeMillis),
        candidateCount = candidateCount,
        includePolicy = AnalysisPreset.Deep.includePolicy,
        refinePolicyMoves = AnalysisPreset.Deep.refinePolicyMoves,
        minVisitsPerCandidate = AnalysisPreset.Deep.minVisitsPerCandidate,
        minTimeMillis = AnalysisPreset.Deep.minTimeMillis,
    )
}

private fun strongerTopMovesTimeMillis(
    current: Long?,
    promoted: Long,
): Long = current?.coerceAtLeast(promoted) ?: promoted

private fun String.withTopMovesStrengthHeader(
    profile: EngineProfile,
    preset: AnalysisPreset,
    limit: AnalysisLimit,
    candidateCount: Int,
    deep: Boolean,
): String {
    val label = if (deep) {
        DifficultyProfile.FullAnalysis.label
    } else if (!preset.promoteTopMovesDifficulty) {
        profile.difficulty.label
    } else {
        profile.difficulty.next().label
    }
    val suffix = if (deep) {
        "manual deep analysis"
    } else if (profile.difficulty.next() == profile.difficulty) {
        "same as max profile"
    } else if (!preset.promoteTopMovesDifficulty) {
        "same as ${profile.difficulty.label}"
    } else {
        "one grade above ${profile.difficulty.label}"
    }
    return "Top Moves request: ${preset.label}, up to $candidateCount candidate(s), $label ($suffix), base ${limit.visits} visits / ${limit.timeMillis ?: 0}ms, refine ${limit.refinePolicyMoves}\n$this"
}

private fun String.withAnalysisCoverage(snapshot: MoveAnalysisSnapshot): String =
    "${snapshot.coverageSummary()}\n$this"

private const val MaxTopMoveCandidateCount = 81
private const val MinScoredTopMovesForDisplay = 5

private data class AnalysisCacheKey(
    val positionFingerprint: String,
    val preset: AnalysisPreset,
    val limit: AnalysisLimit,
    val deep: Boolean,
)

private data class CachedAnalysisResult(
    val snapshot: MoveAnalysisSnapshot,
    val candidateText: String,
)

private class AnalysisResultCache(
    private val maxEntries: Int,
) {
    private val entries = object : LinkedHashMap<AnalysisCacheKey, CachedAnalysisResult>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<AnalysisCacheKey, CachedAnalysisResult>?,
        ): Boolean = size > maxEntries
    }

    private var hits: Int = 0
    private var misses: Int = 0

    fun get(key: AnalysisCacheKey): CachedAnalysisResult? {
        val value = entries[key]
        if (value == null) {
            misses += 1
        } else {
            hits += 1
        }
        return value
    }

    fun put(
        key: AnalysisCacheKey,
        result: CachedAnalysisResult,
    ) {
        entries[key] = result
    }

    fun statsText(): String =
        "entries=${entries.size}, hits=$hits, misses=$misses"
}
