package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GameUiEventTest {
    @Test
    fun gameUiEventsCarryTypedUserIntent() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val playAt = GameUiEvent.PlayAt(coordinate)
        val submitMove = GameUiEvent.SubmitMove(Move.Pass(StoneColor.Black))
        val setup = PlayerSetup()
        val changeSetup = GameUiEvent.ChangePlayerSetup(setup)
        val changeDelay = GameUiEvent.ChangeAutoPlayDelay(AutoPlayDelaySetting.Slow)
        val changeRuleset = GameUiEvent.ChangeScoringRule(Ruleset.Chinese)
        val changeOptions = GameUiEvent.ChangeUxOptions(KaTrainUxOptions(showMoveNumbers = true))

        assertEquals(coordinate, playAt.coordinate)
        assertEquals(Move.Pass(StoneColor.Black), submitMove.move)
        assertSame(setup, changeSetup.setup)
        assertEquals(AutoPlayDelaySetting.Slow, changeDelay.setting)
        assertEquals(Ruleset.Chinese, changeRuleset.ruleset)
        assertEquals(true, changeOptions.options.showMoveNumbers)
    }
}
