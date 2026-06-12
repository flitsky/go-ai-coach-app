package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.Ruleset

internal sealed class EngineOperationGate {
    data object Allow : EngineOperationGate()
    data object NoOp : EngineOperationGate()
    data class Block(val message: String) : EngineOperationGate()
}

internal fun evaluateEngineBenchmarkGate(
    isEngineReady: Boolean,
    supportsDeviceBenchmark: Boolean,
    isEngineBusy: Boolean,
    isBenchmarkRunning: Boolean,
): EngineOperationGate =
    when {
        !isEngineReady ->
            EngineOperationGate.Block("Engine benchmark requires a ready local engine.")

        !supportsDeviceBenchmark ->
            EngineOperationGate.Block("Engine benchmark is available only for the local KataGo process engine.")

        isEngineBusy || isBenchmarkRunning ->
            EngineOperationGate.Block("Engine is busy. Run benchmark after the current response.")

        else -> EngineOperationGate.Allow
    }

internal fun evaluateSearchTimeChangeGate(isEngineBusy: Boolean): EngineOperationGate =
    if (isEngineBusy) {
        EngineOperationGate.Block("Engine is busy. Change search time after the current action.")
    } else {
        EngineOperationGate.Allow
    }

internal fun evaluateScoringRuleChangeGate(
    currentRuleset: Ruleset,
    nextRuleset: Ruleset,
    isEngineBusy: Boolean,
): EngineOperationGate =
    when {
        nextRuleset == currentRuleset -> EngineOperationGate.NoOp
        isEngineBusy -> EngineOperationGate.Block("Engine is busy. Change scoring rule after the current response.")
        else -> EngineOperationGate.Allow
    }
