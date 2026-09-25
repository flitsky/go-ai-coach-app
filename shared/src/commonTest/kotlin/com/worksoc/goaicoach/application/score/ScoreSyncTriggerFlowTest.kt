package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.diagnostic.engineOperationSlowDiagnosticEvent
import com.worksoc.goaicoach.application.diagnostic.engineOperationTimeoutDiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationApplyPlan
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.buildEngineOperationApplyPlan
import com.worksoc.goaicoach.shared.policy.engineOperationRequest
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import com.worksoc.goaicoach.testsupport.RecordingDiagnosticEventLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 세 트리거(계가 규칙 변경·무르기 후·복원 대국)의 score sync 흐름을 **공개 진입점으로만** 재는 특성 테스트
 * (refactor backlog #39 — `ScoreSyncRunner` 3중복 제거의 전후 대조 기준선).
 *
 * 세 진입점 `runScoringRuleSyncApplication`·`runPostUndoScoreSyncApplication`·`runRestoredGameSyncApplication`
 * 은 같은 흐름을 세 벌 갖고 있었다. 이 파일은 그 흐름이 **밖으로 내는 모든 것**을 한 줄짜리 사건으로
 * 순서대로 적고, 세 벌을 하나로 합친 뒤에도 같은 사건이 같은 순서로 나오는지 본다:
 *
 * - 엔진 작업 요청(`EngineOperationRequest`) — 종류·세대·수순·지문·시간 정책·폴백 정책까지 전부
 * - 현재 국면·세대를 **언제** 읽는가 — 엔진 작업 안에서, 엔진 호출 직전에, 국면 먼저
 * - 어느 엔진 호출을 어떤 인자로 부르는가(`syncAndEstimateGraphScore` vs `configureSyncAndEstimateGraphScore`)
 * - 적용 계획(성공 표시 계획·실패 메시지·버림 사유) — 값 전체
 * - 진단 이벤트(시간 초과·지연) — 이벤트 전체
 * - 후속 분석 요청을 **언제** 내는가 — 트리거마다 다르다(아래)
 *
 * ## 세 트리거가 일부러 다른 곳
 * 이 테스트는 차이를 없애지 않고 **고정한다.** 전부 합치기 전 코드에 있던 그대로다:
 * - 표시 계획: 계가 규칙·무르기 후는 `syncAndEstimateGraphScore` + 이후 수 스냅샷을 잘라 냄(`trimAfterMove`),
 *   복원 대국은 `configureSyncAndEstimateGraphScore` + 고정 메시지 + 자르지 않음.
 * - 후속 분석 요청 시점: 계가 규칙·복원 대국은 **작업 블록 안에서**(엔진 작업이 아직 추적 중일 때),
 *   무르기 후는 `runEngineOperation`이 **돌아온 뒤에**. 무르기 후의 `runEngineOperation`은 블록을 기다리는
 *   `suspend` 함수(`runTracked`)이고 나머지 둘은 띄우고 바로 돌아오는 함수(`launchTracked`)다.
 * - 실패 메시지 기본값 셋과 무르기 후의 성공 메시지 기본값.
 */
class ScoreSyncTriggerFlowTest {

    @Test
    fun successRunsOneOperationAndFollowsUpInEachTriggersOwnOrder() {
        Trigger.entries.forEach { trigger ->
            val scenario = Scenario(trigger).also { it.run() }

            assertEquals(listOf(expectedOperation(trigger)), scenario.launched, "$trigger operation")
            assertEquals(
                expectedTrace(trigger, planLabel = "ApplySuccess", followUp = true),
                scenario.trace,
                "$trigger trace",
            )
            assertEquals(
                listOf<ScoreSyncCompletionApplyPlan>(ScoreSyncCompletionApplyPlan.ApplySuccess(expectedSuccessDisplay(trigger), STATE)),
                scenario.applied,
                "$trigger apply plan",
            )
            assertEquals(listOf(STATE), scenario.followUps, "$trigger follow-up")
            assertEquals(emptyList<DiagnosticEvent>(), scenario.diagnostics.events, "$trigger diagnostics")
        }
    }

    @Test
    fun engineFailureAppliesTheErrorMessageAndStillFollowsUp() {
        Trigger.entries.forEach { trigger ->
            val scenario = Scenario(trigger, outcome = EngineOutcome.Failure("engine exploded")).also { it.run() }

            assertEquals(listOf(expectedOperation(trigger)), scenario.launched, "$trigger operation")
            assertEquals(
                expectedTrace(trigger, planLabel = "ApplyFailure", followUp = true),
                scenario.trace,
                "$trigger trace",
            )
            assertEquals(
                listOf<ScoreSyncCompletionApplyPlan>(ScoreSyncCompletionApplyPlan.ApplyFailure("engine exploded", STATE)),
                scenario.applied,
                "$trigger apply plan",
            )
            assertEquals(listOf(STATE), scenario.followUps, "$trigger follow-up")
            assertEquals(emptyList<DiagnosticEvent>(), scenario.diagnostics.events, "$trigger diagnostics")
        }
    }

    @Test
    fun engineFailureWithoutMessageFallsBackToTheTriggersDefaultMessage() {
        Trigger.entries.forEach { trigger ->
            val scenario = Scenario(trigger, outcome = EngineOutcome.Failure(null)).also { it.run() }

            assertEquals(
                listOf<ScoreSyncCompletionApplyPlan>(ScoreSyncCompletionApplyPlan.ApplyFailure(defaultFallbackMessage(trigger), STATE)),
                scenario.applied,
                "$trigger apply plan",
            )
            assertEquals(listOf(STATE), scenario.followUps, "$trigger follow-up")
        }
    }

    @Test
    fun staleResultIsDiscardedAfterTheEngineRanAndNeverFollowsUp() {
        val staleCases = listOf(
            MOVED_STATE to GENERATION,
            STATE to GENERATION + 1,
        )
        Trigger.entries.forEach { trigger ->
            staleCases.forEach { (currentState, currentGeneration) ->
                val scenario = Scenario(
                    trigger,
                    currentState = currentState,
                    currentGeneration = currentGeneration,
                ).also { it.run() }
                val expectedDiscard = buildEngineOperationApplyPlan(
                    request = expectedOperation(trigger),
                    currentState = currentState,
                    currentSessionGeneration = currentGeneration,
                )

                assertTrue(expectedDiscard is EngineOperationApplyPlan.Discard, "$trigger precondition")
                assertEquals(
                    expectedTrace(trigger, planLabel = "Discard", followUp = false),
                    scenario.trace,
                    "$trigger trace (m=${currentState.moves.size}, g=$currentGeneration)",
                )
                assertEquals(
                    listOf<ScoreSyncCompletionApplyPlan>(ScoreSyncCompletionApplyPlan.Discard(expectedDiscard.discard)),
                    scenario.applied,
                    "$trigger apply plan (m=${currentState.moves.size}, g=$currentGeneration)",
                )
                assertEquals(emptyList<GameState>(), scenario.followUps, "$trigger follow-up")
            }
        }
    }

    @Test
    fun engineTimeoutIsRecordedAsTheOperationsTimeoutDiagnosticAndAppliedAsFailure() {
        Trigger.entries.forEach { trigger ->
            val scenario = Scenario(trigger, outcome = EngineOutcome.Timeout).also { it.run() }
            val thrown = assertNotNull(scenario.engine.thrown, "$trigger engine did not time out")

            assertEquals(
                listOf(assertNotNull(engineOperationTimeoutDiagnosticEvent(expectedOperation(trigger)))),
                scenario.diagnostics.events,
                "$trigger diagnostics",
            )
            assertEquals(
                listOf<ScoreSyncCompletionApplyPlan>(
                    ScoreSyncCompletionApplyPlan.ApplyFailure(
                        engineMessage = thrown.message ?: defaultFallbackMessage(trigger),
                        followUpAnalysisState = STATE,
                    ),
                ),
                scenario.applied,
                "$trigger apply plan",
            )
            assertEquals(
                expectedTrace(trigger, planLabel = "ApplyFailure", followUp = true),
                scenario.trace,
                "$trigger trace",
            )
        }
    }

    @Test
    fun slowEngineIsRecordedAsASlowDiagnosticCarryingTheFlowsOperation() {
        val tightTimeout = EngineTimeoutPolicy(timeoutMillis = 1L, label = "trace-tight")
        Trigger.entries.forEach { trigger ->
            val scenario = Scenario(
                trigger,
                outcome = EngineOutcome.Slow(delayMillis = 30L),
                timeoutPolicy = tightTimeout,
            ).also { it.run() }
            val expectedOperation = expectedOperation(trigger, tightTimeout)
            val recorded = scenario.diagnostics.events.single()
            val expectedShape = assertNotNull(
                engineOperationSlowDiagnosticEvent(
                    request = expectedOperation,
                    elapsedMillis = 2L,
                    thresholdMillis = 1L,
                ),
            )

            assertEquals(listOf(expectedOperation), scenario.launched, "$trigger operation")
            assertEquals(
                expectedShape.copy(context = expectedShape.context - "elapsedMillis"),
                recorded.copy(context = recorded.context - "elapsedMillis"),
                "$trigger slow diagnostic",
            )
            assertTrue(recorded.context.getValue("elapsedMillis").toLong() > 1L, "$trigger elapsed")
            assertEquals(
                listOf<ScoreSyncCompletionApplyPlan>(ScoreSyncCompletionApplyPlan.ApplySuccess(expectedSuccessDisplay(trigger), STATE)),
                scenario.applied,
                "$trigger apply plan",
            )
        }
    }

    /**
     * 작업을 띄우기만 하고 돌아오는 `runEngineOperation`(프로덕션의 `launchTracked`)을 흉내 낸다.
     * 블록이 돌기 전에는 아무것도 읽거나 부르지 않아야 하고, 블록이 돌 때 나머지가 전부 일어난다.
     *
     * ⚠️ 무르기 후만 다르다 — 후속 분석 요청이 블록 **밖**, `runEngineOperation`이 돌아온 뒤에 있어서
     * 블록을 기다리지 않는 `runEngineOperation`을 주면 후속 분석은 **끝내 요청되지 않는다.**
     * 프로덕션의 무르기 후 경로는 블록을 기다리는 `runTracked`만 쓰므로 이 경우가 생기지 않는다.
     * 합친 뒤에도 이 성질이 그대로인지 여기서 고정한다.
     */
    @Test
    fun deferredLaunchRunsNothingUntilTheOperationBlockRuns() {
        Trigger.entries.forEach { trigger ->
            val scenario = Scenario(trigger, deferLaunch = true).also { it.run() }
            val operationId = expectedOperation(trigger).operationId

            assertEquals(listOf("operation.start $operationId"), scenario.trace, "$trigger trace before block")
            assertEquals(emptyList<ScoreSyncCompletionApplyPlan>(), scenario.applied, "$trigger applied before block")
            assertEquals(emptyList<GameState>(), scenario.followUps, "$trigger follow-up before block")

            val block = assertNotNull(scenario.deferredBlock, "$trigger block was not handed over")
            runBlocking { block() }

            val followsUpInsideBlock = trigger != Trigger.PostUndo
            assertEquals(
                listOf(
                    "operation.start $operationId",
                    "work.start",
                    "read currentState",
                    "read currentSessionGeneration",
                    expectedEngineCall(trigger),
                    "work.end ApplySuccess",
                    "apply ApplySuccess",
                ) + if (followsUpInsideBlock) listOf("followUp m=${STATE.moves.size}") else emptyList(),
                scenario.trace,
                "$trigger trace after block",
            )
            assertEquals(
                if (followsUpInsideBlock) listOf(STATE) else emptyList<GameState>(),
                scenario.followUps,
                "$trigger follow-up after block",
            )
        }
    }

    @Test
    fun defaultEngineWorkRunsTheSameFlow() {
        Trigger.entries.forEach { trigger ->
            val scenario = Scenario(trigger, useDefaultEngineWork = true).also { it.run() }

            assertEquals(
                expectedTrace(trigger, planLabel = "ApplySuccess", followUp = true)
                    .filterNot { line -> line.startsWith("work.") },
                scenario.trace,
                "$trigger trace",
            )
            assertEquals(
                listOf<ScoreSyncCompletionApplyPlan>(ScoreSyncCompletionApplyPlan.ApplySuccess(expectedSuccessDisplay(trigger), STATE)),
                scenario.applied,
                "$trigger apply plan",
            )
            assertEquals(listOf(STATE), scenario.followUps, "$trigger follow-up")
        }
    }

    /** 위 단언들이 `trimAfterMove` 차이를 실제로 볼 수 있는 입력인지 — 아니면 표시 계획 대조가 공허하다. */
    @Test
    fun fixtureMakesTheTrimDifferenceObservable() {
        assertNotEquals(
            buildEngineEstimateDisplayPlan(STATE, ESTIMATE, PREVIOUS, "m", trimAfterMove = true),
            buildEngineEstimateDisplayPlan(STATE, ESTIMATE, PREVIOUS, "m", trimAfterMove = false),
        )
    }

    private enum class Trigger(val kind: EngineOperationKind) {
        ScoringRule(EngineOperationKind.ScoringRuleSync),
        PostUndo(EngineOperationKind.PostUndoSync),
        RestoredGame(EngineOperationKind.RestoredGameSync),
    }

    private sealed interface EngineOutcome {
        data object Success : EngineOutcome
        data class Failure(val message: String?) : EngineOutcome
        data object Timeout : EngineOutcome
        data class Slow(val delayMillis: Long) : EngineOutcome
    }

    private class TracingScoreEngine(
        private val trace: MutableList<String>,
        private val outcome: EngineOutcome,
    ) : FakeEngineSessionClient() {
        var thrown: Throwable? = null
            private set

        override suspend fun syncAndEstimateGraphScore(
            state: GameState,
            profile: EngineProfile,
        ): ScoreEstimate {
            trace += "engine.syncAndEstimate m=${state.moves.size} profile=${profile.name}"
            return respond()
        }

        override suspend fun configureSyncAndEstimateGraphScore(
            state: GameState,
            profile: EngineProfile,
        ): ScoreEstimate {
            trace += "engine.configureSyncAndEstimate m=${state.moves.size} profile=${profile.name}"
            return respond()
        }

        private suspend fun respond(): ScoreEstimate {
            when (outcome) {
                EngineOutcome.Success -> Unit
                is EngineOutcome.Failure -> throw IllegalStateException(outcome.message).also { thrown = it }
                EngineOutcome.Timeout ->
                    try {
                        withTimeout(1L) { delay(10_000L) }
                    } catch (error: Throwable) {
                        thrown = error
                        throw error
                    }
                is EngineOutcome.Slow -> delay(outcome.delayMillis)
            }
            return ESTIMATE
        }
    }

    private class Scenario(
        val trigger: Trigger,
        outcome: EngineOutcome = EngineOutcome.Success,
        private val currentState: GameState = STATE,
        private val currentGeneration: Long = GENERATION,
        private val timeoutPolicy: EngineTimeoutPolicy = GENEROUS_TIMEOUT,
        private val deferLaunch: Boolean = false,
        private val useDefaultEngineWork: Boolean = false,
    ) {
        val trace = mutableListOf<String>()
        val diagnostics = RecordingDiagnosticEventLog()
        val engine = TracingScoreEngine(trace, outcome)
        val launched = mutableListOf<EngineOperationRequest>()
        val applied = mutableListOf<ScoreSyncCompletionApplyPlan>()
        val followUps = mutableListOf<GameState>()
        var deferredBlock: (suspend () -> Unit)? = null
            private set

        private val readCurrentState: () -> GameState = {
            trace += "read currentState"
            currentState
        }
        private val readCurrentGeneration: () -> Long = {
            trace += "read currentSessionGeneration"
            currentGeneration
        }
        private val tracingEngineWork:
            suspend (suspend () -> ScoreSyncCompletionApplyPlan) -> ScoreSyncCompletionApplyPlan = { block ->
                trace += "work.start"
                block().also { plan -> trace += "work.end ${plan.label()}" }
            }

        /** 프로덕션 `GameSessionDisplayStateApplier.applyScoreSyncCompletion`과 같은 반환 규칙. */
        private val applyCompletion: (ScoreSyncCompletionApplyPlan) -> GameState? = { plan ->
            applied += plan
            trace += "apply ${plan.label()}"
            when (plan) {
                is ScoreSyncCompletionApplyPlan.ApplySuccess -> plan.followUpAnalysisState
                is ScoreSyncCompletionApplyPlan.ApplyFailure -> plan.followUpAnalysisState
                is ScoreSyncCompletionApplyPlan.Discard -> null
            }
        }
        private val requestFollowUpAnalysis: (GameState) -> Unit = { state ->
            followUps += state
            trace += "followUp m=${state.moves.size}"
        }

        /** 띄우고 돌아오는 쪽(`launchTracked`) — 계가 규칙·복원 대국. */
        private val launchOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit = { operation, block ->
            launched += operation
            trace += "operation.start ${operation.operationId}"
            if (deferLaunch) {
                deferredBlock = block
            } else {
                runBlocking { block() }
                trace += "operation.end"
            }
        }

        /** 블록을 기다리는 쪽(`runTracked`) — 무르기 후. */
        private val runOperation: suspend (EngineOperationRequest, suspend () -> Unit) -> Unit = { operation, block ->
            launched += operation
            trace += "operation.start ${operation.operationId}"
            if (deferLaunch) {
                deferredBlock = block
            } else {
                block()
                trace += "operation.end"
            }
        }

        fun run() = runBlocking {
            when (trigger) {
                Trigger.ScoringRule -> {
                    val request = ScoringRuleSyncRunRequest(
                        engineClient = engine,
                        state = STATE,
                        profile = PROFILE,
                        previousSnapshots = PREVIOUS,
                        sessionGeneration = GENERATION,
                        timeoutPolicy = timeoutPolicy,
                        diagnosticEventLog = diagnostics,
                        engineMessage = SCORING_RULE_MESSAGE,
                        currentState = readCurrentState,
                        currentSessionGeneration = readCurrentGeneration,
                        runEngineOperation = launchOperation,
                        applyCompletion = applyCompletion,
                        requestFollowUpAnalysis = requestFollowUpAnalysis,
                    )
                    runScoringRuleSyncApplication(
                        if (useDefaultEngineWork) request else request.copy(runEngineWork = tracingEngineWork),
                    )
                }

                Trigger.PostUndo -> {
                    val request = PostUndoScoreSyncRunRequest(
                        engineClient = engine,
                        state = STATE,
                        profile = PROFILE,
                        previousSnapshots = PREVIOUS,
                        sessionGeneration = GENERATION,
                        timeoutPolicy = timeoutPolicy,
                        diagnosticEventLog = diagnostics,
                        currentState = readCurrentState,
                        currentSessionGeneration = readCurrentGeneration,
                        runEngineOperation = runOperation,
                        applyCompletion = applyCompletion,
                        requestFollowUpAnalysis = requestFollowUpAnalysis,
                    )
                    runPostUndoScoreSyncApplication(
                        if (useDefaultEngineWork) request else request.copy(runEngineWork = tracingEngineWork),
                    )
                }

                Trigger.RestoredGame -> {
                    val request = RestoredGameSyncRunRequest(
                        engineClient = engine,
                        state = STATE,
                        profile = PROFILE,
                        sessionGeneration = GENERATION,
                        timeoutPolicy = timeoutPolicy,
                        diagnosticEventLog = diagnostics,
                        currentState = readCurrentState,
                        currentSessionGeneration = readCurrentGeneration,
                        runEngineOperation = launchOperation,
                        applyCompletion = applyCompletion,
                        requestFollowUpAnalysis = requestFollowUpAnalysis,
                        scoreSnapshots = PREVIOUS,
                    )
                    runRestoredGameSyncApplication(
                        if (useDefaultEngineWork) request else request.copy(runEngineWork = tracingEngineWork),
                    )
                }
            }
        }
    }

    private companion object {
        val STATE: GameState = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val MOVED_STATE: GameState = STATE
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C3", BoardSize.Nine)))
        const val GENERATION = 7L
        val PROFILE = EngineProfile(name = "trace-profile")
        val GENEROUS_TIMEOUT = EngineTimeoutPolicy(timeoutMillis = 60_000L, label = "trace-cap")
        const val SCORING_RULE_MESSAGE = "Scoring rule changed to trace; engine rules synchronized."

        /** 이후 수(3수째) 스냅샷을 하나 넣어 `trimAfterMove`가 결과를 바꾸게 한다. */
        val PREVIOUS = listOf(
            ScoreSnapshot(moveNumber = 0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 3, whiteScoreLead = 2.5, source = ScoreSnapshotSource.EngineEstimate),
        )
        val ESTIMATE = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 0.5,
            whiteWinRate = 0.5,
            summary = "estimate",
        )

        fun expectedOperation(
            trigger: Trigger,
            timeoutPolicy: EngineTimeoutPolicy = GENEROUS_TIMEOUT,
        ): EngineOperationRequest =
            engineOperationRequest(
                kind = trigger.kind,
                state = STATE,
                sessionGeneration = GENERATION,
                timeoutPolicy = timeoutPolicy,
                fallbackPolicy = EngineFallbackPolicy.LocalRules,
            )

        fun expectedEngineCall(trigger: Trigger): String =
            when (trigger) {
                Trigger.ScoringRule, Trigger.PostUndo ->
                    "engine.syncAndEstimate m=${STATE.moves.size} profile=${PROFILE.name}"
                Trigger.RestoredGame ->
                    "engine.configureSyncAndEstimate m=${STATE.moves.size} profile=${PROFILE.name}"
            }

        fun expectedSuccessDisplay(trigger: Trigger) =
            when (trigger) {
                Trigger.ScoringRule ->
                    buildEngineEstimateDisplayPlan(STATE, ESTIMATE, PREVIOUS, SCORING_RULE_MESSAGE, trimAfterMove = true)
                Trigger.PostUndo ->
                    buildEngineEstimateDisplayPlan(
                        STATE,
                        ESTIMATE,
                        PREVIOUS,
                        "Local undo settled; engine analysis synced.",
                        trimAfterMove = true,
                    )
                Trigger.RestoredGame ->
                    buildEngineEstimateDisplayPlan(
                        STATE,
                        ESTIMATE,
                        PREVIOUS,
                        "Previous game restored and engine state synchronized.",
                    )
            }

        fun defaultFallbackMessage(trigger: Trigger): String =
            when (trigger) {
                Trigger.ScoringRule -> "Scoring rule changed, but engine rule sync failed."
                Trigger.PostUndo -> "Local undo engine sync failed."
                Trigger.RestoredGame -> "Saved game restored locally, but engine sync failed."
            }

        /**
         * 블록을 곧바로 돌리는 `runEngineOperation`에서의 사건 순서.
         * 무르기 후만 후속 분석 요청이 `operation.end` **뒤**에 온다(클래스 KDoc 참고).
         */
        fun expectedTrace(
            trigger: Trigger,
            planLabel: String,
            followUp: Boolean,
        ): List<String> {
            val body = listOf(
                "operation.start ${expectedOperation(trigger).operationId}",
                "work.start",
                "read currentState",
                "read currentSessionGeneration",
                expectedEngineCall(trigger),
                "work.end $planLabel",
                "apply $planLabel",
            )
            val followUpLine = if (followUp) listOf("followUp m=${STATE.moves.size}") else emptyList()
            return when (trigger) {
                Trigger.ScoringRule, Trigger.RestoredGame -> body + followUpLine + "operation.end"
                Trigger.PostUndo -> body + "operation.end" + followUpLine
            }
        }

        fun ScoreSyncCompletionApplyPlan.label(): String =
            when (this) {
                is ScoreSyncCompletionApplyPlan.ApplySuccess -> "ApplySuccess"
                is ScoreSyncCompletionApplyPlan.ApplyFailure -> "ApplyFailure"
                is ScoreSyncCompletionApplyPlan.Discard -> "Discard"
            }
    }
}
