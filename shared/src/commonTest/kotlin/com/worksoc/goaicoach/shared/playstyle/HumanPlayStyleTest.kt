package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.fastBeginnerFallbackTier
import com.worksoc.goaicoach.shared.policy.fastBeginnerRank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 캐릭터 열 명의 급수(백로그 #301) — 단계 번호는 저장 형식이라 세기 순서가 아니다. */
class HumanPlayStyleTest {

    private fun style(level: Int) = PlayLevelSetting(PlayLevelGroup.FastBeginner, level).humanPlayStyle()!!

    @Test
    fun theFirstFiveKeepTheProfilesTheUserPlayedAndChose() {
        // 사용자가 폰에서 둬 보고 정한 값(2026-10-06) — #301이 단계 → 급수를 한 곳(`fastBeginnerRank`)으로 옮겨도 그대로다.
        assertEquals(listOf("rank_15k", "rank_9k", "rank_1k", "rank_3d", "rank_7d"), (1..5).map { style(it).profile })
    }

    @Test
    fun theFiveAddedInBetweenPlayTheirOfficialProfiles() {
        // 사용자 2026-11-02: 18급 · 12급 · 6급 · 3급 · 5단. 공식 프로필 그대로(일부러 더 약하게 만들지 않는다 — `DECISIONS.md` §2).
        assertEquals(listOf("rank_18k", "rank_12k", "rank_6k", "rank_3k", "rank_5d"), (6..10).map { style(it).profile })
        (1..10).forEach { level -> assertEquals(fastBeginnerRank(level), style(level).rank, "level $level") }
    }

    @Test
    fun eachRankSitsInsideItsOwnBandAndBandsDoNotOverlap() {
        fun value(label: String): Int = label.dropLast(1).toInt().let { n -> if (label.endsWith("k")) KgsRank.kyu(n).step else KgsRank.dan(n).step }
        val ladder = (1..10).sortedBy { fastBeginnerRank(it) }
        var previousTop = Int.MIN_VALUE
        ladder.forEach { level ->
            val s = style(level)
            val low = value(s.weakestRank)
            val high = value(s.strongestRank)
            assertTrue(fastBeginnerRank(level).step in low..high, "level $level ${s.profile} ∉ ${s.weakestRank}~${s.strongestRank}")
            assertTrue(low > previousTop, "level $level band overlaps the one below")
            previousTop = high
        }
    }

    @Test
    fun theFirstFiveKeepTheirOwnFallbackTier() {
        // 사람 모델을 못 쓰는 엔진의 폴백은 처음 다섯에게 제 번호 그대로다 — 사이에 더한 다섯만 제 급수의 구간을 빌린다.
        assertEquals((1..5).toList(), (1..5).map(::fastBeginnerFallbackTier))
        assertEquals(listOf(1, 2, 3, 3, 5), (6..10).map(::fastBeginnerFallbackTier))
    }
}
