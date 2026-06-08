package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import kotlin.random.Random

internal val HumanPlayer = StoneColor.Black
internal val AiPlayer = StoneColor.White

internal enum class MatchMode(val label: String) {
    HumanVsAi("AI 대국"),
    LocalTwoPlayer("2P 테스트"),
}

internal data class TurnOutcome(
    val gameState: GameState,
    val engineMessage: String,
    val candidateText: String,
    val lastMoveText: String,
)

internal suspend fun applyAiResponseAfterHumanTurn(
    engineAdapter: EngineAdapter,
    stateAfterHuman: GameState,
    humanMove: Move,
    playLevel: PlayLevelSetting,
    onHumanMoveAccepted: suspend () -> Unit = {},
): TurnOutcome {
    val humanStatus = engineAdapter.playMove(humanMove)
    val humanText = humanMove.describe(stateAfterHuman.boardSize)
    onHumanMoveAccepted()

    if (stateAfterHuman.isBoardFull()) {
        return TurnOutcome(
            gameState = stateAfterHuman,
            engineMessage = "${humanStatus.message}\nBoard is full.",
            candidateText = "Game ended after $humanText.",
            lastMoveText = humanText,
        )
    }

    val selectedAiMove = engineAdapter.selectAiMoveFromAnalysis(stateAfterHuman, playLevel)
    if (selectedAiMove != null) {
        val afterAi = runCatching { stateAfterHuman.play(selectedAiMove.move) }.getOrNull()
        if (afterAi != null) {
            val syncStatus = engineAdapter.playMove(selectedAiMove.move)
            val aiText = selectedAiMove.move.describe(stateAfterHuman.boardSize)
            return TurnOutcome(
                gameState = afterAi,
                engineMessage = "${humanStatus.message}\n${syncStatus.message}\nAI selected $aiText from ${playLevel.displayLabel}.",
                candidateText = selectedAiMove.summary,
                lastMoveText = aiText,
            )
        }
    }

    val aiResult = engineAdapter.genMove(AiPlayer)
    val afterAi = stateAfterHuman.play(aiResult.move)
    val aiText = aiResult.move.describe(stateAfterHuman.boardSize)
    return TurnOutcome(
        gameState = afterAi,
        engineMessage = "${humanStatus.message}\n${aiResult.status.message}\n${aiResult.summary}",
        candidateText = "AI replied with $aiText.",
        lastMoveText = aiText,
    )
}

internal fun activePlayer(
    mode: MatchMode,
    gameState: GameState,
): StoneColor =
    when (mode) {
        MatchMode.HumanVsAi -> HumanPlayer
        MatchMode.LocalTwoPlayer -> gameState.nextPlayer
    }

internal fun boardInputEnabled(
    mode: MatchMode,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    nextPlayer: StoneColor,
): Boolean =
    when (mode) {
        MatchMode.HumanVsAi -> isEngineReady && !isEngineBusy && nextPlayer == HumanPlayer
        MatchMode.LocalTwoPlayer -> !isEngineBusy
    }

internal fun turnStatus(
    nextPlayer: StoneColor,
    isEngineBusy: Boolean,
    mode: MatchMode,
): String =
    when {
        isEngineBusy -> "AI thinking"
        mode == MatchMode.LocalTwoPlayer -> "Local turn: ${nextPlayer.label}"
        nextPlayer == HumanPlayer -> "Your turn: ${HumanPlayer.label}"
        else -> "Waiting: ${nextPlayer.label}"
    }

internal fun modeSummary(
    mode: MatchMode,
    engineName: String,
): String =
    when (mode) {
        MatchMode.HumanVsAi -> "9x9 match: human Black vs $engineName White"
        MatchMode.LocalTwoPlayer -> "9x9 local two-player rules test"
    }

private data class SelectedAiMove(
    val move: Move,
    val summary: String,
)

private suspend fun EngineAdapter.selectAiMoveFromAnalysis(
    stateAfterHuman: GameState,
    playLevel: PlayLevelSetting,
): SelectedAiMove? =
    runCatching {
        val analysis = analyze(playLevel.group.defaultAnalysisLimit())
        val scoredCandidates = analysis.candidates
            .filter { candidate ->
                candidate.move.player == AiPlayer && candidate.pointLoss != null
            }
            .sortedBy { it.pointLoss ?: Double.MAX_VALUE }

        val bestCandidate = scoredCandidates.firstOrNull()
        if (bestCandidate?.move is Move.Pass) {
            return@runCatching SelectedAiMove(
                move = bestCandidate.move,
                summary = buildString {
                    appendLine("AI level: ${playLevel.displayLabel}, endgame pass override.")
                    appendLine("AI selected pass because KataGo ranked pass as the best scored candidate.")
                    appendLine(analysis.summary)
                }.trim(),
            )
        }

        val scoredPlayCandidates = scoredCandidates
            .filter { candidate -> candidate.move is Move.Play }
        val range = playLevel.selectionPolicy.candidateIndexRange(scoredPlayCandidates.size)
            ?: return@runCatching null
        val pool = scoredPlayCandidates.slice(range)
        if (pool.isEmpty()) {
            return@runCatching null
        }

        val selected = pool[Random.nextInt(pool.size)]
        val selectedRank = scoredPlayCandidates.indexOf(selected) + 1
        SelectedAiMove(
            move = selected.move,
            summary = buildString {
                appendLine("AI level: ${playLevel.displayLabel}, ${playLevel.selectionPolicy.description}.")
                appendLine("AI selected rank $selectedRank/${scoredPlayCandidates.size}: ${selected.describeForSelection(stateAfterHuman)}.")
                appendLine(analysis.summary)
            }.trim(),
        )
    }.getOrNull()

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
