package com.worksoc.goaicoach.shared.policy

import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.playstyle.humanPlayStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** KGS 급수 29칸(백로그 #217) — 20급부터 9단까지. 급과 단은 숫자의 방향이 반대라, 세기는 칸([KgsRank.step])으로 센다. */
class KgsRankTest {
    @Test
    fun theTwentyNineStepsRunFromTwentyKyuToNineDan() {
        assertEquals(29, KgsRank.all.size)
        assertEquals("rank_20k", KgsRank.Weakest.profile)
        assertEquals("rank_1k", KgsRank(20).profile)
        assertEquals("rank_1d", KgsRank(21).profile)
        assertEquals("rank_9d", KgsRank.Strongest.profile)
        assertEquals(KgsRank(16), KgsRank.kyu(5))
        assertEquals(KgsRank(23), KgsRank.dan(3))
        assertEquals(KgsRank.all, KgsRank.all.sorted(), "a larger step is a stronger rank")
    }

    /** 1급 다음은 1단이다(0급이 없다). 끝을 넘어서는 가지 않는다. */
    @Test
    fun strengthArithmeticCrossesTheKyuDanBoundaryAndStopsAtTheEnds() {
        assertEquals(KgsRank.dan(1), KgsRank.kyu(1).strongerBy(1))
        assertEquals(KgsRank.kyu(2), KgsRank.dan(2).weakerBy(3))
        assertEquals(KgsRank.Strongest, KgsRank.dan(8).strongerBy(5))
        assertEquals(KgsRank.Weakest, KgsRank.kyu(19).weakerBy(5))
        assertFailsWith<IllegalArgumentException> { KgsRank(0) }
        assertEquals(KgsRank.Strongest, KgsRank.ofStepCoerced(99), "a broken stored value must not crash the reader")
    }

    /**
     * 급수를 직접 고른 상대는 **그 급수의 공식 프로필**로 둔다 — 캐릭터와 같은 엔진 경로(사람 모델의 정책)다.
     * 단계 번호가 곧 급수의 칸이라, 저장된 좌석 설정만으로 급수를 되찾는다.
     */
    @Test
    fun aCustomRankOpponentPlaysWithThatRanksOfficialProfile() {
        val fiveKyu = KgsRank.kyu(5).toPlayLevelSetting()

        assertEquals(PlayLevelSetting(PlayLevelGroup.CustomRank, level = 16), fiveKyu)
        assertEquals(KgsRank.kyu(5), fiveKyu.customRank())
        assertEquals("rank_5k", fiveKyu.humanPlayStyle()?.profile)
        assertEquals("rank_3d", KgsRank.dan(3).toPlayLevelSetting().humanPlayStyle()?.profile)
        assertEquals(EngineSearchMode.GtpStatefulFast, fiveKyu.aiMoveSearchMode())
        assertNull(PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3).customRank())
    }

    /** 이름은 급수다 — "16단계"로 적으면 16단과 헷갈린다. */
    @Test
    fun theTierLabelIsTheRankNotAStageNumber() {
        assertEquals("5급", KgsRank.kyu(5).toPlayLevelSetting().tierLabel)
        assertEquals("3단", KgsRank.dan(3).toPlayLevelSetting().tierLabel)
    }

    /**
     * 사람 모델을 못 쓰는 기기에서는 그 급수가 속한 캐릭터 구간의 고르는 법으로 둔다 — 구간표는 캐릭터 5명의 범위 그대로다
     * (12~18급 · 6~12급 · 1~6급 · 1~5단 · 5~9단, 맞닿는 급수는 센 쪽).
     */
    @Test
    fun withoutTheHumanModelACustomRankFallsBackToItsCharacterTier() {
        assertEquals(
            listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5),
            listOf(KgsRank.kyu(20), KgsRank.kyu(13), KgsRank.kyu(12), KgsRank.kyu(7), KgsRank.kyu(6), KgsRank.kyu(1), KgsRank.dan(1), KgsRank.dan(4), KgsRank.dan(5), KgsRank.dan(9))
                .map(::customRankFallbackTier),
        )
        assertEquals(
            PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3).selectionPolicy,
            KgsRank.kyu(3).toPlayLevelSetting().selectionPolicy,
        )
        assertEquals(MoveSelectionPolicy.BestOnly, KgsRank.dan(7).toPlayLevelSetting().selectionPolicy)
    }
}
