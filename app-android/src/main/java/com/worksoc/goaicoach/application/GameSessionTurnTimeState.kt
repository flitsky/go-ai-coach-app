package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.StoneColor
import java.util.Locale

internal data class GameSessionTurnTimeState(
    val currentTurnPlayer: StoneColor,
    val currentTurnStartedAtMillis: Long,
    val blackAccumulatedMillis: Long = 0L,
    val whiteAccumulatedMillis: Long = 0L,
) {
    fun recordMove(
        player: StoneColor,
        nowMillis: Long,
        nextPlayer: StoneColor,
    ): TurnTimeMoveUpdate {
        val elapsedMillis = (nowMillis - currentTurnStartedAtMillis).coerceAtLeast(0L)
        val after = copy(
            currentTurnPlayer = nextPlayer,
            currentTurnStartedAtMillis = nowMillis,
            blackAccumulatedMillis = blackAccumulatedMillis + if (player == StoneColor.Black) elapsedMillis else 0L,
            whiteAccumulatedMillis = whiteAccumulatedMillis + if (player == StoneColor.White) elapsedMillis else 0L,
        )
        return TurnTimeMoveUpdate(
            player = player,
            elapsedMillis = elapsedMillis,
            before = this,
            after = after,
        )
    }

    fun restartCurrentTurn(
        state: GameState,
        nowMillis: Long,
    ): GameSessionTurnTimeState =
        copy(
            currentTurnPlayer = state.nextPlayer,
            currentTurnStartedAtMillis = nowMillis,
        )

    fun summaryText(): String =
        "Time B ${blackAccumulatedMillis.toSecondsText()} / W ${whiteAccumulatedMillis.toSecondsText()}"

    fun debugText(nowMillis: Long): String =
        "blackMillis=$blackAccumulatedMillis, whiteMillis=$whiteAccumulatedMillis, " +
            "currentTurn=${currentTurnPlayer.label}, currentElapsedMillis=${(nowMillis - currentTurnStartedAtMillis).coerceAtLeast(0L)}"

    fun runtimeText(): String =
        "B=${blackAccumulatedMillis.toSecondsText()} W=${whiteAccumulatedMillis.toSecondsText()} current=${currentTurnPlayer.label}"

    companion object {
        fun reset(
            state: GameState,
            nowMillis: Long,
        ): GameSessionTurnTimeState =
            GameSessionTurnTimeState(
                currentTurnPlayer = state.nextPlayer,
                currentTurnStartedAtMillis = nowMillis,
            )
    }
}

internal data class TurnTimeMoveUpdate(
    val player: StoneColor,
    val elapsedMillis: Long,
    val before: GameSessionTurnTimeState,
    val after: GameSessionTurnTimeState,
) {
    fun runtimeText(): String =
        "player=${player.label} elapsed=${elapsedMillis.toSecondsText()} total=${after.runtimeText()}"
}

internal fun Long.toSecondsText(): String =
    String.format(Locale.US, "%.1fs", this / 1000.0)
