package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.savedgame.*

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGamePersistenceTest {
    @Test
    fun skipsUntilSavedSessionCheckCompletes() {
        val plan = planSavedGamePersistence(
            hasCheckedSavedSession = false,
            shouldShowResumePrompt = false,
            isGameEnded = false,
            gameState = playableState(),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            nowMillis = 10L,
        )

        assertEquals(SavedGamePersistencePlan.Skip, plan)
    }

    @Test
    fun skipsWhileResumePromptIsVisible() {
        val plan = planSavedGamePersistence(
            hasCheckedSavedSession = true,
            shouldShowResumePrompt = true,
            isGameEnded = false,
            gameState = playableState(),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            nowMillis = 10L,
        )

        assertEquals(SavedGamePersistencePlan.Skip, plan)
    }

    @Test
    fun clearsWhenGameIsEndedOrNotResumable() {
        val endedPlan = planSavedGamePersistence(
            hasCheckedSavedSession = true,
            shouldShowResumePrompt = false,
            isGameEnded = true,
            gameState = playableState(),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            nowMillis = 10L,
        )
        val emptyPlan = planSavedGamePersistence(
            hasCheckedSavedSession = true,
            shouldShowResumePrompt = false,
            isGameEnded = false,
            gameState = GameState.empty(),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            nowMillis = 10L,
        )

        assertEquals(SavedGamePersistencePlan.Clear, endedPlan)
        assertEquals(SavedGamePersistencePlan.Clear, emptyPlan)
    }

    @Test
    fun savesResumableGameSnapshot() {
        val state = playableState()
        val level = PlayLevelSetting(level = 2)
        val plan = planSavedGamePersistence(
            hasCheckedSavedSession = true,
            shouldShowResumePrompt = false,
            isGameEnded = false,
            gameState = state,
            playerSetup = PlayerSetup(),
            playLevel = level,
            topMovesEnabled = true,
            nowMillis = 99L,
        )

        assertTrue(plan is SavedGamePersistencePlan.Save)
        val snapshot = (plan as SavedGamePersistencePlan.Save).snapshot
        assertEquals(state, snapshot.gameState)
        assertEquals(level, snapshot.playLevel)
        assertEquals(true, snapshot.topMovesEnabled)
        assertEquals(99L, snapshot.savedAtMillis)
    }

    @Test
    fun acceptsSavedSessionUiStateAsPromptGate() {
        val state = playableState()
        val savedSessionUiState = SavedSessionUiState(
            hasCheckedSavedSession = true,
            shouldShowResumePrompt = false,
        )

        val plan = planSavedGamePersistence(
            savedSessionUiState = savedSessionUiState,
            isGameEnded = false,
            gameState = state,
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            nowMillis = 123L,
        )

        assertTrue(plan is SavedGamePersistencePlan.Save)
        assertEquals(state, (plan as SavedGamePersistencePlan.Save).snapshot.gameState)
    }

    @Test
    fun persistenceRunnerAppliesSaveAndClearPlansToStore() {
        val store = RecordingSavedGameStore()
        val state = playableState()

        runSavedGamePersistence(
            request = SavedGamePersistenceRequest(
                savedSessionUiState = SavedSessionUiState(
                    hasCheckedSavedSession = true,
                    shouldShowResumePrompt = false,
                ),
                isGameEnded = false,
                gameState = state,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(level = 3),
                topMovesEnabled = true,
                nowMillis = 777L,
            ),
            store = store,
        )

        assertEquals(listOf("save:777"), store.calls)
        assertEquals(state, store.snapshot?.gameState)

        runSavedGamePersistence(
            request = SavedGamePersistenceRequest(
                savedSessionUiState = SavedSessionUiState(
                    hasCheckedSavedSession = true,
                    shouldShowResumePrompt = false,
                ),
                isGameEnded = true,
                gameState = state,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(),
                topMovesEnabled = false,
                nowMillis = 778L,
            ),
            store = store,
        )

        assertEquals(listOf("save:777", "clear"), store.calls)
    }

    private class RecordingSavedGameStore : SavedGameStorePort {
        val calls = mutableListOf<String>()
        var snapshot: SavedGameSnapshot? = null

        override fun save(snapshot: SavedGameSnapshot) {
            this.snapshot = snapshot
            calls += "save:${snapshot.savedAtMillis}"
        }

        override fun load(): SavedGameSnapshot? = snapshot

        override fun clear() {
            snapshot = null
            calls += "clear"
        }
    }

    private fun playableState(): GameState =
        GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
}
