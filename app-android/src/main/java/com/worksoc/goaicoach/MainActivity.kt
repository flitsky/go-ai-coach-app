package com.worksoc.goaicoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.engine.android.KataGoProcessEngineAdapter
import com.worksoc.goaicoach.engine.android.StubEngineAdapter
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val HumanPlayer = StoneColor.Black
private val AiPlayer = StoneColor.White

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engineAdapter = createEngineAdapter()
        setContent {
            GoCoachApp(engineAdapter = engineAdapter)
        }
    }

    private fun createEngineAdapter(): EngineAdapter {
        val katagoDir = File(filesDir, "katago")
        val executable = File(applicationInfo.nativeLibraryDir, "libkatago.so")
        val model = File(katagoDir, "model.bin.gz")
        val config = File(katagoDir, "gtp_learning.cfg")

        if (!executable.canExecute() || !model.isFile || !config.isFile) {
            return StubEngineAdapter()
        }

        val logsDir = File(katagoDir, "logs").apply { mkdirs() }
        val homeDir = File(katagoDir, "home").apply { mkdirs() }
        return KataGoProcessEngineAdapter(
            KataGoProcessConfig(
                executablePath = executable.absolutePath,
                modelPath = model.absolutePath,
                configPath = config.absolutePath,
                startupOverrides = mapOf(
                    "numSearchThreads" to "1",
                    "logDir" to logsDir.absolutePath,
                    "homeDataDir" to homeDir.absolutePath,
                    "logToStderr" to "false",
                    "logAllGTPCommunication" to "false",
                    "logSearchInfo" to "false",
                    "allowResignation" to "false",
                    "startupPrintMessageToStderr" to "false",
                ),
            ),
        )
    }
}

@Composable
fun GoCoachApp(engineAdapter: EngineAdapter) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2F6B4F),
            secondary = Color(0xFF546E7A),
            background = Color(0xFFF7F4EC),
            surface = Color(0xFFFFFCF4),
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            GoCoachScreen(engineAdapter = engineAdapter)
        }
    }
}

@Composable
private fun GoCoachScreen(engineAdapter: EngineAdapter) {
    val scope = rememberCoroutineScope()
    var gameState by remember { mutableStateOf(GameState.empty(BoardSize.Nine, Ruleset.Chinese)) }
    var engineMessage by remember { mutableStateOf("Engine not initialized.") }
    var candidateText by remember { mutableStateOf("No analysis yet.") }
    var lastMoveText by remember { mutableStateOf("None") }
    var isEngineBusy by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }
    var engineProfile by remember { mutableStateOf(EngineProfile()) }
    val engineName = remember(engineAdapter) { engineAdapter.displayName() }

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
            candidateText = "Check engine packaging or remove the local KataGo seed files to use stub mode."
        }
        isEngineBusy = false
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
            text = "9x9 match: human Black vs $engineName White",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )

        EngineTuningPanel(
            profile = engineProfile,
            enabled = isEngineReady && !isEngineBusy,
            onDifficultyChange = { difficulty ->
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
            inputEnabled = isEngineReady && !isEngineBusy && gameState.nextPlayer == HumanPlayer,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            onCoordinateTap = { coordinate ->
                submitHumanMove(Move.Play(HumanPlayer, coordinate))
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    submitHumanMove(Move.Pass(HumanPlayer))
                },
                enabled = isEngineReady && !isEngineBusy && gameState.nextPlayer == HumanPlayer,
                modifier = Modifier.weight(1f),
            ) {
                Text("Pass")
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
                        }.onFailure { error ->
                            engineMessage = error.message ?: "Analysis failed."
                        }
                        isEngineBusy = false
                    }
                },
                enabled = isEngineReady && !isEngineBusy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Analyze")
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        isEngineBusy = true
                        runCatching {
                            withContext(Dispatchers.IO) {
                                engineAdapter.newGame(BoardSize.Nine, Ruleset.Chinese)
                            }
                        }.onSuccess { status ->
                            gameState = GameState.empty(BoardSize.Nine, Ruleset.Chinese)
                            candidateText = "No analysis yet."
                            lastMoveText = "None"
                            engineMessage = status.message
                        }.onFailure { error ->
                            engineMessage = error.message ?: "New game failed."
                        }
                        isEngineBusy = false
                    }
                },
                enabled = isEngineReady && !isEngineBusy,
                modifier = Modifier.weight(1f),
            ) {
                Text("New")
            }
        }

        EngineResponsePanel(
            nextPlayer = gameState.nextPlayer,
            moveCount = gameState.moves.size,
            lastMoveText = lastMoveText,
            isEngineBusy = isEngineBusy,
            engineMessage = engineMessage,
            candidateText = candidateText,
        )
    }
}

@Composable
private fun EngineTuningPanel(
    profile: EngineProfile,
    enabled: Boolean,
    onDifficultyChange: (DifficultyProfile) -> Unit,
    onVisitsChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Engine", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "time ${profile.analysisLimit.timeMillis ?: 0}ms",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onDifficultyChange(profile.difficulty.previous()) },
                    enabled = enabled && profile.difficulty != DifficultyProfile.entries.first(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("-")
                }
                Text(
                    text = profile.difficulty.label,
                    modifier = Modifier.weight(2f),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { onDifficultyChange(profile.difficulty.next()) },
                    enabled = enabled && profile.difficulty != DifficultyProfile.entries.last(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("+")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onVisitsChange(previousVisits(profile.analysisLimit.visits)) },
                    enabled = enabled && profile.analysisLimit.visits > VisitOptions.first(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("-")
                }
                Text(
                    text = "Visits ${profile.analysisLimit.visits}",
                    modifier = Modifier.weight(2f),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { onVisitsChange(nextVisits(profile.analysisLimit.visits)) },
                    enabled = enabled && profile.analysisLimit.visits < VisitOptions.last(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
private fun EngineResponsePanel(
    nextPlayer: StoneColor,
    moveCount: Int,
    lastMoveText: String,
    isEngineBusy: Boolean,
    engineMessage: String,
    candidateText: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(turnStatus(nextPlayer, isEngineBusy), fontWeight = FontWeight.SemiBold)
                Text("Moves: $moveCount", color = MaterialTheme.colorScheme.secondary)
            }

            Text(
                text = "Last: $lastMoveText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            Text(
                text = engineMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = candidateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun GoBoard(
    gameState: GameState,
    inputEnabled: Boolean,
    modifier: Modifier = Modifier,
    onCoordinateTap: (BoardCoordinate) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .background(Color(0xFFD7A85E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF7A4D20), RoundedCornerShape(8.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(gameState.boardSize, inputEnabled) {
                    detectTapGestures { offset ->
                        if (!inputEnabled) {
                            return@detectTapGestures
                        }
                        coordinateFromTap(offset, canvasSize, gameState.boardSize)
                            ?.let(onCoordinateTap)
                    }
                },
        ) {
            val geometry = BoardGeometry.from(size, gameState.boardSize)
            drawBoardGrid(geometry, gameState.boardSize)

            for ((coordinate, stone) in gameState.stones) {
                drawStone(geometry.pointFor(coordinate), geometry.spacing * 0.42f, stone)
            }

            val lastMove = gameState.moves.lastOrNull() as? Move.Play
            if (lastMove != null) {
                drawCircle(
                    color = Color(0xFFE53935),
                    radius = geometry.spacing * 0.12f,
                    center = geometry.pointFor(lastMove.coordinate),
                )
            }
        }
    }
}

private data class TurnOutcome(
    val gameState: GameState,
    val engineMessage: String,
    val candidateText: String,
    val lastMoveText: String,
)

private suspend fun applyAiResponseAfterHumanTurn(
    engineAdapter: EngineAdapter,
    stateAfterHuman: GameState,
    humanMove: Move,
): TurnOutcome {
    val humanStatus = engineAdapter.playMove(humanMove)
    val humanText = humanMove.describe(stateAfterHuman.boardSize)

    if (stateAfterHuman.isBoardFull()) {
        return TurnOutcome(
            gameState = stateAfterHuman,
            engineMessage = "${humanStatus.message}\nBoard is full.",
            candidateText = "Game ended after $humanText.",
            lastMoveText = humanText,
        )
    }

    val aiResult = engineAdapter.genMove(AiPlayer)
    val afterAi = stateAfterHuman.play(aiResult.move)
    val aiText = aiResult.move.describe(stateAfterHuman.boardSize)
    return TurnOutcome(
        gameState = afterAi,
        engineMessage = "${humanStatus.message}\n${aiResult.status.message}\n${aiResult.summary}",
        candidateText = "AI replied with $aiText.",
        lastMoveText = aiText,
    )
}

private val VisitOptions = listOf(16, 64, 160, 400, 1_000)

private fun previousVisits(current: Int): Int =
    VisitOptions.lastOrNull { it < current } ?: VisitOptions.first()

private fun nextVisits(current: Int): Int =
    VisitOptions.firstOrNull { it > current } ?: VisitOptions.last()

private fun EngineAdapter.displayName(): String =
    when (this) {
        is KataGoProcessEngineAdapter -> "KataGo"
        else -> "stub AI"
    }

private fun turnStatus(nextPlayer: StoneColor, isEngineBusy: Boolean): String =
    when {
        isEngineBusy -> "AI thinking"
        nextPlayer == HumanPlayer -> "Your turn: ${HumanPlayer.label}"
        else -> "Waiting: ${nextPlayer.label}"
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoardGrid(
    geometry: BoardGeometry,
    boardSize: BoardSize,
) {
    val lineColor = Color(0xFF4A2F17)
    for (index in 0 until boardSize.value) {
        val startHorizontal = geometry.pointFor(BoardCoordinate(index, 0))
        val endHorizontal = geometry.pointFor(BoardCoordinate(index, boardSize.value - 1))
        drawLine(lineColor, startHorizontal, endHorizontal, strokeWidth = 2f)

        val startVertical = geometry.pointFor(BoardCoordinate(0, index))
        val endVertical = geometry.pointFor(BoardCoordinate(boardSize.value - 1, index))
        drawLine(lineColor, startVertical, endVertical, strokeWidth = 2f)
    }

    for (starPoint in starPoints(boardSize)) {
        drawCircle(
            color = lineColor,
            radius = geometry.spacing * 0.08f,
            center = geometry.pointFor(starPoint),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStone(
    center: Offset,
    radius: Float,
    stone: StoneColor,
) {
    val fillColor = when (stone) {
        StoneColor.Black -> Color(0xFF1B1B1B)
        StoneColor.White -> Color(0xFFF8F8F2)
    }
    drawCircle(fillColor, radius, center)
    drawCircle(Color(0xFF1B1B1B), radius, center, style = Stroke(width = 2f))
}

private data class BoardGeometry(
    val origin: Offset,
    val spacing: Float,
) {
    fun pointFor(coordinate: BoardCoordinate): Offset =
        Offset(
            x = origin.x + coordinate.column * spacing,
            y = origin.y + coordinate.row * spacing,
        )

    companion object {
        fun from(size: Size, boardSize: BoardSize): BoardGeometry {
            val side = min(size.width, size.height)
            val boardPadding = side * 0.08f
            val origin = Offset(
                x = (size.width - side) / 2f + boardPadding,
                y = (size.height - side) / 2f + boardPadding,
            )
            return BoardGeometry(
                origin = origin,
                spacing = (side - boardPadding * 2f) / (boardSize.value - 1),
            )
        }
    }
}

private fun coordinateFromTap(
    offset: Offset,
    canvasSize: IntSize,
    boardSize: BoardSize,
): BoardCoordinate? {
    if (canvasSize.width == 0 || canvasSize.height == 0) {
        return null
    }
    val geometry = BoardGeometry.from(
        size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
        boardSize = boardSize,
    )
    val column = ((offset.x - geometry.origin.x) / geometry.spacing).roundToInt()
    val row = ((offset.y - geometry.origin.y) / geometry.spacing).roundToInt()
    val coordinate = BoardCoordinate(
        row = row.coerceAtLeast(0),
        column = column.coerceAtLeast(0),
    )
    if (!coordinate.isInside(boardSize)) {
        return null
    }
    val snapped = geometry.pointFor(coordinate)
    val tolerance = geometry.spacing * 0.45f
    return if (abs(offset.x - snapped.x) <= tolerance && abs(offset.y - snapped.y) <= tolerance) {
        coordinate
    } else {
        null
    }
}

private fun starPoints(boardSize: BoardSize): List<BoardCoordinate> =
    when (boardSize.value) {
        9 -> listOf(
            BoardCoordinate(2, 2),
            BoardCoordinate(2, 6),
            BoardCoordinate(4, 4),
            BoardCoordinate(6, 2),
            BoardCoordinate(6, 6),
        )

        13 -> listOf(
            BoardCoordinate(3, 3),
            BoardCoordinate(3, 9),
            BoardCoordinate(6, 6),
            BoardCoordinate(9, 3),
            BoardCoordinate(9, 9),
        )

        19 -> listOf(
            BoardCoordinate(3, 3),
            BoardCoordinate(3, 9),
            BoardCoordinate(3, 15),
            BoardCoordinate(9, 3),
            BoardCoordinate(9, 9),
            BoardCoordinate(9, 15),
            BoardCoordinate(15, 3),
            BoardCoordinate(15, 9),
            BoardCoordinate(15, 15),
        )

        else -> emptyList()
    }

private fun AnalysisResult.toCandidateText(boardSize: BoardSize): String {
    if (candidates.isEmpty()) {
        return summary
    }
    return buildString {
        appendLine(summary)
        candidates.forEachIndexed { index, candidate ->
            append(index + 1)
            append(". ")
            append(candidate.move.describe(boardSize))
            candidate.winRate?.let { append(" WR=${(it * 100).roundToInt()}%") }
            candidate.scoreLead?.let { append(" score=${it}") }
            candidate.visits?.let { append(" visits=${it}") }
            candidate.note?.let { append(" - ${it}") }
            if (index != candidates.lastIndex) {
                appendLine()
            }
        }
    }
}
