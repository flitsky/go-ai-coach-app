package com.worksoc.goaicoach.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 접바둑 보정 방식(refactor backlog #106). KataGo `whiteHandicapBonus`의 세 값과 같은 셈이어야 한다 —
 * 엔진은 같은 [Ruleset.handicapBonusRule]을 받아 이 셈으로 대국 중 점수를 낸다.
 */
class HandicapBonusRuleTest {
    @Test
    fun eachRuleGivesWhiteItsBonusAndOneStoneOrLessIsNeverAHandicap() {
        val counts = listOf(0, 1, 2, 9)
        val expected = mapOf(
            HandicapBonusRule.N to listOf(0.0, 0.0, 2.0, 9.0),
            HandicapBonusRule.NMinusOne to listOf(0.0, 0.0, 1.0, 8.0),
            HandicapBonusRule.Zero to listOf(0.0, 0.0, 0.0, 0.0),
        )

        assertEquals(HandicapBonusRule.entries.toSet(), expected.keys, "새 방식을 더했으면 여기에 기대값도 더하라.")
        for ((rule, bonuses) in expected) {
            assertEquals(bonuses, counts.map(rule::whiteHandicapBonus), "$rule at $counts")
        }
    }

    /**
     * 지금 값을 못 박는다 — 면적계가 N, 집계가 0(refactor backlog #89). 바꾸는 것은 사용자에게 보이는 계가
     * 변경이다. 엔진은 따라오지만(이름 룰 기본값과 다르면 덮어쓰기를 싣는다) 원격 요청은 이 값을 싣지 않으니
     * engine-android의 `KataGoNamedRulesTest`도 함께 본다.
     */
    @Test
    fun areaScoringUsesNAndTerritoryScoringUsesZero() {
        assertEquals(HandicapBonusRule.N, Ruleset.Chinese.handicapBonusRule)
        assertEquals(HandicapBonusRule.Zero, Ruleset.Japanese.handicapBonusRule)
    }
}
