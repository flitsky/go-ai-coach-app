package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.application.session.GameSessionControllerState

internal object GoCoachScreenStateAssembler {
    data class Input(
        val controller: GameSessionControllerState,
        val uxOptions: KaTrainUxOptions,
        val engineRuntime: EngineRuntime,
        val displayRuntime: DisplayRuntime,
    )

    data class EngineRuntime(
        val name: String,
        val diagnostic: String,
        val isReady: Boolean,
        val isBusy: Boolean,
        val isBlockingBusy: Boolean,
        val hasCompletedStartup: Boolean,
    )

    data class DisplayRuntime(
        val analysisCacheStats: String,
        val isScoreGraphExpanded: Boolean,
        val turnTimeText: String,
    )

    fun assemble(input: Input): GameScreenState =
        buildGameScreenState(
            buildGameScreenStateInput(
                controller = input.controller,
                uxOptions = input.uxOptions,
                engineName = input.engineRuntime.name,
                engineDiagnostic = input.engineRuntime.diagnostic,
                isEngineReady = input.engineRuntime.isReady,
                isEngineBusy = input.engineRuntime.isBusy,
                isEngineBlockingBusy = input.engineRuntime.isBlockingBusy,
                analysisCacheStats = input.displayRuntime.analysisCacheStats,
                isScoreGraphExpanded = input.displayRuntime.isScoreGraphExpanded,
                turnTimeText = input.displayRuntime.turnTimeText,
                hasCompletedEngineStartup = input.engineRuntime.hasCompletedStartup,
            ),
        )
}
