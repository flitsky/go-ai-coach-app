package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.HumanPolicy
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사람 정책에서 수를 뽑는 법(백로그 #215)이 **실험실과 같은 숫자**를 내는가.
 *
 * 기대값은 `engine-lab/lab/human.py`(`move_temperature`·`temperature_transform`, R2 꼬리 누르기)가 낸 값을 그대로 옮긴 것이다 —
 * 실험실의 대국·손해 측정(E2·E2b·E4·E8)이 전부 그 함수로 뽑았으므로, 여기가 어긋나면 실험실이 잰 세기와 앱이 두는 세기가 달라진다.
 */
class HumanMoveSamplerTest {
    private val policy = mapOf(point(0) to 0.60, point(1) to 0.25, point(2) to 0.10, point(3) to 0.03, point(4) to 0.008, point(5) to 0.006, point(6) to 0.004, point(7) to 0.002)

    @Test
    fun theTemperatureCoolsWithTheMoveNumberAndFasterOnSmallBoards() {
        assertClose(0.850000, HumanMoveSampler.temperatureAt(0, BoardSize.Thirteen))
        assertClose(0.790387, HumanMoveSampler.temperatureAt(40, BoardSize.Thirteen))
        assertClose(0.732820, HumanMoveSampler.temperatureAt(120, BoardSize.Thirteen))
        assertClose(0.806066, HumanMoveSampler.temperatureAt(40, BoardSize.Nineteen))
        assertClose(0.772167, HumanMoveSampler.temperatureAt(40, BoardSize.Nine))
    }

    /** 1% 위의 수는 사람 정책 그대로(다시 맞춘 비율만 달라진다), 그 아래의 꼬리만 눌린다. */
    @Test
    fun onlyTheTailBelowOnePercentIsPressedDown() {
        val early = HumanMoveSampler.choiceDistribution(policy, HumanMoveSampler.temperatureAt(0, BoardSize.Thirteen))
        val late = HumanMoveSampler.choiceDistribution(policy, HumanMoveSampler.temperatureAt(120, BoardSize.Thirteen))

        assertEquals(listOf(0.601153, 0.25048, 0.100192, 0.030058, 0.007706, 0.005493, 0.003409, 0.001508), early.values.map(::round6))
        assertEquals(listOf(0.602209, 0.25092, 0.100368, 0.03011, 0.007402, 0.004999, 0.002875, 0.001116), late.values.map(::round6))
        assertClose(1.0, early.values.sum())
        assertClose(0.60 / 0.25, early.getValue(point(0)) / early.getValue(point(1)))
    }

    @Test
    fun samplingFollowsTheDistribution() {
        val human = HumanPolicy("rank_15k", policy, passProbability = 0.0)
        val random = Random(20261005)
        val counts = (1..20_000).groupingBy { HumanMoveSampler.sample(human, moveNumber = 0, BoardSize.Thirteen, random) }.eachCount()

        assertTrue(abs(counts.getValue(point(0)) / 20_000.0 - 0.601) < 0.015, "the favourite is drawn about 60% of the time: $counts")
        assertTrue(abs(counts.getValue(point(1)) / 20_000.0 - 0.250) < 0.015)
        assertTrue(counts.getValue(point(7)) < 100, "a 0.2% move stays rare")
    }

    @Test
    fun excludedPointsAreNeverDrawnAndNothingLeftMeansNull() {
        val human = HumanPolicy("rank_15k", mapOf(point(0) to 0.9, point(1) to 0.1), passProbability = 0.0)

        repeat(200) { assertEquals(point(1), HumanMoveSampler.sample(human, 0, BoardSize.Nine, Random(it), excluding = setOf(point(0)))) }
        assertNull(HumanMoveSampler.sample(human, 0, BoardSize.Nine, Random(1), excluding = setOf(point(0), point(1))))
    }

    /** 통과는 급수 프로필이 1% 이상 떠올렸을 때만 묻고, 가장 센 프로필의 정책에서 통과가 1위일 때만 한다. */
    @Test
    fun thePassRule() {
        val quiet = HumanPolicy("rank_15k", policy, passProbability = 0.004)
        val tempted = HumanPolicy("rank_15k", policy, passProbability = 0.01)

        assertTrue(!HumanMoveSampler.shouldAskJudgeAboutPass(quiet))
        assertTrue(HumanMoveSampler.shouldAskJudgeAboutPass(tempted))
        assertTrue(HumanMoveSampler.shouldPass(HumanPolicy("rank_9d", mapOf(point(0) to 0.3), passProbability = 0.6)))
        assertTrue(!HumanMoveSampler.shouldPass(HumanPolicy("rank_9d", mapOf(point(0) to 0.6), passProbability = 0.3)))
        assertTrue(!HumanMoveSampler.shouldPass(HumanPolicy("rank_9d", mapOf(point(0) to 0.6), passProbability = null)))
    }

    /**
     * 캐릭터 다섯이 모두 사람 모델로 둔다 — 판다 15급 · 돌뫼 9급 · 반상 1급 · 사범 꼬북 3단 · 관장 천원 7단(사용자 2026-10-06, 폰에서 둬 본 뒤).
     * 숨겨 둔 그룹은 예전 방식이다. 급수를 직접 정한 상대(승급 대국, #219)는 캐릭터가 아니라 따로 본다(`KgsRankTest`).
     */
    @Test
    fun onlyTheThreeKyuCharactersHaveAHumanStyle() {
        assertEquals("rank_15k", PlayLevelSetting(level = 1).humanPlayStyle()?.profile)
        assertEquals("rank_9k", PlayLevelSetting(level = 2).humanPlayStyle()?.profile)
        assertEquals("rank_1k", PlayLevelSetting(level = 3).humanPlayStyle()?.profile)
        assertEquals("rank_3d", PlayLevelSetting(level = 4).humanPlayStyle()?.profile)
        assertEquals("rank_7d", PlayLevelSetting(level = 5).humanPlayStyle()?.profile)
        PlayLevelGroup.entries.filter { it != PlayLevelGroup.FastBeginner && it != PlayLevelGroup.CustomRank }.forEach { group ->
            assertNull(PlayLevelSetting(group = group, level = 1).humanPlayStyle(), "$group keeps its current move selection")
        }
    }

    private fun point(index: Int) = BoardCoordinate(0, index)

    private fun round6(value: Double): Double = kotlin.math.round(value * 1_000_000) / 1_000_000

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 5e-7, "expected $expected but was $actual")
}
