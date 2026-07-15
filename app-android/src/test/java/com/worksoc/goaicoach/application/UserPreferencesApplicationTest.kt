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
import com.worksoc.goaicoach.shared.SearchTimeLimit
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
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds),
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
        assertEquals(5_000L, plan.runtime.engineProfile.analysisLimit.timeMillis)
        assertEquals(false, plan.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Study, plan.autoPlayDelaySetting)
        assertEquals(
            GameSettings(
                ruleset = Ruleset.Chinese,
                topMovesEnabled = false,
                autoPlayDelaySetting = AutoPlayDelaySetting.Study,
                searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds),
                boardSize = BoardSize.Nine,
                handicapCount = 0,
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
            handicapCount = 5,
            topMovesEnabled = false,
            showCoordinates = false,
            showMoveNumbers = true,
            showLastMoveRing = false,
            showOwnershipOverlay = false,
            autoPlayDelaySetting = AutoPlayDelaySetting.Short,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds),
        )

        assertEquals(setup, snapshot.playerSetup)
        assertEquals(Ruleset.Chinese, snapshot.ruleset)
        assertEquals(5, snapshot.handicapCount)
        assertFalse(snapshot.topMovesEnabled)
        assertFalse(snapshot.showCoordinates)
        assertTrue(snapshot.showMoveNumbers)
        assertFalse(snapshot.showLastMoveRing)
        assertFalse(snapshot.showOwnershipOverlay)
        assertEquals(AutoPlayDelaySetting.Short.millis, snapshot.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds), snapshot.searchTimeSettings)
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
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.Off),
            topMovesEnabled = true,
            boardSize = BoardSize.Nine,
            handicapCount = 5,
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
        assertEquals(5, snapshot.handicapCount)
        assertTrue(snapshot.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Study.millis, snapshot.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(SearchTimeLimit.Off), snapshot.searchTimeSettings)
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
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinOneSecond),
            topMovesEnabled = true,
            boardSize = BoardSize.Nine,
            handicapCount = 3,
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
                isDirectPlayEnabled = true,
            ),
            store = store,
        )

        val saved = store.saved
        assertEquals(setup, saved.playerSetup)
        assertEquals(Ruleset.Japanese, saved.ruleset)
        assertEquals(3, saved.handicapCount)
        assertTrue(saved.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Short.millis, saved.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinOneSecond), saved.searchTimeSettings)
        assertTrue(saved.showCoordinates)
        assertFalse(saved.showMoveNumbers)
        assertTrue(saved.showLastMoveRing)
        assertTrue(saved.showOwnershipOverlay)
        assertTrue(saved.isDirectPlayEnabled)
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
