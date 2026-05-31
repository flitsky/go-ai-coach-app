package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

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
): TurnOutcome {
    val humanStatus = engineAdapter.playMove(humanMove)
    val humanText = humanMove.describe(stateAfterHuman.boardSize)

    if (stateAfterHuman.isBoardFull()) {
        return TurnOutcome(
            gameState = stateAfterHuman,
            engineMessage = "${humanStatus.message}\nBoard is full.",
            candidateText = "Game ended after $humanText.",
            lastMoveText = humanText,
        )
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
        MatchMode.LocalTwoPlayer -> true
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
