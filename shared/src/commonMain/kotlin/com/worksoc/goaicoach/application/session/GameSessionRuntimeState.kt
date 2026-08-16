package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.autoai.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.PlayLevelSetting

data class GameSessionRuntimeState(
    val playLevel: PlayLevelSetting,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val sessionGeneration: Long = 0L,
    // Identifies the current match for concerns that must survive an in-match
    // undo (e.g. premium activation binding). Unlike sessionGeneration — which
    // bumps on undo too, to invalidate stale async engine results — this only
    // bumps when a genuinely new match starts.
    val matchGeneration: Long = 0L,
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

    fun nextMatchGeneration(): GameSessionRuntimeState =
        copy(matchGeneration = matchGeneration + 1)
}
