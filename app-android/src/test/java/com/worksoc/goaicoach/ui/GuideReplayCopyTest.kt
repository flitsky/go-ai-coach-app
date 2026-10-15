package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.guide.GuideStep
import com.worksoc.goaicoach.application.premium.state.MatchFreeUses
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureDemotionLosses
import com.worksoc.goaicoach.ui.l10n.GuideToolLabels
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStringsGuideReplay
import com.worksoc.goaicoach.ui.l10n.guideBodyFor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #261 — 「가이드 다시보기」 문구가 네 언어에 다 있고, 화면의 이름이 빠짐없이 끼워지고, 무료 횟수를 상수에서 읽는가.
 * (사용자 2026-10-13: *"가이드 다시보기 내용이 낡았다"* — 형세·추천이 매 판 무료 3회(#242)를 말하지 않고 있었다.)
 */
class GuideReplayCopyTest {

    private val n = MatchFreeUses.PerMatch.toString()

    @Test
    fun everyReplayLineExistsInEveryLanguageWithNamesFilledIn() {
        for (language in UiLanguage.entries) {
            val lines = listOf(
                UiStringsGuideReplay.myCharacters(language, "<CHARS>"),
                UiStringsGuideReplay.premium(language),
                UiStringsGuideReplay.rankMeasure(language),
                UiStringsGuideReplay.gameHistory(language),
                UiStringsGuideReplay.study(language),
                UiStringsGuideReplay.confirmButton(language, "<CONFIRM>", "<PLAY>"),
                UiStringsGuideReplay.undo(language, "<UNDO>"),
                UiStringsGuideReplay.boardControls(language, "<HELP>", "<GUIDE>"),
            )
            lines.forEach { line ->
                assertTrue("$language: 빈 줄", line.isNotBlank())
                assertFalse("$language: 끼우지 못한 자리표시자 — $line", line.contains("%") || line.contains("\$"))
            }
            assertTrue("$language: 내 캐릭터 이름이 없다", lines[0].contains("<CHARS>"))
            val confirm = UiStringsGuideReplay.confirmButton(language, "<CONFIRM>", "<PLAY>")
            assertTrue("$language: 착수 확인 문구에 두 버튼 이름이 다 있어야 한다", confirm.contains("<CONFIRM>") && confirm.contains("<PLAY>"))
            assertTrue("$language: 무르기 문구에 무료 횟수가 없다", UiStringsGuideReplay.undo(language, "<UNDO>").contains(n))
            // 강급 연패 수는 규칙의 상수에서 읽는다(#268 — 2연패 → 3연패 때 이 문구가 옛 숫자로 남을 뻔했다).
            assertTrue("$language: 승급 대국 문구에 강급 연패 수가 없다", UiStringsGuideReplay.rankMeasure(language).contains(RankMeasureDemotionLosses.toString()))
            val board = UiStringsGuideReplay.boardControls(language, "<HELP>", "<GUIDE>")
            assertTrue("$language: 조작법으로 가는 길에 절·줄 이름이 다 있어야 한다", board.contains("<HELP>") && board.contains("<GUIDE>"))
        }
    }

    /** 형세 보기·추천 수의 코치마크(첫 대국)와 다시보기가 같은 문구다 — 매 판 무료 횟수를 말한다. */
    @Test
    fun evalAndTopMovesMentionTheFreeUsesPerGame() {
        val labels = GuideToolLabels(eval = "<EVAL>", topMoves = "<TOP>")
        for (language in UiLanguage.entries) {
            listOf(GuideStep.InGameEval, GuideStep.InGameTopMoves).forEach { step ->
                val body = guideBodyFor(language, step, toolLabels = labels)
                assertTrue("$language.${step.name}: 매 판 무료 횟수($n)를 말하지 않는다(#261)", body.contains(n))
            }
        }
    }
}
