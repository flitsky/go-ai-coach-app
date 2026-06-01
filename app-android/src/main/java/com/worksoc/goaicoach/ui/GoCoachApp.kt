package com.worksoc.goaicoach.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.HumanPlayer
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.activePlayer
import com.worksoc.goaicoach.match.applyAiResponseAfterHumanTurn
import com.worksoc.goaicoach.match.boardInputEnabled
import com.worksoc.goaicoach.match.modeSummary
import com.worksoc.goaicoach.shared.BoardAreaScorer
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
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
    var gameState by remember { mutableStateOf(GameState.empty(BoardSize.Nine, Ruleset.Chinese)) }
    var engineMessage by remember { mutableStateOf("Engine not initialized.") }
    var candidateText by remember { mutableStateOf(engineDiagnostic) }
    var candidateMoves by remember { mutableStateOf(emptyList<CandidateMove>()) }
    var scoreText by remember { mutableStateOf("No score estimate yet.") }
    var moveReviewText by remember { mutableStateOf("No move review yet.") }
    var lastMoveReview by remember { mutableStateOf<MoveReviewMarker?>(null) }
    var lastMoveText by remember { mutableStateOf("None") }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }
    var engineProfile by remember { mutableStateOf(EngineProfile()) }
    var matchMode by remember { mutableStateOf(MatchMode.HumanVsAi) }
    var hintEnabled by remember { mutableStateOf(false) }
    var hintCount by remember { mutableStateOf(1) }
    var lastHintKey by remember { mutableStateOf<String?>(null) }
    var isGameEnded by remember { mutableStateOf(false) }

    LaunchedEffect(engineAdapter) {
        isEngineBusy = true
        runCatching {
            val init = withContext(Dispatchers.IO) { engineAdapter.initialize(engineProfile) }
            val newGame = withContext(Dispatchers.IO) {
                engineAdapter.newGame(gameState.boardSize, gameState.ruleset)
            }
            "Ready for 9x9 match.\n${init.message}\n${newGame.message}"
        }.onSuccess { message ->
            isEngineReady = true
            engineMessage = message
        }.onFailure { error ->
            isEngineReady = false
            engineMessage = "Engine initialization failed.\n${error.message ?: "Unknown error"}"
            candidateText = "2P test mode is still available.\n$engineDiagnostic"
        }
        isEngineBusy = false
    }

    fun hintKeyFor(
        state: GameState,
        count: Int,
    ): String =
        buildString {
            append(state.nextPlayer.name)
            append("|")
            append(count)
            append("|")
            append(engineProfile.analysisLimit.visits)
            append("|")
            append(engineProfile.analysisLimit.timeMillis ?: "none")
            append("|")
            state.moves.forEach { move ->
                append(move.describe(state.boardSize))
                append(";")
            }
        }

    fun clearHints(message: String? = null) {
        candidateMoves = emptyList()
        lastHintKey = null
        if (message != null) {
            candidateText = message
        }
    }

    fun requestHintsForState(
        targetState: GameState,
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

        val hintKey = hintKeyFor(targetState, hintCount)
        if (automatic && hintKey == lastHintKey) {
            return
        }

        lastHintKey = hintKey
        scope.launch {
            isEngineBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.analyze(
                        engineProfile.analysisLimit.copy(candidateCount = hintCount),
                    )
                }
            }.onSuccess { result ->
                engineMessage = result.status.message
                candidateText = result.toCandidateText(targetState.boardSize)
                candidateMoves = result.candidates.take(hintCount)
            }.onFailure { error ->
                engineMessage = error.message ?: "Hint analysis failed."
                clearHints("Hint analysis failed.")
            }
            isEngineBusy = false
        }
    }

    fun requestHintsForCurrentState(automatic: Boolean) {
        requestHintsForState(gameState, automatic)
    }

    fun resetLocalGame(message: String) {
        gameState = GameState.empty(BoardSize.Nine, Ruleset.Chinese)
        isGameEnded = false
        clearHints("No analysis yet.")
        scoreText = "No score estimate yet."
        moveReviewText = "No move review yet."
        lastMoveReview = null
        lastMoveText = "None"
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
            }.onFailure { error ->
                engineMessage = error.message ?: "Score estimate failed."
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
                    engineAdapter.newGame(BoardSize.Nine, Ruleset.Chinese)
                }
            }.onSuccess { status ->
                resetLocalGame(status.message)
                nextHintState = gameState
            }.onFailure { error ->
                resetLocalGame(error.message ?: "New AI game failed.")
            }
            isEngineBusy = false
            if (hintEnabled) {
                requestHintsForState(nextHintState ?: gameState, automatic = true)
            }
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
            lastMoveReview = null
            lastMoveText = move.describe(beforeMove.boardSize)
            scoreText = "Score estimate not current."
            if (afterMove.hasConsecutivePasses()) {
                val finalScore = BoardAreaScorer.score(afterMove)
                isGameEnded = true
                scoreText = finalScore.toDisplayText()
                candidateText = "Game ended after two passes."
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
        val afterHuman = runCatching { beforeMove.play(move) }
            .onFailure { error ->
                engineMessage = error.message ?: "Illegal move."
            }
            .getOrNull()
            ?: return

        gameState = afterHuman
        val moveReview = buildMoveReview(move, candidateMoves, beforeMove.boardSize)
        moveReviewText = moveReview.text
        lastMoveReview = moveReview.marker
        clearHints()
        scoreText = "Score estimate not current."
        lastMoveText = move.describe(beforeMove.boardSize)
        candidateText = "AI is thinking..."
        engineMessage = "Submitted ${move.describe(beforeMove.boardSize)}."
        isEngineBusy = true

        scope.launch {
            var nextHintState: GameState? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    applyAiResponseAfterHumanTurn(
                        engineAdapter = engineAdapter,
                        stateAfterHuman = afterHuman,
                        humanMove = move,
                    )
                }
            }.onSuccess { outcome ->
                gameState = outcome.gameState
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
                        scoreText = finalScore.toDisplayText()
                        engineMessage = "${outcome.engineMessage}\n${finalScore.status.message}"
                        candidateText = "Game ended. Final score is available below."
                    }.onFailure { error ->
                        engineMessage = "${outcome.engineMessage}\nFinal score failed: ${error.message ?: "Unknown error"}"
                        candidateText = "Game ended after two passes, but final score failed."
                    }
                }
            }.onFailure { error ->
                gameState = beforeMove
                lastMoveReview = null
                moveReviewText = "Move review cleared after rollback."
                engineMessage = error.message ?: "Move failed."
                candidateText = "Move was rolled back after engine failure."
                lastMoveText = "None"
            }
            isEngineBusy = false
            if (hintEnabled) {
                nextHintState?.let { requestHintsForState(it, automatic = true) }
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
            lastMoveReview = null
            moveReviewText = "Move review cleared by undo."
            lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None"
            candidateText = "Captured: Black ${nextState.capturedBy(StoneColor.Black)} / White ${nextState.capturedBy(StoneColor.White)}"
            scoreText = "Score estimate not current."
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
                lastMoveReview = null
                moveReviewText = "Move review cleared by undo."
                lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None"
                candidateText = "Undo cleared current analysis hints."
                scoreText = "Score estimate not current."
                engineMessage = "Undid $undoCount move(s) in local state and engine state."
                nextHintState = nextState
            }.onFailure { error ->
                engineMessage = error.message ?: "Undo failed."
            }
            isEngineBusy = false
            if (hintEnabled) {
                nextHintState?.let { requestHintsForState(it, automatic = true) }
            }
        }
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
                    requestHintsForCurrentState(automatic = true)
                } else {
                    clearHints("Hints disabled.")
                }
            },
            onHintCountChange = { count ->
                hintCount = count
                clearHints("Hint count set to $count.")
                if (hintEnabled) {
                    requestHintsForCurrentState(automatic = true)
                }
            },
        )

        GoBoard(
            gameState = gameState,
            candidateMoves = candidateMoves,
            lastMoveReview = lastMoveReview,
            inputEnabled = !isGameEnded && boardInputEnabled(matchMode, isEngineReady, isEngineBusy, gameState.nextPlayer),
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

private data class MoveReviewResult(
    val marker: MoveReviewMarker?,
    val text: String,
)

private fun buildMoveReview(
    move: Move,
    candidates: List<CandidateMove>,
    boardSize: BoardSize,
): MoveReviewResult {
    val play = move as? Move.Play
        ?: return MoveReviewResult(
            marker = null,
            text = "Move review: pass/resign has no board spot evaluation.",
        )

    if (candidates.isEmpty()) {
        return MoveReviewResult(
            marker = null,
            text = "Move review: no pre-move hint cache. Turn hints on before playing to review the move.",
        )
    }

    val matchedCandidate = candidates.firstOrNull { candidate ->
        (candidate.move as? Move.Play)?.coordinate == play.coordinate
    }
    if (matchedCandidate == null) {
        return MoveReviewResult(
            marker = MoveReviewMarker(
                coordinate = play.coordinate,
                tone = MoveReviewTone.Mistake,
                label = "?",
            ),
            text = "Move review: ${play.coordinate.label(boardSize)} was outside the analyzed top ${candidates.size} candidate(s).",
        )
    }

    val pointLoss = matchedCandidate.pointLoss
    val tone = moveReviewToneFor(pointLoss)
    val label = pointLoss?.let { (-it).toSignedOneDecimal() }.orEmpty()
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
            tone = tone,
            label = label,
        ),
        text = "Move review: ${play.coordinate.label(boardSize)} ${moveReviewTextFor(pointLoss)} ($lossText$scoreText$priorText).",
    )
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
