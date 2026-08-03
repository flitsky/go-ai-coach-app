package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.DefaultKomi
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class KataGoProcessEngineAdapter(
    private val processConfig: KataGoProcessConfig,
) : EngineCoreApi {
    private var profile: EngineProfile = EngineProfile(mode = EngineMode.LocalProcess)
    private var boardSize: BoardSize = BoardSize.Nine
    private var ruleset: Ruleset = Ruleset.Japanese
    private var handicapCount: Int = 0
    private var komi: Double = DefaultKomi
    private var nextPlayer: StoneColor = StoneColor.Black
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null
    private var analysisProcess: Process? = null
    private var analysisInput: BufferedWriter? = null
    private var analysisOutput: BufferedReader? = null
    private var analysisQueryCounter: Int = 0
    private val playedMoves = mutableListOf<Move>()

    // Serializes access to each process's stdin/stdout so two concurrent engine
    // operations (e.g. a background analysis and a human-move sync) can never
    // interleave reads/writes on the same shared stream and steal each other's
    // response lines.
    private val commandMutex = Mutex()
    private val analysisQueryMutex = Mutex()

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

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStatus {
        ensureProcessStarted()
        this.boardSize = boardSize
        this.ruleset = ruleset
        this.handicapCount = handicapCount
        this.komi = komi
        nextPlayer = if (handicapCount > 0) StoneColor.White else StoneColor.Black
        playedMoves.clear()
        sendCommand(KataGoProtocolCommands.boardSize(boardSize))
        sendCommand(KataGoProtocolCommands.komi(komi))
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
        val response = sendCommand(
            command = KataGoProtocolCommands.genMove(player),
            timeoutMillis = searchTimeoutMillisFor(profile.analysisLimit.timeMillis),
        )
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

    // Deliberately does not acquire commandMutex/analysisQueryMutex: if a call is
    // genuinely stuck holding one, waiting for it here would defeat the entire
    // purpose of a manual "unstick this now" recovery action. destroy() closes the
    // process's underlying pipes, which is what actually unblocks a thread stuck in
    // a blocking read (a plain Thread.interrupt() would not — see sendCommand/
    // sendAnalysisQuery). The stuck call then errors out on its own and releases
    // its mutex normally; the next sendCommand/sendAnalysisQuery call sees process
    // == null and starts a fresh one via ensureProcessStarted()/ensureAnalysisProcessStarted().
    override fun forceReset() {
        restartProcessAfterTimeout()
        restartAnalysisProcessAfterTimeout()
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

    private suspend fun sendCommand(
        command: String,
        timeoutMillis: Long = DefaultCommandTimeoutMillis,
    ): String =
        commandMutex.withLock {
            try {
                withTimeout(timeoutMillis) {
                    runInterruptible(Dispatchers.IO) {
                        sendCommandBlocking(command)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                restartProcessAfterTimeout()
                throw timeout
            }
        }

    private fun sendCommandBlocking(command: String): String {
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

    private suspend fun sendAnalysisQuery(
        query: JSONObject,
        timeoutMillis: Long = DefaultCommandTimeoutMillis,
    ): String =
        analysisQueryMutex.withLock {
            try {
                withTimeout(timeoutMillis) {
                    runInterruptible(Dispatchers.IO) {
                        sendAnalysisQueryBlocking(query)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                restartAnalysisProcessAfterTimeout()
                throw timeout
            }
        }

    private fun sendAnalysisQueryBlocking(query: JSONObject): String {
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

    // A GTP round-trip that misses its deadline is treated as a wedged process:
    // the underlying blocking read is not guaranteed to unblock on coroutine
    // cancellation alone (java.io streams ignore thread interrupts), so the
    // stuck reader thread could otherwise keep consuming responses meant for
    // whatever command runs next. Tearing the process down guarantees the next
    // sendCommand() starts from a clean process and streams.
    private fun restartProcessAfterTimeout() {
        runCatching { process?.destroy() }
        process = null
        input = null
        output = null
    }

    private fun restartAnalysisProcessAfterTimeout() {
        runCatching { analysisProcess?.destroy() }
        analysisProcess = null
        analysisInput = null
        analysisOutput = null
    }

    private fun gtpAnalysisClient(): KataGoGtpAnalysisClient =
        KataGoGtpAnalysisClient(
            sendCommand = { command, timeoutMillis -> sendCommand(command, timeoutMillis) },
            applySearchLimit = { limit -> applySearchLimit(limit) },
            restoreSearchLimit = { profile.analysisLimit },
            contextProvider = ::analysisContext,
        )

    private fun jsonPositionAnalysisClient(): KataGoJsonPositionAnalysisClient =
        KataGoJsonPositionAnalysisClient(
            sendAnalysisQuery = { query, timeoutMillis -> sendAnalysisQuery(query, timeoutMillis) },
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
            handicapCount = handicapCount,
        )

    private suspend fun applySearchLimit(limit: AnalysisLimit) {
        // maxTime is process-global in GTP. Passing only the parameter name is
        // KataGo's supported way to remove a prior dynamic override, so an
        // in-app switch from a capped value to Off cannot inherit the old cap.
        KataGoProtocolCommands.searchLimitCommands(limit).forEach { command -> sendCommand(command) }
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
            komi = komi,
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
