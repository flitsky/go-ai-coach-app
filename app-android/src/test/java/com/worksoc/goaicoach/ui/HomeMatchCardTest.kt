package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.persistence.ReferenceGameHistoryId
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.ui.home.lastPlayedCharacterOrNull
import com.worksoc.goaicoach.ui.home.twoLineCardTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 홈 대국 카드 둘(#288, 사용자 2026-10-24) — 「일반 대국」 그림이 언제 캐릭터이고 언제 흑백 돌인지, 카드 이름을 어디서 두 줄로 접는지.
 */
class HomeMatchCardTest {

    private val vsPanda = PlayerSetup()
    private val vsLevel5 = PlayerSetup(
        black = SidePlayerSetup(controller = SeatController.Human),
        white = SidePlayerSetup(controller = SeatController.Ai, playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 5)),
    )
    private val twoPeople = PlayerSetup(
        black = SidePlayerSetup(controller = SeatController.Human),
        white = SidePlayerSetup(controller = SeatController.Human),
    )
    private val rankMatch = PlayerSetup(
        black = SidePlayerSetup(controller = SeatController.Human, humanGameType = HumanGameType.RankMeasure),
        white = SidePlayerSetup(controller = SeatController.Ai, playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 2)),
    )

    private fun played(id: String, at: Long, playerSetup: PlayerSetup) = GameHistoryEntry(
        id = id,
        playedAtMillis = at,
        boardSize = 9,
        ruleset = Ruleset.entries.first(),
        komi = 6.5,
        handicapCount = 0,
        playerSetup = playerSetup,
        moveCount = 40,
        humanColor = StoneColor.Black,
        winner = null,
    )

    private fun character(playerSetup: PlayerSetup, resumable: Boolean = false, history: List<GameHistoryEntry> = emptyList()) =
        lastPlayedCharacterOrNull(playerSetup, resumable) { history }?.id?.raw

    @Test
    fun aNewPlayerSeesTheStones() {
        assertNull(character(vsPanda))
    }

    /** 첫 설정 그대로(백 판다) 둔 사람 — 좌석만으로는 새 사람과 같아 보인다. 이어 둘 판이나 기록이 판다를 알려 준다. */
    @Test
    fun thePandaIsShownOnceSomeonePlayedIt() {
        assertEquals("fast_beginner_1", character(vsPanda, resumable = true))
        assertEquals("fast_beginner_1", character(vsPanda, history = listOf(played("a", 1L, vsPanda))))
    }

    @Test
    fun aPickedCharacterIsShown() {
        assertEquals("fast_beginner_5", character(vsLevel5))
    }

    /** 승급 대국의 상대는 급수로 정한 AI다 — 건너뛰고 그 앞의 일반 대국 상대를 보인다. 참고 기보는 사용자가 둔 판이 아니다. */
    @Test
    fun aRankMatchIsSkippedForTheLastStandardGame() {
        val history = listOf(
            played("a", 1L, vsLevel5),
            played("b", 2L, rankMatch),
            played(ReferenceGameHistoryId, 3L, vsPanda),
        )
        assertEquals("fast_beginner_5", character(rankMatch, resumable = true, history = history))
        assertNull(character(rankMatch, history = listOf(played("b", 2L, rankMatch))))
    }

    @Test
    fun twoPeopleOnOnePhoneHaveNoCharacter() {
        assertNull(character(twoPeople))
        assertNull(character(vsPanda, history = listOf(played("a", 1L, twoPeople))))
    }

    @Test
    fun cardTitlesFoldIntoTwoLinesInEveryLanguage() {
        assertEquals("일반\n대국", twoLineCardTitle("일반 대국"))
        assertEquals("승급\n대국", twoLineCardTitle("승급 대국"))
        assertEquals("Standard\nMatch", twoLineCardTitle("Standard Match"))
        assertEquals("Rank\nMatch", twoLineCardTitle("Rank Match"))
        assertEquals("通常\n対局", twoLineCardTitle("通常対局"))
        assertEquals("升级\n对局", twoLineCardTitle("升级对局"))
    }
}
