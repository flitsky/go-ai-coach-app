package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.SearchTimeLimit
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.BoardSize
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
            boardSize = BoardSize.Nine,
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
            boardSize = BoardSize.Nine,
        )

        val updated = state.applySearchTimeSettings(SearchTimeSettings(SearchTimeLimit.Off))

        assertEquals(SearchTimeSettings(SearchTimeLimit.Off), updated.searchTimeSettings)
        assertEquals(SearchTimeLimit.Off, updated.searchTimeSettings.limit)
    }

    @Test
    fun togglesTopMovesWithoutChangingOtherSettings() {
        val state = GameSessionSettingsState(
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.Off),
            topMovesEnabled = false,
            boardSize = BoardSize.Nine,
        )

        val shown = state.showTopMoves()
        val hidden = shown.hideTopMoves()

        assertTrue(shown.topMovesEnabled)
        assertFalse(hidden.topMovesEnabled)
        assertEquals(AutoPlayDelaySetting.Slow, hidden.autoPlayDelaySetting)
        assertEquals(SearchTimeSettings(SearchTimeLimit.Off), hidden.searchTimeSettings)
    }

    @Test
    fun appliesSavedGameRestoreSettingsTogether() {
        val state = GameSessionSettingsState(
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.Off),
            topMovesEnabled = false,
            boardSize = BoardSize.Nine,
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
        assertEquals(SearchTimeSettings(SearchTimeLimit.Off), restored.searchTimeSettings)
    }
}
