package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate

/**
 * 5계층 — **진 판을 AI가 어떻게 끝내는가**의 판정(백로그 #213, 사용자 2026-10-07).
 *
 * 급수 캐릭터가 이미 끝난 판에서 상대 집 안에 계속 뒀다(폰: 9줄에서 130수 — 사용자가 열다섯 번 통과하는 동안 AI가 매번 뒀다).
 * 사용자가 정한 대응은 **둘로 갈린다**:
 *
 * 1. **종국의 통과** — 통과는 AI가 *더 둘 것이 없고 계가가 맞다*고 볼 때만 한다. **대국 중반에는 통과하지 않는다.**
 *    진 판([isLost])이고 **판의 80%를 둔 뒤**([isEndgame])면 통과한다. 그 밖의 통과는 심판(9단 정책)이 통과를 1위로 볼 때뿐이다
 *    (`HumanMoveSampler.shouldPass`).
 * 2. **중반의 기권 제안** — 가망이 없으면 AI가 **한 번** 기권을 제안하고, 사용자가 받아들이거나 계속 둔다([offersResignation]).
 *
 * ⚠️ 숫자를 바꾸려면 사용자에게 묻는다. **사용자가 정한 것**: 승률 99% 이상 · AI 집 0 · 2수 연속(2026-10-07 오전 — 그때는 통과의 조건이었다),
 * 통과는 종국에서만 · 「판의 80%를 둔 상태」 · 중반에는 한 번 제안하고 사용자가 고른다(같은 날 오후), 10수(2026-10-03, #213의 목표).
 * 「집이 엄청 크다 = 30집」은 스레드가 실험실 E10으로 제안해 사용자가 골랐다.
 * **스레드가 메운 것**(사용자에게 알렸다 — 백로그 U-66): 80%를 **수순 길이**로 읽는다(놓인 돌 수로는 종국에도 80%에 닿지 않는다) · 통과에도 2수 연속을 둔다 ·
 * 제안의 조건은 오전의 통과 조건(집 0 · 99% · 30집 · 2수)과 #213의 10수(99% · 30집)를 옮겨 쓴 것이다.
 * ⚠️ 승률만 믿지 않는다 — 사람 모델의 승률은 쉽게 바닥을 친다(E10: 「승률 ≤ 1% 2수」의 8.7%는 그 진영이 이겼다).
 * 그래서 중반의 판단에는 점수차를, 통과에는 판의 진행을 함께 건다. 그렇게 건 값(E10 보조 `split.py`, 기보 414판): 종국의 통과 169판에 잘못 1,
 * 중반의 제안 147판에 잘못 4(2.7% — 바로 기권하지 않고 **묻는** 까닭이다).
 */
object HopelessPosition {
    /** 상대 승률 99% 이상 — 곧 내 승률이 이 값 이하(사용자가 말한 「승률 0%」). */
    const val MaxOwnWinRate: Double = 0.01

    /** 「상대의 집이 엄청 크다」 — 형세 점수차가 이만큼 이상 뒤진다. */
    const val MinScoreDeficit: Double = 30.0

    /** 빈 자리가 이만큼 한쪽으로 기울어야 그 진영의 집으로 센다(E10이 잰 값과 같다). */
    const val ConfidentOwnership: Double = 0.6

    /** 종국 — 판의 자리 수의 이만큼을 뒀다(수순 길이 기준). 봇끼리 둔 판의 길이 중앙값이 9줄 0.73 · 13줄 0.80 · 19줄 0.78이다(E10). */
    const val EndgameMoveShare: Double = 0.8

    /** 진 판이 종국에서 AI의 수 몇 번 이어지면 통과하는가. */
    const val LostTurnsBeforePass: Int = 2

    /** 집이 하나도 없이 크게 뒤진 상태가 AI의 수 몇 번 이어지면 기권을 제안하는가. */
    const val BarrenTurnsBeforeOffer: Int = 2

    /** 크게 뒤진 상태가 AI의 수 몇 번 이어지면(집이 있더라도) 기권을 제안하는가 — #213의 「불리한 채 10수」. */
    const val FarBehindTurnsBeforeOffer: Int = 10

    /** 판의 80%를 뒀는가 — 통과가 「대국 중반의 통과」가 되지 않게 하는 문이다. */
    fun isEndgame(state: GameState): Boolean {
        val points = state.boardSize.value * state.boardSize.value
        return state.moves.size >= points * EndgameMoveShare
    }

    /** [player]에게 진 판인가 — 상대 승률 99% 이상. 값이 없으면 아니다(모르는 것으로 통과하지 않는다). */
    fun isLost(player: StoneColor, estimate: ScoreEstimate): Boolean {
        val whiteWinRate = estimate.whiteWinRate ?: return false
        val ownWinRate = if (player == StoneColor.White) whiteWinRate else 1.0 - whiteWinRate
        return ownWinRate <= MaxOwnWinRate
    }

    /** 진 판이고 30집 이상 뒤지는가. */
    fun isFarBehind(player: StoneColor, estimate: ScoreEstimate): Boolean {
        val whiteScoreLead = estimate.whiteScoreLead ?: return false
        return isLost(player, estimate) && whiteScoreLead * towardWhite(player) <= -MinScoreDeficit
    }

    /**
     * 크게 뒤지고 **집이 하나도 없는가** — [player]가 방금 둔 뒤의 형세([estimate], 그 뒤의 판 [state]).
     * 「집 0」만으로는 초반에 걸린다(아무도 집이 없을 때는 저절로 참이다 — E10) — 그래서 늘 [isFarBehind]와 함께 본다.
     */
    fun isBarren(player: StoneColor, estimate: ScoreEstimate, state: GameState): Boolean {
        if (!isFarBehind(player, estimate)) return false
        val points = estimate.ownership?.points?.takeIf { it.isNotEmpty() } ?: return false
        // 영역 값은 백 쪽이 +다(`KataGoAnalysisParser`). 돌이 놓인 자리는 집이 아니다.
        return points.none { point -> state.stoneAt(point.coordinate) == null && point.value * towardWhite(player) >= ConfidentOwnership }
    }

    private fun towardWhite(player: StoneColor): Double = if (player == StoneColor.White) 1.0 else -1.0
}

/**
 * 한 진영의 「진 판」이 이어진 차례 수 — AI 차례가 끝날 때마다 [after]로 잇는다.
 *
 * @property countedAtMoveCount 센 직후의 수순 길이. 그 뒤로 상대가 정확히 한 수 둔 판이어야 「이어진 판」이다([continuesAt]) —
 *   새 대국·무르기·이어하기면 어긋나서 처음부터 센다.
 */
data class LosingStreak(
    val lostTurns: Int = 0,
    val farBehindTurns: Int = 0,
    val barrenTurns: Int = 0,
    val countedAtMoveCount: Int = -1,
) {
    /** [state]가 이 기록에 이어지는 판인가 — 센 뒤로 상대가 한 수 뒀다. */
    fun continuesAt(state: GameState): Boolean = countedAtMoveCount + 1 == state.moves.size

    /** [player]가 방금 둔 뒤([state])의 형세로 기록을 잇는다. 형세를 모르면([estimate]가 `null`) 처음부터 센다. */
    fun after(player: StoneColor, estimate: ScoreEstimate?, state: GameState): LosingStreak {
        fun next(turns: Int, holds: Boolean) = if (holds) turns + 1 else 0
        return LosingStreak(
            lostTurns = next(lostTurns, estimate != null && HopelessPosition.isLost(player, estimate)),
            farBehindTurns = next(farBehindTurns, estimate != null && HopelessPosition.isFarBehind(player, estimate)),
            barrenTurns = next(barrenTurns, estimate != null && HopelessPosition.isBarren(player, estimate, state)),
            countedAtMoveCount = state.moves.size,
        )
    }

    /** 이 기록 다음의 AI 차례([state])에 **통과**하는가 — 진 판이 이어졌고 종국이다. 대국 중반에는 통과하지 않는다. */
    fun passesAt(state: GameState): Boolean =
        continuesAt(state) && lostTurns >= HopelessPosition.LostTurnsBeforePass && HopelessPosition.isEndgame(state)

    /**
     * 방금 둔 뒤([state]) **기권을 제안할 만한가** — 집 없이 크게 뒤진 채 2수, 또는 크게 뒤진 채 10수. 종국이면 제안하지 않는다
     * (다음 차례에 통과로 끝낸다 — 계가로 끝나야 집 수 차이가 남는다). 한 판에 한 번만 묻는 것은 묻는 쪽(세션)이 지킨다.
     */
    fun offersResignation(state: GameState): Boolean =
        !HopelessPosition.isEndgame(state) &&
            (barrenTurns >= HopelessPosition.BarrenTurnsBeforeOffer || farBehindTurns >= HopelessPosition.FarBehindTurnsBeforeOffer)
}
