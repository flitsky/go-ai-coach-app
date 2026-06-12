package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStoneCleaner
import com.worksoc.goaicoach.shared.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.DeadStoneDetector
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EndgameScoreSelector
import com.worksoc.goaicoach.shared.EndgameScoreSource
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.describe

internal data class AiEndgameResolution(
    val cleanup: DeadStoneCleanupResult,
    val finalScore: FinalScoreResult,
    val scoreSource: EndgameScoreSource,
    val localFinalScore: FinalScoreResult,
    val deadStonesResult: DeadStonesResult?,
    val deadStonesError: String?,
    val locallyInferredDeadStones: List<BoardCoordinate>,
    val engineScoreEstimate: ScoreEstimate?,
    val engineScoreEstimateError: String?,
    val engineFinalScore: FinalScoreResult?,
    val engineFinalScoreError: String?,
    val prePassCandidates: List<CandidateMove>,
) {
    fun toEngineMessage(): String =
        buildString {
            if (cleanup.removedCount > 0) {
                append("Dead-stone cleanup removed ${cleanup.removedCount} stone(s). ")
            } else {
                append("Dead-stone cleanup found no stones to remove. ")
            }
            append(finalScore.status.message)
            if (scoreSource == EndgameScoreSource.UnsettledEngineEstimate) {
                append("\nShowing uncertain KataGo estimate because local area final disagrees with engine evaluation.")
            }
            if (scoreSource == EndgameScoreSource.UnsettledPrePassTopMoveEstimate) {
                append("\nShowing uncertain pre-pass Top Moves estimate because pass/pass final disagrees with the best pre-pass continuation.")
            }
            engineFinalScore?.let { append("\nDiagnostic KataGo final_score: ${it.rawScore}") }
            deadStonesError?.let { append("\nDead-stone status failed: $it") }
            engineScoreEstimateError?.let { append("\nEndgame estimate failed: $it") }
            engineFinalScoreError?.let { append("\nDiagnostic final_score failed: $it") }
        }

    fun toCandidateText(): String =
        when {
            cleanup.removedCount > 0 ->
                "Game ended after pass/pass. Removed ${cleanup.removedCount} engine-marked dead stone(s)."

            scoreSource == EndgameScoreSource.UnsettledEngineEstimate ->
                "Game ended after pass/pass, but the board looks unsettled. Showing KataGo estimate instead of raw local area."

            scoreSource == EndgameScoreSource.UnsettledPrePassTopMoveEstimate ->
                "Game ended after pass/pass, but pre-pass Top Moves indicated a better cleanup/continuation. Showing uncertain pre-pass estimate."

            else ->
                "Game ended after pass/pass. KataGo did not mark dead stones for removal."
        }

    fun toLogDetail(originalState: GameState): String =
        buildString {
            appendLine("lastMove=${originalState.moves.lastOrNull()?.describe(originalState.boardSize) ?: "None"}")
            appendLine("originalStoneCount=${originalState.stones.size}")
            appendLine("cleanedStoneCount=${cleanup.state.stones.size}")
            appendLine("deadStoneStatus=${deadStonesResult?.summary ?: "failed"}")
            appendLine("deadStoneError=${deadStonesError ?: "none"}")
            appendLine("locallyInferredDeadStones=${locallyInferredDeadStones.toCoordinateLogText(originalState.boardSize)}")
            appendLine("removedStones=${cleanup.removedStones.toLogText(originalState.boardSize)}")
            appendLine("displayScoreSource=$scoreSource")
            appendLine("localAreaAfterCleanup=${localFinalScore.rawScore}")
            appendLine("prePassTopMoves=${prePassCandidates.take(8).toCandidateLogText(originalState.boardSize)}")
            appendLine("engineEstimateWhiteLead=${engineScoreEstimate?.whiteScoreLead ?: "none"}")
            appendLine("engineEstimateWhiteWinRate=${engineScoreEstimate?.whiteWinRate ?: "none"}")
            appendLine("engineEstimateError=${engineScoreEstimateError ?: "none"}")
            appendLine("diagnosticKataGoFinalScore=${engineFinalScore?.rawScore ?: "none"}")
            appendLine("diagnosticKataGoFinalScoreError=${engineFinalScoreError ?: "none"}")
        }.trim()
}

internal suspend fun resolveAiEndgame(
    engineAdapter: EngineCoreApi,
    originalState: GameState,
    estimateLimit: AnalysisLimit,
    prePassCandidates: List<CandidateMove> = emptyList(),
): AiEndgameResolution {
    var deadStonesResult: DeadStonesResult? = null
    var deadStonesError: String? = null
    runCatching { engineAdapter.deadStones() }
        .onSuccess { deadStonesResult = it }
        .onFailure { deadStonesError = it.message ?: "Unknown error" }

    val locallyInferredDeadStones = DeadStoneDetector.capturableDeadStones(originalState)
    val cleanup = DeadStoneCleaner.apply(
        state = originalState,
        deadStoneCoordinates = deadStonesResult?.coordinates.orEmpty() + locallyInferredDeadStones,
    )
    val localFinalScore = BoardScorer.score(cleanup.state)

    var engineScoreEstimate: ScoreEstimate? = null
    var engineScoreEstimateError: String? = null
    runCatching { engineAdapter.estimateScore(estimateLimit) }
        .onSuccess { engineScoreEstimate = it }
        .onFailure { engineScoreEstimateError = it.message ?: "Unknown error" }
    val scoreSelection = EndgameScoreSelector.selectDisplayScore(
        cleanup = cleanup,
        localScore = localFinalScore,
        engineEstimate = engineScoreEstimate,
        prePassCandidates = prePassCandidates,
    )

    var engineFinalScore: FinalScoreResult? = null
    var engineFinalScoreError: String? = null
    runCatching { engineAdapter.scoreFinal() }
        .onSuccess { engineFinalScore = it }
        .onFailure { engineFinalScoreError = it.message ?: "Unknown error" }

    return AiEndgameResolution(
        cleanup = cleanup,
        finalScore = scoreSelection.displayScore,
        scoreSource = scoreSelection.source,
        localFinalScore = localFinalScore,
        deadStonesResult = deadStonesResult,
        deadStonesError = deadStonesError,
        locallyInferredDeadStones = locallyInferredDeadStones,
        engineScoreEstimate = engineScoreEstimate,
        engineScoreEstimateError = engineScoreEstimateError,
        engineFinalScore = engineFinalScore,
        engineFinalScoreError = engineFinalScoreError,
        prePassCandidates = prePassCandidates,
    )
}
