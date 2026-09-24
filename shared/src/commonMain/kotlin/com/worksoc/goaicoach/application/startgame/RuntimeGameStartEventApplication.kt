package com.worksoc.goaicoach.application.startgame

import com.worksoc.goaicoach.application.contract.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.contextTransitionAfter
import com.worksoc.goaicoach.application.runtime.runtimeLogSnippet
import com.worksoc.goaicoach.application.runtime.runtimeLogSummary
import com.worksoc.goaicoach.shared.domain.Ruleset

fun runtimeGameResetLog(
    context: RuntimeLogContext,
    reset: GameSessionResetPlan,
): String =
    context.event(
        name = "game_reset",
        phase = "game_setup",
        transition = contextTransitionAfter(reset.gameState, context),
        detail = "New local board prepared. message=${reset.engineMessage.runtimeLogSnippet(220)}",
    )

fun runtimeEngineGameStartRequestLog(
    context: RuntimeLogContext,
    ruleset: Ruleset,
    runtime: RuntimePlayLevelSelection,
): String =
    context.event(
        name = "engine_game_start_request",
        phase = "engine_game_setup",
        transition = "start_engine_new_game_then_reset_local_board",
        detail = "ruleset=$ruleset runtimeLevel=${runtime.playLevel.displayLabel} " +
            "limit=${runtime.engineProfile.analysisLimit.runtimeLogSummary()}",
    )
