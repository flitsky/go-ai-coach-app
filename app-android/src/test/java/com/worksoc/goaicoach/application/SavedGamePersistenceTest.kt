package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.savedgame.*
import com.worksoc.goaicoach.persistence.SavedGameSessionCodec
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
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
            scoreSnapshots = emptyList(),
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
            scoreSnapshots = emptyList(),
            nowMillis = 10L,
        )

        assertEquals(SavedGamePersistencePlan.Skip, plan)
    }

    @Test
    fun clearsWhenGameIsEnded() {
        val endedPlan = planSavedGamePersistence(
            hasCheckedSavedSession = true,
            shouldShowResumePrompt = false,
            isGameEnded = true,
            gameState = playableState(),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            scoreSnapshots = emptyList(),
            nowMillis = 10L,
        )

        assertEquals(SavedGamePersistencePlan.Clear, endedPlan)
    }

    @Test
    fun skipsWhenNotResumable() {
        val emptyPlan = planSavedGamePersistence(
            hasCheckedSavedSession = true,
            shouldShowResumePrompt = false,
            isGameEnded = false,
            gameState = GameState.empty(),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            scoreSnapshots = emptyList(),
            nowMillis = 10L,
        )

        assertEquals(SavedGamePersistencePlan.Skip, emptyPlan)
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
            scoreSnapshots = emptyList(),
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
            scoreSnapshots = emptyList(),
            nowMillis = 123L,
        )

        assertTrue(plan is SavedGamePersistencePlan.Save)
        assertEquals(state, (plan as SavedGamePersistencePlan.Save).snapshot.gameState)
    }

    @Test
    fun savedGameSessionCodecSerializesAndDeserializesScoreSnapshots() {
        val state = playableState()
        val scoreSnapshots = listOf(
            ScoreSnapshot(moveNumber = 1, whiteScoreLead = -4.5, whiteWinRate = 0.85, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = -2.1, whiteWinRate = 0.52, source = ScoreSnapshotSource.LocalAreaEstimate)
        )
        val snapshot = SavedGameSnapshot(
            gameState = state,
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 999L,
            scoreSnapshots = scoreSnapshots
        )
        val encoded = SavedGameSessionCodec.encode(snapshot)
        val decoded = SavedGameSessionCodec.decode(encoded)

        assertTrue(decoded != null)
        assertEquals(scoreSnapshots.size, decoded!!.scoreSnapshots.size)
        assertEquals(1, decoded.scoreSnapshots[0].moveNumber)
        assertEquals(-4.5, decoded.scoreSnapshots[0].whiteScoreLead!!, 0.001)
        assertEquals(0.85, decoded.scoreSnapshots[0].whiteWinRate!!, 0.001)
        assertEquals(ScoreSnapshotSource.EngineEstimate, decoded.scoreSnapshots[0].source)

        assertEquals(2, decoded.scoreSnapshots[1].moveNumber)
        assertEquals(-2.1, decoded.scoreSnapshots[1].whiteScoreLead!!, 0.001)
        assertEquals(0.52, decoded.scoreSnapshots[1].whiteWinRate!!, 0.001)
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, decoded.scoreSnapshots[1].source)
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
                scoreSnapshots = emptyList(),
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
                scoreSnapshots = emptyList(),
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

        override fun readRawJson(): String? = null
    }

    private fun playableState(): GameState =
        GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
}
