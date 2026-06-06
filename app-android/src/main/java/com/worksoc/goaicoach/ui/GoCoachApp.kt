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
import com.worksoc.goaicoach.match.HumanPlayer
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.match.activePlayer
import com.worksoc.goaicoach.match.applyAiResponseAfterHumanTurn
import com.worksoc.goaicoach.match.boardInputEnabled
import com.worksoc.goaicoach.match.modeSummary
import com.worksoc.goaicoach.shared.AnalysisLimit
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
import kotlin.math.roundToInt

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
    var engineProfile by remember { mutableStateOf(EngineProfile()) }
    var matchMode by remember { mutableStateOf(MatchMode.HumanVsAi) }
    var topMovesEnabled by remember { mutableStateOf(false) }
    var uxOptions by remember { mutableStateOf(KaTrainUxOptions()) }
    var isDisplayMenuExpanded by remember { mutableStateOf(false) }
    var isScoreGraphExpanded by remember { mutableStateOf(false) }
    var lastAnalysisKey by remember { mutableStateOf<String?>(null) }
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
    ): String =
        buildString {
            append(state.nextPlayer.name)
            append("|")
            append(limit.candidateCount)
            append("|")
            append(limit.visits)
            append("|")
            append(limit.timeMillis ?: "none")
            append("|")
            state.moves.forEach { move ->
                append(move.describe(state.boardSize))
                append(";")
            }
        }

    fun clearTopMoveSpots(message: String? = null) {
        candidateMoves = emptyList()
        if (message != null) {
            candidateText = message
        }
    }

    fun topMoveCandidateCountFor(state: GameState): Int =
        LegalMoveGenerator.legalPlayCount(state).coerceAtLeast(1)

    fun currentTopMoveAnalysisLimit(state: GameState): AnalysisLimit =
        topMovesAnalysisLimitFor(
            profile = engineProfile,
            candidateCount = topMoveCandidateCountFor(state),
        )

    fun requestTopMoveAnalysisForState(
        targetState: GameState,
        automatic: Boolean,
    ) {
        if (
            isGameEnded ||
            !isEngineReady ||
            isEngineBusy
        ) {
            return
        }
        if (matchMode == MatchMode.HumanVsAi && targetState.nextPlayer != HumanPlayer) {
            return
        }

        val candidateCount = topMoveCandidateCountFor(targetState)
        val analysisLimit = topMovesAnalysisLimitFor(engineProfile, candidateCount)
        val analysisKey = analysisKeyFor(targetState, analysisLimit)
        if (automatic && analysisKey == lastAnalysisKey) {
            if (topMovesEnabled && candidateMoves.isEmpty() && reviewCandidateMoves.isNotEmpty()) {
                candidateMoves = reviewCandidateMoves
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
                reviewCandidateMoves = result.candidates.take(candidateCount)
                candidateText = result.toCandidateText(targetState.boardSize)
                    .withTopMovesStrengthHeader(engineProfile, analysisLimit, candidateCount)
                if (topMovesEnabled) {
                    engineMessage = result.status.message
                    candidateMoves = reviewCandidateMoves
                } else {
                    engineMessage = "Top Moves analysis ready for ${targetState.nextPlayer.label}: ${reviewCandidateMoves.size}/${candidateCount} spot(s)."
                }
            }.onFailure { error ->
                engineMessage = error.message ?: "Top Moves analysis failed."
                reviewCandidateMoves = emptyList()
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
            reviewCandidateMoves.isNotEmpty() &&
            lastAnalysisKey == analysisKeyFor(gameState, currentTopMoveAnalysisLimit(gameState))
        ) {
            candidateMoves = reviewCandidateMoves
            engineMessage = "Showing ${candidateMoves.size} Top Moves from cached pre-move analysis."
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
        reviewCandidateMoves = emptyList()
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
        reviewCandidateMoves = emptyList()
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

    fun startAiGame() {
        val targetRuleset = gameState.ruleset
        matchMode = MatchMode.HumanVsAi
        if (!isEngineReady) {
            resetLocalGame("AI mode selected, but engine is not ready.", targetRuleset)
            return
        }
        if (isEngineBusy) {
            engineMessage = "AI is busy. Change mode after the current response."
            return
        }

        scope.launch {
            isEngineBusy = true
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val status = engineAdapter.newGame(BoardSize.Nine, targetRuleset)
                    val estimate = runCatching {
                        engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile))
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

    fun startLocalTwoPlayerGame() {
        val targetRuleset = gameState.ruleset
        matchMode = MatchMode.LocalTwoPlayer
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

    fun configureEngine(nextProfile: EngineProfile) {
        if (!isEngineReady) {
            engineMessage = "Engine is not ready."
            return
        }
        if (isEngineBusy) {
            engineMessage = "AI is busy. Change settings after the current response."
            return
        }

        engineProfile = nextProfile
        reviewCandidateMoves = emptyList()
        lastAnalysisKey = null
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) { engineAdapter.configure(nextProfile) }
            }.onSuccess { status ->
                engineMessage = status.message
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

    fun submitHumanMove(move: Move) {
        if (matchMode == MatchMode.LocalTwoPlayer) {
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
                candidates = reviewCandidateMoves,
                boardSize = beforeMove.boardSize,
                moveNumber = afterMove.moves.size,
            )
            clearTopMoveSpots()
            moveReviewText = moveReview.text
            moveReviews = previousMoveReviews.withReviewMarker(moveReview.marker)
            reviewCandidateMoves = emptyList()
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
                    scoreEstimate = null
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
                        source = "local-two-player-consecutive-pass",
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
                            source = "local-two-player-engine-dead-stone-cleanup",
                            state = endgame.cleanup.state,
                            finalScoreText = finalScoreText,
                            detail = endgame.toLogDetail(afterMove),
                        )
                        engineMessage = "2P game ended after two passes.\n${endgame.toEngineMessage()}"
                        candidateText = endgame.toCandidateText()
                    } else if (estimate != null) {
                        scoreText = estimate.toDisplayText()
                        scoreEstimate = estimate
                        scoreSnapshots = ScoreTimeline.record(
                            scoreSnapshots,
                            ScoreTimeline.fromEstimate(afterMove.moves.size, estimate),
                        )
                        candidateText = "Captured: Black ${afterMove.capturedBy(StoneColor.Black)} / White ${afterMove.capturedBy(StoneColor.White)}"
                        engineMessage = "Local move accepted and engine analysis synced: ${move.describe(beforeMove.boardSize)}."
                        nextAnalysisState = afterMove
                    }
                }.onFailure { error ->
                    scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localScoreSnapshot(afterMove))
                    candidateText = "Captured: Black ${afterMove.capturedBy(StoneColor.Black)} / White ${afterMove.capturedBy(StoneColor.White)}"
                    engineMessage = error.message ?: "2P engine sync failed."
                }
                isEngineBusy = false
                nextAnalysisState?.let { state ->
                    requestTopMoveAnalysisForState(
                        targetState = state,
                        automatic = true,
                    )
                }
            }
            return
        }

        if (!isEngineReady) {
            engineMessage = "Engine is not ready."
            return
        }
        if (isEngineBusy) {
            engineMessage = "AI is thinking. Wait for the current response."
            return
        }
        if (gameState.nextPlayer != HumanPlayer) {
            engineMessage = "It is not the human player's turn."
            return
        }

        val beforeMove = gameState
        val previousReviewCandidates = reviewCandidateMoves
        val previousAnalysisKey = lastAnalysisKey
        val afterHuman = runCatching { beforeMove.play(move) }
            .onFailure { error ->
                engineMessage = error.message ?: "Illegal move."
            }
            .getOrNull()
            ?: return

        gameState = afterHuman
        val previousMoveReviews = moveReviews
        val previousScoreSnapshots = scoreSnapshots
        val moveReview = buildMoveReview(
            move = move,
            candidates = reviewCandidateMoves,
            boardSize = beforeMove.boardSize,
            moveNumber = afterHuman.moves.size,
        )
        moveReviewText = moveReview.text
        moveReviews = previousMoveReviews.withReviewMarker(moveReview.marker)
        reviewCandidateMoves = emptyList()
        lastAnalysisKey = null
        clearTopMoveSpots()
        scoreText = "Score estimate not current."
        scoreEstimate = null
        lastMoveText = move.describe(beforeMove.boardSize)
        candidateText = "AI is thinking..."
        engineMessage = "Submitted ${move.describe(beforeMove.boardSize)}."
        isEngineBusy = true

        scope.launch {
            var nextAnalysisState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    var nextScoreSnapshots = previousScoreSnapshots
                    val outcome = applyAiResponseAfterHumanTurn(
                        engineAdapter = engineAdapter,
                        stateAfterHuman = afterHuman,
                        humanMove = move,
                        onHumanMoveAccepted = {
                            runCatching {
                                engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile))
                            }.getOrNull()?.let { estimate ->
                                nextScoreSnapshots = ScoreTimeline.record(
                                    nextScoreSnapshots,
                                    ScoreTimeline.fromEstimate(afterHuman.moves.size, estimate),
                                )
                            }
                        },
                    )
                    runCatching {
                        engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile))
                    }.getOrNull()?.let { estimate ->
                        nextScoreSnapshots = ScoreTimeline.record(
                            nextScoreSnapshots,
                            ScoreTimeline.fromEstimate(outcome.gameState.moves.size, estimate),
                        )
                    }
                    ScoredTurnOutcome(
                        turnOutcome = outcome,
                        scoreSnapshots = nextScoreSnapshots,
                    )
                }
            }.onSuccess { scoredOutcome ->
                val outcome = scoredOutcome.turnOutcome
                gameState = outcome.gameState
                scoreSnapshots = scoredOutcome.scoreSnapshots
                clearTopMoveSpots()
                engineMessage = outcome.engineMessage
                candidateText = outcome.candidateText
                lastMoveText = outcome.lastMoveText
                nextAnalysisState = outcome.gameState
                if (outcome.gameState.hasConsecutivePasses() || outcome.gameState.isBoardFull()) {
                    isGameEnded = true
                    runCatching {
                        withContext(Dispatchers.IO) {
                            resolveAiEndgame(
                                engineAdapter = engineAdapter,
                                originalState = outcome.gameState,
                                estimateLimit = scoreGraphAnalysisLimit(engineProfile),
                                prePassCandidates = if (move is Move.Pass) {
                                    previousReviewCandidates
                                } else {
                                    emptyList()
                                },
                            )
                        }
                    }.onSuccess { endgame ->
                        gameState = endgame.cleanup.state
                        nextAnalysisState = null
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
                            source = "ai-engine-dead-stone-cleanup",
                            state = endgame.cleanup.state,
                            finalScoreText = finalScoreText,
                            detail = endgame.toLogDetail(outcome.gameState),
                        )
                        engineMessage = "${outcome.engineMessage}\n${endgame.toEngineMessage()}"
                        candidateText = endgame.toCandidateText()
                    }.onFailure { error ->
                        val finalScoreText = "Final score failed: ${error.message ?: "Unknown error"}"
                        endgameLog = buildEndgameLog(
                            source = "ai-engine-final-score-failed",
                            state = outcome.gameState,
                            finalScoreText = finalScoreText,
                            detail = "lastMove=${outcome.gameState.moves.lastOrNull()?.describe(outcome.gameState.boardSize) ?: "None"}",
                        )
                        engineMessage = "${outcome.engineMessage}\nFinal score failed: ${error.message ?: "Unknown error"}"
                        candidateText = "Game ended after two passes, but final score failed."
                    }
                }
            }.onFailure { error ->
                gameState = beforeMove
                moveReviews = previousMoveReviews
                reviewCandidateMoves = previousReviewCandidates
                lastAnalysisKey = previousAnalysisKey
                scoreSnapshots = previousScoreSnapshots
                moveReviewText = "Move review cleared after rollback."
                engineMessage = error.message ?: "Move failed."
                candidateText = "Move was rolled back after engine failure."
                lastMoveText = "None"
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

        val undoCount = minOf(2, gameState.moves.size)
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
            engineName = engineName,
            engineDiagnostic = engineDiagnostic,
            engineProfile = engineProfile,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            isGameEnded = isGameEnded,
            topMovesEnabled = topMovesEnabled,
            topMoveCandidateCount = reviewCandidateMoves.size,
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
        matchMode,
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
                    text = modeSummary(matchMode, engineName),
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
            ModePanel(
                mode = matchMode,
                engineName = engineName,
                engineDiagnostic = engineDiagnostic,
                canStartAi = isEngineReady && !isEngineBusy,
                canStartLocal = !isEngineBusy || !isEngineReady,
                onAiMode = ::startAiGame,
                onLocalTwoPlayerMode = ::startLocalTwoPlayerGame,
            )

            GameMenuActionsPanel(
                mode = matchMode,
                ruleset = gameState.ruleset,
                canStartNew = !isEngineBusy && (matchMode == MatchMode.LocalTwoPlayer || isEngineReady),
                canChangeRuleset = !isEngineBusy,
                onNewGame = {
                    when (matchMode) {
                        MatchMode.HumanVsAi -> startAiGame()
                        MatchMode.LocalTwoPlayer -> startLocalTwoPlayerGame()
                    }
                },
                onCopyLog = ::copyDebugReport,
                onRulesetChange = ::changeScoringRule,
            )

            KaTrainUxMenuPanel(
                options = uxOptions,
                onOptionsChange = { nextOptions -> uxOptions = nextOptions },
            )

            EngineTuningPanel(
                profile = engineProfile,
                enabled = isEngineReady && !isEngineBusy,
                onDifficultyChange = { difficulty: DifficultyProfile ->
                    configureEngine(
                        engineProfile.copy(
                            difficulty = difficulty,
                            analysisLimit = difficulty.defaultAnalysisLimit(),
                        ),
                    )
                },
                onVisitsChange = { visits ->
                    configureEngine(
                        engineProfile.copy(
                            analysisLimit = engineProfile.analysisLimit.copy(visits = visits),
                        ),
                    )
                },
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
            inputEnabled = !isGameEnded && boardInputEnabled(matchMode, isEngineReady, isEngineBusy, gameState.nextPlayer),
            engineBusy = matchMode == MatchMode.HumanVsAi && isEngineBusy,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            onCoordinateTap = { coordinate ->
                submitHumanMove(Move.Play(activePlayer(matchMode, gameState), coordinate))
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    submitHumanMove(Move.Pass(activePlayer(matchMode, gameState)))
                },
                enabled = !isGameEnded && boardInputEnabled(matchMode, isEngineReady, isEngineBusy, gameState.nextPlayer),
                modifier = Modifier.weight(1f),
                contentPadding = ActionButtonContentPadding,
            ) {
                ActionButtonText("Pass")
            }

            OutlinedButton(
                onClick = ::undoLastTurn,
                enabled = when (matchMode) {
                    MatchMode.HumanVsAi -> isEngineReady && !isEngineBusy && gameState.moves.isNotEmpty()
                    MatchMode.LocalTwoPlayer -> !isEngineBusy && gameState.moves.isNotEmpty()
                },
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
                enabled = when (matchMode) {
                    MatchMode.HumanVsAi -> isEngineReady && !isEngineBusy
                    MatchMode.LocalTwoPlayer -> !isEngineBusy
                },
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
            isEngineBusy = matchMode == MatchMode.HumanVsAi && isEngineBusy,
            mode = matchMode,
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

private data class ScoredTurnOutcome(
    val turnOutcome: TurnOutcome,
    val scoreSnapshots: List<ScoreSnapshot>,
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
    engineName: String,
    engineDiagnostic: String,
    engineProfile: EngineProfile,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isGameEnded: Boolean,
    topMovesEnabled: Boolean,
    topMoveCandidateCount: Int,
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
        appendLine("engineName=$engineName")
        appendLine("engineReady=$isEngineReady")
        appendLine("engineBusy=$isEngineBusy")
        appendLine("gameEnded=$isGameEnded")
        appendLine("engineProfile=${engineProfile.name}/${engineProfile.mode}/${engineProfile.difficulty.label}")
        appendLine("analysisLimit=visits:${engineProfile.analysisLimit.visits}, timeMillis:${engineProfile.analysisLimit.timeMillis}, candidates:${engineProfile.analysisLimit.candidateCount}")
        appendLine("topMovesEnabled=$topMovesEnabled")
        appendLine("topMoveCandidateCount=$topMoveCandidateCount")
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
    candidates: List<CandidateMove>,
    boardSize: BoardSize,
    moveNumber: Int,
): MoveReviewResult {
    val play = move as? Move.Play
        ?: return MoveReviewResult(
            marker = null,
            text = "Move review: pass/resign has no board spot evaluation.",
        )

    if (candidates.isEmpty()) {
        return MoveReviewResult(
            marker = null,
            text = "Move review: no pre-move analysis cache was ready.",
        )
    }

    val matchedCandidate = candidates.firstOrNull { candidate ->
        (candidate.move as? Move.Play)?.coordinate == play.coordinate
    }
    if (matchedCandidate == null) {
        return MoveReviewResult(
            marker = MoveReviewMarker(
                coordinate = play.coordinate,
                moveNumber = moveNumber,
                tone = MoveReviewTone.Mistake,
            ),
            text = "Move review: ${play.coordinate.label(boardSize)} was outside the analyzed top ${candidates.size} candidate(s).",
        )
    }

    val pointLoss = matchedCandidate.pointLoss
    val tone = moveReviewToneFor(pointLoss)
    val scoreText = matchedCandidate.scoreLead
        ?.toPlayerPerspective(play.player)
        ?.toSignedOneDecimal()
        ?.let { ", score lead $it" }
        .orEmpty()
    val lossText = pointLoss
        ?.let { "loss ${it.formatOneDecimal()} point(s) vs best" }
        ?: "score loss unavailable"
    val priorText = matchedCandidate.policyPrior
        ?.let { ", policy ${(it * 100).toInt()}%" }
        .orEmpty()

    return MoveReviewResult(
        marker = MoveReviewMarker(
            coordinate = play.coordinate,
            moveNumber = moveNumber,
            tone = tone,
        ),
        text = "Move review: ${play.coordinate.label(boardSize)} ${moveReviewTextFor(pointLoss)} ($lossText$scoreText$priorText).",
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

private fun Double.toPlayerPerspective(player: StoneColor): Double =
    when (player) {
        StoneColor.Black -> -this
        StoneColor.White -> this
    }

private fun Double.toSignedOneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    val normalized = if (kotlin.math.abs(rounded) < 0.05) 0.0 else rounded
    return if (normalized > 0.0) "+$normalized" else normalized.toString()
}

private fun Double.formatOneDecimal(): String =
    ((this * 10).roundToInt() / 10.0).toString()

private fun topMovesAnalysisLimitFor(
    profile: EngineProfile,
    candidateCount: Int,
): AnalysisLimit {
    val promoted = profile.difficulty.next().defaultAnalysisLimit()
    val promotedTimeMillis = promoted.timeMillis
        ?: profile.analysisLimit.timeMillis
        ?: 1_000L
    return profile.analysisLimit.copy(
        visits = maxOf(profile.analysisLimit.visits, promoted.visits),
        timeMillis = strongerTopMovesTimeMillis(profile.analysisLimit.timeMillis, promotedTimeMillis),
        candidateCount = candidateCount,
    )
}

private fun strongerTopMovesTimeMillis(
    current: Long?,
    promoted: Long,
): Long = current?.coerceAtLeast(promoted) ?: promoted

private fun String.withTopMovesStrengthHeader(
    profile: EngineProfile,
    limit: AnalysisLimit,
    candidateCount: Int,
): String {
    val analysisDifficulty = profile.difficulty.next()
    val suffix = if (analysisDifficulty == profile.difficulty) {
        "same as max profile"
    } else {
        "one grade above ${profile.difficulty.label}"
    }
    return "Top Moves request: $candidateCount legal spot(s), ${analysisDifficulty.label} ($suffix), base ${limit.visits} visits / ${limit.timeMillis ?: 0}ms\n$this"
}
