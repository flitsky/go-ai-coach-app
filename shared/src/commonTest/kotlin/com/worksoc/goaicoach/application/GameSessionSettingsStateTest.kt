package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.HandicapKomi
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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

    /**
     * 접바둑 전환이 **덤을 함께** 옮기는지(refactor backlog #93) — 규칙표 자체는
     * `HandicapKomiPolicyTest`가 갖고, 여기서는 상태 전이가 그 규칙을 실제로 거치는지만 본다.
     */
    @Test
    fun handicapChangeCarriesKomiWithIt() {
        val even = GameSessionSettingsState(
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = false,
            boardSize = BoardSize.Nineteen,
        )

        val handicap = even.applyHandicap(3)
        assertEquals(3, handicap.handicapCount)
        assertEquals(HandicapKomi, handicap.komi, "호선 → 3점에서 덤이 0.5로 바뀌지 않았다")

        val moreStones = handicap.applyHandicap(5)
        assertEquals(HandicapKomi, moreStones.komi)

        val backToEven = moreStones.applyHandicap(0)
        assertEquals(0, backToEven.handicapCount)
        assertEquals(DefaultKomi, backToEven.komi, "0.5 그대로 호선으로 돌아왔는데 기본 덤으로 되돌리지 않았다")

        val userKomi = moreStones.applyKomi(7.5).applyHandicap(0)
        assertEquals(7.5, userKomi.komi, "사용자가 고친 덤 7.5를 호선으로 돌아오며 덮어썼다")
    }

    /**
     * 판 크기를 줄여 접바둑이 **클램프**돼도(19줄 9점 → 9줄 5점) 덤은 그대로다 — 접바둑이
     * 접바둑으로 남으므로 규칙 2(점수만 바꿈)와 같다. 클램프가 호선까지 내려가는 판 크기는 없다.
     */
    @Test
    fun boardSizeClampKeepsKomi() {
        val nineStones = GameSessionSettingsState(
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = false,
            boardSize = BoardSize.Nineteen,
            handicapCount = 9,
            komi = 7.5,
        )

        val clamped = nineStones.applyBoardSize(BoardSize.Nine)

        assertEquals(5, clamped.handicapCount)
        assertEquals(7.5, clamped.komi)
    }
}
