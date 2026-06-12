package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.Ruleset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOperationPolicyTest {
    @Test
    fun benchmarkGateRequiresReadyLocalIdleEngine() {
        assertEquals(
            EngineOperationGate.Block("Engine benchmark requires a ready local engine."),
            evaluateEngineBenchmarkGate(
                isEngineReady = false,
                supportsDeviceBenchmark = true,
                isEngineBusy = false,
                isBenchmarkRunning = false,
            ),
        )
        assertEquals(
            EngineOperationGate.Block("Engine benchmark is available only for the local KataGo process engine."),
            evaluateEngineBenchmarkGate(
                isEngineReady = true,
                supportsDeviceBenchmark = false,
                isEngineBusy = false,
                isBenchmarkRunning = false,
            ),
        )
        assertEquals(
            EngineOperationGate.Block("Engine is busy. Run benchmark after the current response."),
            evaluateEngineBenchmarkGate(
                isEngineReady = true,
                supportsDeviceBenchmark = true,
                isEngineBusy = true,
                isBenchmarkRunning = false,
            ),
        )
        assertEquals(
            EngineOperationGate.Allow,
            evaluateEngineBenchmarkGate(
                isEngineReady = true,
                supportsDeviceBenchmark = true,
                isEngineBusy = false,
                isBenchmarkRunning = false,
            ),
        )
    }

    @Test
    fun searchTimeChangeGateBlocksOnlyWhenBusy() {
        assertEquals(
            EngineOperationGate.Block("Engine is busy. Change search time after the current action."),
            evaluateSearchTimeChangeGate(isEngineBusy = true),
        )
        assertEquals(EngineOperationGate.Allow, evaluateSearchTimeChangeGate(isEngineBusy = false))
    }

    @Test
    fun scoringRuleChangeGateDistinguishesNoOpBusyAndAllowed() {
        assertEquals(
            EngineOperationGate.NoOp,
            evaluateScoringRuleChangeGate(
                currentRuleset = Ruleset.Japanese,
                nextRuleset = Ruleset.Japanese,
                isEngineBusy = true,
            ),
        )

        val busy = evaluateScoringRuleChangeGate(
            currentRuleset = Ruleset.Japanese,
            nextRuleset = Ruleset.Chinese,
            isEngineBusy = true,
        )
        assertTrue(busy is EngineOperationGate.Block)

        assertEquals(
            EngineOperationGate.Allow,
            evaluateScoringRuleChangeGate(
                currentRuleset = Ruleset.Japanese,
                nextRuleset = Ruleset.Chinese,
                isEngineBusy = false,
            ),
        )
    }
}
