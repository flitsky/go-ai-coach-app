package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.savedgame.*
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameApplicationRunnerTest {
    @Test
    fun promptRunnerLoadsStoreAndAppliesPrompt() {
        val store = RecordingSavedGameStore()
        val snapshot = savedGameSnapshot()
        store.snapshot = snapshot
        var applied: SavedSessionPromptPlan? = null

        runSavedSessionPromptApplication(
            SavedSessionPromptRunRequest(
                store = store,
                applyPrompt = { prompt -> applied = prompt },
            ),
        )

        assertEquals(snapshot, applied?.pendingSavedSession)
        assertEquals(true, applied?.shouldShowResumePrompt)
        assertEquals(true, applied?.hasCheckedSavedSession)
    }

    @Test
    fun persistenceRunnerBuildsAndAppliesStoreAction() {
        val store = RecordingSavedGameStore()
        val state = playableState()

        runSavedGamePersistenceApplication(
            SavedGamePersistenceRunRequest(
                savedSessionUiState = SavedSessionUiState(
                    hasCheckedSavedSession = true,
                    shouldShowResumePrompt = false,
                ),
                isGameEnded = false,
                gameState = state,
                playerSetup = PlayerSetup(),
                playLevel = PlayLevelSetting(level = 4),
                topMovesEnabled = true,
                scoreSnapshots = emptyList(),
                nowMillis = 456L,
                store = store,
            ),
        )

        assertEquals(listOf("save:456"), store.calls)
        assertEquals(state, store.snapshot?.gameState)
    }

    @Test
    fun restoreRunnerBlocksWithoutApplyingRestoreWhileEngineBusy() {
        val messages = mutableListOf<String>()
        var restore: SavedGameRestorePlan? = null

        val result = runSavedGameRestoreApplication(
            SavedGameRestoreRunRequest(
                snapshot = savedGameSnapshot(),
                currentProfile = EngineProfile(),
                defaultPlayLevel = PlayLevelSetting(),
                isEngineBusy = true,
                isEngineReady = true,
                searchTimeSettings = SearchTimeSettings(),
                showMessage = { message -> messages += message },
                applyRestore = { plan -> restore = plan },
            ),
        )

        assertSame(SavedGameRestoreRunResult.Blocked, result)
        assertEquals(
            listOf("Engine is busy. Restore the saved game after the current action."),
            messages,
        )
        assertNull(restore)
    }

    @Test
    fun restoreRunnerAppliesRestoreAndReportsSyncDecision() {
        var restore: SavedGameRestorePlan? = null

        val result = runSavedGameRestoreApplication(
            SavedGameRestoreRunRequest(
                snapshot = savedGameSnapshot(),
                currentProfile = EngineProfile(),
                defaultPlayLevel = PlayLevelSetting(),
                isEngineBusy = false,
                isEngineReady = true,
                searchTimeSettings = SearchTimeSettings(),
                showMessage = {},
                applyRestore = { plan -> restore = plan },
            ),
        )

        assertTrue(result is SavedGameRestoreRunResult.Restored)
        val restored = result as SavedGameRestoreRunResult.Restored
        assertEquals("Black E5", restore?.lastMoveText)
        assertEquals(restore?.gameState, restored.gameState)
        assertEquals(restore?.runtime?.engineProfile, restored.engineProfile)
        assertEquals(true, restored.syncEngineAfterRestore)
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

    private fun savedGameSnapshot(): SavedGameSnapshot =
        SavedGameSnapshot(
            gameState = playableState(),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 123L,
        )

    private fun playableState(): GameState =
        GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
}
