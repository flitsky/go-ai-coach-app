package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.concurrency.sharedLock
import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationTarget
import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.analysisFingerprint
import com.worksoc.goaicoach.shared.domain.describe
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.DeadStonesResult
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * 오퍼레이션 단위 직렬화(refactor backlog #15) — [LocalEngineSessionClient]의 공개 suspend 메서드 하나가 엔진에
 * 보내는 명령 묶음은 다른 오퍼레이션의 명령과 섞이지 않는다.
 *
 * 예전에는 섞였다. busy 게이트는 5계층의 check-then-act이고 세대로 걸러 세므로(무르기 뒤의 낡은 작업은 안 보인다),
 * 게이트를 지난 두 오퍼레이션은 IO 스레드 둘에서 실제로 겹쳐 돌았다. 그리고 `syncToGameState` 하나가 이미
 * `newGame` + 수마다 `playMove`인 N+1개의 독립 명령이라, 그 사이에 다른 오퍼레이션의 명령이 끼었다.
 *
 * 전부 `runBlocking`의 **한 스레드 이벤트 루프**에서 돈다 — 가짜 엔진이 정해 둔 명령에서 멈추면(park) 그동안 다른
 * 코루틴이 무엇을 보내는지가 결정적으로 보인다(`:shared`에는 coroutines-test가 없다). 기다려서는 안 되는 호출은
 * [withTimeoutOrNull]로 감싼다 — 잘못 기다리면 매달리지 않고 `null`로 빨개진다.
 */
class LocalEngineSessionClientSerializationTest {
    private val engine = ParkingCoreApi()
    private val client = LocalEngineSessionClient(coreApi = engine, currentSessionGeneration = { 0L })

    /**
     * T1 — 두 곳에서 동시에 건 재동기화(무르기 뒤·계가 규칙 변경 뒤 같은)가 명령 묶음 둘로 나란히 간다.
     * 예전: 앞의 것이 수순을 다시 두는 도중에 뒤의 것의 `newGame`이 끼어들어, 엔진 판은 두 판이 섞인 것이 됐다.
     */
    @Test
    fun twoGraphResyncsFromTwoLaunchersRunAsContiguousCommandBlocks() = runBlocking {
        engine.parkAt("play White C3")
        val first = async { runCatching { client.syncAndEstimateGraphScore(TwoMoves, Profile) } }
        engine.awaitParked()
        val second = async { runCatching { client.syncAndEstimateGraphScore(BlackAtD4, Profile) } }
        settle()
        engine.release()
        first.await().getOrThrow()
        second.await().getOrThrow()

        assertEquals(TwoMovesResync + BlackAtD4Resync, engine.calls)
    }

    /**
     * T2 — AI가 생각하는 동안 형세 추정·추천 수 분석은 **기다리지 않고 곧바로 포기한다**(기존 "잠시 뒤" 흐름으로
     * 넘어가도록). 포기한 호출은 엔진에 아무것도 보내지 않는다. 예전: 둘 다 그 자리에서 AI 판을 자기 국면으로 덮어썼다.
     */
    @Test
    fun analysisAndScoreEstimateGiveUpAtOnceInsteadOfWaitingForARunningAiTurn() = runBlocking {
        engine.parkAt("analyze")
        val turn = async { runCatching { client.runAutoAiTurn(EmptyBoard, PlayLevelSetting(), Profile, SearchTimeSettings(), EngineSearchMode.GtpStatefulFast, isolateSearchCache = false) } }
        engine.awaitParked()
        val callsWhileTheTurnThinks = engine.calls

        val analysis = withTimeoutOrNull(RefusalBudgetMillis) { runCatching { client.analyzePosition(BlackAtD4, SmallLimit) } }
        val estimate = withTimeoutOrNull(RefusalBudgetMillis) { runCatching { client.estimateScoreForState(BlackAtD4, Profile, syncFirst = true) } }
        val callsAfterTheRefusals = engine.calls
        engine.release()
        turn.await().getOrThrow()

        assertGaveUpAtOnce(analysis, "analyzePosition")
        assertGaveUpAtOnce(estimate, "estimateScoreForState")
        assertEquals(callsWhileTheTurnThinks, callsAfterTheRefusals, "a refused call must send nothing to the engine")
        assertEquals(AiTurnGtpCommands, engine.calls)
    }

    /**
     * T3 — 기다리는 오퍼레이션은 앞의 것이 끝나기 전에는 한 명령도 보내지 않고, 앞의 것이 **취소되면 곧바로** 돈다
     * (무르기가 AI 차례를 취소하면 재동기화가 곧바로 이어지듯이). 취소된 쪽은 그 뒤로 아무것도 보내지 않는다.
     */
    @Test
    fun aWaitingOperationStartsOnlyAfterTheHolderAndAtOnceWhenTheHolderIsCancelled() = runBlocking {
        engine.parkAt("estimate")
        val holder = async { client.syncAndEstimateGraphScore(TwoMoves, Profile) }
        engine.awaitParked()
        val waiter = async { runCatching { client.syncAndEstimateGraphScore(BlackAtD4, Profile) } }
        settle()
        val callsWhileHolding = engine.calls

        holder.cancel()
        val waited = withTimeoutOrNull(RefusalBudgetMillis) { waiter.await() }

        assertEquals(TwoMovesResync, callsWhileHolding, "the waiter must not send anything while the holder runs")
        assertNotNull(waited, "the waiter must run as soon as the holder is cancelled").getOrThrow()
        assertTrue(holder.isCancelled)
        assertEquals(TwoMovesResync + BlackAtD4Resync, engine.calls, "the cancelled holder sends nothing more")
    }

    /** T3b — 기다리다 취소된 오퍼레이션은 끝내 돌지 않고, 그 뒤에 줄 선 오퍼레이션은 그래도 돈다. */
    @Test
    fun anOperationCancelledWhileWaitingNeverRunsAndTheNextOneStillDoes() = runBlocking {
        engine.parkAt("estimate")
        val holder = async { runCatching { client.syncAndEstimateGraphScore(TwoMoves, Profile) } }
        engine.awaitParked()
        val abandoned = async { client.syncAndEstimateGraphScore(BlackAtC7, Profile) }
        val next = async { runCatching { client.syncAndEstimateGraphScore(BlackAtD4, Profile) } }
        settle()
        abandoned.cancel()
        settle()
        engine.release()
        holder.await().getOrThrow()
        withTimeout(RefusalBudgetMillis) { next.await() }.getOrThrow()

        assertEquals(TwoMovesResync + BlackAtD4Resync, engine.calls)
    }

    /**
     * T8(회귀 그물) — AI 차례 안의 분석은 **그 차례가 쥔 채로** 돈다. 공개 `analyzePosition`을 다시 부르면 자기 자신에게
     * 막혀 포기하고, AI는 조용히 `genMove`로 떨어진다(오늘도 초록 — 그렇게 되지 않게 지킨다). JSON 경로가 AI의 기본이다.
     */
    @Test
    fun anAiTurnAnalyzesUnderItsOwnHoldWithoutGivingUpOrFallingBackToGenMove() = runBlocking {
        val result = withTimeout(RefusalBudgetMillis) {
            client.runAutoAiTurn(EmptyBoard, PlayLevelSetting(), Profile, SearchTimeSettings(), EngineSearchMode.JsonPositionAnalysis, isolateSearchCache = false)
        }

        assertEquals(AiTurnGtpCommands, engine.calls)
        assertEquals(Move.Play(StoneColor.Black, E5), result.turnOutcome.gameState.moves.last())
    }

    /**
     * 오퍼레이션 안에서 같은 클라이언트를 다시 부르면 **크게 실패한다** — 포기(추천 수는 조용히 "잠시 뒤"로, AI는
     * 조용히 `genMove`로)하거나 자기 자신을 기다려 멈추지 않는다. 락이 재진입을 모르기 때문이다.
     */
    @Test
    fun callingTheClientAgainFromInsideAnOperationFailsLoudlyInsteadOfGivingUpOrWaiting() = runBlocking {
        var inner: Result<Any>? = null
        engine.onAnalyze = {
            engine.onAnalyze = {}
            inner = withTimeoutOrNull(RefusalBudgetMillis) { runCatching { client.analyzePosition(BlackAtD4, SmallLimit) } }
        }

        withTimeout(RefusalBudgetMillis) { client.analyzePosition(TwoMoves, SmallLimit) }

        val failure = assertNotNull(inner, "the re-entrant call must not wait for its own operation").exceptionOrNull()
        assertTrue(failure is IllegalStateException, "a re-entrant call must fail loudly: $failure")
    }

    /**
     * T11 — 기기 벤치마크는 끝의 복원 동기화까지 엔진을 쥔다. 그동안 분석은 포기하고, 재동기화는 복원 **뒤에** 돈다.
     * 예전: 재동기화가 벤치마크 판 위에 끼었고, 벤치마크의 복원이 그 뒤에 판을 다시 덮었다.
     */
    @Test
    fun aBenchmarkHoldsTheEngineThroughItsRestoreSyncAndAnalysisGivesUpMeanwhile() = runBlocking {
        engine.analysisCandidates = emptyList()
        engine.parkAt("analyze")
        val benchmark = async { runCatching { client.runStartupBenchmark(restoreState = BlackAtD4, nowMillis = 1L, onProgress = {}) } }
        engine.awaitParked()
        val analysis = withTimeoutOrNull(RefusalBudgetMillis) { runCatching { client.analyzePosition(TwoMoves, SmallLimit) } }
        val resync = async { runCatching { client.syncAndEstimateGraphScore(TwoMoves, Profile) } }
        settle()
        val callsWhileBenchmarking = engine.calls
        engine.release()
        benchmark.await().getOrThrow()
        withTimeout(RefusalBudgetMillis) { resync.await() }.getOrThrow()

        assertGaveUpAtOnce(analysis, "analyzePosition during the benchmark")
        assertEquals(listOf("newGame", "analyze"), callsWhileBenchmarking)
        assertEquals(listOf("newGame", "play Black D4") + TwoMovesResync, engine.calls.takeLast(6))
    }

    /** T12 — 대국 후 캐시 최적화는 목표마다 따로 쥔다. 기다리던 AI 차례는 목표와 목표 **사이에** 돈다. */
    @Test
    fun anAiTurnWaitingOnACacheOptimizationRunsBetweenItsTargets() = runBlocking {
        engine.parkAt("analyze")
        val optimization = async { runCatching { client.optimizePositionAnalysisCache(TwoTargetPlan) } }
        engine.awaitParked()
        val turn = async { runCatching { client.runAutoAiTurn(EmptyBoard, PlayLevelSetting(), Profile, SearchTimeSettings(), EngineSearchMode.GtpStatefulFast, isolateSearchCache = false) } }
        settle()
        val callsWhileTheFirstTargetRuns = engine.calls
        engine.release()
        optimization.await().getOrThrow()
        turn.await().getOrThrow()

        assertEquals(BlackAtD4Analysis, callsWhileTheFirstTargetRuns)
        assertEquals(BlackAtD4Analysis + AiTurnGtpCommands + TwoMovesAnalysis, engine.calls)
    }

    // ── 도우미 ─────────────────────────────────────────────────────────────────

    /** 한 스레드 이벤트 루프에서 다른 코루틴이 멈출 수 있는 데까지 돌게 둔다. */
    private suspend fun settle() {
        repeat(50) { yield() }
    }

    /** 기다리지 않고(시간 안에) 끝났고, 취소가 아닌 실패로 끝났다 — 결과를 내지 않았다. */
    private fun assertGaveUpAtOnce(outcome: Result<Any>?, what: String) {
        assertNotNull(outcome, "$what must give up at once instead of waiting for the running operation")
        val failure = outcome.exceptionOrNull()
        assertTrue(failure != null && failure !is CancellationException, "$what must give up (not run, not be cancelled): $outcome")
    }

    private companion object {
        /** 곧바로 포기해야 하는 호출이 이 안에 돌아오지 않으면 기다린 것이다. 한 스레드라 실제로는 0에 가깝다. */
        const val RefusalBudgetMillis = 1_000L

        val Profile = EngineProfile()
        val SmallLimit = AnalysisLimit(visits = 16, timeMillis = 500L, candidateCount = 3)

        val E5 = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val EmptyBoard: GameState = GameState.empty()
        val TwoMoves: GameState = EmptyBoard
            .play(Move.Play(StoneColor.Black, E5))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C3", BoardSize.Nine)))
        val BlackAtD4: GameState = EmptyBoard.play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("D4", BoardSize.Nine)))
        val BlackAtC7: GameState = EmptyBoard.play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("C7", BoardSize.Nine)))

        val TwoMovesResync = listOf("newGame", "play Black E5", "play White C3", "estimate")
        val BlackAtD4Resync = listOf("newGame", "play Black D4", "estimate")
        val TwoMovesAnalysis = listOf("newGame", "play Black E5", "play White C3", "analyze")
        val BlackAtD4Analysis = listOf("newGame", "play Black D4", "analyze")

        /** 빈 판의 흑 AI 차례 한 번 — 설정, 동기화, (안쪽 분석의) 동기화 + 분석, 착수, 형세 추정. */
        val AiTurnGtpCommands = listOf("configure", "newGame", "newGame", "analyze", "play Black E5", "estimate")

        val TwoTargetPlan = PositionAnalysisCacheOptimizationPlan(
            gameFingerprint = TwoMoves.analysisFingerprint(),
            finalState = TwoMoves,
            finalMoveCount = TwoMoves.moves.size,
            targets = listOf(BlackAtD4, TwoMoves).map { state ->
                val limit = AnalysisLimit(visits = 32, timeMillis = 2_000L, candidateCount = 8, includePolicy = true)
                PositionAnalysisCacheOptimizationTarget(
                    state = state,
                    moveNumber = state.moves.size,
                    levelLabel = "초급 7단계",
                    cacheLimit = limit,
                    executionLimit = limit.copy(timeMillis = null),
                )
            },
        )
    }
}

/**
 * 받은 명령을 순서대로 적는 가짜 엔진. [parkAt]으로 고른 명령 하나에서 **한 번** 멈춘다(그 명령은 적힌 뒤 멈춘다).
 * 멈춤은 취소된다 — 막힌 파이프 읽기의 취소 뒤 동작(#14의 배수·폐기)은 `engine-android`의 진짜 어댑터 테스트가 맡는다.
 */
private class ParkingCoreApi : EngineCoreApi {
    private val lock = sharedLock()
    private val recorded = mutableListOf<String>()
    private var parkTarget: String? = null
    private val parked = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()

    /** `analyze`가 돌려주는 후보. 기본은 흑 E5 하나 — 빈 판의 흑 AI가 그 수를 둔다. */
    var analysisCandidates: List<CandidateMove> = listOf(
        CandidateMove(move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)), pointLoss = 0.0, visits = 16, engineOrder = 0),
    )

    /** `analyze`마다 적은 뒤에 부른다 — 오퍼레이션 안에서 같은 클라이언트를 다시 부르는 코드를 흉내 낸다. */
    var onAnalyze: suspend () -> Unit = {}

    val calls: List<String>
        get() = lock.withLock { recorded.toList() }

    fun parkAt(command: String) {
        parkTarget = command
    }

    suspend fun awaitParked() {
        withTimeout(2_000L) { parked.await() }
    }

    /** 멈춘 명령을 풀어 준다. (반환형을 적는 것은 `TestAnnotationContractTest`의 모양 검사 때문이다 — 인자 없는 `Unit` 함수는 테스트로 읽힌다.) */
    fun release(): Boolean = released.complete(Unit)

    private suspend fun record(command: String) {
        val park = lock.withLock {
            recorded += command
            (command == parkTarget).also { matched -> if (matched) parkTarget = null }
        }
        if (!park) return
        parked.complete(Unit)
        released.await()
    }

    override suspend fun initialize(profile: EngineProfile): EngineStatus {
        record("initialize")
        return EngineStatus.ready("initialized")
    }

    override suspend fun configure(profile: EngineProfile): EngineStatus {
        record("configure")
        return EngineStatus.ready("configured")
    }

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStatus {
        record("newGame")
        return EngineStatus.ready("new game")
    }

    override suspend fun syncStaticPosition(state: GameState): EngineStatus {
        record("syncStaticPosition")
        return EngineStatus.ready("static position synced")
    }

    override suspend fun playMove(move: Move): EngineStatus {
        record("play ${move.describe(BoardSize.Nine)}")
        return EngineStatus.ready("played")
    }

    override suspend fun genMove(player: StoneColor): MoveResult {
        record("genMove ${player.label}")
        return MoveResult(status = EngineStatus.ready("generated"), move = Move.Pass(player), summary = "generated")
    }

    override suspend fun undoMove(): EngineStatus {
        record("undo")
        return EngineStatus.ready("undone")
    }

    override suspend fun clearSearchCache(): EngineStatus {
        record("clearSearchCache")
        return EngineStatus.ready("cache cleared")
    }

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        record("analyze")
        onAnalyze()
        return AnalysisResult(
            status = EngineStatus.ready("analyzed"),
            candidates = analysisCandidates.map { candidate -> candidate.copy(visits = limit.visits) },
            summary = "analyzed",
            rootVisits = limit.visits,
            elapsedMillis = 10L,
        )
    }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate {
        record("estimate")
        return ScoreEstimate(status = EngineStatus.ready("estimated"), whiteScoreLead = 1.5, whiteWinRate = 0.55, summary = "estimated")
    }

    override suspend fun deadStones(): DeadStonesResult {
        record("deadStones")
        return DeadStonesResult(status = EngineStatus.ready("dead stones"), coordinates = emptyList(), summary = "dead stones")
    }

    override suspend fun scoreFinal(): FinalScoreResult {
        record("scoreFinal")
        return FinalScoreResult(status = EngineStatus.ready("final"), rawScore = "B+0.5", summary = "final")
    }

    override suspend fun stop(): EngineStatus {
        record("stop")
        return EngineStatus.stopped("stopped")
    }

    override fun forceReset() {
        lock.withLock { recorded += "forceReset" }
    }
}
