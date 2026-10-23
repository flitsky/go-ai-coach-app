package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePlayerSetup
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.resumeSummaryFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「이전 대국 이어하기」의 둘째 줄(#287) — 대국 · 상대 · 내 돌 · 판 크기와 수. 사용자가 고른 구성의 예 넷이 그대로 나온다. */
class ResumeSummaryTest {
    private val korean = UiStrings.forLanguage(UiLanguage.Korean)

    @Test
    fun rankMatchNamesTheModeAndTheRank() {
        val setup = rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(8))

        assertEquals("승급 대국 · 8급 AI · 흑 · 19줄 47수", summaryOf(setup, BoardSize.Nineteen, moves = 47))
    }

    @Test
    fun aCharacterGameNamesTheCharacterWithItsRank() {
        val banSang = BotCharacterCatalog.all.single { korean.botCharacterName(it).endsWith("반상") }
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai, playLevel = banSang.toPlayLevelSetting()!!),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        assertEquals("${korean.botCharacterName(banSang)}(1급) · 백 · 13줄 47수", summaryOf(setup, BoardSize.Thirteen, moves = 47))
    }

    /** 접바둑은 늘 흑이 놓는다 — 내가 흑이면 내 돌 자리에 「흑 3점」으로 합치고, 내가 백이면 따로 적는다. */
    @Test
    fun handicapStonesAreCountedOnBlack() {
        val panda = BotCharacterCatalog.all.single { korean.botCharacterName(it).endsWith("판다") }
        val userBlack = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai, playLevel = panda.toPlayLevelSetting()!!),
        )
        val userWhite = PlayerSetup(black = userBlack.white, white = userBlack.black)

        assertEquals("${korean.botCharacterName(panda)}(15급) · 흑 3점 · 9줄 21수", summaryOf(userBlack, BoardSize.Nine, moves = 21, handicap = 3))
        assertEquals("${korean.botCharacterName(panda)}(15급) · 백 · 흑 3점 · 9줄 21수", summaryOf(userWhite, BoardSize.Nine, moves = 21, handicap = 3))
    }

    @Test
    fun twoPlayersHaveNoSideOfTheirOwn() {
        val setup = PlayerSetup(black = SidePlayerSetup(controller = SeatController.Human), white = SidePlayerSetup(controller = SeatController.Human))

        assertEquals("둘이서 · 19줄 112수", summaryOf(setup, BoardSize.Nineteen, moves = 112))
    }

    private fun summaryOf(setup: PlayerSetup, size: BoardSize, moves: Int, handicap: Int = 0): String {
        val state = GameState.empty(boardSize = size, ruleset = Ruleset.Japanese)
            .copy(moves = List(moves) { Move.Pass(StoneColor.Black) }, handicapCount = handicap)
        val snapshot = SavedGameSnapshot(
            gameState = state,
            playerSetup = setup,
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = 0L,
        )
        return resumeSummaryFor(korean, snapshot, engineName = "KataGo")
    }
}
