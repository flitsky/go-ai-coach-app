package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.play.shouldShowFinalResultBadge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #191 — 새 대국 준비 구간(끝남 플래그 참 + 0수)에는 결과 배지가 「무승부」로 뜨면 안 된다. */
class FinalResultBadgeVisibilityTest {

    @Test
    fun theNewGamePreparationWindowShowsNoBadge() {
        assertFalse(shouldShowFinalResultBadge(isGameEnded = true, moveCount = 0))
    }

    @Test
    fun realGameEndsStillShowTheBadge() {
        assertTrue("기권은 1수로 끝날 수 있다", shouldShowFinalResultBadge(isGameEnded = true, moveCount = 1))
        assertTrue("양통과", shouldShowFinalResultBadge(isGameEnded = true, moveCount = 2))
        assertTrue(shouldShowFinalResultBadge(isGameEnded = true, moveCount = 120))
    }

    @Test
    fun aGameInProgressNeverShowsTheBadge() {
        assertFalse(shouldShowFinalResultBadge(isGameEnded = false, moveCount = 30))
    }
}
