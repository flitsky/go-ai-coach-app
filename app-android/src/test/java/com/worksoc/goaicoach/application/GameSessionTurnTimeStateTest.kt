package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Test

class GameSessionTurnTimeStateTest {
    @Test
    fun resetStartsCurrentPlayerTimerWithZeroTotals() {
        val state = GameState.empty(nextPlayer = StoneColor.White)
        val timer = GameSessionTurnTimeState.reset(state, nowMillis = 1_000L)

        assertEquals(StoneColor.White, timer.currentTurnPlayer)
        assertEquals(1_000L, timer.currentTurnStartedAtMillis)
        assertEquals(0L, timer.blackAccumulatedMillis)
        assertEquals(0L, timer.whiteAccumulatedMillis)
        assertEquals("Time B 0.0s / W 0.0s", timer.summaryText())
    }

    @Test
    fun recordMoveAccumulatesElapsedMillisForMovingSideAndStartsNextTurn() {
        val timer = GameSessionTurnTimeState.reset(GameState.empty(), nowMillis = 1_000L)

        val blackUpdate = timer.recordMove(
            player = StoneColor.Black,
            nowMillis = 2_250L,
            nextPlayer = StoneColor.White,
        )
        val whiteUpdate = blackUpdate.after.recordMove(
            player = StoneColor.White,
            nowMillis = 4_500L,
            nextPlayer = StoneColor.Black,
        )

        assertEquals(1_250L, blackUpdate.elapsedMillis)
        assertEquals(1_250L, blackUpdate.after.blackAccumulatedMillis)
        assertEquals(StoneColor.White, blackUpdate.after.currentTurnPlayer)
        assertEquals(2_250L, whiteUpdate.elapsedMillis)
        assertEquals(1_250L, whiteUpdate.after.blackAccumulatedMillis)
        assertEquals(2_250L, whiteUpdate.after.whiteAccumulatedMillis)
        assertEquals("Time B 1.3s / W 2.3s", whiteUpdate.after.summaryText())
    }

    @Test
    fun recordMoveClampsNegativeElapsedMillisToZero() {
        val timer = GameSessionTurnTimeState.reset(GameState.empty(), nowMillis = 5_000L)

        val update = timer.recordMove(
            player = StoneColor.Black,
            nowMillis = 4_000L,
            nextPlayer = StoneColor.White,
        )

        assertEquals(0L, update.elapsedMillis)
        assertEquals(0L, update.after.blackAccumulatedMillis)
    }

    @Test
    fun restartCurrentTurnKeepsAccumulatedTotals() {
        val timer = GameSessionTurnTimeState(
            currentTurnPlayer = StoneColor.White,
            currentTurnStartedAtMillis = 1_000L,
            blackAccumulatedMillis = 2_000L,
            whiteAccumulatedMillis = 3_000L,
        )
        val state = GameState.empty(nextPlayer = StoneColor.Black)

        val restarted = timer.restartCurrentTurn(state, nowMillis = 9_000L)

        assertEquals(StoneColor.Black, restarted.currentTurnPlayer)
        assertEquals(9_000L, restarted.currentTurnStartedAtMillis)
        assertEquals(2_000L, restarted.blackAccumulatedMillis)
        assertEquals(3_000L, restarted.whiteAccumulatedMillis)
    }
}
