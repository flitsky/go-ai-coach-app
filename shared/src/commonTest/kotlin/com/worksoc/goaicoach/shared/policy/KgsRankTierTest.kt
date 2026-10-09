package com.worksoc.goaicoach.shared.policy

import kotlin.test.Test
import kotlin.test.assertEquals

/** 기력의 구간 일곱(백로그 #236) — 급수 29칸이 빠짐없이, 겹치지 않고 한 구간에 든다. */
class KgsRankTierTest {
    @Test
    fun everyRankBelongsToExactlyOneTierAndTiersRunWeakestToStrongest() {
        KgsRank.all.forEach { rank ->
            assertEquals(1, KgsRankTier.entries.count { rank in it }, "${rank.profile} must sit in exactly one tier")
        }
        assertEquals(KgsRank.Weakest, KgsRankTier.entries.first().weakest)
        assertEquals(KgsRank.Strongest, KgsRankTier.entries.last().strongest)
        KgsRankTier.entries.zipWithNext { weaker, stronger ->
            assertEquals(weaker.strongest.strongerBy(1), stronger.weakest, "$weaker must hand over to $stronger without a gap")
        }
    }

    /** 사용자가 정한 경계(2026-10-09) — 급은 다섯 칸씩, 단은 세 칸씩. */
    @Test
    fun theBoundariesAreTheOnesTheUserNamed() {
        val expected = mapOf(
            KgsRankTier.Bronze to (KgsRank.kyu(20) to KgsRank.kyu(16)),
            KgsRankTier.Silver to (KgsRank.kyu(15) to KgsRank.kyu(11)),
            KgsRankTier.Gold to (KgsRank.kyu(10) to KgsRank.kyu(6)),
            KgsRankTier.Platinum to (KgsRank.kyu(5) to KgsRank.kyu(1)),
            KgsRankTier.Diamond to (KgsRank.dan(1) to KgsRank.dan(3)),
            KgsRankTier.Master to (KgsRank.dan(4) to KgsRank.dan(6)),
            KgsRankTier.Grandmaster to (KgsRank.dan(7) to KgsRank.dan(9)),
        )
        assertEquals(expected, KgsRankTier.entries.associateWith { it.weakest to it.strongest })
        assertEquals(KgsRankTier.Platinum, KgsRank.kyu(1).tier)
        assertEquals(KgsRankTier.Diamond, KgsRank.dan(1).tier)
    }
}
