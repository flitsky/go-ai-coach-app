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
    var lastMoveText by remember { mutableStateOf("None") }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }
    var engineProfile by remember { mutableStateOf(EngineProfile()) }
    var matchMode by remember { mutableStateOf(MatchMode.HumanVsAi) }

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

    fun resetLocalGame(message: String) {
        gameState = GameState.empty(BoardSize.Nine, Ruleset.Chinese)
        candidateText = "No analysis yet."
        candidateMoves = emptyList()
        lastMoveText = "None"
        engineMessage = message
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
            runCatching {
                withContext(Dispatchers.IO) {
                    engineAdapter.newGame(BoardSize.Nine, Ruleset.Chinese)
                }
            }.onSuccess { status ->
                resetLocalGame(status.message)
            }.onFailure { error ->
                resetLocalGame(error.message ?: "New AI game failed.")
            }
            isEngineBusy = false
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
            candidateMoves = emptyList()
            lastMoveText = move.describe(beforeMove.boardSize)
            candidateText = "Captured: Black ${afterMove.capturedBy(StoneColor.Black)} / White ${afterMove.capturedBy(StoneColor.White)}"
            engineMessage = "Local move accepted: ${move.describe(beforeMove.boardSize)}."
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
        candidateMoves = emptyList()
        lastMoveText = move.describe(beforeMove.boardSize)
        candidateText = "AI is thinking..."
        engineMessage = "Submitted ${move.describe(beforeMove.boardSize)}."
        isEngineBusy = true

        scope.launch {
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
                candidateMoves = emptyList()
                engineMessage = outcome.engineMessage
                candidateText = outcome.candidateText
                lastMoveText = outcome.lastMoveText
            }.onFailure { error ->
                gameState = beforeMove
                engineMessage = error.message ?: "Move failed."
                candidateText = "Move was rolled back after engine failure."
                lastMoveText = "None"
            }
            isEngineBusy = false
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
            candidateMoves = emptyList()
            lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None"
            candidateText = "Captured: Black ${nextState.capturedBy(StoneColor.Black)} / White ${nextState.capturedBy(StoneColor.White)}"
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
            runCatching {
                withContext(Dispatchers.IO) {
                    repeat(undoCount) {
                        engineAdapter.undoMove()
                    }
                }
            }.onSuccess {
                val nextState = gameState.replayWithoutLastMoves(undoCount)
                gameState = nextState
                candidateMoves = emptyList()
                lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None"
                candidateText = "Undo cleared current analysis hints."
                engineMessage = "Undid $undoCount move(s) in local state and engine state."
            }.onFailure { error ->
                engineMessage = error.message ?: "Undo failed."
            }
            isEngineBusy = false
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

        GoBoard(
            gameState = gameState,
            candidateMoves = candidateMoves,
            inputEnabled = boardInputEnabled(matchMode, isEngineReady, isEngineBusy, gameState.nextPlayer),
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
                enabled = boardInputEnabled(matchMode, isEngineReady, isEngineBusy, gameState.nextPlayer),
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
                onClick = {
                    scope.launch {
                        isEngineBusy = true
                        runCatching {
                            withContext(Dispatchers.IO) {
                                engineAdapter.analyze(engineProfile.analysisLimit)
                            }
                        }.onSuccess { result ->
                            engineMessage = result.status.message
                            candidateText = result.toCandidateText(gameState.boardSize)
                            candidateMoves = result.candidates
                        }.onFailure { error ->
                            engineMessage = error.message ?: "Analysis failed."
                            candidateMoves = emptyList()
                        }
                        isEngineBusy = false
                    }
                },
                enabled = matchMode == MatchMode.HumanVsAi && isEngineReady && !isEngineBusy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Analyze")
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
        )
    }
}
