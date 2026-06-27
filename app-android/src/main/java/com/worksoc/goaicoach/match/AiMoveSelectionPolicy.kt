package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveSelectionPolicy
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.aiMoveAnalysisLimitWith
import com.worksoc.goaicoach.shared.aiMoveSearchMode
import com.worksoc.goaicoach.shared.describe
import com.worksoc.goaicoach.shared.fastCandidateAnalysis
import kotlin.random.Random

internal data class SelectedAiMove(
    val move: Move,
    val summary: String,
)

internal object AiMoveSelectionPolicy {
    fun analysisLimitFor(
        playLevel: PlayLevelSetting,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
    ): AnalysisLimit =
        when (searchMode) {
            EngineSearchMode.GtpStatefulFast -> {
                val baseLimit = playLevel.analysisLimitWith(searchTimeSettings)
                val count = if (playLevel.selectionPolicy is MoveSelectionPolicy.BestOnly) 1 else baseLimit.candidateCount
                baseLimit.fastCandidateAnalysis(candidateCount = count)
            }
            EngineSearchMode.JsonPositionAnalysis ->
                playLevel.aiMoveAnalysisLimitWith(searchTimeSettings)
        }

    fun select(
        currentState: GameState,
        aiPlayer: StoneColor,
        playLevel: PlayLevelSetting,
        searchMode: EngineSearchMode,
        candidates: List<CandidateMove>,
        analysisSummary: String,
        random: Random = Random.Default,
    ): SelectedAiMove? {
        val scoredCandidates = candidates
            .filter { candidate ->
                candidate.move.player == aiPlayer && candidate.pointLoss != null
            }

        val bestCandidate = scoredCandidates.firstOrNull()
        if (bestCandidate?.move is Move.Pass) {
            return SelectedAiMove(
                move = bestCandidate.move,
                summary = buildString {
                    appendLine("AI level: ${playLevel.displayLabel}, endgame pass override.")
                    appendLine("AI selected pass because KataGo ranked pass as the best scored candidate.")
                    appendAiCandidateList(
                        currentState = currentState,
                        candidates = scoredCandidates,
                        selectedMove = bestCandidate.move,
                    )
                    appendLine(analysisSummary)
                }.trim(),
            )
        }

        val scoredPlayCandidates = scoredCandidates
            .filter { candidate -> candidate.move is Move.Play }
        val range = playLevel.selectionPolicy.candidateIndexRange(scoredPlayCandidates.size)
            ?: return null
        val pool = scoredPlayCandidates.slice(range)
        if (pool.isEmpty()) {
            return null
        }

        val selected = pool[random.nextInt(pool.size)]
        val selectedRank = scoredPlayCandidates.indexOf(selected) + 1
        return SelectedAiMove(
            move = selected.move,
            summary = buildString {
                appendLine("AI level: ${playLevel.displayLabel}, ${playLevel.selectionPolicy.description}.")
                appendLine("Search mode: ${searchMode.label}.")
                appendLine("AI selected rank $selectedRank/${scoredPlayCandidates.size}: ${selected.describeForSelection(currentState)}.")
                appendAiCandidateList(
                    currentState = currentState,
                    candidates = scoredCandidates,
                    selectedMove = selected.move,
                )
                appendLine(analysisSummary)
            }.trim(),
        )
    }
}

private fun StringBuilder.appendAiCandidateList(
    currentState: GameState,
    candidates: List<CandidateMove>,
    selectedMove: Move,
) {
    if (candidates.isEmpty()) {
        return
    }
    appendLine("AI candidates:")
    candidates.forEachIndexed { index, candidate ->
        append(index + 1)
        append(". ")
        append(candidate.describeForSelection(currentState))
        if (candidate.move == selectedMove) {
            append(" [selected]")
        }
        appendLine()
    }
}

private fun CandidateMove.describeForSelection(stateAfterHuman: GameState): String =
    buildString {
        append(move.describe(stateAfterHuman.boardSize))
        pointLoss?.let { append(" loss=${it.formatOneDecimal()}") }
        winRate?.let { append(" winRate=${(it * 100).toInt()}%") }
        scoreLead?.let { append(" scoreLead=${it.formatOneDecimal()}") }
        visits?.let { append(" visits=$it") }
    }

private fun Double.formatOneDecimal(): String =
    (kotlin.math.round(this * 10.0) / 10.0)
        .let { if (kotlin.math.abs(it) < 0.05) 0.0 else it }
        .toString()
