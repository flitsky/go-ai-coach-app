package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.ui.play.isFinalResultKnown
import com.worksoc.goaicoach.ui.play.shouldShowFinalResultBadge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 결과 배지는 **승자를 모를 때** 「무승부」라고 말한다 — 그래서 모르는 구간에는 그리지 않는다.
 *
 * - #191: 새 대국 준비 구간(끝남 플래그 참 + 0수).
 * - 2026-10-08: 양통과로 끝난 뒤 **계가 판정을 기다리는 몇 초**(판정 결과 창이 뜨기 직전에 「무승부」가 잠깐 보였다).
 */
class FinalResultBadgeVisibilityTest {

    @Test
    fun theNewGamePreparationWindowShowsNoBadge() {
        assertFalse(shouldShowFinalResultBadge(isGameEnded = true, moveCount = 0, isResultKnown = true))
    }

    @Test
    fun realGameEndsStillShowTheBadge() {
        assertTrue("기권은 1수로 끝날 수 있다", shouldShowFinalResultBadge(isGameEnded = true, moveCount = 1, isResultKnown = true))
        assertTrue("양통과", shouldShowFinalResultBadge(isGameEnded = true, moveCount = 2, isResultKnown = true))
        assertTrue(shouldShowFinalResultBadge(isGameEnded = true, moveCount = 120, isResultKnown = true))
    }

    @Test
    fun aGameInProgressNeverShowsTheBadge() {
        assertFalse(shouldShowFinalResultBadge(isGameEnded = false, moveCount = 30, isResultKnown = true))
    }

    @Test
    fun anEndedGameStillWaitingForItsJudgementShowsNoBadge() {
        assertFalse(
            "판정을 기다리는 동안 배지가 뜬다 — 승자가 없어 「무승부」라고 말한다.",
            shouldShowFinalResultBadge(isGameEnded = true, moveCount = 64, isResultKnown = false),
        )
    }

    @Test
    fun theResultIsUnknownUntilTheJudgementArrivesAfterTwoPasses() {
        val passedOut = GameState.empty(ruleset = Ruleset.Japanese)
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))

        assertFalse("양통과 직후 — 판정이 아직 없다", isFinalResultKnown(passedOut, judgement = null))
        assertTrue("판정이 왔다", isFinalResultKnown(passedOut, judgement = judgement(winner = StoneColor.Black)))
        assertTrue("진짜 무승부 — 판정은 있고 승자가 없다", isFinalResultKnown(passedOut, judgement = judgement(winner = null)))
    }

    @Test
    fun aResignedGameNeedsNoJudgement() {
        val resigned = GameState.empty(ruleset = Ruleset.Japanese).play(Move.Resign(StoneColor.Black))

        assertTrue("기권으로 끝난 판은 판정이 없다 — 마지막 수가 승자를 말한다", isFinalResultKnown(resigned, judgement = null))
    }

    private fun judgement(winner: StoneColor?): FinalScoreJudgement =
        FinalScoreJudgement(
            winner = winner,
            margin = if (winner == null) 0.0 else 8.5,
            ruleset = Ruleset.Japanese,
            isEstimatedDisplay = false,
            removedBlack = 0,
            removedWhite = 0,
            blackArea = 31.0,
            whiteAreaWithKomi = 22.5,
            capturedByBlack = 0,
            capturedByWhite = 0,
            komi = 6.5,
        )
}
