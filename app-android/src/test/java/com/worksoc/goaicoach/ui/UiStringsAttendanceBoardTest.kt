package com.worksoc.goaicoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #57 — 도장판 문구가 네 언어에 빠짐없이 있는지 본다.
 *
 * ⚠️ **#57에서 이 그물이 더 중요해졌다.** 칸의 보상이 글리프가 되면서 **화면에 글자가 거의 남지
 * 않았고**, 뜻을 전하는 일이 `contentDescription`으로 옮겨 갔다. 그 말이 한 언어에서라도 비면
 * 그 언어 사용자에게 도장판은 **숫자 열 개**가 된다 — 눈으로는 안 드러나는 종류의 사고다.
 */
class UiStringsAttendanceBoardTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyBoardStringExistsInEveryLanguage() {
        // 표가 `getValue`로 읽히므로, 네 언어를 훑는 것만으로 빠진 키에서 던진다.
        languages.forEach { language ->
            listOf(
                attendanceBoardSectionTitleFor(language),
                attendanceBoardBeyondNoticeFor(language),
                attendanceAtStockCapNoticeFor(language),
                attendanceUpcomingNoticeFor(language),
                attendanceStampedNoticeFor(language),
            ).forEach { text -> assertTrue("$language 문구가 비었다", text.isNotBlank()) }
        }
    }

    /**
     * 세 가지 칸 상태가 **소리로 구분되는지** 본다. 셋 중 둘이 같은 말이면 스크린 리더 사용자는
     * 받아 간 칸과 아직인 칸을 구별할 수 없다.
     */
    @Test
    fun theThreeCellStatesReadDifferentlyInEveryLanguage() {
        languages.forEach { language ->
            val spoken = listOf(
                attendanceStampedNoticeFor(language),
                attendanceUpcomingNoticeFor(language),
                UiStrings.forLanguage(language).attendanceRewardClaimAction,
            )
            assertEquals("$language: 칸 상태 표현이 겹친다 ($spoken)", 3, spoken.distinct().size)
        }
    }

    /**
     * ⚠️ 한국어 표에 다른 언어 글자가 섞이는 사고가 이 저장소에서 실제로 있었다(#32).
     * 도장판 문구에도 같은 그물을 건다.
     */
    @Test
    fun theKoreanTableIsActuallyKorean() {
        listOf(
            attendanceBoardSectionTitleFor(UiLanguage.Korean),
            attendanceUpcomingNoticeFor(UiLanguage.Korean),
            attendanceStampedNoticeFor(UiLanguage.Korean),
        ).forEach { text ->
            assertTrue("한국어가 아닌 문구: $text", text.containsHangul())
        }
    }
}
