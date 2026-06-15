package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.PlayLevelSetting

internal data class GameSessionRuntimeState(
    val playLevel: PlayLevelSetting,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val sessionGeneration: Long = 0L,
) {
    fun applySelection(selection: RuntimePlayLevelSelection): GameSessionRuntimeState =
        copy(
            playLevel = selection.playLevel,
            engineProfile = selection.engineProfile,
            analysisPreset = selection.analysisPreset,
        )

    fun applyAutoAiTurnDisplayPlan(display: AutoAiTurnDisplayPlan): GameSessionRuntimeState =
        copy(
            playLevel = display.playLevel,
            engineProfile = display.profile,
            analysisPreset = display.analysisPreset,
        )

    fun nextSessionGeneration(): GameSessionRuntimeState =
        copy(sessionGeneration = sessionGeneration + 1)
}
