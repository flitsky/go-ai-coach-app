package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.HumanPolicy
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * 5계층 — 사람 정책에서 **한 수를 뽑는 법**(백로그 #215).
 *
 * 레시피는 「꼬리 누르기」 하나다 — 확률이 [OnlyBelowProbability](1%) 아래인 수에만 온도(처음 0.85, 수가 갈수록 0.70으로)를 걸어
 * 터무니없는 수가 덜 나오게 하고, 그 위의 수들은 사람 정책 그대로 둔다. KataGo의 `gtp_human5k_example.cfg`·go-bot이 쓰는 값이고
 * (사람과 둬서 보정된 유일한 레시피), 실험실의 대국·손해 측정(E2·E2b·E4)이 전부 이것으로 뽑았다.
 *
 * ⚠️ **실험실과 같은 숫자를 내야 한다** — `engine-lab/lab/human.py`의 `move_temperature`·`temperature_transform`을 그대로 옮겼다
 * (그쪽은 KataGo v1.16.4 `Search::interpolateEarly`·`chooseIndexWithTemperature`를 옮긴 것이다). 한쪽만 고치면 실험실이 잰 세기와
 * 앱이 두는 세기가 달라진다 — `HumanMoveSamplerTest`가 실험실이 낸 값을 그대로 들고 있다.
 */
object HumanMoveSampler {
    const val TemperatureEarly: Double = 0.85
    const val Temperature: Double = 0.70

    /** 온도가 처음 값에서 끝 값으로 반쯤 가는 수 — **19줄 기준**이라 판이 작으면 그만큼 빨리 식는다. */
    const val TemperatureHalflifeMoves: Double = 80.0
    const val OnlyBelowProbability: Double = 0.01

    /** [moveNumber]번째 수를 둘 때의 온도. */
    fun temperatureAt(moveNumber: Int, boardSize: BoardSize): Double {
        val halflives = (moveNumber / TemperatureHalflifeMoves) * 19.0 / boardSize.value
        return Temperature + (TemperatureEarly - Temperature) * 0.5.pow(halflives)
    }

    /**
     * 사람 정책 → 뽑힐 확률. 가장 센 수에 견준 비율이 문턱(전체의 1%) 아래인 수만 온도로 누른다. 합이 1이 되게 맞춘다.
     * 둘 수 있는 자리가 없으면 빈 맵이다.
     */
    fun choiceDistribution(
        policy: Map<BoardCoordinate, Double>,
        temperature: Double,
        onlyBelowProbability: Double = OnlyBelowProbability,
    ): Map<BoardCoordinate, Double> {
        val positive = policy.filterValues { it > 0.0 }
        if (positive.isEmpty()) return emptyMap()
        val logMax = ln(positive.values.max())
        val logThreshold = min(0.0, ln(onlyBelowProbability.coerceAtLeast(1e-50)) + ln(positive.values.sum()) - logMax)
        val weights = positive.mapValues { (_, probability) ->
            val logRelative = ln(probability) - logMax
            exp(if (logRelative > logThreshold) logRelative else (logRelative - logThreshold) / temperature + logThreshold)
        }
        val total = weights.values.sum()
        return weights.mapValues { (_, weight) -> weight / total }
    }

    /**
     * 이 국면에서 둘 자리 하나. [excluding]은 이미 뽑았다가 둘 수 없다고 판명된 자리다(패 등) — 빼고 다시 뽑는다.
     * 뽑을 것이 없으면 `null`.
     */
    fun sample(
        policy: HumanPolicy,
        moveNumber: Int,
        boardSize: BoardSize,
        random: Random,
        excluding: Set<BoardCoordinate> = emptySet(),
    ): BoardCoordinate? {
        val distribution = choiceDistribution(policy.moves - excluding, temperatureAt(moveNumber, boardSize))
        if (distribution.isEmpty()) return null
        var remaining = random.nextDouble()
        for ((coordinate, probability) in distribution) {
            remaining -= probability
            if (remaining < 0.0) return coordinate
        }
        // 부동소수 오차로 끝까지 온 경우 — 가장 큰 것을 준다.
        return distribution.maxByOrNull { it.value }?.key
    }

    /**
     * 급수 프로필의 통과 확률이 이 값 이상일 때만 **가장 센 프로필의 정책을 한 번 더** 본다([shouldPass]).
     * 대국의 대부분은 통과 확률이 0에 가까워 그 평가 1회를 아낀다(실험실 E8).
     */
    const val PassCheckProbability: Double = 0.01

    /** 급수 프로필이 통과를 떠올렸는가 — 그렇다면 가장 센 프로필에게 물어볼 차례다. */
    fun shouldAskJudgeAboutPass(own: HumanPolicy): Boolean = (own.passProbability ?: 0.0) >= PassCheckProbability

    /**
     * 통과하는가. **약한 프로필은 너무 일찍 통과하려 든다**(KataGo 문서의 경고) — 그래서 통과는 그 급수가 아니라
     * 가장 센 프로필([judge], `rank_9d`)의 정책에서 통과가 1위일 때만 한다. 주 모델이 올라가 있지 않은 동안 통과를 정하는 자리다.
     */
    fun shouldPass(judge: HumanPolicy): Boolean {
        val pass = judge.passProbability ?: return false
        return pass > (judge.moves.values.maxOrNull() ?: 0.0)
    }
}
