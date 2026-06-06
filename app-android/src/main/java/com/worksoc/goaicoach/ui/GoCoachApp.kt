package com.worksoc.goaicoach.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.worksoc.goaicoach.shared.BoardAreaScorer
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
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
    var gameState by remember { mutableStateOf(GameState.empty(BoardSize.Nine, Ruleset.Chinese)) }
    var engineMessage by remember { mutableStateOf("Engine not initialized.") }
    var candidateText by remember { mutableStateOf(engineDiagnostic) }
    var candidateMoves by remember { mutableStateOf(emptyList<CandidateMove>()) }
    var reviewCandidateMoves by remember { mutableStateOf(emptyList<CandidateMove>()) }
    var scoreText by remember { mutableStateOf("No score estimate yet.") }
    var scoreEstimate by remember { mutableStateOf<ScoreEstimate?>(null) }
    var scoreSnapshots by remember {
        mutableStateOf(listOf(localScoreSnapshot(GameState.empty(BoardSize.Nine, Ruleset.Chinese))))
    }
    var moveReviewText by remember { mutableStateOf("No move review yet.") }
    var moveReviews by remember { mutableStateOf(emptyList<MoveReviewMarker>()) }
    var lastMoveText by remember { mutableStateOf("None") }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }
    var engineProfile by remember { mutableStateOf(EngineProfile()) }
    var matchMode by remember { mutableStateOf(MatchMode.HumanVsAi) }
    var hintEnabled by remember { mutableStateOf(false) }
    var hintCount by remember { mutableStateOf(1) }
    var uxOptions by remember { mutableStateOf(KaTrainUxOptions()) }
    var isDisplayMenuExpanded by remember { mutableStateOf(false) }
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

    fun clearHints(message: String? = null) {
        candidateMoves = emptyList()
        if (message != null) {
            candidateText = message
        }
    }

    fun requestPreMoveAnalysisForState(
        targetState: GameState,
        showHints: Boolean,
        automatic: Boolean,
    ) {
        if (
            isGameEnded ||
            !isEngineReady ||
            isEngineBusy ||
            matchMode != MatchMode.HumanVsAi ||
            targetState.nextPlayer != HumanPlayer
        ) {
            return
        }

        val candidateCount = maxOf(BackgroundReviewCandidateCount, if (showHints) hintCount else 0)
        val analysisLimit = hintAnalysisLimitFor(engineProfile, candidateCount)
        val analysisKey = analysisKeyFor(targetState, analysisLimit)
        if (automatic && analysisKey == lastAnalysisKey) {
            if (showHints && candidateMoves.isEmpty() && reviewCandidateMoves.isNotEmpty()) {
                candidateMoves = reviewCandidateMoves.take(hintCount)
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
                if (showHints) {
                    engineMessage = result.status.message
                    candidateText = result.toCandidateText(targetState.boardSize)
                        .withHintStrengthHeader(engineProfile, analysisLimit)
                    candidateMoves = result.candidates.take(hintCount)
                } else {
                    engineMessage = "Move review analysis ready for ${targetState.nextPlayer.label}."
                }
            }.onFailure { error ->
                engineMessage = error.message ?: "Hint analysis failed."
                reviewCandidateMoves = emptyList()
                lastAnalysisKey = null
                if (showHints) {
                    clearHints("Hint analysis failed.")
                }
            }
            isEngineBusy = false
        }
    }

    fun requestHintsForCurrentState(automatic: Boolean) {
        requestPreMoveAnalysisForState(
            targetState = gameState,
            showHints = true,
            automatic = automatic,
        )
    }

    fun resetLocalGame(message: String) {
        gameState = GameState.empty(BoardSize.Nine, Ruleset.Chinese)
        isGameEnded = false
        clearHints("No analysis yet.")
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

        if (matchMode == MatchMode.LocalTwoPlayer) {
            val score = BoardAreaScorer.score(gameState)
            scoreText = score.toDisplayText()
            scoreEstimate = null
            scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localScoreSnapshot(gameState))
            engineMessage = "Local area estimate refreshed."
            return
        }

        if (!isEngineReady) {
            engineMessage = "Engine is not ready."
            return
        }

        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
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
        matchMode = MatchMode.HumanVsAi
        if (!isEngineReady) {
            resetLocalGame("AI mode selected, but engine is not ready.")
            return
        }
        if (isEngineBusy) {
            engineMessage = "AI is busy. Change mode after the current response."
            return
        }

        scope.launch {
            isEngineBusy = true
            var nextHintState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val status = engineAdapter.newGame(BoardSize.Nine, Ruleset.Chinese)
                    val estimate = runCatching {
                        engineAdapter.estimateScore(scoreGraphAnalysisLimit(engineProfile))
                    }.getOrNull()
                    EngineStartupResult(
                        message = status.message,
                        scoreSnapshot = estimate?.let { ScoreTimeline.fromEstimate(0, it) },
                    )
                }
            }.onSuccess { result ->
                resetLocalGame(result.message)
                scoreSnapshots = listOf(result.scoreSnapshot ?: localScoreSnapshot(gameState))
                nextHintState = gameState
            }.onFailure { error ->
                resetLocalGame(error.message ?: "New AI game failed.")
            }
            isEngineBusy = false
            requestPreMoveAnalysisForState(
                targetState = nextHintState ?: gameState,
                showHints = hintEnabled,
                automatic = true,
            )
        }
    }

    fun startLocalTwoPlayerGame() {
        matchMode = MatchMode.LocalTwoPlayer
        resetLocalGame("2P test mode. Local shared rules handle captures, suicide, and simple ko.")
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
            requestPreMoveAnalysisForState(
                targetState = gameState,
                showHints = hintEnabled,
                automatic = true,
            )
        }
    }

    fun submitHumanMove(move: Move) {
        if (matchMode == MatchMode.LocalTwoPlayer) {
            val beforeMove = gameState
            val afterMove = runCatching { beforeMove.play(move) }
                .onFailure { error ->
                    engineMessage = error.message ?: "Illegal move."
                }
                .getOrNull()
                ?: return

            gameState = afterMove
            clearHints()
            moveReviewText = "Move review is available in AI hint mode."
            moveReviews = emptyList()
            lastMoveText = move.describe(beforeMove.boardSize)
            scoreText = "Score estimate not current."
            scoreEstimate = null
            scoreSnapshots = ScoreTimeline.record(scoreSnapshots, localScoreSnapshot(afterMove))
            if (afterMove.hasConsecutivePasses()) {
                val finalScore = BoardAreaScorer.score(afterMove)
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
                engineMessage = "Local move accepted: ${move.describe(beforeMove.boardSize)}."
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
        clearHints()
        scoreText = "Score estimate not current."
        scoreEstimate = null
        lastMoveText = move.describe(beforeMove.boardSize)
        candidateText = "AI is thinking..."
        engineMessage = "Submitted ${move.describe(beforeMove.boardSize)}."
        isEngineBusy = true

        scope.launch {
            var nextHintState: GameState? = null
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
                clearHints()
                engineMessage = outcome.engineMessage
                candidateText = outcome.candidateText
                lastMoveText = outcome.lastMoveText
                nextHintState = outcome.gameState
                if (outcome.gameState.hasConsecutivePasses() || outcome.gameState.isBoardFull()) {
                    isGameEnded = true
                    runCatching {
                        withContext(Dispatchers.IO) { engineAdapter.scoreFinal() }
                    }.onSuccess { finalScore ->
                        val finalScoreText = finalScore.toDisplayText()
                        scoreText = finalScoreText
                        scoreEstimate = null
                        scoreSnapshots = ScoreTimeline.record(
                            scoreSnapshots,
                            ScoreTimeline.fromFinalScore(
                                moveNumber = outcome.gameState.moves.size,
                                finalScore = finalScore,
                                source = ScoreSnapshotSource.FinalScore,
                            ),
                        )
                        endgameLog = buildEndgameLog(
                            source = "ai-engine-final-score",
                            state = outcome.gameState,
                            finalScoreText = finalScoreText,
                            detail = "lastMove=${outcome.gameState.moves.lastOrNull()?.describe(outcome.gameState.boardSize) ?: "None"}",
                        )
                        engineMessage = "${outcome.engineMessage}\n${finalScore.status.message}"
                        candidateText = "Game ended. Final score is available below."
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
            nextHintState?.let { state ->
                requestPreMoveAnalysisForState(
                    targetState = state,
                    showHints = hintEnabled,
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
            val nextState = gameState.replayWithoutLastMoves(1)
            gameState = nextState
            isGameEnded = false
            clearHints()
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
            engineMessage = "Local undo completed."
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
            var nextHintState: GameState? = null
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
                clearHints()
                moveReviews = moveReviews.filter { marker -> marker.moveNumber <= nextState.moves.size }
                moveReviewText = "Move review cleared by undo."
                lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None"
                candidateText = "Undo cleared current analysis hints."
                scoreText = "Score estimate not current."
                scoreEstimate = null
                scoreSnapshots = ScoreTimeline.trimAfter(scoreSnapshots, nextState.moves.size)
                endgameLog = "Endgame log cleared by undo."
                engineMessage = "Undid $undoCount move(s) in local state and engine state."
                nextHintState = nextState
            }.onFailure { error ->
                engineMessage = error.message ?: "Undo failed."
            }
            isEngineBusy = false
            nextHintState?.let { state ->
                requestPreMoveAnalysisForState(
                    targetState = state,
                    showHints = hintEnabled,
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
            hintEnabled = hintEnabled,
            hintCount = hintCount,
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
        hintEnabled,
        hintCount,
    ) {
        requestPreMoveAnalysisForState(
            targetState = gameState,
            showHints = hintEnabled,
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

        ModePanel(
            mode = matchMode,
            engineName = engineName,
            engineDiagnostic = engineDiagnostic,
            canStartAi = isEngineReady && !isEngineBusy,
            canStartLocal = !isEngineBusy || !isEngineReady,
            onAiMode = ::startAiGame,
            onLocalTwoPlayerMode = ::startLocalTwoPlayerGame,
        )

        KaTrainUxQuickOptionsPanel(
            options = uxOptions,
            onOptionsChange = { nextOptions -> uxOptions = nextOptions },
        )

        KaTrainUxMenuControls(
            options = uxOptions,
            menuExpanded = isDisplayMenuExpanded,
            onMenuExpandedChange = { expanded -> isDisplayMenuExpanded = expanded },
            onOptionsChange = { nextOptions -> uxOptions = nextOptions },
        )

        if (isDisplayMenuExpanded) {
            EngineTuningPanel(
                profile = engineProfile,
                enabled = matchMode == MatchMode.HumanVsAi && isEngineReady && !isEngineBusy,
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

            HintControlsPanel(
                hintEnabled = hintEnabled,
                hintCount = hintCount,
                enabled = matchMode == MatchMode.HumanVsAi && isEngineReady && !isEngineBusy,
                onHintEnabledChange = { enabled ->
                    hintEnabled = enabled
                    if (enabled) {
                        requestHintsForCurrentState(automatic = false)
                    } else {
                        clearHints("Hints disabled.")
                    }
                },
                onHintCountChange = { count ->
                    hintCount = count
                    clearHints("Hint count set to $count.")
                    if (hintEnabled) {
                        requestHintsForCurrentState(automatic = false)
                    }
                },
            )
        }

        if (uxOptions.showHintLegend) {
            HintLegendPanel()
        }

        if (uxOptions.showGameStatusStrip) {
            GameStatusStrip(
                nextPlayer = gameState.nextPlayer,
                moveCount = gameState.moves.size,
                capturedByBlack = gameState.capturedBy(StoneColor.Black),
                capturedByWhite = gameState.capturedBy(StoneColor.White),
                lastMoveText = lastMoveText,
            )
        }

        if (uxOptions.showEngineStatusBadge) {
            EngineStatusBadge(
                engineName = engineName,
                isEngineReady = isEngineReady,
                isEngineBusy = matchMode == MatchMode.HumanVsAi && isEngineBusy,
                engineDiagnostic = engineDiagnostic,
            )
        }

        if (uxOptions.showScoreGraph) {
            ScoreGraphPanel(scoreSnapshots)
        }

        GoBoard(
            gameState = gameState,
            candidateMoves = candidateMoves,
            moveReviews = moveReviews,
            ownershipEstimate = scoreEstimate?.ownership,
            uxOptions = uxOptions,
            inputEnabled = !isGameEnded && boardInputEnabled(matchMode, isEngineReady, isEngineBusy, gameState.nextPlayer),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            onCoordinateTap = { coordinate ->
                submitHumanMove(Move.Play(activePlayer(matchMode, gameState), coordinate))
            },
        )

        if (uxOptions.showCandidateList) {
            CandidateMovesPanel(
                candidates = candidateMoves,
                boardSize = gameState.boardSize,
            )
        }

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
            ) {
                Text("Pass")
            }

            OutlinedButton(
                onClick = ::undoLastTurn,
                enabled = when (matchMode) {
                    MatchMode.HumanVsAi -> isEngineReady && !isEngineBusy && gameState.moves.isNotEmpty()
                    MatchMode.LocalTwoPlayer -> !isEngineBusy && gameState.moves.isNotEmpty()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Undo")
            }

            OutlinedButton(
                onClick = { requestHintsForCurrentState(automatic = false) },
                enabled = !isGameEnded && matchMode == MatchMode.HumanVsAi && isEngineReady && !isEngineBusy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Hint")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = ::requestScoreEstimate,
                enabled = when (matchMode) {
                    MatchMode.HumanVsAi -> isEngineReady && !isEngineBusy
                    MatchMode.LocalTwoPlayer -> !isEngineBusy
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Eval")
            }

            OutlinedButton(
                onClick = {
                    when (matchMode) {
                        MatchMode.HumanVsAi -> startAiGame()
                        MatchMode.LocalTwoPlayer -> startLocalTwoPlayerGame()
                    }
                },
                enabled = matchMode == MatchMode.LocalTwoPlayer || (isEngineReady && !isEngineBusy),
                modifier = Modifier.weight(1f),
            ) {
                Text("New")
            }

            OutlinedButton(
                onClick = ::copyDebugReport,
                modifier = Modifier.weight(1f),
            ) {
                Text("Copy Log")
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

private data class EngineStartupResult(
    val message: String,
    val scoreSnapshot: ScoreSnapshot?,
)

private data class ScoredTurnOutcome(
    val turnOutcome: TurnOutcome,
    val scoreSnapshots: List<ScoreSnapshot>,
)

private fun scoreGraphAnalysisLimit(profile: EngineProfile): AnalysisLimit =
    profile.analysisLimit.copy(candidateCount = 1)

private fun localScoreSnapshot(state: GameState): ScoreSnapshot =
    ScoreTimeline.fromFinalScore(
        moveNumber = state.moves.size,
        finalScore = BoardAreaScorer.score(state),
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
    hintEnabled: Boolean,
    hintCount: Int,
    gameState: GameState,
    engineMessage: String,
    candidateText: String,
    scoreText: String,
    scoreSnapshots: List<ScoreSnapshot>,
    moveReviewText: String,
    lastMoveText: String,
    endgameLog: String,
): String {
    val localScoreText = BoardAreaScorer.score(gameState).toDisplayText()

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
        appendLine("hintEnabled=$hintEnabled")
        appendLine("hintCount=$hintCount")
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
        appendLine("[LocalAreaScoreNow]")
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

private fun hintAnalysisLimitFor(
    profile: EngineProfile,
    candidateCount: Int,
): AnalysisLimit {
    val promoted = profile.difficulty.next().defaultAnalysisLimit()
    val promotedTimeMillis = promoted.timeMillis
        ?: profile.analysisLimit.timeMillis
        ?: 1_000L
    return profile.analysisLimit.copy(
        visits = maxOf(profile.analysisLimit.visits, promoted.visits),
        timeMillis = strongerHintTimeMillis(profile.analysisLimit.timeMillis, promotedTimeMillis),
        candidateCount = candidateCount,
    )
}

private fun strongerHintTimeMillis(
    current: Long?,
    promoted: Long,
): Long = current?.coerceAtLeast(promoted) ?: promoted

private fun String.withHintStrengthHeader(
    profile: EngineProfile,
    limit: AnalysisLimit,
): String {
    val hintDifficulty = profile.difficulty.next()
    val suffix = if (hintDifficulty == profile.difficulty) {
        "same as max profile"
    } else {
        "one grade above ${profile.difficulty.label}"
    }
    return "Hint strength: ${hintDifficulty.label} ($suffix), ${limit.visits} visits / ${limit.timeMillis ?: 0}ms\n$this"
}

private const val BackgroundReviewCandidateCount = 12
