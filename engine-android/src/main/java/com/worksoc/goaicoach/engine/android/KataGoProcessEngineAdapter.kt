package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.describe
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.DeadStonesResult
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
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

internal class KataGoProcessEngineAdapter(
    private val processConfig: KataGoProcessConfig,
) : EngineCoreApi {
    private var profile: EngineProfile = EngineProfile(mode = EngineMode.LocalProcess)
    private var boardSize: BoardSize = BoardSize.Nine
    private var ruleset: Ruleset = Ruleset.Japanese
    private var handicapCount: Int = 0
    private var komi: Double = DefaultKomi
    private var nextPlayer: StoneColor = StoneColor.Black
    private var initialStones: Map<BoardCoordinate, StoneColor> = emptyMap()

    /**
     * 정적 국면의 **시작 차례** — [initialStones] 위에서 첫 수를 둘 쪽(refactor backlog #91).
     * [syncStaticPosition]이 받은 국면의 `nextPlayer`를 적고, 그 뒤의 착수·물리기는 건드리지 않는다.
     * [nextPlayer]는 "지금 둘 차례"라 홀수 수 뒤에는 이것과 다르다. [initialStones]가 비어 있으면 쓰이지 않는다.
     */
    private var staticStartPlayer: StoneColor = StoneColor.Black
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null
    private var analysisProcess: Process? = null
    private var analysisInput: BufferedWriter? = null
    private var analysisOutput: BufferedReader? = null
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
        initialStones = emptyMap()
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

    override suspend fun syncStaticPosition(state: GameState): EngineStatus {
        ensureProcessStarted()
        this.boardSize = state.boardSize
        this.ruleset = state.ruleset
        this.handicapCount = state.handicapCount
        this.komi = state.komi
        this.nextPlayer = state.nextPlayer
        this.staticStartPlayer = state.nextPlayer
        this.initialStones = state.stones
        this.playedMoves.clear()
        this.playedMoves += state.moves
        return EngineStatus.ready("KataGo static position synced: ${state.stones.size} stone(s), ${state.nextPlayer} turn")
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
        // ⚠️ 폴백 판정은 [attemptJsonAnalysis]가 한다 — 취소/타임아웃을 삼키지 않기 위해서다.
        // 여기서 runCatching으로 되돌리지 마라(refactor backlog #16ⓐ, 그 함수의 KDoc 참고).
        val attempt = if (effectiveLimit.needsJsonAnalysis()) {
            attemptJsonAnalysis {
                val analysisConfigPath = processConfig.resolveAnalysisConfigPath()
                    ?: return@attemptJsonAnalysis null
                ensureAnalysisProcessStarted(analysisConfigPath)
                jsonPositionAnalysisClient().analyze(effectiveLimit, limit.candidateCount)
            }
        } else {
            JsonAnalysisAttempt<AnalysisResult>(result = null, fallback = null)
        }
        attempt.result?.let { jsonResult -> return jsonResult }

        val gtpResult = gtpAnalysisClient().analyze(
            effectiveLimit = effectiveLimit,
            requestedLimit = limit,
        )
        return attempt.fallback?.let { fallback -> gtpResult.copy(fallback = fallback) } ?: gtpResult
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
            initialStones = initialStones,
            initialPlayer = startingPlayer(),
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
        return KataGoJsonAnalysisQueryFactory.build(
            id = KataGoJsonAnalysisQueryFactory.nextQueryId(
                boardSize = boardSize,
                playedMoves = playedMoves,
                refineMove = refineMove,
            ),
            boardSize = boardSize,
            ruleset = ruleset,
            playedMoves = playedMoves,
            limit = this,
            refineMove = refineMove,
            includePolicyOverride = includePolicyOverride,
            komi = komi,
            initialStones = jsonQueryInitialStones(),
            initialPlayer = startingPlayer(),
        )
    }

    /**
     * 시작판([jsonQueryInitialStones]) 위에서 **첫 수를 둘** 차례. JSON 쿼리의 `initialPlayer`와
     * [KataGoAnalysisContext.replayState]가 이 한 곳을 같이 쓴다(refactor backlog #91).
     *
     * 정적 국면이면 [staticStartPlayer]다. 예전에는 둘 다 [nextPlayer](지금 차례)를 썼다 — 동기화 직후나
     * 짝수 수 뒤에는 같은 값이라 드러나지 않았고, 홀수 수 뒤에는 `replayState`가 던져 JSON 분석이 GTP로
     * 폴백하고 GTP 쪽 보충 후보 계산이 같은 예외로 `analyze()`를 끝냈다. 앱의 `syncToGameState`는 정적
     * 국면을 수순 없이만 보내고 매번 다시 동기화하므로 그 상태에 닿지 않는다.
     */
    private fun startingPlayer(): StoneColor =
        when {
            initialStones.isNotEmpty() -> staticStartPlayer
            handicapCount > 0 -> StoneColor.White
            else -> StoneColor.Black
        }

    /**
     * JSON 쿼리의 시작판 — GTP 쪽이 `set_free_handicap`으로 놓은 판과 **같은 판**이어야 한다
     * (refactor backlog #65).
     *
     * - [initialStones]가 있으면(정적 국면) 그것이 판 전체다. 접바둑 돌을 보태지 않는다.
     * - 없고 접바둑이면 [BoardSize.handicapStonePositions]의 흑돌을 싣는다. 예전에는 빈 판이
     *   나가 KataGo가 흑돌 N개 없는 판을 분석했다(aace70da, 2026-07-16부터). 따낸 접바둑 돌도
     *   그대로 싣는다 — KataGo가 수순을 다시 두며 스스로 따내고, 접바둑 보정 N은 시작판의
     *   흑돌 수로 센다. `whiteHandicapBonus`는 싣지 않는다: 룰셋 기본값이 GTP와 같다.
     *
     * ⚠️ 이 보충을 `newGame`으로 옮겨 [initialStones]를 채우지 마라. [initialStones]는 "밖에서 받은 정적
     * 국면"의 표지이기도 하다 — 채워져 있으면 [startingPlayer]가 [staticStartPlayer]를 시작 차례로 내고,
     * 그 값은 [syncStaticPosition]만 적으므로 접바둑의 백 차례라는 보장이 없다(기본값은 흑). 접바둑 국면
     * 복원은 `GameStateReplayer`가 `handicapCount`로 이미 한다. (예전 이유였던 *"replayState가 지금 차례부터
     * 쌓아 홀수 수 뒤에 던진다"* 는 refactor backlog #91이 시작 차례를 따로 적으면서 없어졌다.)
     */
    private fun jsonQueryInitialStones(): List<Pair<StoneColor, BoardCoordinate>> =
        when {
            initialStones.isNotEmpty() -> initialStones.map { (coord, color) -> color to coord }
            handicapCount > 0 -> boardSize.handicapStonePositions(handicapCount).map { coord -> StoneColor.Black to coord }
            else -> emptyList()
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
