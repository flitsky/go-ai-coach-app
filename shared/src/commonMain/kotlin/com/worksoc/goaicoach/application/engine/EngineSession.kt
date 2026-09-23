package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.scoring.BoardScorer
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.scoring.ScoreTimeline
import com.worksoc.goaicoach.shared.policy.TurnAnalysisPurpose
import com.worksoc.goaicoach.shared.policy.turnAnalysisLimitFor

data class EngineStartupResult(
    val message: String,
    val scoreSnapshot: ScoreSnapshot?,
)

data class AutoAiTurnResult(
    val turnOutcome: TurnOutcome,
    val scoreEstimate: ScoreEstimate?,
    val profile: EngineProfile,
    val playLevel: PlayLevelSetting,
)

data class LocalEngineMoveResult(
    val estimate: ScoreEstimate? = null,
    val endgame: AiEndgameResolution? = null,
)

suspend fun EngineCoreApi.syncToGameState(state: GameState): EngineStatus {
    if (state.handicapCount == 0 && state.moves.isEmpty() && state.stones.isNotEmpty()) {
        return syncStaticPosition(state)
    }
    val status = newGame(state.boardSize, state.ruleset, state.handicapCount, state.komi)
    state.moves.forEach { move ->
        playMove(move)
    }
    return status
}

fun scoreGraphAnalysisLimit(profile: EngineProfile): AnalysisLimit =
    profile.turnAnalysisLimitFor(TurnAnalysisPurpose.ScoreGraph)

fun localScoreSnapshot(state: GameState): ScoreSnapshot =
    ScoreTimeline.fromFinalScore(
        moveNumber = state.moves.size,
        finalScore = BoardScorer.score(state),
        source = ScoreSnapshotSource.LocalAreaEstimate,
    )
