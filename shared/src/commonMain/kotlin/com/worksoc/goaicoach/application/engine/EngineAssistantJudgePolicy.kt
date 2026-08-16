package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.shared.EngineProfile

internal const val AssistantJudgeDeadStonesTimeCapMillis: Long = 2_000L
internal const val AssistantJudgeFinalScoreTimeCapMillis: Long = 1_000L
internal const val AssistantJudgeEndgameTotalTimeCapMillis: Long =
    AssistantJudgeDeadStonesTimeCapMillis + AssistantJudgeFinalScoreTimeCapMillis

internal fun EngineProfile.withAssistantJudgeDeadStonesTimeCap(): EngineProfile =
    copy(
        analysisLimit = analysisLimit.copy(timeMillis = AssistantJudgeDeadStonesTimeCapMillis),
    )

internal fun EngineProfile.withAssistantJudgeFinalScoreTimeCap(): EngineProfile =
    copy(
        analysisLimit = analysisLimit.copy(timeMillis = AssistantJudgeFinalScoreTimeCapMillis),
    )
