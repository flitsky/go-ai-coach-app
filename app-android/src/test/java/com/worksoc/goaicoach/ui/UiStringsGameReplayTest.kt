package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.gamehistory.BlunderPointLossThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #156 — 대국 다시보기 문구.
 *
 * ⚠️ **이 파일이 그 문구들의 유일한 그물이다.** `UiStringsTest`의 리플렉션 그물은 [UiStrings]의
 * `String` 필드만 훑으므로, `UiStringsGameReplay.kt`의 표와 함수는 **하나도 보지 않는다**
 * (함정 10). 지우면 한 언어가 비어도 아무도 모른다.
 */
class UiStringsGameReplayTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyReplayStringExistsInEveryLanguage() {
        languages.forEach { language ->
            listOf(
                "제목" to gameReplayTitleFor(language),
                "목록 꼬리표" to gameReplayRowBadgeFor(language),
                "본문 없음" to gameReplayUnavailableFor(language),
                "시작 국면" to gameReplayStartPositionFor(language),
                "형세 절 제목" to gameReplayScoreSectionFor(language),
                "형세 없음" to gameReplayNoScoreDataFor(language),
                "실수 절 제목" to gameReplayBlunderSectionFor(language),
                "실착 표식" to gameReplayBlunderBadgeFor(language),
                "평가 없음" to gameReplayNoMoveEvaluationsFor(language),
                "실수 없음" to gameReplayNoBlundersFor(language),
                "판정 기준" to gameReplayBlunderCriterionFor(language),
                "손실 표기" to gameReplayPointLossFor(language, 12.5),
                "끊긴 기록" to gameReplayTruncatedFor(language, 48),
                "수순 표시 체크박스 라벨" to gameReplayShowMoveNumbersLabelFor(language),
            ).forEach { (what, text) ->
                assertTrue("$language / $what 문구가 비었다", text.isNotBlank())
            }
            ReplayNavigation.entries.forEach { step ->
                assertTrue(
                    "$language / $step 이동 라벨이 비었다",
                    gameReplayNavigationLabelFor(language, step).isNotBlank(),
                )
            }
        }
    }

    /**
     * ⚠️ **임계값을 문구에 박지 않는다.** 10집은 [BlunderPointLossThreshold] 하나가 정본이고
     * 네 언어가 그 값을 따라와야 한다 — 숫자를 손으로 적어 두면 임계를 바꾼 다음 스레드가
     * **코드는 12집인데 화면은 10집이라고 말하는** 상태를 만든다.
     */
    @Test
    fun theBlunderCriterionFollowsTheThresholdConstant() {
        languages.forEach { language ->
            assertTrue(
                "$language 기준 문구가 기본 임계(10)를 말하지 않는다",
                gameReplayBlunderCriterionFor(language).contains("10"),
            )
            assertTrue(
                "$language 기준 문구가 바뀐 임계(25)를 따라오지 않는다",
                gameReplayBlunderCriterionFor(language, 25.0).contains("25"),
            )
            assertFalse(
                "$language 기준 문구에 옛 임계가 남았다",
                gameReplayBlunderCriterionFor(language, 25.0).contains("10"),
            )
        }
        assertEquals(10.0, BlunderPointLossThreshold, 0.0)
    }

    /** 정수는 소수점을 떼고, 소수는 한 자리만. `12.5`를 `12.5`로, `10.0`을 `10`으로. */
    @Test
    fun pointLossIsWrittenWithAtMostOneDecimal() {
        assertEquals("−12.5 집", gameReplayPointLossFor(UiLanguage.Korean, 12.5))
        assertEquals("−10 집", gameReplayPointLossFor(UiLanguage.Korean, 10.0))
        assertEquals("−12.5 pts", gameReplayPointLossFor(UiLanguage.English, -12.53))
    }

    /**
     * ⚠️ **본문이 없는 옛 기록에 "준비 중"이라고 말하지 않는다** — 기다려도 생기지 않는다.
     * 2026-09-18 이전 기록에는 수순이 저장된 적이 없다(#151). 이 앱의 기조는 *"시간이 지나면
     * 저절로 풀리는가"* 로 안내를 가르고, 이것은 안 풀리는 쪽이다.
     */
    @Test
    fun theMissingRecordNoticeNeverPromisesItIsComing() {
        val banned = listOf("준비 중", "곧", "coming", "soon", "準備", "即将", "稍后")
        languages.forEach { language ->
            val text = gameReplayUnavailableFor(language)
            banned.forEach { word ->
                assertFalse(
                    "$language 안내가 기다리면 된다고 말한다('$word'): $text",
                    text.contains(word, ignoreCase = true),
                )
            }
        }
    }
}
