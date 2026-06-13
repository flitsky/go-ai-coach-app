package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.SearchTimeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionSettingsStateTest {
    @Test
    fun exposesMatchModeFromPlayerSetup() {
        val state = GameSessionSettingsState(
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Ai),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = false,
        )

        assertEquals(MatchMode.AiVsAi, state.matchMode)
    }

    @Test
    fun appliesSearchTimeSettingsAsNormalizedDomainState() {
        val state = GameSessionSettingsState(
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = false,
        )

        val updated = state.applySearchTimeSettings(
            SearchTimeSettings(
                b16Millis = 123L,
                b32Millis = 999L,
                b64Millis = 42L,
                timeCapEnabled = false,
            ),
        )

        assertEquals(SearchTimeSettings(timeCapEnabled = false), updated.searchTimeSettings)
        assertFalse(updated.searchTimeSettings.timeCapEnabled)
    }

    @Test
    fun togglesTopMovesWithoutChangingOtherSettings() {
        val state = GameSessionSettingsState(
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
            searchTimeSettings = SearchTimeSettings(timeCapEnabled = false),
            topMovesEnabled = false,
        )

        val shown = state.showTopMoves()
        val hidden = shown.hideTopMoves()

        assertTrue(shown.topMovesEnabled)
        assertFalse(hidden.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Slow, hidden.autoPlayDelaySetting)
        assertEquals(SearchTimeSettings(timeCapEnabled = false), hidden.searchTimeSettings)
    }

    @Test
    fun appliesSavedGameRestoreSettingsTogether() {
        val state = GameSessionSettingsState(
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(timeCapEnabled = false),
            topMovesEnabled = false,
        )
        val restoredSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        val restored = state.applySavedGameRestore(
            restoredSetup = restoredSetup,
            restoredTopMovesEnabled = true,
        )

        assertEquals(restoredSetup, restored.playerSetup)
        assertTrue(restored.topMovesEnabled)
        assertEquals(SearchTimeSettings(timeCapEnabled = false), restored.searchTimeSettings)
    }
}
