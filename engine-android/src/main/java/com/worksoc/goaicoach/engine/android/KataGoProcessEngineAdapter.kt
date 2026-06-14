package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.CandidateMoveSource
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameStateReplayer
import com.worksoc.goaicoach.shared.LegalMoveGenerator
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.allCoordinates
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

    override suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset): EngineStatus {
        ensureProcessStarted()
        this.boardSize = boardSize
        this.ruleset = ruleset
        nextPlayer = StoneColor.Black
        playedMoves.clear()
        sendCommand(KataGoProtocolCommands.boardSize(boardSize))
        sendCommand(KataGoProtocolCommands.komi())
        sendCommand(KataGoProtocolCommands.rules(ruleset))
        sendCommand(KataGoProtocolCommands.clearBoard())
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
                analyzeWithJson(effectiveLimit, limit.candidateCount)
            }.getOrNull()?.let { jsonResult ->
                return jsonResult
            }
        }

        val gtpResult = analyzeWithGtp(effectiveLimit, limit)
        val candidates = gtpResult.candidates
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
                rootVisits = gtpResult.rootVisits,
                elapsedMs = gtpResult.elapsedMs,
            ),
            rootVisits = gtpResult.rootVisits,
            elapsedMillis = gtpResult.elapsedMs,
        )
    }

    private fun analyzeWithJson(
        effectiveLimit: AnalysisLimit,
        candidateCount: Int,
    ): AnalysisResult? {
        val analysisConfigPath = processConfig.resolveAnalysisConfigPath() ?: return null
        ensureAnalysisProcessStarted(analysisConfigPath)
        val startNanos = System.nanoTime()
        val response = sendAnalysisQuery(effectiveLimit.toJsonAnalysisQuery())
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        val rootVisits = KataGoJsonAnalysisParser.parseRootVisits(response)
        val searchedCandidates = KataGoJsonAnalysisParser.parseCandidates(
            response = response,
            player = nextPlayer,
            boardSize = boardSize,
            maxCandidates = candidateCount,
        )
        val currentState = GameStateReplayer
            .replay(boardSize = boardSize, ruleset = ruleset, moves = playedMoves)
        val legalCoordinates = LegalMoveGenerator
            .legalPlayCoordinates(currentState, nextPlayer)
            .toSet()
        val illegalCoordinates = boardSize.allCoordinates().toSet() - legalCoordinates
        val searchedCoordinates = searchedCandidates.playCoordinates()
        val policyCandidates = if (effectiveLimit.includePolicy) {
            KataGoJsonAnalysisParser.parsePolicyCandidates(
                response = response,
                player = nextPlayer,
                boardSize = boardSize,
                maxCandidates = candidateCount,
                excludedCoordinates = searchedCoordinates + illegalCoordinates,
            )
        } else {
            emptyList()
        }
        val referenceScoreLead = KataGoJsonAnalysisParser.parseRootWhiteScoreLead(response)
        val refinedCandidates = if (referenceScoreLead == null || effectiveLimit.refinePolicyMoves <= 0) {
            emptyList()
        } else {
            refineJsonPolicyCandidates(
                policyCandidates = policyCandidates,
                referenceScoreLead = referenceScoreLead,
                maxCandidates = candidateCount,
                refineBudget = effectiveLimit.refinePolicyMoves,
            )
        }
        val refinedCoordinates = refinedCandidates.playCoordinates()
        val candidates = (
            searchedCandidates +
                refinedCandidates +
                policyCandidates.filterNot { candidate ->
                    (candidate.move as? Move.Play)?.coordinate in refinedCoordinates
                }
            )
            .take(candidateCount)
        val scoredCount = candidates.count { it.pointLoss != null }
        val policyOnlyCount = candidates.count { it.pointLoss == null && it.policyPrior != null }
        return AnalysisResult(
            status = EngineStatus.ready(
                "KataGo JSON analysis complete for ${nextPlayer.label}: $scoredCount/$candidateCount scored candidate(s)",
            ),
            candidates = candidates,
            summary = buildJsonAnalysisSummary(
                effectiveLimit = effectiveLimit,
                rootVisits = rootVisits,
                elapsedMs = elapsedMs,
                scoredCount = scoredCount,
                policyOnlyCount = policyOnlyCount,
                refinedCount = refinedCandidates.size,
            ),
            rootVisits = rootVisits,
            elapsedMillis = elapsedMs,
        )
    }

    private fun buildJsonAnalysisSummary(
        effectiveLimit: AnalysisLimit,
        rootVisits: Int?,
        elapsedMs: Long,
        scoredCount: Int,
        policyOnlyCount: Int,
        refinedCount: Int,
    ): String {
        val fillStatus = when {
            rootVisits == null -> "UNKNOWN"
            rootVisits < effectiveLimit.visits -> "SHORT"
            else -> "OK"
        }
        return buildString {
            append("KataGo JSON analysis with ${effectiveLimit.visits} visits / ${effectiveLimit.timeMillis ?: 0}ms.")
            append(" Visit diagnostics: request=${effectiveLimit.visits}, root=${rootVisits ?: "none"}, elapsedMs=$elapsedMs, timeCapMs=${effectiveLimit.timeMillis ?: "none"}, fill=$fillStatus.")
            append(" Returned $scoredCount scored, $policyOnlyCount policy-only candidate(s); refined $refinedCount/${effectiveLimit.refinePolicyMoves} policy move(s).")
        }
    }

    private fun refineJsonPolicyCandidates(
        policyCandidates: List<CandidateMove>,
        referenceScoreLead: Double,
        maxCandidates: Int,
        refineBudget: Int,
    ): List<CandidateMove> {
        val refineCount = (maxCandidates / JsonRefineCandidateDivisor)
            .coerceIn(0, refineBudget)
        if (refineCount <= 0) {
            return emptyList()
        }

        return policyCandidates
            .asSequence()
            .mapNotNull { candidate ->
                val move = candidate.move as? Move.Play ?: return@mapNotNull null
                move to candidate.policyPrior
            }
            .take(refineCount)
            .mapNotNull { (move, policyPrior) ->
                runCatching {
                    val response = sendAnalysisQuery(
                        JsonRefineLimit.toJsonAnalysisQuery(
                            refineMove = move,
                            includePolicyOverride = false,
                        ),
                    )
                    KataGoJsonAnalysisParser.parseRefinedCandidate(
                        response = response,
                        player = nextPlayer,
                        move = move,
                        referenceScoreLead = referenceScoreLead,
                        policyPrior = policyPrior,
                    )
                }.getOrNull()
            }
            .toList()
    }

    private fun analyzeWithGtp(
        effectiveLimit: AnalysisLimit,
        limit: AnalysisLimit,
    ): GtpAnalysisResult =
        try {
            applySearchLimit(effectiveLimit)
            val startNanos = System.nanoTime()
            val response = sendCommand(KataGoProtocolCommands.searchAnalyze(nextPlayer, effectiveLimit))
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            val candidates = KataGoAnalysisParser.attachPointLoss(
                candidates = KataGoAnalysisParser.parseCandidates(
                    response = response,
                    player = nextPlayer,
                    boardSize = boardSize,
                    maxCandidates = limit.candidateCount,
                ),
            ).fillFromPolicyIfNeeded(limit)
            GtpAnalysisResult(
                candidates = candidates,
                rootVisits = KataGoAnalysisParser.parseRootVisitsEstimate(response),
                elapsedMs = elapsedMs,
            )
        } finally {
            applySearchLimit(profile.analysisLimit)
        }

    private data class GtpAnalysisResult(
        val candidates: List<CandidateMove>,
        val rootVisits: Int?,
        val elapsedMs: Long,
    )

    private fun List<CandidateMove>.fillFromPolicyIfNeeded(
        limit: AnalysisLimit,
    ): List<CandidateMove> {
        val remaining = limit.candidateCount - size
        if (remaining <= 0) {
            return take(limit.candidateCount)
        }

        val currentState = GameStateReplayer
            .replay(boardSize = boardSize, ruleset = ruleset, moves = playedMoves)
        val legalCoordinates = LegalMoveGenerator
            .legalPlayCoordinates(currentState, nextPlayer)
            .toSet()
        val illegalCoordinates = boardSize.allCoordinates().toSet() - legalCoordinates
        val occupiedCoordinates = currentState
            .stones
            .keys
        val searchCoordinates = mapNotNull { candidate ->
            (candidate.move as? Move.Play)?.coordinate
        }
        val policyCandidates = if (limit.includePolicy) {
            val policyResponse = sendCommand(KataGoProtocolCommands.rawNn())
            KataGoAnalysisParser.parsePolicyCandidates(
                response = policyResponse,
                player = nextPlayer,
                boardSize = boardSize,
                maxCandidates = remaining,
                excludedCoordinates = occupiedCoordinates + illegalCoordinates + searchCoordinates,
            )
        } else {
            emptyList()
        }
        val usedCoordinates = occupiedCoordinates + illegalCoordinates + searchCoordinates +
            policyCandidates.mapNotNull { candidate -> (candidate.move as? Move.Play)?.coordinate }
        val legalFallbackCandidates = boardSize.allCoordinates()
            .filter { coordinate -> coordinate in legalCoordinates && coordinate !in usedCoordinates }
            .take(remaining - policyCandidates.size)
            .mapIndexed { index, coordinate ->
                CandidateMove(
                    move = Move.Play(nextPlayer, coordinate),
                    source = CandidateMoveSource.LegalFallback,
                    note = "Legal fallback ${index + 1}",
                )
            }
            .toList()

        return (this + policyCandidates + legalFallbackCandidates).take(limit.candidateCount)
    }

    private fun List<CandidateMove>.playCoordinates(): Set<BoardCoordinate> =
        mapNotNull { candidate -> (candidate.move as? Move.Play)?.coordinate }
            .toSet()

    private fun buildAnalysisSummary(
        requestedLimit: AnalysisLimit,
        effectiveLimit: AnalysisLimit,
        candidateCount: Int,
        scoredCount: Int,
        policyFallbackCount: Int,
        legalFallbackCount: Int,
        rootVisits: Int?,
        elapsedMs: Long,
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
        val fillStatus = when {
            rootVisits == null -> "UNKNOWN"
            rootVisits < effectiveLimit.visits -> "SHORT"
            else -> "OK"
        }
        val diagnosticsText =
            " Visit diagnostics: request=${effectiveLimit.visits}, root=${rootVisits ?: "none"}, elapsedMs=$elapsedMs, timeCapMs=${effectiveLimit.timeMillis ?: "none"}, fill=$fillStatus."
        return if (policyFallbackCount > 0 || legalFallbackCount > 0) {
            buildString {
                append(searchText)
                append(diagnosticsText)
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
            "$searchText$diagnosticsText Showing $scoredCount/${requestedLimit.candidateCount} scored spot(s)."
        }
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

    private fun applySearchLimit(limit: AnalysisLimit) {
        sendCommand(KataGoProtocolCommands.setMaxVisits(limit.visits))
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
        private const val JsonRefineCandidateDivisor = 4
        private val JsonRefineLimit = AnalysisLimit(
            visits = 8,
            includePolicy = false,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )
    }
}
