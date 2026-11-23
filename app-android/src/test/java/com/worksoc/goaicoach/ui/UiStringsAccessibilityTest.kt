package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.history.ReplayFastStep
import com.worksoc.goaicoach.ui.l10n.ReplayTransportStep
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.backNavigationLabelFor
import com.worksoc.goaicoach.ui.l10n.gameMenuButtonLabelFor
import com.worksoc.goaicoach.ui.l10n.replayTransportLabelFor
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA-22(2026-11-23) — 스크린 리더만 읽는 이름(`UiStringsAccessibility.kt`).
 *
 * ⚠️ 이 파일이 그 표들의 「네 언어가 다 있는가」 그물이다 — `UiStringWidthParityTest`는 언어가 빠진 표를 조용히 건너뛰고,
 * 빠진 언어는 그 언어로 화면을 열 때 `getValue`가 터진다(함정 10과 같은 사각지대).
 */
class UiStringsAccessibilityTest {

    @Test
    fun everyNameExistsInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            assertTrue("$language / ← 이름이 비었다", backNavigationLabelFor(language).isNotBlank())
            assertTrue("$language / ☰ 이름이 비었다", gameMenuButtonLabelFor(language).isNotBlank())
            ReplayTransportStep.entries.forEach { step ->
                assertTrue("$language / $step 이동 단추 이름이 비었다", replayTransportLabelFor(language, step).isNotBlank())
            }
        }
    }

    /** ← 는 한 단계 돌아가는 단추다 — 「닫기」(✕)와 같은 이름이면 QA-22가 되돌아간 것이다. */
    @Test
    fun theBackArrowIsNotCalledClose() {
        UiLanguage.entries.forEach { language ->
            assertNotEquals("$language / ← 이름이 「닫기」와 같다", UiStrings.forLanguage(language).close, backNavigationLabelFor(language))
        }
    }

    /** 빨리 감기 이름의 「10」은 표에 적은 글자다 — `ReplayFastStep`이 바뀌면 이름도 함께 고친다. */
    @Test
    fun theFastStepNamesSayTheRealStep() {
        UiLanguage.entries.forEach { language ->
            listOf(ReplayTransportStep.FastBackward, ReplayTransportStep.FastForward).forEach { step ->
                assertTrue(
                    "$language / $step 이름이 ${ReplayFastStep}수를 말하지 않는다",
                    replayTransportLabelFor(language, step).contains(ReplayFastStep.toString()),
                )
            }
        }
    }
}
