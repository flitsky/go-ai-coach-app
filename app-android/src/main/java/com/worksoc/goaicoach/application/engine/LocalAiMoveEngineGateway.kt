package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.match.AiMoveEngineGateway
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.StoneColor

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
