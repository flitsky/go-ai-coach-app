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
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsKorean
import com.worksoc.goaicoach.ui.play.blackLine
import com.worksoc.goaicoach.ui.play.resultText
import com.worksoc.goaicoach.ui.play.whiteLine
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
        // 집수는 붙여 쓴다 — 대국 기록 · 명국 기보와 같은 꼴, 옛 「백 + 1.5집 승」(QA-23f, 사용자 2026-11-23).
        assertEquals("백 1.5집승", judgement.resultText(strings))
        assertEquals("흑: 집 15 + 사석 5 = 20집", judgement.blackLine(strings))
        assertEquals("백: 집 13 + 사석 2 + 덤 6.5 = 21.5집", judgement.whiteLine(strings))

        // 영어 결과 문구는 문법대로 — 옛 「White + 1.5 points win」 · 「White win」(QA-14, 2026-11-23).
        val english = UiStrings.forLanguage(UiLanguage.English)
        assertEquals("White wins by 1.5", judgement.resultText(english))
        assertEquals("White wins", english.winnerWithoutMarginLabel(english.colorLabel(StoneColor.White)))
        // 영어 대국 기록 줄은 짧은 꼴 그대로, 중국어는 판정 창과 같은 붙여 쓴 꼴(QA-23f, 2026-11-23).
        assertEquals("White +1.5", english.gameHistoryOutcomeLabel(StoneColor.White, isResign = false, margin = 1.5))
        assertEquals("白胜1.5目", UiStrings.forLanguage(UiLanguage.ChineseSimplified).gameHistoryOutcomeLabel(StoneColor.White, isResign = false, margin = 1.5))
        // 다시보기 형세 표기 — 진영은 화면 언어 글자, 영어만 B/W(QA-23d, 2026-11-23).
        assertEquals("흑 +5.4", UiStringsKorean.leadLabel(5.4))
        assertEquals("백 +6.5", UiStringsKorean.leadLabel(-6.5))
        assertEquals("B +5.4", english.leadLabel(5.4))
        assertEquals("0.0", UiStringsKorean.leadLabel(0.0))

        // 일본어는 진영과 결과를 붙여 쓰고, 대국 기록 줄도 판정 창과 같은 함수다(QA-23e · 23f, 2026-11-23).
        val japanese = UiStrings.forLanguage(UiLanguage.Japanese)
        assertEquals("白1.5目勝ち", judgement.resultText(japanese))
        assertEquals("白1.5目勝ち", japanese.gameHistoryOutcomeLabel(StoneColor.White, isResign = false, margin = 1.5))
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
        assertEquals("흑 72.5집승", judgement.resultText(strings))
        assertEquals("흑: 돌 + 집 = 81집", judgement.blackLine(strings))
        assertEquals("백: 돌 + 집 + 덤 6.5 + 접바둑 보정 2 = 8.5집", judgement.whiteLine(strings))
        assertTrue(plan.scoreText.contains("handicap bonus 2"))

        val legacy = judgement.copy(whiteAreaWithKomi = 6.5, whiteHandicapBonus = 0.0)
        assertEquals("백: 돌 + 집 + 덤 6.5 = 6.5집", legacy.whiteLine(strings))
    }

    /**
     * 집계가 백 줄도 보정을 따로 떼어 밝힌다(refactor backlog #106 — 보정은 계가 방식이 아니라 룰셋의 값이
     * 정한다). 지금 집계가 룰셋은 보정 0이라 실제 판정에선 예전 줄 그대로이고, 여기서는 보정이 있는 판정을
     * 직접 만들어 집(13)이 보정만큼 부풀지 않는지 본다.
     */
    @Test
    fun territoryWhiteLineSeparatesAHandicapBonusFromTheTerritory() {
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
        val judgement = plan.judgement ?: error("missing judgement")

        val withBonus = judgement.copy(whiteAreaWithKomi = 23.5, whiteHandicapBonus = 2.0)
        assertEquals("백: 집 13 + 사석 2 + 덤 6.5 + 접바둑 보정 2 = 23.5집", withBonus.whiteLine(UiStringsKorean))
    }
}
