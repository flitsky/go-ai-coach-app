package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PAD 에셋 팩 다운로드 가이드 카드 3장 및 상태 문구의 4개 국어 누락 여부 검증 (백로그 #245 U-73).
 */
class UiStringsDownloadGuideTest {

    @Test
    fun everyGuideCardSpeaksInEveryLanguage() {
        for (language in UiLanguage.entries) {
            for (index in 0 until 3) {
                val card = UiStringsDownloadGuide.cardFor(language, index)
                assertFalse(
                    "$language card $index title is blank",
                    card.title.isBlank(),
                )
                assertFalse(
                    "$language card $index body is blank",
                    card.body.isBlank(),
                )
            }
        }
    }

    @Test
    fun statusStringsExistInEveryLanguage() {
        for (language in UiLanguage.entries) {
            assertTrue(
                "$language waitingForWifi is blank",
                UiStringsDownloadGuide.waitingForWifi(language).isNotBlank(),
            )
            assertTrue(
                "$language failed is blank",
                UiStringsDownloadGuide.failed(language).isNotBlank(),
            )
            assertTrue(
                "$language retry is blank",
                UiStringsDownloadGuide.retry(language).isNotBlank(),
            )
        }
    }
}
