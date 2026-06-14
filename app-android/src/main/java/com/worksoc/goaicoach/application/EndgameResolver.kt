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
    val timings: EndgameResolutionTimings = EndgameResolutionTimings(),
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
            appendLine("timingSummary=${timings.summary()}")
            appendLine("syncReplayMs=${timings.syncReplayMs ?: "unknown"}")
            appendLine("deadStonesMs=${timings.deadStonesMs}")
            appendLine("localDeadStoneDetectionMs=${timings.localDeadStoneDetectionMs}")
            appendLine("localCleanupScoringMs=${timings.localCleanupScoringMs}")
            appendLine("engineEstimateMs=${timings.engineEstimateMs}")
            appendLine("scoreSelectionMs=${timings.scoreSelectionMs}")
            appendLine("diagnosticFinalScoreMs=${timings.diagnosticFinalScoreMs}")
            appendLine("resolverTotalMs=${timings.resolverTotalMs}")
            appendLine("totalWithSyncReplayMs=${timings.totalWithSyncReplayMs ?: "unknown"}")
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

internal data class EndgameResolutionTimings(
    val syncReplayMs: Long? = null,
    val deadStonesMs: Long = 0L,
    val localDeadStoneDetectionMs: Long = 0L,
    val localCleanupScoringMs: Long = 0L,
    val engineEstimateMs: Long = 0L,
    val scoreSelectionMs: Long = 0L,
    val diagnosticFinalScoreMs: Long = 0L,
    val resolverTotalMs: Long = 0L,
) {
    val totalWithSyncReplayMs: Long?
        get() = syncReplayMs?.let { it + resolverTotalMs }

    fun summary(): String =
        "syncReplayMs=${syncReplayMs ?: "unknown"} " +
            "deadStonesMs=$deadStonesMs " +
            "localDetectMs=$localDeadStoneDetectionMs " +
            "localCleanupScoreMs=$localCleanupScoringMs " +
            "estimateMs=$engineEstimateMs " +
            "scoreSelectMs=$scoreSelectionMs " +
            "finalScoreMs=$diagnosticFinalScoreMs " +
            "resolverTotalMs=$resolverTotalMs " +
            "totalWithSyncMs=${totalWithSyncReplayMs ?: "unknown"}"
}

internal suspend fun resolveAiEndgame(
    engineAdapter: EngineCoreApi,
    originalState: GameState,
    estimateLimit: AnalysisLimit,
    prePassCandidates: List<CandidateMove> = emptyList(),
    syncReplayMs: Long? = null,
): AiEndgameResolution {
    // This resolver composes raw engine primitives and local scoring. It is not
    // the product SLA boundary by itself. Default pass/pass UX should call this
    // through the assistant-judge policy: show a result within 5s even if the
    // user's normal search-time cap is off. Unbounded chief-judge analysis is
    // allowed only after an explicit user objection, isolated by match/session
    // generation or a separate engine worker so New Game/Undo cannot receive a
    // stale result.
    val resolverStartMillis = System.currentTimeMillis()
    var deadStonesResult: DeadStonesResult? = null
    var deadStonesError: String? = null
    val deadStonesStartMillis = System.currentTimeMillis()
    runCatching { engineAdapter.deadStones() }
        .onSuccess { deadStonesResult = it }
        .onFailure { deadStonesError = it.message ?: "Unknown error" }
    val deadStonesMs = System.currentTimeMillis() - deadStonesStartMillis

    val localDetectStartMillis = System.currentTimeMillis()
    val locallyInferredDeadStones = DeadStoneDetector.capturableDeadStones(originalState)
    val localDeadStoneDetectionMs = System.currentTimeMillis() - localDetectStartMillis

    val localCleanupStartMillis = System.currentTimeMillis()
    val cleanup = DeadStoneCleaner.apply(
        state = originalState,
        deadStoneCoordinates = deadStonesResult?.coordinates.orEmpty() + locallyInferredDeadStones,
    )
    val localFinalScore = BoardScorer.score(cleanup.state)
    val localCleanupScoringMs = System.currentTimeMillis() - localCleanupStartMillis

    var engineScoreEstimate: ScoreEstimate? = null
    var engineScoreEstimateError: String? = null
    val engineEstimateStartMillis = System.currentTimeMillis()
    runCatching { engineAdapter.estimateScore(estimateLimit) }
        .onSuccess { engineScoreEstimate = it }
        .onFailure { engineScoreEstimateError = it.message ?: "Unknown error" }
    val engineEstimateMs = System.currentTimeMillis() - engineEstimateStartMillis

    val scoreSelectionStartMillis = System.currentTimeMillis()
    val scoreSelection = EndgameScoreSelector.selectDisplayScore(
        cleanup = cleanup,
        localScore = localFinalScore,
        engineEstimate = engineScoreEstimate,
        prePassCandidates = prePassCandidates,
    )
    val scoreSelectionMs = System.currentTimeMillis() - scoreSelectionStartMillis

    var engineFinalScore: FinalScoreResult? = null
    var engineFinalScoreError: String? = null
    val finalScoreStartMillis = System.currentTimeMillis()
    runCatching { engineAdapter.scoreFinal() }
        .onSuccess { engineFinalScore = it }
        .onFailure { engineFinalScoreError = it.message ?: "Unknown error" }
    val diagnosticFinalScoreMs = System.currentTimeMillis() - finalScoreStartMillis

    val timings = EndgameResolutionTimings(
        syncReplayMs = syncReplayMs,
        deadStonesMs = deadStonesMs,
        localDeadStoneDetectionMs = localDeadStoneDetectionMs,
        localCleanupScoringMs = localCleanupScoringMs,
        engineEstimateMs = engineEstimateMs,
        scoreSelectionMs = scoreSelectionMs,
        diagnosticFinalScoreMs = diagnosticFinalScoreMs,
        resolverTotalMs = System.currentTimeMillis() - resolverStartMillis,
    )

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
        timings = timings,
    )
}
