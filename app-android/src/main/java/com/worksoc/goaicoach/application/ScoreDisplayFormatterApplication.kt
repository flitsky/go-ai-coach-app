package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.FinalScoreResult

internal data class FinalScoreDisplayText(
    val scoreText: String,
    val engineMessage: String,
    val candidateText: String,
)

internal data class EndgameFailureDisplayText(
    val finalScoreText: String,
    val engineMessage: String,
    val candidateText: String,
)

internal fun buildLocalFinalScoreDisplayText(
    finalScore: FinalScoreResult,
    engineMessage: String,
    candidateText: String,
): FinalScoreDisplayText =
    FinalScoreDisplayText(
        scoreText = finalScore.toDisplayText(),
        engineMessage = engineMessage,
        candidateText = candidateText,
    )

internal fun buildResolvedEndgameDisplayText(
    resolution: AiEndgameResolution,
    engineMessagePrefix: String? = null,
): FinalScoreDisplayText =
    FinalScoreDisplayText(
        scoreText = resolution.finalScore.toDisplayText(),
        engineMessage = listOfNotNull(engineMessagePrefix, resolution.toEngineMessage()).joinToString("\n"),
        candidateText = resolution.toCandidateText(),
    )

internal fun buildEndgameFailureDisplayText(
    errorMessage: String,
    engineMessagePrefix: String? = null,
): EndgameFailureDisplayText {
    val finalScoreText = "Final score failed: $errorMessage"
    return EndgameFailureDisplayText(
        finalScoreText = finalScoreText,
        engineMessage = listOfNotNull(engineMessagePrefix, finalScoreText).joinToString("\n"),
        candidateText = "Game ended after two passes, but final score failed.",
    )
}
