package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.TurnAnalysisPurpose
import com.worksoc.goaicoach.shared.policy.turnAnalysisLimitFor
import com.worksoc.goaicoach.shared.scoring.BoardScorer
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.scoring.ScoreTimeline

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

/**
 * 엔진 판을 [state]로 맞춘다 — 첫 국면을 세우고 둔 수를 차례로 둔다.
 *
 * 첫 국면이 **밖에서 받은 국면**이면(시작 배치 — 바둑판 사진에서 「이 국면부터 대국」, 백로그 #266) 정적 국면으로 세운다
 * (`syncStaticPosition` — GTP는 `loadsgf`, JSON 분석은 `initialStones`). 예전에는 시작 배치를 몰라 둔 수가 하나라도 있으면
 * 「새 판 + 수순」으로 맞췄고, AI가 둘째 수부터 사진의 돌이 없는 판을 봤다. 시작 배치 없이 수순도 없이 돌만 있는 국면
 * (보정 화면의 분석)은 그 돌을 시작 배치로 본다.
 */
suspend fun EngineCoreApi.syncToGameState(state: GameState): EngineStatus {
    val setupStones = state.setupStones.ifEmpty {
        if (state.handicapCount == 0 && state.moves.isEmpty()) state.stones else emptyMap()
    }
    if (setupStones.isNotEmpty()) {
        val start = GameState.withHandicap(state.setup.copy(setupStones = setupStones))
        val status = syncStaticPosition(start)
        state.moves.forEach { move -> playMove(move) }
        return status
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
