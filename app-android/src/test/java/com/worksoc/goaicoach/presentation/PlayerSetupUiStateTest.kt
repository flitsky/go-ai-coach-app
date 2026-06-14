package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSetupUiStateTest {
    @Test
    fun buildsLabelsForHumanAndAiSeats() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7),
            ),
        )

        val state = buildPlayerSetupUiState(
            setup = setup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
            engineName = "KataGo",
        )

        assertEquals(setup, state.setup)
        assertEquals("흑", state.black.seatLabel)
        assertEquals("플레이어", state.black.controllerLabel)
        assertEquals("백", state.white.seatLabel)
        assertEquals("AI", state.white.controllerLabel)
        assertEquals("초급", state.white.aiLevelGroupLabel)
        assertEquals("7단계", state.white.aiLevelLabel)
        assertTrue(state.white.aiDetailText.contains("32 visits"))
        assertFalse(state.showAutoPlayDelay)
        assertTrue(state.summaryText.contains("KataGo 초급 7단계"))
    }

    @Test
    fun autoPlayDelayIsVisibleOnlyWhenBothSeatsAreAi() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        val state = buildPlayerSetupUiState(
            setup = setup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Study,
            engineName = "KataGo",
        )

        assertTrue(state.showAutoPlayDelay)
        assertEquals(AutoPlayDelaySetting.Study, state.autoPlayDelaySetting)
    }
}
