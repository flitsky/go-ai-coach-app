package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.preferences.*
import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesApplicationTest {
    @Test
    fun buildsInitialGameAndRuntimeFromSavedPreferences() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 3),
            ),
        )
        val preferences = UserPreferencesSnapshot(
            playerSetup = setup,
            ruleset = Ruleset.Chinese,
            topMovesEnabled = false,
            autoPlayDelayMillis = AutoPlayDelaySetting.Study.millis,
            searchTimeSettings = SearchTimeSettings(b32Millis = 4_000L),
            boardSize = BoardSize.Nine,
        )

        val plan = buildInitialUserPreferencesPlan(
            preferences = preferences,
            defaultPlayLevel = PlayLevelSetting(),
            currentProfile = EngineProfile(),
        )

        assertEquals(Ruleset.Chinese, plan.gameState.ruleset)
        assertEquals(StoneColor.Black, plan.gameState.nextPlayer)
        assertEquals(setup, plan.playerSetup)
        assertEquals(PlayLevelSetting(PlayLevelGroup.Beginner, level = 3), plan.runtime.playLevel)
        assertEquals(4_000L, plan.runtime.engineProfile.analysisLimit.timeMillis)
        assertEquals(false, plan.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Study, plan.autoPlayDelaySetting)
        assertEquals(
            GameSettings(
                ruleset = Ruleset.Chinese,
                topMovesEnabled = false,
                autoPlayDelaySetting = AutoPlayDelaySetting.Study,
                searchTimeSettings = SearchTimeSettings(b32Millis = 4_000L),
                boardSize = BoardSize.Nine,
            ),
            plan.settings,
        )
    }

    @Test
    fun buildsSnapshotFromCurrentUiSettings() {
        val setup = PlayerSetup()

        val snapshot = buildUserPreferencesSnapshot(
            playerSetup = setup,
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Chinese,
            topMovesEnabled = false,
            showCoordinates = false,
            showMoveNumbers = true,
            showLastMoveRing = false,
            showOwnershipOverlay = false,
            autoPlayDelaySetting = AutoPlayDelaySetting.Short,
            searchTimeSettings = SearchTimeSettings(b16Millis = 1_500L),
        )

        assertEquals(setup, snapshot.playerSetup)
        assertEquals(Ruleset.Chinese, snapshot.ruleset)
        assertFalse(snapshot.topMovesEnabled)
        assertFalse(snapshot.showCoordinates)
        assertTrue(snapshot.showMoveNumbers)
        assertFalse(snapshot.showLastMoveRing)
        assertFalse(snapshot.showOwnershipOverlay)
        assertEquals(AutoPlayDelaySetting.Short.millis, snapshot.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(b16Millis = 1_500L), snapshot.searchTimeSettings)
    }

    @Test
    fun buildsSnapshotFromSessionSettingsState() {
        val setup = PlayerSetup(
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Intermediate, level = 5),
            ),
        )
        val settingsState = GameSessionSettingsState(
            playerSetup = setup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Study,
            searchTimeSettings = SearchTimeSettings(timeCapEnabled = false),
            topMovesEnabled = true,
            boardSize = BoardSize.Nine,
        )

        val snapshot = buildUserPreferencesSnapshot(
            settingsState = settingsState,
            ruleset = Ruleset.Chinese,
            showCoordinates = true,
            showMoveNumbers = false,
            showLastMoveRing = true,
            showOwnershipOverlay = false,
        )

        assertEquals(setup, snapshot.playerSetup)
        assertEquals(Ruleset.Chinese, snapshot.ruleset)
        assertTrue(snapshot.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Study.millis, snapshot.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(timeCapEnabled = false), snapshot.searchTimeSettings)
        assertTrue(snapshot.showCoordinates)
        assertFalse(snapshot.showMoveNumbers)
        assertTrue(snapshot.showLastMoveRing)
        assertFalse(snapshot.showOwnershipOverlay)
    }

    @Test
    fun autosaveRunnerWritesCurrentPreferencesSnapshot() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 4),
            ),
        )
        val settingsState = GameSessionSettingsState(
            playerSetup = setup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Short,
            searchTimeSettings = SearchTimeSettings(b16Millis = 1_000L),
            topMovesEnabled = true,
            boardSize = BoardSize.Nine,
        )
        val store = RecordingUserPreferencesStore()

        runUserPreferencesAutosave(
            request = UserPreferencesAutosaveRequest(
                settingsState = settingsState,
                ruleset = Ruleset.Japanese,
                showCoordinates = true,
                showMoveNumbers = false,
                showLastMoveRing = true,
                showOwnershipOverlay = true,
            ),
            store = store,
        )

        val saved = store.saved
        assertEquals(setup, saved.playerSetup)
        assertEquals(Ruleset.Japanese, saved.ruleset)
        assertTrue(saved.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Short.millis, saved.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(b16Millis = 1_000L), saved.searchTimeSettings)
        assertTrue(saved.showCoordinates)
        assertFalse(saved.showMoveNumbers)
        assertTrue(saved.showLastMoveRing)
        assertTrue(saved.showOwnershipOverlay)
    }

    private class RecordingUserPreferencesStore : UserPreferencesStorePort {
        lateinit var saved: UserPreferencesSnapshot

        override fun save(snapshot: UserPreferencesSnapshot) {
            saved = snapshot
        }

        override fun load(): UserPreferencesSnapshot =
            if (::saved.isInitialized) saved else UserPreferencesSnapshot()
    }
}
