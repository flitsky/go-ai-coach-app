package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.endgame.EndgameJudgeGateway
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.ScoreEstimate

internal class LocalEndgameJudgeGateway(
    private val coreApi: EngineCoreApi,
) : EndgameJudgeGateway {
    override suspend fun configure(profile: EngineProfile): EngineStatus =
        coreApi.configure(profile)

    override suspend fun deadStones(): DeadStonesResult =
        coreApi.deadStones()

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate =
        coreApi.estimateScore(limit)

    override suspend fun scoreFinal(): FinalScoreResult =
        coreApi.scoreFinal()
}
