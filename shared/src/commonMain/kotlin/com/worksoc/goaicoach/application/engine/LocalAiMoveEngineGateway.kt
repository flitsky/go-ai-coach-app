package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.match.AiMoveEngineGateway
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.MoveResult

internal class LocalAiMoveEngineGateway(
    private val coreApi: EngineCoreApi,
) : AiMoveEngineGateway {
    override suspend fun playMove(move: Move): EngineStatus =
        coreApi.playMove(move)

    override suspend fun genMove(player: StoneColor): MoveResult =
        coreApi.genMove(player)

    override suspend fun clearSearchCache(): EngineStatus =
        coreApi.clearSearchCache()

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult =
        coreApi.analyze(limit)
}
