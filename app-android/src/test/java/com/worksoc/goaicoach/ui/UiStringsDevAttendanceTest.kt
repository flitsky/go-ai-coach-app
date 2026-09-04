package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.attendance.isRewardedTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 개발자 2차 출석 부제([UiStrings.settingsDevAttendanceSubtitle])의 **손 그물**(백로그 #71).
 *
 * ⚠️ **`UiStringsTest`의 리플렉션 그물은 String *필드*만 본다** — 이 문구는 상태(일차, 보상 여부)를
 * 말해야 해서 `fun`이고, 따라서 자동 그물에 **아예 안 잡힌다**(함정 10번). `botUnlockHint`가 그렇게
 * 네 언어에 *"7일차 출석에서 조각을 얻는다"* 는 **사실이 아닌 문구**를 오래 달고 있었다.
 *
 * ⚠️ 그래서 문구만 보지 않고 **문구가 말하는 사실을 도메인에 되묻는다** —
 * "다음이 보상 회차인가"의 정답은 [isRewardedTier] 하나여야 한다.
 */
class UiStringsDevAttendanceTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyLanguageHasCopyForBothTheRewardedAndUnrewardedCase() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            listOf(true, false).forEach { rewarded ->
                val subtitle = strings.settingsDevAttendanceSubtitle(current = 6, next = 7, nextIsRewarded = rewarded)
                assertTrue("$language / rewarded=$rewarded 부제가 비었다", subtitle.isNotBlank())
                assertTrue("$language / rewarded=$rewarded 부제에 현재 일차가 없다", subtitle.contains("6"))
                assertTrue("$language / rewarded=$rewarded 부제에 다음 일차가 없다", subtitle.contains("7"))
            }
        }
    }

    /**
     * ⚠️ **보상 회차와 아닌 회차의 문구가 갈려야 한다.** 같으면 8~13일차 구간에서 버튼을 눌러도
     * 팝업이 안 뜨는 이유를 화면이 말해 주지 못하고, 그것이 이 부제가 존재하는 이유 전부다.
     */
    @Test
    fun theRewardedAndUnrewardedCasesReadDifferentlyInEveryLanguage() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            assertNotEquals(
                "$language: 보상 회차와 아닌 회차의 부제가 같다 — 팝업이 안 뜨는 이유를 못 알린다(#71).",
                strings.settingsDevAttendanceSubtitle(current = 7, next = 8, nextIsRewarded = false),
                strings.settingsDevAttendanceSubtitle(current = 6, next = 7, nextIsRewarded = true),
            )
        }
    }

    /**
     * 비한국어가 한국어를 그대로 물려받지 않았는지 — 자동 그물이 필드에 대해 하는 일을 손으로 한다.
     */
    @Test
    fun nonKoreanCopyDoesNotInheritTheKoreanBaseline() {
        val korean = UiStrings.forLanguage(UiLanguage.Korean)
            .settingsDevAttendanceSubtitle(current = 6, next = 7, nextIsRewarded = true)
        languages.filter { it != UiLanguage.Korean }.forEach { language ->
            assertNotEquals(
                "$language 부제가 한국어와 같다 — 번역이 빠졌다.",
                korean,
                UiStrings.forLanguage(language).settingsDevAttendanceSubtitle(current = 6, next = 7, nextIsRewarded = true),
            )
        }
    }

    /**
     * ⚠️ **문구가 말하는 사실을 도메인에 되묻는다.** 8~13·15~20·22~27일차는 보상 회차가 아니므로
     * 그 구간에서는 "보상 없음" 쪽 문구가 쓰여야 한다 — 판정의 단일 출처는 [isRewardedTier]다.
     */
    @Test
    fun theUnrewardedRangeTheCopyWarnsAboutIsTheOneTheDomainDefines() {
        assertTrue("1~7일차는 모두 보상 회차여야 한다", (1..7).all { isRewardedTier(it) })
        assertTrue("8~13일차에 보상 회차가 섞여 있다 — 부제 분기의 전제가 무너진다", (8..13).none { isRewardedTier(it) })
        assertTrue("14·21·28일차는 보상 회차여야 한다", listOf(14, 21, 28).all { isRewardedTier(it) })
        assertFalse("29일차가 보상 회차로 판정됐다", isRewardedTier(29))
    }
}
