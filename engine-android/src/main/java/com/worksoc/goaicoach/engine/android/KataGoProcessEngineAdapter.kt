package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import org.json.JSONObject

class KataGoProcessEngineAdapter(
    private val processConfig: KataGoProcessConfig,
) : EngineCoreApi {
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

    override suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset, handicapCount: Int): EngineStatus {
        ensureProcessStarted()
        this.boardSize = boardSize
        this.ruleset = ruleset
        nextPlayer = if (handicapCount > 0) StoneColor.White else StoneColor.Black
        playedMoves.clear()
        sendCommand(KataGoProtocolCommands.boardSize(boardSize))
        sendCommand(KataGoProtocolCommands.komi())
        sendCommand(KataGoProtocolCommands.rules(ruleset))
        sendCommand(KataGoProtocolCommands.clearBoard())
        if (handicapCount > 0) {
            val positions = boardSize.handicapStonePositions(handicapCount)
            sendCommand(KataGoProtocolCommands.setFreeHandicap(positions, boardSize))
        }
        return EngineStatus.ready("KataGo new ${boardSize.value}x${boardSize.value} ${ruleset.scoringLabel} game")
    }

    override suspend fun playMove(move: Move): EngineStatus {
        ensureProcessStarted()
        sendCommand(KataGoProtocolCommands.play(move, boardSize))
        playedMoves += move
        if (move is Move.Play || move is Move.Pass) {
            nextPlayer = move.player.opponent
        }
        return EngineStatus.ready("KataGo accepted ${move.describe(boardSize)}")
    }

    override suspend fun genMove(player: StoneColor): MoveResult {
        ensureProcessStarted()
        val response = sendCommand(KataGoProtocolCommands.genMove(player))
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
        sendCommand(KataGoProtocolCommands.undo())
        val removed = playedMoves.removeLastOrNull()
        if (removed != null) {
            nextPlayer = removed.player
        }
        return EngineStatus.ready("KataGo undid one move")
    }

    override suspend fun clearSearchCache(): EngineStatus {
        ensureProcessStarted()
        // Used only when shared-process AI-vs-AI play must prevent one side's
        // deeper search tree from becoming the other side's effective budget.
        sendCommand(KataGoProtocolCommands.clearSearchCache())
        return EngineStatus.ready("KataGo search cache cleared")
    }

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        ensureProcessStarted()
        val effectiveLimit = limit.effectiveAnalysisLimit()
        if (effectiveLimit.needsJsonAnalysis()) {
            runCatching {
                val analysisConfigPath = processConfig.resolveAnalysisConfigPath() ?: return@runCatching null
                ensureAnalysisProcessStarted(analysisConfigPath)
                jsonPositionAnalysisClient().analyze(effectiveLimit, limit.candidateCount)
            }.getOrNull()?.let { jsonResult ->
                return jsonResult
            }
        }

        return gtpAnalysisClient().analyze(
            effectiveLimit = effectiveLimit,
            requestedLimit = limit,
        )
    }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate {
        ensureProcessStarted()
        val response = sendCommand(KataGoProtocolCommands.rawNn())
        return KataGoAnalysisParser.parseScoreEstimate(response, boardSize)
    }

    override suspend fun scoreFinal(): FinalScoreResult {
        ensureProcessStarted()
        val response = sendCommand(KataGoProtocolCommands.finalScore())
        return KataGoAnalysisParser.parseFinalScore(response)
    }

    override suspend fun deadStones(): DeadStonesResult {
        ensureProcessStarted()
        val response = sendCommand(KataGoProtocolCommands.finalStatusList("dead"))
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
                sendCommand(KataGoProtocolCommands.quit())
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

        processConfig.validateGtpFiles()
        val command = processConfig.buildGtpCommand(profile).commandLine

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

        val command = processConfig.buildAnalysisCommand(
            analysisConfigPath = analysisConfigPath,
            analysisSearchThreads = AnalysisSearchThreads,
        ).commandLine

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

    private fun gtpAnalysisClient(): KataGoGtpAnalysisClient =
        KataGoGtpAnalysisClient(
            sendCommand = ::sendCommand,
            applySearchLimit = ::applySearchLimit,
            restoreSearchLimit = { profile.analysisLimit },
            contextProvider = ::analysisContext,
        )

    private fun jsonPositionAnalysisClient(): KataGoJsonPositionAnalysisClient =
        KataGoJsonPositionAnalysisClient(
            sendAnalysisQuery = ::sendAnalysisQuery,
            buildAnalysisQuery = { limit, refineMove, includePolicyOverride ->
                limit.toJsonAnalysisQuery(
                    refineMove = refineMove,
                    includePolicyOverride = includePolicyOverride,
                )
            },
            contextProvider = ::analysisContext,
        )

    private fun analysisContext(): KataGoAnalysisContext =
        KataGoAnalysisContext(
            boardSize = boardSize,
            ruleset = ruleset,
            nextPlayer = nextPlayer,
            playedMoves = playedMoves.toList(),
        )

    private fun applySearchLimit(limit: AnalysisLimit) {
        sendCommand(KataGoProtocolCommands.setMaxVisits(limit.visits))
        // KataGo GTP maxTime is process-global. When timeMillis is null we
        // intentionally do not send a replacement command here, so engine
        // tuning work must verify whether the previous maxTime remains active,
        // whether KataGo treats the missing value as uncapped for the next
        // command, and how that affects final_status_list/final_score. Default
        // pass/pass UX must be wrapped by the application endgame SLA instead
        // of relying on this low-level state.
        limit.timeMillis?.let { timeMillis ->
            sendCommand(KataGoProtocolCommands.setMaxTime(timeMillis))
        }
    }

    private fun AnalysisLimit.effectiveAnalysisLimit(): AnalysisLimit {
        val minimumVisits = (candidateCount * minVisitsPerCandidate).coerceAtLeast(visits)
        val minimumTimeMillis = minTimeMillis?.let { minimum ->
            timeMillis?.coerceAtLeast(minimum) ?: minimum
        } ?: timeMillis
        return copy(
            visits = minimumVisits,
            timeMillis = minimumTimeMillis,
        )
    }

    private fun AnalysisLimit.needsJsonAnalysis(): Boolean =
        includePolicy || refinePolicyMoves > 0

    private fun AnalysisLimit.toJsonAnalysisQuery(
        refineMove: Move.Play? = null,
        includePolicyOverride: Boolean? = null,
    ): JSONObject {
        analysisQueryCounter += 1
        return KataGoJsonAnalysisQueryFactory.build(
            id = "go-ai-coach-analysis-$analysisQueryCounter",
            boardSize = boardSize,
            ruleset = ruleset,
            playedMoves = playedMoves,
            limit = this,
            refineMove = refineMove,
            includePolicyOverride = includePolicyOverride,
        )
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

    private fun EngineProfile.describe(): String =
        "${difficulty.label}, visits=${analysisLimit.visits}, time=${analysisLimit.timeMillis ?: "none"}ms"

    private companion object {
        private const val AnalysisSearchThreads = 4
    }
}
