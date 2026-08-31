package com.worksoc.goaicoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 #53 — 업데이트 줄 문구가 네 언어에 빠짐없이 있는지. */
class UiStringsAppUpdateTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyUpdateStringExistsInEveryLanguage() {
        languages.forEach { language ->
            listOf(
                appUpdateAvailableLabelFor(language),
                appUpdateActionLabelFor(language),
                appUpToDateLabelFor(language),
                appUpdateCheckStoreLabelFor(language),
            ).forEach { text -> assertTrue("$language 문구가 비었다", text.isNotBlank()) }
        }
    }

    /** "있음"과 "최신"이 같은 말이면 사용자는 둘을 구별할 수 없다. */
    @Test
    fun availableAndUpToDateNeverReadTheSame() {
        languages.forEach { language ->
            assertEquals(
                "$language: 두 안내가 겹친다",
                2,
                setOf(appUpdateAvailableLabelFor(language), appUpToDateLabelFor(language)).size,
            )
        }
    }

    /**
     * ⚠️ 폴백 문구는 **실패를 말하지 않는다**(사용자가 고칠 수 없는 사정이다). "실패/오류" 류가
     * 들어가면 Play 설치본이 아닌 모든 기기의 설정 화면이 경고를 띄우는 화면이 된다.
     */
    @Test
    fun theFallbackNeverAnnouncesAFailure() {
        val banned = listOf("실패", "오류", "failed", "error", "失敗", "エラー", "失败", "错误")
        languages.forEach { language ->
            val copy = appUpdateCheckStoreLabelFor(language)
            banned.forEach { word ->
                assertFalse(
                    "$language 폴백 문구가 실패를 말한다('$word'): $copy",
                    copy.contains(word, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun theKoreanTableIsActuallyKorean() {
        listOf(
            appUpdateAvailableLabelFor(UiLanguage.Korean),
            appUpdateActionLabelFor(UiLanguage.Korean),
            appUpToDateLabelFor(UiLanguage.Korean),
            appUpdateCheckStoreLabelFor(UiLanguage.Korean),
        ).forEach { text -> assertTrue("한국어가 아닌 문구: $text", text.containsHangul()) }
    }
}
