package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameStateReplayer
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
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
    private var ruleset: Ruleset = Ruleset.Chinese
    private var nextPlayer: StoneColor = StoneColor.Black
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null
    private val playedMoves = mutableListOf<Move>()

    override suspend fun initialize(profile: EngineProfile): EngineStatus {
        this.profile = profile.copy(mode = EngineMode.LocalProcess)
        ensureProcessStarted()
        configure(this.profile)
        return EngineStatus.ready("KataGo process ready: ${this.profile.describe()}")
    }

    override suspend fun configure(profile: EngineProfile): EngineStatus {
        this.profile = profile.copy(mode = EngineMode.LocalProcess)
        ensureProcessStarted()
        applySearchLimit(this.profile.analysisLimit)
        return EngineStatus.ready("KataGo process configured: ${this.profile.describe()}")
    }

    override suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset): EngineStatus {
        ensureProcessStarted()
        this.boardSize = boardSize
        this.ruleset = ruleset
        nextPlayer = StoneColor.Black
        playedMoves.clear()
        sendCommand("boardsize ${boardSize.value}")
        sendCommand("komi 6.5")
        sendCommand("clear_board")
        return EngineStatus.ready("KataGo new ${boardSize.value}x${boardSize.value} $ruleset game")
    }

    override suspend fun playMove(move: Move): EngineStatus {
        ensureProcessStarted()
        sendCommand(move.toGtpCommand(boardSize))
        playedMoves += move
        if (move is Move.Play || move is Move.Pass) {
            nextPlayer = move.player.opponent
        }
        return EngineStatus.ready("KataGo accepted ${move.describe(boardSize)}")
    }

    override suspend fun genMove(player: StoneColor): MoveResult {
        ensureProcessStarted()
        val response = sendCommand("genmove ${player.toGtpColor()}")
        val move = response.toMove(player, boardSize)
        playedMoves += move
        if (move is Move.Play || move is Move.Pass) {
            nextPlayer = move.player.opponent
        }
        return MoveResult(
            status = EngineStatus.ready("KataGo generated ${move.describe(boardSize)}"),
            move = move,
            summary = "KataGo process response: $response",
        )
    }

    override suspend fun undoMove(): EngineStatus {
        ensureProcessStarted()
        sendCommand("undo")
        val removed = playedMoves.removeLastOrNull()
        if (removed != null) {
            nextPlayer = removed.player
        }
        return EngineStatus.ready("KataGo undid one move")
    }

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        ensureProcessStarted()
        val effectiveLimit = limit.effectiveAnalysisLimit()
        val candidates = try {
            applySearchLimit(effectiveLimit)
            val response = sendCommand("kata-search_analyze ${nextPlayer.toGtpColor()}")
            KataGoAnalysisParser.attachPointLoss(
                candidates = KataGoAnalysisParser.parseCandidates(
                    response = response,
                    player = nextPlayer,
                    boardSize = boardSize,
                    maxCandidates = limit.candidateCount,
                ),
            ).fillFromPolicyIfNeeded(limit)
        } finally {
            applySearchLimit(profile.analysisLimit)
        }
        val policyFallbackCount = candidates.count { it.visits == null && it.policyPrior != null }
        val legalFallbackCount = candidates.count { it.visits == null && it.policyPrior == null }
        return AnalysisResult(
            status = EngineStatus.ready("KataGo analysis complete for ${nextPlayer.label}: ${candidates.size}/${limit.candidateCount} candidate(s)"),
            candidates = candidates,
            summary = buildAnalysisSummary(
                requestedLimit = limit,
                effectiveLimit = effectiveLimit,
                shownCount = candidates.size,
                policyFallbackCount = policyFallbackCount,
                legalFallbackCount = legalFallbackCount,
            ),
        )
    }

    private fun List<CandidateMove>.fillFromPolicyIfNeeded(
        limit: AnalysisLimit,
    ): List<CandidateMove> {
        val remaining = limit.candidateCount - size
        if (remaining <= 0) {
            return take(limit.candidateCount)
        }

        val currentState = GameStateReplayer
            .replay(boardSize = boardSize, ruleset = ruleset, moves = playedMoves)
        val legalCoordinates = allCoordinates()
            .filter { coordinate ->
                runCatching { currentState.play(Move.Play(nextPlayer, coordinate)) }.isSuccess
            }
            .toSet()
        val illegalCoordinates = allCoordinates().toSet() - legalCoordinates
        val occupiedCoordinates = currentState
            .stones
            .keys
        val searchCoordinates = mapNotNull { candidate ->
            (candidate.move as? Move.Play)?.coordinate
        }
        val policyResponse = sendCommand("kata-raw-nn 0")
        val policyCandidates = KataGoAnalysisParser.parsePolicyCandidates(
            response = policyResponse,
            player = nextPlayer,
            boardSize = boardSize,
            maxCandidates = remaining,
            excludedCoordinates = occupiedCoordinates + illegalCoordinates + searchCoordinates,
        )
        val usedCoordinates = occupiedCoordinates + illegalCoordinates + searchCoordinates +
            policyCandidates.mapNotNull { candidate -> (candidate.move as? Move.Play)?.coordinate }
        val legalFallbackCandidates = allCoordinates()
            .filter { coordinate -> coordinate in legalCoordinates && coordinate !in usedCoordinates }
            .take(remaining - policyCandidates.size)
            .mapIndexed { index, coordinate ->
                CandidateMove(
                    move = Move.Play(nextPlayer, coordinate),
                    note = "Legal fallback ${index + 1}",
                )
            }
            .toList()

        return (this + policyCandidates + legalFallbackCandidates).take(limit.candidateCount)
    }

    private fun allCoordinates(): Sequence<BoardCoordinate> =
        sequence {
            for (row in 0 until boardSize.value) {
                for (column in 0 until boardSize.value) {
                    yield(BoardCoordinate(row, column))
                }
            }
        }

    private fun buildAnalysisSummary(
        requestedLimit: AnalysisLimit,
        effectiveLimit: AnalysisLimit,
        shownCount: Int,
        policyFallbackCount: Int,
        legalFallbackCount: Int,
    ): String {
        val searchText = if (
            effectiveLimit.visits != requestedLimit.visits ||
            effectiveLimit.timeMillis != requestedLimit.timeMillis
        ) {
            "KataGo search analysis raised search to ${effectiveLimit.visits} visits / ${effectiveLimit.timeMillis ?: 0}ms for ${requestedLimit.candidateCount} candidate(s)."
        } else {
            "KataGo search analysis with ${effectiveLimit.visits} visits / ${effectiveLimit.timeMillis ?: 0}ms."
        }
        val searchedCount = shownCount - policyFallbackCount - legalFallbackCount
        return if (policyFallbackCount > 0 || legalFallbackCount > 0) {
            buildString {
                append(searchText)
                append(" Returned $searchedCount searched candidate(s)")
                if (policyFallbackCount > 0) {
                    append("; filled $policyFallbackCount spot(s) from raw NN policy without score loss")
                }
                if (legalFallbackCount > 0) {
                    append("; filled $legalFallbackCount legal spot(s) without engine score/policy")
                }
                append(".")
            }
        } else {
            "$searchText Showing $shownCount/${requestedLimit.candidateCount} scored spot(s)."
        }
    }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate {
        ensureProcessStarted()
        val response = sendCommand("kata-raw-nn 0")
        return KataGoAnalysisParser.parseScoreEstimate(response, boardSize)
    }

    override suspend fun scoreFinal(): FinalScoreResult {
        ensureProcessStarted()
        val response = sendCommand("final_score")
        return KataGoAnalysisParser.parseFinalScore(response)
    }

    override suspend fun deadStones(): DeadStonesResult {
        ensureProcessStarted()
        val response = sendCommand("final_status_list dead")
        val coordinates = KataGoAnalysisParser.parseFinalStatusList(response, boardSize)
        return DeadStonesResult(
            status = EngineStatus.ready("KataGo dead-stone status complete: ${coordinates.size} stone(s)."),
            coordinates = coordinates,
            summary = if (coordinates.isEmpty()) {
                "KataGo final_status_list dead returned no dead stones."
            } else {
                "KataGo final_status_list dead returned ${coordinates.size} dead stone(s)."
            },
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

    private fun applySearchLimit(limit: AnalysisLimit) {
        sendCommand("kata-set-param maxVisits ${limit.visits}")
        limit.timeMillis?.let { timeMillis ->
            val seconds = (timeMillis / 1_000.0).coerceAtLeast(0.001)
            sendCommand("kata-set-param maxTime $seconds")
        }
    }

    private fun AnalysisLimit.effectiveAnalysisLimit(): AnalysisLimit {
        val minimumVisits = (candidateCount * VisitsPerCandidate).coerceAtLeast(visits)
        val minimumTimeMillis = timeMillis
            ?.coerceAtLeast(MinAnalysisTimeMillis)
            ?: MinAnalysisTimeMillis
        return copy(
            visits = minimumVisits,
            timeMillis = minimumTimeMillis,
        )
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

    private companion object {
        private const val VisitsPerCandidate = 10
        private const val MinAnalysisTimeMillis = 1_000L
    }
}
