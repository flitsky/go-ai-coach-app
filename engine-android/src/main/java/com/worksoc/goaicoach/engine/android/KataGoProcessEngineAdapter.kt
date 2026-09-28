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
import com.worksoc.goaicoach.shared.enginecontract.DefaultCommandTimeoutMillis
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.enginecontract.analysisSearchTimeMillis
import com.worksoc.goaicoach.shared.enginecontract.searchTimeoutMillisFor
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * 로컬 KataGo(GTP 프로세스 + JSON analysis 프로세스) 위의 [EngineCoreApi].
 *
 * ## 프로세스 수명(refactor backlog #14)
 * - 기동·폐기는 [EngineProcessSlot]만 한다 — 기동은 슬롯의 수명 락 안에서 double-checked, 폐기는 락 없이 자기
 *   핸들만(ABA 방지). 이 어댑터는 프로세스를 만들지 않고 [EngineProcessHandle]의 writer/reader만 쓴다.
 * - 엔진 호출 하나는 시작할 때 핸들 하나를 잡아 끝까지 **그 프로세스에만** 쓴다. 도중에 그 프로세스가 내려가면
 *   남은 명령은 보내지 않고 실패한다 — 새(빈 판의) 프로세스로 옮겨 가 반쪽 설정을 보내지 않는다.
 * - 왕복 하나가 제 마감을 넘기면 **그 호출의** 프로세스를 SIGKILL로 내린다 — 진짜로 막힌 읽기도 마감에 풀린다.
 *   예전 `withTimeout { runInterruptible { readLine() } }`는 늦게라도 오는 답만 끊었다(파이프 읽기는 인터럽트를
 *   무시한다). 호출자가 취소된 것(무르기·나가기)은 프로세스를 내릴 이유가 아니다 — [roundTrip].
 * - 새 프로세스의 **첫 답**만은 명령 마감 위에 기동 예산([EngineStartupBudgetMillis])을 더 기다린다 — 모델 적재가
 *   첫 명령의 마감을 먹지 않게(refactor backlog #17). SIGKILL·세대 규칙은 같다: 그 예산도 넘기면 그 핸들만 내린다.
 * - 취소된 호출자는 답을 기다리지 않고 곧바로 돌아간다 — 답은 뒤에 남은 배수가 받아 스트림을 맞추고, 그동안 그
 *   프로세스의 왕복 락을 쥔다(refactor backlog #15, [roundTrip]). 그래야 호출자가 쥔 오퍼레이션 락이 곧바로 풀린다.
 *   JSON 분석이면 배수가 먼저 그 쿼리에 `terminate`를 보내 탐색 끝까지 기다리지 않는다(refactor backlog #17).
 * - 재시작 뒤의 재동기화는 지금처럼 호출자 몫이다. 이 어댑터는 새 프로세스에 명령을 스스로 더 보내지 않는다.
 * - 여러 호출로 된 오퍼레이션(동기화 + 분석)이 **다른 오퍼레이션과 섞이지 않게** 하는 것은 이 층이 아니라 3계층의
 *   오퍼레이션 락이다(refactor backlog #15, `LocalEngineSessionClient`). 이 층의 판 거울(`playedMoves` 등)도 그 락이
 *   지킨다 — 한 번에 한 오퍼레이션만 쓴다. ⚠️ 한 오퍼레이션이 **도중에** 프로세스를 잃었을 때(시간 초과·리셋) 그 뒤의
 *   호출이 새(빈 판의) 프로세스로 가는 것은 아직 막지 않는다 — 예: 종국 판정이 삼킨 시간 초과 뒤의 형세 추정.
 *   실패의 종류(판을 잃었는가)를 가르는 일과 함께 #17로 넘겼다.
 *
 * @param runtime 1계층 — 프로세스를 띄우는 자리(refactor backlog #14). 이 어댑터는 프로세스를 직접 만들지 않는다.
 * @param deadlineMillis 명령마다 정해진 마감(ms)과 기동 예산([EngineStartupBudgetMillis])을 실제로 기다릴 마감으로
 *   옮긴다. 프로덕션은 항등이다 — 마감 값을 바꾸는 자리가 아니라, 테스트가 30초·120초 마감과 60초 기동 예산을 짧게
 *   줄여 시간 초과 경로를 재는 이음새다.
 */
internal class KataGoProcessEngineAdapter(
    private val runtime: EngineProcessRuntime,
    private val deadlineMillis: (Long) -> Long = { timeoutMillis -> timeoutMillis },
) : EngineCoreApi {
    constructor(processConfig: KataGoProcessConfig) : this(runtime = LocalKataGoProcessRuntime(processConfig))

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
    private val playedMoves = mutableListOf<Move>()

    /**
     * 종류마다 "지금 프로세스" 자리. 한 프로세스의 stdin/stdout 왕복은 그 핸들의 락이 한 번에 하나로 묶는다 —
     * 두 오퍼레이션(예: 배경 분석과 착수 동기화)이 같은 스트림에서 서로의 답 줄을 가로채지 않게.
     */
    private val gtpSlot = EngineProcessSlot(EngineProcessKind.Gtp, EngineProcessEvents.StdErr)
    private val analysisSlot = EngineProcessSlot(EngineProcessKind.Analysis, EngineProcessEvents.StdErr)

    /**
     * 파이프 왕복(블로킹 쓰기·읽기)이 도는 곳 — 호출자의 코루틴과 떼어 둔다. 마감이 지나면 호출자는 막힌 읽기를
     * 기다리지 않고 돌아가고, 그 읽기는 프로세스가 내려가며 EOF로 혼자 끝난다. 거기서 난 예외는 누가 기다리지
     * 않아도 앱을 죽이지 않는다(`async`라서다 — `launch`로 바꾸지 말 것).
     */
    private val pipeIo = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("KataGoPipeIo"))

    override suspend fun initialize(profile: EngineProfile): EngineStatus {
        this.profile = profile.copy(mode = EngineMode.LocalProcess)
        acquireGtp()
        configure(this.profile)
        return EngineStatus.ready("KataGo process ready: ${this.profile.describe()}")
    }

    override suspend fun configure(profile: EngineProfile): EngineStatus {
        this.profile = profile.copy(mode = EngineMode.LocalProcess)
        val gtp = acquireGtp()
        applySearchLimit(gtp, this.profile.analysisLimit)
        return EngineStatus.ready("KataGo process configured: ${this.profile.describe()}")
    }

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStatus {
        val gtp = acquireGtp()
        this.boardSize = boardSize
        this.ruleset = ruleset
        this.handicapCount = handicapCount
        this.komi = komi
        nextPlayer = if (handicapCount > 0) StoneColor.White else StoneColor.Black
        playedMoves.clear()
        initialStones = emptyMap()
        sendCommand(gtp, KataGoProtocolCommands.boardSize(boardSize))
        sendCommand(gtp, KataGoProtocolCommands.komi(komi))
        KataGoProtocolCommands.ruleCommands(ruleset).forEach { command -> sendCommand(gtp, command) }
        sendCommand(gtp, KataGoProtocolCommands.clearBoard())
        if (handicapCount > 0) {
            val positions = boardSize.handicapStonePositions(handicapCount)
            sendCommand(gtp, KataGoProtocolCommands.setFreeHandicap(positions, boardSize))
        }
        return EngineStatus.ready("KataGo new ${boardSize.value}x${boardSize.value} ${ruleset.scoringLabel} game")
    }

    override suspend fun syncStaticPosition(state: GameState): EngineStatus {
        // 보내는 명령은 없다 — 오늘처럼 GTP 프로세스를 띄워 두기만 한다(기동 실패도 여기서 드러난다).
        acquireGtp()
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
        val gtp = acquireGtp()
        sendCommand(gtp, KataGoProtocolCommands.play(move, boardSize))
        playedMoves += move
        if (move is Move.Play || move is Move.Pass) {
            nextPlayer = move.player.opponent
        }
        return EngineStatus.ready("KataGo accepted ${move.describe(boardSize)}")
    }

    override suspend fun genMove(player: StoneColor): MoveResult {
        val gtp = acquireGtp()
        val response = sendCommand(
            gtp = gtp,
            command = KataGoProtocolCommands.genMove(player),
            timeoutMillis = searchTimeoutMillisFor(profile.analysisLimit.timeMillis),
        )
        val move = response.toGtpMove(player, boardSize)
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
        val gtp = acquireGtp()
        sendCommand(gtp, KataGoProtocolCommands.undo())
        val removed = playedMoves.removeLastOrNull()
        if (removed != null) {
            nextPlayer = removed.player
        }
        return EngineStatus.ready("KataGo undid one move")
    }

    override suspend fun clearSearchCache(): EngineStatus {
        val gtp = acquireGtp()
        // Used only when shared-process AI-vs-AI play must prevent one side's
        // deeper search tree from becoming the other side's effective budget.
        sendCommand(gtp, KataGoProtocolCommands.clearSearchCache())
        return EngineStatus.ready("KataGo search cache cleared")
    }

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        val gtp = acquireGtp()
        val effectiveLimit = limit.effectiveAnalysisLimit()
        // ⚠️ 폴백 판정은 [attemptJsonAnalysis]가 한다 — 취소/타임아웃을 삼키지 않기 위해서다.
        // 여기서 runCatching으로 되돌리지 마라(refactor backlog #16ⓐ, 그 함수의 KDoc 참고).
        val attempt = if (effectiveLimit.needsJsonAnalysis()) {
            attemptJsonAnalysis {
                val analysisConfigPath = runtime.analysisConfigPathOrNull()
                    ?: return@attemptJsonAnalysis null
                val analysis = analysisSlot.acquire { runtime.startAnalysis(analysisConfigPath) }
                jsonPositionAnalysisClient(analysis).analyze(effectiveLimit, limit.candidateCount)
            }
        } else {
            JsonAnalysisAttempt<AnalysisResult>(result = null, fallback = null)
        }
        attempt.result?.let { jsonResult -> return jsonResult }

        val gtpResult = gtpAnalysisClient(gtp).analyze(
            effectiveLimit = effectiveLimit,
            requestedLimit = limit,
        )
        return attempt.fallback?.let { fallback -> gtpResult.copy(fallback = fallback) } ?: gtpResult
    }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate {
        val gtp = acquireGtp()
        val response = sendCommand(gtp, KataGoProtocolCommands.rawNn())
        return KataGoAnalysisParser.parseScoreEstimate(response, boardSize)
    }

    override suspend fun scoreFinal(): FinalScoreResult {
        val gtp = acquireGtp()
        val response = sendCommand(gtp, KataGoProtocolCommands.finalScore())
        return KataGoAnalysisParser.parseFinalScore(response)
    }

    override suspend fun deadStones(): DeadStonesResult {
        val gtp = acquireGtp()
        val response = sendCommand(gtp, KataGoProtocolCommands.finalStatusList("dead"))
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
        // 붙잡은 핸들만 내린다 — `quit`을 기다리는 사이 다른 호출이 띄운 새 세대는 건드리지 않는다. `quit`의 대기도
        // 이제 마감이 있다: 앞선 호출이 멈춰 있어도 그 호출의 마감에 풀린다.
        val gtp = gtpSlot.currentOrNull()
        val analysis = analysisSlot.currentOrNull()
        runCatching {
            if (gtp != null) {
                sendCommand(gtp, KataGoProtocolCommands.quit())
            }
        }
        gtp?.let { gtpSlot.retire(it, EngineProcessRetireReason.Stop) }
        analysis?.let { analysisSlot.retire(it, EngineProcessRetireReason.Stop) }
        return EngineStatus.stopped("KataGo process stopped")
    }

    /**
     * 「엔진 다시 시작하기」 — 지금 두 프로세스를 SIGKILL로 내린다(refactor backlog #14).
     *
     * - **어떤 락도 잡지 않고, 기다리지도 서스펜드하지도 않는다** — 메인 스레드에서 불리고, 멈춘 것을 풀려고 부르는
     *   함수다(함정 71). 멈춘 호출이 쥔 왕복 락도, 진행 중인 기동이 쥔 수명 락도 기다리지 않는다.
     * - SIGKILL인 이유: 막힌 파이프 읽기는 프로세스가 내려가야만 풀린다 — 스레드 인터럽트로도, 기기(Linux)에서는
     *   우리 쪽 스트림을 닫아도 안 풀린다. SIGTERM은 SIGSTOP된 프로세스에서 보류되고, 신호를 붙잡는 프로세스는 안
     *   내려갈 수 있다. 예전 주석의 *"destroy()가 파이프를 닫아 막힌 읽기를 푼다"* 는 macOS(JVM 테스트)에서는 맞지만
     *   기기에서는 보장되지 않는다(함정 71).
     * - reader/writer는 닫지 않는다 — 막힌 `readLine()`과 같은 락을 잡아 이 함수가 같이 멈춘다.
     * - 멈춘 호출은 EOF로 풀려 끝난다(호출자가 살아 있으면 `IllegalStateException`, 취소됐으면 취소). 그 프로세스에
     *   묶여 줄 서 있던 호출은 보내지 않고 곧바로 실패한다. 다음 호출이 새 세대를 띄운다.
     * - 기동이 진행 중인 프로세스는 아직 슬롯에 없으므로 건드리지 않는다 — 막 뜨는 새 프로세스다.
     */
    override fun forceReset() {
        gtpSlot.retireCurrent(EngineProcessRetireReason.ForceReset)
        analysisSlot.retireCurrent(EngineProcessRetireReason.ForceReset)
    }

    private suspend fun acquireGtp(): EngineProcessHandle = gtpSlot.acquire { runtime.startGtp(profile) }

    private suspend fun sendCommand(
        gtp: EngineProcessHandle,
        command: String,
        timeoutMillis: Long = DefaultCommandTimeoutMillis,
    ): String =
        roundTrip(gtpSlot, gtp, label = command, timeoutMillis = timeoutMillis) { writer, reader ->
            exchangeGtpCommand(writer, reader, command)
        }

    private suspend fun sendAnalysisQuery(
        analysis: EngineProcessHandle,
        query: JSONObject,
        timeoutMillis: Long = DefaultCommandTimeoutMillis,
    ): String =
        roundTrip(
            slot = analysisSlot,
            handle = analysis,
            label = query.getString("id"),
            timeoutMillis = timeoutMillis,
            // 호출자가 취소되면 그 쿼리의 탐색을 멈춘다 — 배수가 탐색 끝까지 기다리지 않게(refactor backlog #17).
            abandon = { writer -> writer.writeLine(KataGoJsonAnalysisQueryFactory.terminate(query.getString("id")).toString()) },
        ) { writer, reader ->
            exchangeAnalysisQuery(writer, reader, query)
        }

    /**
     * [handle]의 프로세스와 한 번 주고받는다 — 그 핸들의 왕복 락 안에서, 마감([timeoutMillis]를 [deadlineMillis]로
     * 옮긴 값)까지만.
     *
     * | 경우 | 결과 |
     * | --- | --- |
     * | 답이 온다 | 그 값 |
     * | 마감까지 답이 없다(늦은 답·진짜로 멈춤) | 이 핸들을 SIGKILL로 내리고 마감에 [TimeoutCancellationException] — 막힌 읽기를 기다리지 않는다 |
     * | 새 프로세스의 첫 왕복 | 마감 = 명령 마감 + 기동 예산([EngineStartupBudgetMillis]) 중 남은 몫 — 모델 적재가 명령 마감을 먹지 않게(#17) |
     * | 그사이 프로세스가 끝났다(forceReset·크래시) | EOF → `IllegalStateException`(예전 문구 그대로), 이 핸들은 `Died` |
     * | 호출자가 취소됐다(무르기·나가기) | **곧바로** 취소를 올린다. 답은 뒤에 남은 배수([drainThenUnlock])가 끝까지 받아 스트림을 맞춘다 — 단 마감까지만, 넘으면 내린다 |
     * | 이 핸들이 이미 내려갔다 | 보내지 않고 곧바로 `IllegalStateException` |
     * | 호출자가 이미 취소돼 있다 | 보내지 않고 취소(예전 `withContext` 입구와 같다) |
     *
     * ⚠️ 시간 초과가 내리는 것은 **이 호출이 잡은 핸들**뿐이다(ABA 방지 — [EngineProcessSlot.retire]). "지금 프로세스"를
     * 내리면, 이 호출이 멈춰 있던 사이 forceReset 뒤에 뜬 새 세대를 죽인다(예전 코드가 그랬다).
     * ⚠️ 호출자 취소로 프로세스를 내리지 말 것 — 무르기를 연타하면 KataGo가 그때마다 다시 뜬다.
     *
     * ## 취소된 호출자는 답을 기다리지 않는다(refactor backlog #15)
     * 예전에는 취소된 호출자가 `NonCancellable` 안에서 답(길면 탐색 하나 전부)을 기다렸다. 호출자는 3계층의 오퍼레이션
     * 락을 쥔 채이므로, 그렇게 기다리면 무르기 뒤의 재동기화가 **다른 프로세스(GTP)로 갈 일인데도** 그 탐색이 끝날
     * 때까지 막힌다(JSON 분석 중 무르기). 그래서 답 받기를 [pipeIo]의 배수에 넘기고, 이 핸들의 왕복 락도 그 배수가
     * 쥐었다가 푼다. 같은 프로세스로 가는 다음 명령은 지금처럼 이 락에 줄 서서 배수가 끝난 뒤에 나간다 — 늦은 답을
     * 제 답으로 읽지 않는다. forceReset(EOF)도 지금처럼 배수를 끝낸다.
     * ⚠️ 왕복 락을 `withLock`으로 되돌리지 말 것 — 락을 넘길 수 없어 호출자가 다시 답을 기다리게 된다.
     *
     * [abandon]이 있으면 호출자가 취소됐을 때 배수가 먼저 그것을 보낸다 — JSON 분석의 `terminate`(refactor backlog #17).
     * 그러면 KataGo가 탐색을 멈추고 곧바로 끝 답을 내, 배수가 탐색이 끝날 때까지 왕복 락을 쥐지 않는다.
     */
    private suspend fun <T> roundTrip(
        slot: EngineProcessSlot,
        handle: EngineProcessHandle,
        label: String,
        timeoutMillis: Long,
        abandon: ((BufferedWriter) -> Unit)? = null,
        exchange: (BufferedWriter, BufferedReader) -> T,
    ): T {
        // 락은 try 밖에서 잡는다 — 기다리다 취소되면 잡지 않은 채 여기서 끝난다(풀 것이 없다).
        handle.roundTripMutex.lock()
        // 이 락을 푸는 쪽은 둘 중 하나다: 여기(평소), 또는 호출자가 취소됐을 때 답을 마저 받는 배수.
        var unlockHere = true
        try {
            currentCoroutineContext().ensureActive()
            if (!handle.isUsable) {
                slot.retire(handle, EngineProcessRetireReason.Died)
                throw IllegalStateException("KataGo $handle was retired (${handle.retireReason}) before `$label`")
            }
            // 새 프로세스의 첫 답은 모델 적재를 기다린다 — 기동 예산을 명령 마감 위에 따로 얹는다(refactor backlog #17).
            val budgetMillis = deadlineMillis(timeoutMillis) +
                handle.startupAllowanceMillis(deadlineMillis(EngineStartupBudgetMillis))
            val startedAtNanos = System.nanoTime()
            val work = pipeIo.async { exchangeOrRetire(slot, handle, exchange) }
            try {
                return withTimeout(budgetMillis) { work.await() }
            } catch (cancellation: CancellationException) {
                if (cancellation is TimeoutCancellationException && currentCoroutineContext().isActive) {
                    // 이 호출의 마감이다 — 막힌 읽기는 프로세스를 내려야만 풀린다. 그 읽기를 기다리지 않는다.
                    slot.retire(handle, EngineProcessRetireReason.Timeout)
                } else if (!work.isCompleted) {
                    val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
                    unlockHere = false
                    drainThenUnlock(slot, handle, work, remainingMillis = budgetMillis - elapsedMillis, abandon = abandon)
                }
                throw cancellation
            }
        } finally {
            if (unlockHere) handle.roundTripMutex.unlock()
        }
    }

    private fun <T> exchangeOrRetire(
        slot: EngineProcessSlot,
        handle: EngineProcessHandle,
        exchange: (BufferedWriter, BufferedReader) -> T,
    ): T =
        try {
            exchange(handle.writer, handle.reader).also { handle.markReplied() }
        } catch (ended: EngineStreamEnded) {
            slot.retire(handle, EngineProcessRetireReason.Died)
            throw ended
        } catch (broken: IOException) {
            slot.retire(handle, EngineProcessRetireReason.Died)
            throw broken
        }

    /**
     * 호출자가 취소됐다(무르기·나가기). 답은 끝까지 받아 스트림을 맞춘다 — 다음 명령이 이 답을 제 답으로 읽지 않게.
     * 단 원래 마감까지만: 그때까지 안 오면 진짜로 멈춘 것이니 프로세스를 내려 읽기를 푼다(설계 R8). 그다음에
     * [handle]의 왕복 락을 푼다 — [roundTrip]이 넘겨준 락이다.
     *
     * 호출자와 떼어 [pipeIo]에서 돈다(refactor backlog #15) — 호출자는 곧바로 돌아가 오퍼레이션 락을 놓는다. 되돌릴 수
     * 없는 정리라 호출자의 취소와 상관없이 끝까지 한다(예전의 `NonCancellable`과 같은 뜻). `async`인 이유는 [pipeIo]와
     * 같다 — 여기서 무엇이 던져져도 앱을 죽이지 않는다. 락은 무슨 일이 있어도 `finally`에서 푼다.
     */
    private fun drainThenUnlock(
        slot: EngineProcessSlot,
        handle: EngineProcessHandle,
        work: Deferred<*>,
        remainingMillis: Long,
        abandon: ((BufferedWriter) -> Unit)?,
    ) {
        // 따로 띄운다 — 블로킹 쓰기가 멈춰도(프로세스가 stdin을 안 읽는다) 아래 마감이 그대로 돌아 프로세스를 내리고,
        // 그러면 그 쓰기도 풀린다. 줄 하나를 한 번에 쓰므로 다음 명령의 줄과 섞이지 않는다([writeLine]).
        abandon?.let { stop -> pipeIo.async { stop(handle.writer) } }
        pipeIo.async {
            try {
                val replied = withTimeoutOrNull(remainingMillis.coerceAtLeast(1)) { work.join() } != null
                if (!replied) slot.retire(handle, EngineProcessRetireReason.Timeout)
            } finally {
                handle.roundTripMutex.unlock()
            }
        }
    }

    private fun exchangeGtpCommand(
        writer: BufferedWriter,
        reader: BufferedReader,
        command: String,
    ): String {
        writer.write(command)
        writer.newLine()
        writer.flush()

        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: throw EngineStreamEnded("KataGo process ended while waiting for: $command")
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

    private fun exchangeAnalysisQuery(
        writer: BufferedWriter,
        reader: BufferedReader,
        query: JSONObject,
    ): String {
        writer.writeLine(query.toString())

        val queryId = query.getString("id")
        while (true) {
            val line = reader.readLine() ?: throw EngineStreamEnded("KataGo analysis process ended while waiting for: $queryId")
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
            // `terminate`로 탐색 전에 멈춘 쿼리의 끝 답 — `moveInfos`가 없다(refactor backlog #17). 기다리는 쪽은 이미
            // 취소된 호출자뿐이라, 스트림을 맞추는 데까지만 쓰인다.
            if (response.optBoolean("noResults", false)) {
                return trimmed
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

    private fun gtpAnalysisClient(gtp: EngineProcessHandle): KataGoGtpAnalysisClient =
        KataGoGtpAnalysisClient(
            sendCommand = { command, timeoutMillis -> sendCommand(gtp, command, timeoutMillis) },
            applySearchLimit = { limit -> applySearchLimit(gtp, limit) },
            restoreSearchLimit = { profile.analysisLimit },
            contextProvider = ::analysisContext,
        )

    private fun jsonPositionAnalysisClient(analysis: EngineProcessHandle): KataGoJsonPositionAnalysisClient =
        KataGoJsonPositionAnalysisClient(
            sendAnalysisQuery = { query, timeoutMillis -> sendAnalysisQuery(analysis, query, timeoutMillis) },
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

    private suspend fun applySearchLimit(
        gtp: EngineProcessHandle,
        limit: AnalysisLimit,
    ) {
        // maxTime is process-global in GTP. Passing only the parameter name is
        // KataGo's supported way to remove a prior dynamic override, so an
        // in-app switch from a capped value to Off cannot inherit the old cap.
        KataGoProtocolCommands.searchLimitCommands(limit).forEach { command -> sendCommand(gtp, command) }
    }

    private fun AnalysisLimit.effectiveAnalysisLimit(): AnalysisLimit {
        val minimumVisits = (candidateCount * minVisitsPerCandidate).coerceAtLeast(visits)
        return copy(
            visits = minimumVisits,
            timeMillis = analysisSearchTimeMillis(),
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
     *   흑돌 수로 센다. 보정 방식은 GTP와 같은 `Ruleset.handicapBonusRule`을 읽는다 — 이름 룰 기본값과 다를
     *   때만 쿼리 팩토리가 최상위 `whiteHandicapBonus`를 싣는다(#106).
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

    private fun EngineProfile.describe(): String =
        "${difficulty.label}, visits=${analysisLimit.visits}, time=${analysisLimit.timeMillis ?: "none"}ms"
}

/** 답을 다 받기 전에 파이프가 끝났다(프로세스가 죽었다). 문구는 예전 `error(...)` 그대로다 — 호출자에게는 `IllegalStateException`. */
private class EngineStreamEnded(message: String) : IllegalStateException(message)

/**
 * 줄 하나를 **한 번의 쓰기로** 보낸다 — `write` 한 번은 writer의 락을 통째로 쥐므로, 배수가 따로 보내는 `terminate`와
 * 다음 명령의 쿼리가 줄 중간에서 섞이지 않는다(refactor backlog #17).
 */
private fun BufferedWriter.writeLine(line: String) {
    write(line + "\n")
    flush()
}
