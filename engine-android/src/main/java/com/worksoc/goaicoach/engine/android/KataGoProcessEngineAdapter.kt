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
import org.json.JSONArray
import org.json.JSONObject

data class KataGoProcessConfig(
    val executablePath: String,
    val modelPath: String,
    val configPath: String,
    val analysisConfigPath: String? = null,
    val startupOverrides: Map<String, String> = emptyMap(),
)

class KataGoProcessEngineAdapter(
    private val processConfig: KataGoProcessConfig,
) : EngineAdapter {
    private var profile: EngineProfile = EngineProfile(mode = EngineMode.LocalProcess)
    private var boardSize: BoardSize = BoardSize.Nine
    private var ruleset: Ruleset = Ruleset.Japanese
    private var nextPlayer: StoneColor = StoneColor.Black
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null
    private var analysisProcess: Process? = null
    private var analysisInput: BufferedWriter? = null
    private var analysisOutput: BufferedReader? = null
    private var analysisQueryCounter: Int = 0
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
        sendCommand("kata-set-rules ${ruleset.katagoName}")
        sendCommand("clear_board")
        return EngineStatus.ready("KataGo new ${boardSize.value}x${boardSize.value} ${ruleset.scoringLabel} game")
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
        runCatching {
            analyzeWithJson(effectiveLimit, limit.candidateCount)
        }.getOrNull()?.let { jsonResult ->
            return jsonResult
        }

        val candidates = analyzeWithGtp(effectiveLimit, limit)
        val policyFallbackCount = candidates.count { it.visits == null && it.policyPrior != null }
        val legalFallbackCount = candidates.count { it.visits == null && it.policyPrior == null }
        val scoredCount = candidates.count { it.pointLoss != null }
        return AnalysisResult(
            status = EngineStatus.ready(
                "KataGo analysis complete for ${nextPlayer.label}: $scoredCount scored / ${limit.candidateCount} requested candidate(s)",
            ),
            candidates = candidates,
            summary = buildAnalysisSummary(
                requestedLimit = limit,
                effectiveLimit = effectiveLimit,
                candidateCount = candidates.size,
                scoredCount = scoredCount,
                policyFallbackCount = policyFallbackCount,
                legalFallbackCount = legalFallbackCount,
            ),
        )
    }

    private fun analyzeWithJson(
        effectiveLimit: AnalysisLimit,
        candidateCount: Int,
    ): AnalysisResult? {
        val analysisConfigPath = processConfig.analysisConfigPath
            ?.takeIf { File(it).isFile }
            ?: return null
        ensureAnalysisProcessStarted(analysisConfigPath)
        val response = sendAnalysisQuery(effectiveLimit.toJsonAnalysisQuery())
        val candidates = KataGoJsonAnalysisParser.parseCandidates(
            response = response,
            player = nextPlayer,
            boardSize = boardSize,
            maxCandidates = candidateCount,
        )
        val scoredCount = candidates.count { it.pointLoss != null }
        return AnalysisResult(
            status = EngineStatus.ready(
                "KataGo JSON analysis complete for ${nextPlayer.label}: $scoredCount/$candidateCount scored candidate(s)",
            ),
            candidates = candidates,
            summary = "KataGo JSON analysis with ${effectiveLimit.visits} visits / ${effectiveLimit.timeMillis ?: 0}ms. Returned ${candidates.size} scored candidate(s).",
        )
    }

    private fun analyzeWithGtp(
        effectiveLimit: AnalysisLimit,
        limit: AnalysisLimit,
    ): List<CandidateMove> =
        try {
            applySearchLimit(effectiveLimit)
            val response = sendCommand(effectiveLimit.toSearchAnalyzeCommand(nextPlayer))
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
        candidateCount: Int,
        scoredCount: Int,
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
        val searchedCount = candidateCount - policyFallbackCount - legalFallbackCount
        return if (policyFallbackCount > 0 || legalFallbackCount > 0) {
            buildString {
                append(searchText)
                append(" Returned $searchedCount searched candidate(s)")
                if (policyFallbackCount > 0) {
                    append("; kept $policyFallbackCount raw NN policy fallback candidate(s) for logs only")
                }
                if (legalFallbackCount > 0) {
                    append("; kept $legalFallbackCount legal fallback candidate(s) for logs only")
                }
                append(". ")
                append("Showing $scoredCount scored spot(s) on the board")
                append(".")
            }
        } else {
            "$searchText Showing $scoredCount/${requestedLimit.candidateCount} scored spot(s)."
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
        analysisInput = null
        analysisOutput = null
        analysisProcess?.destroy()
        analysisProcess = null
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

    private fun ensureAnalysisProcessStarted(analysisConfigPath: String) {
        if (analysisProcess?.isAlive == true) {
            return
        }

        val analysisOverrides = (
            processConfig.startupOverrides
                .filterKeys { key ->
                    key in setOf("logDir", "homeDataDir", "logToStderr")
                } +
                mapOf(
                    "numAnalysisThreads" to "1",
                    "numSearchThreads" to AnalysisSearchThreads.toString(),
                    "logToStderr" to "false",
                    "logAllRequests" to "false",
                    "logAllResponses" to "false",
                    "logSearchInfo" to "false",
                )
            )
            .entries
            .joinToString(",") { (key, value) -> "$key=$value" }

        val command = listOf(
            processConfig.executablePath,
            "analysis",
            "-model",
            processConfig.modelPath,
            "-config",
            analysisConfigPath,
            "-override-config",
            analysisOverrides,
        )

        analysisProcess = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        analysisInput = BufferedWriter(OutputStreamWriter(analysisProcess!!.outputStream))
        analysisOutput = BufferedReader(InputStreamReader(analysisProcess!!.inputStream))
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

    private fun sendAnalysisQuery(query: JSONObject): String {
        val writer = requireNotNull(analysisInput) { "KataGo analysis process input is not initialized" }
        val reader = requireNotNull(analysisOutput) { "KataGo analysis process output is not initialized" }
        writer.write(query.toString())
        writer.newLine()
        writer.flush()

        val queryId = query.getString("id")
        while (true) {
            val line = reader.readLine() ?: error("KataGo analysis process ended while waiting for: $queryId")
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) {
                continue
            }
            val response = JSONObject(trimmed)
            if (response.optString("id") != queryId) {
                continue
            }
            require(!response.has("error")) {
                "KataGo JSON analysis failed for `$queryId`: ${response.optString("error")}"
            }
            if (response.has("warning") || !response.has("moveInfos")) {
                continue
            }
            if (response.optBoolean("isDuringSearch", false)) {
                continue
            }
            return trimmed
        }
    }

    private fun applySearchLimit(limit: AnalysisLimit) {
        sendCommand("kata-set-param maxVisits ${limit.visits}")
        limit.timeMillis?.let { timeMillis ->
            val seconds = (timeMillis / 1_000.0).coerceAtLeast(0.001)
            sendCommand("kata-set-param maxTime $seconds")
        }
    }

    private fun AnalysisLimit.toSearchAnalyzeCommand(player: StoneColor): String {
        val timeMillis = timeMillis ?: return "kata-search_analyze ${player.toGtpColor()}"
        val centiseconds = ((timeMillis + 9) / 10).coerceAtLeast(1)
        return "kata-search_analyze ${player.toGtpColor()} $centiseconds"
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

    private fun AnalysisLimit.toJsonAnalysisQuery(): JSONObject {
        analysisQueryCounter += 1
        val overrideSettings = JSONObject()
        timeMillis?.let { overrideSettings.put("maxTime", it / 1_000.0) }
        return JSONObject()
            .put("id", "go-ai-coach-analysis-$analysisQueryCounter")
            .put("rules", ruleset.katagoName)
            .put("komi", 6.5)
            .put("boardXSize", boardSize.value)
            .put("boardYSize", boardSize.value)
            .put("initialPlayer", "B")
            .put("initialStones", JSONArray())
            .put("moves", playedMoves.toJsonMoves())
            .put("analyzeTurns", JSONArray().put(playedMoves.size))
            .put("maxVisits", visits)
            .put("includeOwnership", false)
            .put("includeMovesOwnership", false)
            .put("includePolicy", true)
            .put("overrideSettings", overrideSettings)
            .put("priority", 0)
    }

    private fun List<Move>.toJsonMoves(): JSONArray =
        JSONArray().also { moves ->
            forEach { move ->
                moves.put(
                    JSONArray()
                        .put(move.player.toGtpColor())
                        .put(move.toGtpVertex(boardSize)),
                )
            }
        }

    private fun Move.toGtpCommand(boardSize: BoardSize): String =
        when (this) {
            is Move.Play -> "play ${player.toGtpColor()} ${toGtpVertex(boardSize)}"
            is Move.Pass -> "play ${player.toGtpColor()} ${toGtpVertex(boardSize)}"
            is Move.Resign -> "play ${player.toGtpColor()} ${toGtpVertex(boardSize)}"
        }

    private fun Move.toGtpVertex(boardSize: BoardSize): String =
        when (this) {
            is Move.Play -> coordinate.label(boardSize)
            is Move.Pass -> "pass"
            is Move.Resign -> "resign"
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
        private const val VisitsPerCandidate = 20
        private const val MinAnalysisTimeMillis = 2_000L
        private const val AnalysisSearchThreads = 4
    }
}
