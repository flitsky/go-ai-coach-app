package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PAD 에셋 팩 다운로드 가이드 카드 및 상태 문구의 4개 국어 누락 여부 검증 (백로그 #245 U-73).
 */
class UiStringsDownloadGuideTest {

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
            assertTrue(
                "$language aiReady is blank",
                UiStringsDownloadGuide.aiReady(language).isNotBlank(),
            )
            assertTrue(
                "$language close is blank",
                UiStringsDownloadGuide.close(language).isNotBlank(),
            )
            assertTrue(
                "$language boardControlsGuide is blank",
                UiStringsDownloadGuide.boardControlsGuide(language).isNotBlank(),
            )
            // 엔진 준비 화면(#249)
            assertTrue("$language setupSubtitle is blank", UiStringsDownloadGuide.setupSubtitle(language).isNotBlank())
            assertTrue("$language later is blank", UiStringsDownloadGuide.later(language).isNotBlank())
            assertTrue("$language start is blank", UiStringsDownloadGuide.start(language).isNotBlank())
            assertTrue("$language preparing is blank", UiStringsDownloadGuide.preparing(language).isNotBlank())
        }
    }
}
