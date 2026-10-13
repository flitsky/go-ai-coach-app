package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.WhatsNewCatalog
import com.worksoc.goaicoach.ui.l10n.whatsNewHomeLineFor
import com.worksoc.goaicoach.ui.l10n.whatsNewOpenFor
import com.worksoc.goaicoach.ui.l10n.whatsNewTitleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 #257 — 앱에 싣는 「새로워진 점」 묶음. 버전 번호가 맞고, 네 언어가 다 있고, 줄 수가 언어마다 같다. */
class WhatsNewCatalogTest {

    @Test
    fun everyReleaseIsNamedByItsVersionCodeAndUnique() {
        val codes = WhatsNewCatalog.releases.map { it.versionCode }
        assertEquals("versionCode가 겹친다", codes.distinct(), codes)
        WhatsNewCatalog.releases.forEach { release ->
            // versionCode = MAJOR×10000 + MINOR×100 + PATCH(릴리스 규칙 — 활성 백로그 「지금의 앱」).
            val (major, minor, patch) = release.versionName.split(".").map(String::toInt)
            assertEquals("${release.versionName}의 versionCode가 규칙과 다르다", major * 10000 + minor * 100 + patch, release.versionCode)
        }
    }

    @Test
    fun everyReleaseSpeaksEveryLanguageWithTheSameNumberOfLines() {
        WhatsNewCatalog.releases.forEach { release ->
            val counts = UiLanguage.entries.map { language ->
                val lines = release.linesFor(language)
                lines.forEach { assertTrue("${release.versionName} $language: 빈 줄", it.isNotBlank()) }
                lines.size
            }
            assertTrue("${release.versionName}: 묶음이 비었다", counts.first() > 0)
            assertEquals("${release.versionName}: 언어마다 줄 수가 다르다 — 한 언어만 고쳤다", 1, counts.distinct().size)
        }
        UiLanguage.entries.forEach { language ->
            assertTrue(whatsNewTitleFor(language).isNotBlank() && whatsNewOpenFor(language).isNotBlank())
            assertTrue(whatsNewHomeLineFor(language, "9.9.9").contains("9.9.9"))
        }
    }

    @Test
    fun settingsShowsOnlyReleasesUpToTheApp() {
        assertTrue(WhatsNewCatalog.releasesUpTo(10306).isEmpty())
        assertEquals(listOf(10400), WhatsNewCatalog.releasesUpTo(10400).map { it.versionCode })
    }
}
