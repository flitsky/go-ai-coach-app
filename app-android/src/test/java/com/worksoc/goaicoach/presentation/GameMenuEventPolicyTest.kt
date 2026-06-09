package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.shared.Ruleset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMenuEventPolicyTest {
    @Test
    fun newGameCollapsesMenuSoBoardGetsFocus() {
        assertTrue(shouldCollapseMenuAfterEvent(GameUiEvent.StartConfiguredGame))
    }

    @Test
    fun configurationEventsKeepMenuOpen() {
        assertFalse(shouldCollapseMenuAfterEvent(GameUiEvent.CopyDebugReport))
        assertFalse(shouldCollapseMenuAfterEvent(GameUiEvent.ChangeScoringRule(Ruleset.Chinese)))
        assertFalse(shouldCollapseMenuAfterEvent(GameUiEvent.ChangeUxOptions(KaTrainUxOptions(showMoveNumbers = true))))
    }
}
