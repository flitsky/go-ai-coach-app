package com.worksoc.goaicoach.ui.history

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.ReferenceGameHistoryId
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.gameHistoryHideShortGamesLabelFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 #208 — 대국 기록 「10수 이하 기록 제외하기」. */
class GameHistoryShortGameFilterTest {

    private fun entry(id: String, moveCount: Int) = GameHistoryEntry(
        id = id,
        playedAtMillis = 0L,
        boardSize = 9,
        ruleset = Ruleset.entries.first(),
        komi = 6.5,
        handicapCount = 0,
        playerSetup = PlayerSetup(),
        moveCount = moveCount,
        humanColor = null,
        winner = null,
    )

    private val entries = listOf(
        entry(ReferenceGameHistoryId, 3), // 참고 기보 — 수가 적어도 남는다
        entry("one", 1),
        entry("ten", ShortGameMaxMoveCount),
        entry("eleven", ShortGameMaxMoveCount + 1),
        entry("long", 120),
    )

    /** 「이하」다 — 정확히 10수인 판도 숨긴다. 참고 기보는 늘 남는다. */
    @Test
    fun checkedHidesGamesOfTenMovesOrFewerButKeepsTheReferenceGame() {
        assertEquals(
            listOf(ReferenceGameHistoryId, "eleven", "long"),
            visibleGameHistoryEntries(entries, hideShortGames = true).map { it.id },
        )
    }

    @Test
    fun uncheckedShowsEverything() {
        assertEquals(entries, visibleGameHistoryEntries(entries, hideShortGames = false))
    }

    /** 수는 상수를 따른다 — 문구에 손으로 적지 않는다. */
    @Test
    fun theLabelNamesTheConstantInEveryLanguage() {
        assertEquals(10, ShortGameMaxMoveCount)
        assertEquals(
            "10수 이하 기록 제외하기",
            gameHistoryHideShortGamesLabelFor(UiLanguage.Korean, ShortGameMaxMoveCount),
        )
        UiLanguage.entries.forEach { language ->
            assertTrue(
                "$language 라벨이 바뀐 수(25)를 따라오지 않는다",
                gameHistoryHideShortGamesLabelFor(language, 25).contains("25"),
            )
        }
    }
}
