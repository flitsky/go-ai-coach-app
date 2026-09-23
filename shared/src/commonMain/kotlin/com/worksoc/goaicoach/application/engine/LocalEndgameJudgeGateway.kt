package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.endgame.EndgameJudgeGateway
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.DeadStonesResult
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate

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
