package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate

/**
 * 5계층 — **가망 없는 판**의 판정(백로그 #213의 첫 칸, 사용자 2026-10-07).
 *
 * 급수 캐릭터가 이미 끝난 판에서 상대 집 안에 계속 뒀다(폰: 9줄에서 130수 — 28수째에 이미 AI 기준 −69집·집 0이었다).
 * 사용자가 정한 조건: **AI의 집이 0이고, 상대의 집이 엄청 크고, 상대 승률이 99% 이상**인 상태가 **AI의 수 2번** 이어지면
 * AI는 **통과**한다 — 기권이 아니다. 통과는 잘못 걸려도 다음 수에 되돌릴 수 있고, 계가로 끝나야 집 수 차이가 남는다
 * (기력 측정 대국의 승급 폭이 그 차이로 정해진다).
 *
 * ⚠️ **세 조건을 함께** 건다(실험실 E10, 기보 414판) — 승률만으로는 8.7%를 잘못 걸고(사람 모델의 승률은 쉽게 바닥을 친다),
 * 「집 0」만 더하면 **초반**에 걸린다(아무도 집이 없을 때는 그 조건이 저절로 참이다). 점수차 30집까지 걸면 18판에 잘못 0이었다.
 * ⚠️ 숫자를 바꾸려면 사용자에게 묻는다 — 조건도 숫자도 사용자가 정했다(「엄청 크다 = 30집」은 스레드가 E10으로 제안해 사용자가 골랐다).
 */
object HopelessPosition {
    /** 상대 승률 99% 이상 — 곧 내 승률이 이 값 이하. */
    const val MaxOwnWinRate: Double = 0.01

    /** 「상대의 집이 엄청 크다」 — 형세 점수차가 이만큼 이상 뒤진다. */
    const val MinScoreDeficit: Double = 30.0

    /** 빈 자리가 이만큼 한쪽으로 기울어야 그 진영의 집으로 센다(E10이 잰 값과 같다). */
    const val ConfidentOwnership: Double = 0.6

    /** 이 상태가 AI의 수 몇 번 이어지면 통과하는가. */
    const val ConsecutiveTurns: Int = 2

    /**
     * [player]가 방금 둔 뒤의 형세([estimate], 그 뒤의 판 [state])가 그 진영에게 가망 없는가.
     * 값이 하나라도 없으면(형세 추정 실패·영역 없음) 가망 없다고 보지 않는다 — 모르는 것으로 통과하지 않는다.
     */
    fun isHopelessFor(player: StoneColor, estimate: ScoreEstimate, state: GameState): Boolean {
        val whiteWinRate = estimate.whiteWinRate ?: return false
        val whiteScoreLead = estimate.whiteScoreLead ?: return false
        val points = estimate.ownership?.points?.takeIf { it.isNotEmpty() } ?: return false
        // 영역 값은 백 쪽이 +다(`KataGoAnalysisParser`).
        val towardPlayer = if (player == StoneColor.White) 1.0 else -1.0
        val ownWinRate = if (player == StoneColor.White) whiteWinRate else 1.0 - whiteWinRate
        if (ownWinRate > MaxOwnWinRate || whiteScoreLead * towardPlayer > -MinScoreDeficit) return false
        return points.none { point -> state.stoneAt(point.coordinate) == null && point.value * towardPlayer >= ConfidentOwnership }
    }
}
