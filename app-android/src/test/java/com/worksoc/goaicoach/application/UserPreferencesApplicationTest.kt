package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.persistence.UserPreferencesSnapshot
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
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
        assertEquals(false, plan.topMovesEnabled)
    }

    @Test
    fun buildsSnapshotFromCurrentUiSettings() {
        val setup = PlayerSetup()

        val snapshot = buildUserPreferencesSnapshot(
            playerSetup = setup,
            ruleset = Ruleset.Chinese,
            topMovesEnabled = false,
            showCoordinates = false,
            showMoveNumbers = true,
            showLastMoveRing = false,
            showOwnershipOverlay = false,
        )

        assertEquals(setup, snapshot.playerSetup)
        assertEquals(Ruleset.Chinese, snapshot.ruleset)
        assertFalse(snapshot.topMovesEnabled)
        assertFalse(snapshot.showCoordinates)
        assertTrue(snapshot.showMoveNumbers)
        assertFalse(snapshot.showLastMoveRing)
        assertFalse(snapshot.showOwnershipOverlay)
    }
}
