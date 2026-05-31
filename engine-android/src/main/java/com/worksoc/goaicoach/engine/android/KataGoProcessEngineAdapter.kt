package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class KataGoProcessConfig(
    val executablePath: String,
    val modelPath: String,
    val configPath: String,
    val startupOverrides: Map<String, String> = emptyMap(),
)

class KataGoProcessEngineAdapter(
    private val processConfig: KataGoProcessConfig,
) : EngineAdapter {
    private var profile: EngineProfile = EngineProfile(mode = EngineMode.LocalProcess)
    private var boardSize: BoardSize = BoardSize.Nine
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null

    override suspend fun initialize(profile: EngineProfile): EngineStatus {
        this.profile = profile.copy(mode = EngineMode.LocalProcess)
        ensureProcessStarted()
        configure(this.profile)
        return EngineStatus.ready("KataGo process ready: ${this.profile.describe()}")
    }

    override suspend fun configure(profile: EngineProfile): EngineStatus {
        this.profile = profile.copy(mode = EngineMode.LocalProcess)
        ensureProcessStarted()
        sendCommand("kata-set-param maxVisits ${this.profile.analysisLimit.visits}")
        this.profile.analysisLimit.timeMillis?.let { timeMillis ->
            val seconds = (timeMillis / 1_000.0).coerceAtLeast(0.001)
            sendCommand("kata-set-param maxTime $seconds")
        }
        return EngineStatus.ready("KataGo process configured: ${this.profile.describe()}")
    }

    override suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset): EngineStatus {
        ensureProcessStarted()
        this.boardSize = boardSize
        sendCommand("boardsize ${boardSize.value}")
        sendCommand("komi 6.5")
        sendCommand("clear_board")
        return EngineStatus.ready("KataGo new ${boardSize.value}x${boardSize.value} $ruleset game")
    }

    override suspend fun playMove(move: Move): EngineStatus {
        ensureProcessStarted()
        sendCommand(move.toGtpCommand(boardSize))
        return EngineStatus.ready("KataGo accepted ${move.describe(boardSize)}")
    }

    override suspend fun genMove(player: StoneColor): MoveResult {
        ensureProcessStarted()
        val response = sendCommand("genmove ${player.toGtpColor()}")
        val move = response.toMove(player, boardSize)
        return MoveResult(
            status = EngineStatus.ready("KataGo generated ${move.describe(boardSize)}"),
            move = move,
            summary = "KataGo process response: $response",
        )
    }

    override suspend fun undoMove(): EngineStatus {
        ensureProcessStarted()
        sendCommand("undo")
        return EngineStatus.ready("KataGo undid one move")
    }

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        return AnalysisResult(
            status = EngineStatus.error("KataGo process analysis is not implemented in this spike adapter"),
            candidates = emptyList(),
            summary = "The process spike currently supports configure/newGame/playMove/genMove/stop. Analysis streaming needs a separate parser and cancellation path.",
        )
    }

    override suspend fun stop(): EngineStatus {
        runCatching {
            if (process != null) {
                sendCommand("quit")
            }
        }
        input = null
        output = null
        process?.destroy()
        process = null
        return EngineStatus.stopped("KataGo process stopped")
    }

    private fun ensureProcessStarted() {
        if (process?.isAlive == true) {
            return
        }

        require(File(processConfig.executablePath).canExecute()) {
            "KataGo executable is not executable: ${processConfig.executablePath}"
        }
        require(File(processConfig.modelPath).isFile) {
            "KataGo model not found: ${processConfig.modelPath}"
        }
        require(File(processConfig.configPath).isFile) {
            "KataGo config not found: ${processConfig.configPath}"
        }

        val overrides = (
            processConfig.startupOverrides +
                mapOf(
                    "maxVisits" to profile.analysisLimit.visits.toString(),
                    "logToStderr" to "false",
                )
            )
            .entries
            .joinToString(",") { (key, value) -> "$key=$value" }

        val command = listOf(
            processConfig.executablePath,
            "gtp",
            "-model",
            processConfig.modelPath,
            "-config",
            processConfig.configPath,
            "-override-config",
            overrides,
        )

        process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        input = BufferedWriter(OutputStreamWriter(process!!.outputStream))
        output = BufferedReader(InputStreamReader(process!!.inputStream))
    }

    private fun sendCommand(command: String): String {
        val writer = requireNotNull(input) { "KataGo process input is not initialized" }
        val reader = requireNotNull(output) { "KataGo process output is not initialized" }
        writer.write(command)
        writer.newLine()
        writer.flush()

        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: error("KataGo process ended while waiting for: $command")
            if (line.isBlank()) {
                if (lines.isNotEmpty()) {
                    break
                }
            } else {
                lines += line
            }
        }

        val first = lines.firstOrNull().orEmpty()
        require(first.startsWith("=")) {
            "KataGo command failed for `$command`: ${lines.joinToString("\\n")}"
        }
        return lines
            .joinToString("\n")
            .removePrefix("=")
            .trim()
    }

    private fun Move.toGtpCommand(boardSize: BoardSize): String =
        when (this) {
            is Move.Play -> "play ${player.toGtpColor()} ${coordinate.label(boardSize)}"
            is Move.Pass -> "play ${player.toGtpColor()} pass"
            is Move.Resign -> "play ${player.toGtpColor()} resign"
        }

    private fun String.toMove(
        player: StoneColor,
        boardSize: BoardSize,
    ): Move =
        when (lowercase()) {
            "pass" -> Move.Pass(player)
            "resign" -> Move.Resign(player)
            else -> Move.Play(player, BoardCoordinate.fromLabel(this, boardSize))
        }

    private fun StoneColor.toGtpColor(): String =
        when (this) {
            StoneColor.Black -> "B"
            StoneColor.White -> "W"
        }

    private fun EngineProfile.describe(): String =
        "${difficulty.label}, visits=${analysisLimit.visits}, time=${analysisLimit.timeMillis ?: "none"}ms"
}
