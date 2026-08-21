package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.endgame.buildEndgameLog
import com.worksoc.goaicoach.application.analysis.toDisplayText
import com.worksoc.goaicoach.shared.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

fun FinalScoreStateResult.toFinalScoreDisplayPlan(
    text: FinalScoreDisplayText,
): FinalScoreDisplayPlan =
    toFinalScoreDisplayPlan(
        scoreText = text.scoreText,
        engineMessage = text.engineMessage,
        candidateText = text.candidateText,
    )

fun FinalScoreStateResult.toFinalScoreDisplayPlan(
    scoreText: String,
    engineMessage: String,
    candidateText: String,
): FinalScoreDisplayPlan =
    FinalScoreDisplayPlan(
        gameState = gameState,
        scoreText = scoreText,
        scoreEstimate = scoreEstimate,
        scoreSnapshots = scoreSnapshots,
        endgameLog = endgameLog,
        engineMessage = engineMessage,
        candidateText = candidateText,
        endgameTimingSummary = endgameTimingSummary,
        judgement = judgement,
    )

fun buildLocalFinalScoreStateResult(
    source: String,
    state: GameState,
    finalScore: FinalScoreResult,
    previousSnapshots: List<ScoreSnapshot>,
    detail: String,
): FinalScoreStateResult {
    val finalScoreText = finalScore.toDisplayText()
    return FinalScoreStateResult(
        gameState = state,
        scoreEstimate = null,
        scoreSnapshots = ScoreTimeline.record(
            previousSnapshots,
            ScoreTimeline.fromFinalScore(
                moveNumber = state.moves.size,
                finalScore = finalScore,
                source = ScoreSnapshotSource.FinalScore,
            ),
        ),
        endgameLog = buildEndgameLog(
            source = source,
            state = state,
            finalScoreText = finalScoreText,
            detail = detail,
        ),
        judgement = buildFinalScoreJudgement(
            state = state,
            displayScore = finalScore,
            localScore = finalScore,
            cleanup = DeadStoneCleanupResult(state = state, removedStones = emptyList()),
        ),
    )
}

fun buildLocalFinalScoreDisplayPlan(
    source: String,
    state: GameState,
    finalScore: FinalScoreResult,
    previousSnapshots: List<ScoreSnapshot>,
    detail: String,
    engineMessage: String,
    candidateText: String,
): FinalScoreDisplayPlan {
    val text = buildLocalFinalScoreDisplayText(
        finalScore = finalScore,
        engineMessage = engineMessage,
        candidateText = candidateText,
    )
    return buildLocalFinalScoreStateResult(
        source = source,
        state = state,
        finalScore = finalScore,
        previousSnapshots = previousSnapshots,
        detail = detail,
    ).toFinalScoreDisplayPlan(text)
}

internal fun buildResolvedEndgameStateResult(
    source: String,
    originalState: GameState,
    resolution: AiEndgameResolution,
    previousSnapshots: List<ScoreSnapshot>,
): FinalScoreStateResult {
    val cleanupState = resolution.cleanup.state
    val finalScoreText = resolution.finalScore.toDisplayText()
    return FinalScoreStateResult(
        gameState = cleanupState,
        scoreEstimate = resolution.engineScoreEstimate,
        scoreSnapshots = ScoreTimeline.record(
            previousSnapshots,
            ScoreTimeline.fromFinalScore(
                moveNumber = cleanupState.moves.size,
                finalScore = resolution.finalScore,
                source = ScoreSnapshotSource.FinalScore,
            ),
        ),
        endgameLog = buildEndgameLog(
            source = source,
            state = cleanupState,
            finalScoreText = finalScoreText,
            detail = resolution.toLogDetail(originalState),
        ),
        endgameTimingSummary = resolution.timings.summary(),
        judgement = buildFinalScoreJudgement(
            state = cleanupState,
            displayScore = resolution.finalScore,
            localScore = resolution.localFinalScore,
            cleanup = resolution.cleanup,
        ),
    )
}

private fun buildFinalScoreJudgement(
    state: GameState,
    displayScore: FinalScoreResult,
    localScore: FinalScoreResult,
    cleanup: DeadStoneCleanupResult,
): FinalScoreJudgement {
    val removedBlack = cleanup.removedStones.count { it.color == StoneColor.Black }
    val removedWhite = cleanup.removedStones.count { it.color == StoneColor.White }
    val isEstimatedDisplay = displayScore.rawScore.endsWith("?")
    return FinalScoreJudgement(
        winner = displayScore.winner,
        margin = displayScore.margin,
        ruleset = state.ruleset,
        isEstimatedDisplay = isEstimatedDisplay,
        removedBlack = removedBlack,
        removedWhite = removedWhite,
        blackArea = localScore.blackArea,
        whiteAreaWithKomi = localScore.whiteAreaWithKomi,
        capturedByBlack = state.capturedBy(StoneColor.Black),
        capturedByWhite = state.capturedBy(StoneColor.White),
        komi = localScore.komi,
        handicapCount = state.handicapCount,
    )
}

fun buildResolvedEndgameDisplayPlan(
    source: String,
    originalState: GameState,
    resolution: AiEndgameResolution,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessagePrefix: String? = null,
): FinalScoreDisplayPlan {
    val text = buildResolvedEndgameDisplayText(
        resolution = resolution,
        engineMessagePrefix = engineMessagePrefix,
    )
    return buildResolvedEndgameStateResult(
        source = source,
        originalState = originalState,
        resolution = resolution,
        previousSnapshots = previousSnapshots,
    ).toFinalScoreDisplayPlan(text)
}

fun buildEndgameFailureDisplayPlan(
    source: String,
    state: GameState,
    errorMessage: String,
    engineMessagePrefix: String? = null,
): EndgameFailureDisplayPlan {
    val text = buildEndgameFailureDisplayText(
        errorMessage = errorMessage,
        engineMessagePrefix = engineMessagePrefix,
    )
    return EndgameFailureDisplayPlan(
        endgameLog = buildEndgameLog(
            source = source,
            state = state,
            finalScoreText = text.finalScoreText,
            detail = "lastMove=${state.moves.lastOrNull()?.describe(state.boardSize) ?: "None"}",
        ),
        engineMessage = text.engineMessage,
        candidateText = text.candidateText,
    )
}
