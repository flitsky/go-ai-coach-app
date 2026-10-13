package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStringsBoardControlsGuide
import com.worksoc.goaicoach.ui.setup.BoardControlsStep
import com.worksoc.goaicoach.ui.setup.GuideAutoMode
import com.worksoc.goaicoach.ui.setup.GuideIdleReturnMillis
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
                    UiStringsBoardControlsGuide.demoHint(language),
                    UiStringsBoardControlsGuide.practiceHint(language),
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

    /** #258(사용자 2026-10-13) — 손대면 직접, 손을 떼고 기다리면 시연을 처음부터. 누르고 있는 동안은 돌아가지 않는다. */
    @Test
    fun touchingSwitchesToPracticeAndIdlingAfterReleaseRestartsTheDemo() {
        val start = GuideAutoMode()
        assertFalse("처음에는 시연이다", start.practicing)
        assertFalse("시연 중에는 기다릴 것이 없다", start.waitsToReturn)

        val touched = start.pressed()
        assertTrue("닿는 순간 직접이다", touched.practicing)
        assertFalse("누르고 있는 동안은 세지 않는다", touched.waitsToReturn)
        assertEquals("누르고 있는 동안 시간이 지나도 시연으로 안 간다", touched, touched.idleElapsed())
        assertEquals("직접으로 넘어갈 때 판을 새로 만들지 않는다 — 첫 터치를 판이 받아야 한다", start.demoRun, touched.demoRun)

        val lifted = touched.released()
        assertTrue("손을 떼면 기다린다", lifted.waitsToReturn)
        assertTrue("다시 누르면 기다림이 처음부터다(뗄 때마다 releases가 바뀐다)", lifted.pressed().released().releases > lifted.releases)

        val back = lifted.idleElapsed()
        assertFalse("기다리면 시연으로 돌아간다", back.practicing)
        assertEquals("돌아온 시연은 처음부터 — 판을 새로 만든다", lifted.demoRun + 1, back.demoRun)
        assertEquals("시연 중에 시간이 지나도 아무 일 없다", back, back.idleElapsed())

        val stepped = lifted.stepChanged()
        assertFalse("단계를 바꾸면 그 단계의 시연부터다", stepped.practicing)
        assertTrue("단계를 바꾸면 판을 새로 만든다", stepped.demoRun > lifted.demoRun)
        assertEquals("기다리는 시간은 4초다(사용자 2026-10-13)", 4_000L, GuideIdleReturnMillis)
    }

    /**
     * #258의 핵심 함정 — **첫 터치가 삼켜지지 않게.** `GoBoard`는 누르는 순간 입력이 꺼져 있으면 그 제스처를 버리고, 입력 값이 바뀌면
     * 처리를 새로 시작한다. 그래서 무대의 판은 시연 중에도 입력을 켜 두고, 모드는 판보다 먼저(Initial) **소비하지 않고** 본다.
     */
    @Test
    fun theFirstTouchReachesTheBoard() {
        val guide = RepoPaths.uiFile("BoardControlsGuide.kt").readContractSource()
        assertFalse("시연 중에 판의 입력을 끈다 — 첫 터치를 판이 버린다(#258).", guide.contains("inputEnabled = practice"))
        assertFalse("판의 입력을 끄는 곳이 있다(#258).", guide.contains("inputEnabled = false"))
        assertTrue("무대가 손가락을 판보다 먼저 보지 않는다(#258).", guide.contains("PointerEventPass.Initial"))
        val observer = guide.substring(guide.indexOf("awaitEachGesture {"), guide.indexOf("auto = auto.released()"))
        assertFalse("무대가 손가락을 소비한다 — 판과 버튼이 그 손가락을 못 받는다(#258).", observer.contains(".consume()"))
        assertFalse(
            "사용자 입력 경로가 모드를 묻는다 — 빠른 첫 탭이 옛 모드를 읽어 무시된다(#258).",
            guide.contains("if (!practice) return") || guide.contains("if (practice) isMax"),
        )
        assertFalse("모드가 판의 key에 들어 있다 — 직접으로 넘어가는 순간 판이 새로 만들어져 첫 터치를 잃는다(#258).", guide.contains("key(step, trying"))
    }
}
