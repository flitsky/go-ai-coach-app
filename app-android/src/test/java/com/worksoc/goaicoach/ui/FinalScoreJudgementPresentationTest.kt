package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.score.buildLocalFinalScoreDisplayPlan
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.scoring.BoardScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 끝난 판의 판정([com.worksoc.goaicoach.application.score.FinalScoreJudgement])을 결과 팝업의 세 줄로
 * 옮기는 app-android 표시 확장(`FinalScoreJudgementPresentationExtensions.kt`)을 잰다.
 *
 * `ScoreDisplayApplicationTest`에 있던 두 테스트다 — 그 파일이 `:shared`의 commonTest로 옮겨 갈 때
 * (refactor backlog #97) 이 둘은 app-android의 `internal` 확장과 `UiStrings`를 불러서 따라갈 수 없어 남았다.
 */
class FinalScoreJudgementPresentationTest {
    @Test
    fun finalScoreJudgementOmitsDecimalForWholeNumbers() {
        val state = GameState.empty(ruleset = Ruleset.Japanese)
            .copy(capturedByBlack = 5, capturedByWhite = 2)
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("W+1.5"),
            rawScore = "W+1.5",
            winner = StoneColor.White,
            margin = 1.5,
            blackArea = 20.0,
            whiteAreaWithKomi = 21.5,
            komi = 6.5,
            summary = "final",
        )

        val plan = buildLocalFinalScoreDisplayPlan(
            source = "test-final",
            state = state,
            finalScore = finalScore,
            previousSnapshots = emptyList(),
            detail = "test",
            engineMessage = "final",
            candidateText = "ended",
        )

        val strings = UiStringsKorean
        val judgement = plan.judgement ?: error("missing judgement")
        assertEquals("백 + 1.5집 승", judgement.resultText(strings))
        assertEquals("흑: 집 15 + 사석 5 = 20집", judgement.blackLine(strings))
        assertEquals("백: 집 13 + 사석 2 + 덤 6.5 = 21.5집", judgement.whiteLine(strings))
    }

    /**
     * 면적계가 접바둑의 결과 팝업은 백 줄에 **접바둑 보정 N**을 밝힌다(refactor backlog #89).
     * 합계(8.5)에 보정 2가 들어 있으므로, 항을 빼면 "0 + 6.5 = 8.5"처럼 계산이 틀려 보인다.
     * 보정 0인 판정(옛 저장본·맞바둑)은 예전 줄 그대로다.
     */
    @Test
    fun chineseHandicapJudgementNamesTheHandicapBonusOnTheWhiteLine() {
        val state = GameState.withHandicap(BoardSize.Nine, Ruleset.Chinese, handicapCount = 2, komi = 6.5)
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))

        val plan = buildLocalFinalScoreDisplayPlan(
            source = "test-final",
            state = state,
            finalScore = BoardScorer.score(state),
            previousSnapshots = emptyList(),
            detail = "test",
            engineMessage = "final",
            candidateText = "ended",
        )

        val strings = UiStringsKorean
        val judgement = plan.judgement ?: error("missing judgement")
        assertEquals(2.0, judgement.whiteHandicapBonus, 0.0)
        assertEquals("흑 + 72.5집 승", judgement.resultText(strings))
        assertEquals("흑: 돌 + 집 = 81집", judgement.blackLine(strings))
        assertEquals("백: 돌 + 집 + 덤 6.5 + 접바둑 보정 2 = 8.5집", judgement.whiteLine(strings))
        assertTrue(plan.scoreText.contains("handicap bonus 2"))

        val legacy = judgement.copy(whiteAreaWithKomi = 6.5, whiteHandicapBonus = 0.0)
        assertEquals("백: 돌 + 집 + 덤 6.5 = 6.5집", legacy.whiteLine(strings))
    }
}
