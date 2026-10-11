package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStringsBoardControlsGuide
import com.worksoc.goaicoach.ui.setup.BoardControlsStep
import com.worksoc.goaicoach.ui.setup.boardControlsSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 바둑판 조작법 가이드(백로그 #249 ③) — 판다의 말이 네 언어에 다 있고, 대국 화면의 버튼 이름이 빠짐없이 끼워지는가.
 */
class BoardControlsGuideTest {

    @Test
    fun confirmStepFollowsTheConfirmModeFlag() {
        assertEquals(BoardControlsStep.entries.toList(), boardControlsSteps(isConfirmModeEnabled = true))
        assertFalse(
            "착수 확인이 꺼진 빌드에서 그 기능을 가르치면 안 된다(함정 51)",
            boardControlsSteps(isConfirmModeEnabled = false).contains(BoardControlsStep.Confirm),
        )
    }

    @Test
    fun pandaSpeaksEveryLineInEveryLanguageWithLabelsFilledIn() {
        for (language in UiLanguage.entries) {
            val lines = UiStringsBoardControlsGuide.dragLines(language) +
                UiStringsBoardControlsGuide.confirmLines(language, "<CONFIRM>", "<PLAY>") +
                UiStringsBoardControlsGuide.sizeLines(language, "<MAX>") +
                listOf(
                    UiStringsBoardControlsGuide.stepDrag(language),
                    UiStringsBoardControlsGuide.watchDemo(language),
                    UiStringsBoardControlsGuide.tryIt(language),
                    UiStringsBoardControlsGuide.startOver(language),
                    UiStringsBoardControlsGuide.tryDrag(language),
                    UiStringsBoardControlsGuide.tryConfirm(language, "<CONFIRM>", "<PLAY>"),
                    UiStringsBoardControlsGuide.trySize(language, "<MAX>"),
                    UiStringsBoardControlsGuide.wellDone(language),
                )
            lines.forEach { line ->
                assertTrue("$language: 빈 줄", line.isNotBlank())
                assertFalse("$language: 끼우지 못한 자리표시자 — $line", line.contains("%"))
            }
            val confirm = UiStringsBoardControlsGuide.confirmLines(language, "<CONFIRM>", "<PLAY>")
            assertTrue("$language: 첫 마디가 착수 확인 이름을 부르지 않는다", confirm.first().contains("<CONFIRM>"))
            assertTrue("$language: 마지막 마디가 착수 이름을 부르지 않는다", confirm.last().contains("<PLAY>"))
            val tryConfirm = UiStringsBoardControlsGuide.tryConfirm(language, "<CONFIRM>", "<PLAY>")
            assertTrue("$language: 직접 해 보기 안내에 두 이름이 다 있어야 한다", tryConfirm.contains("<CONFIRM>") && tryConfirm.contains("<PLAY>"))
            assertTrue(UiStringsBoardControlsGuide.sizeLines(language, "<MAX>").first().contains("<MAX>"))
        }
    }
}
