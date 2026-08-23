package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

/** 사람 플레이어 기준 결과. [Resign]은 어느 쪽이 기권했는지 구분하지 않는다(사용자 요청). */
enum class GameHistoryResult {
    Win,
    Loss,
    Draw,
    Resign,
}

/**
 * 완료된 대국 한 판의 기록. [SavedGameSnapshot](../savedgame/SavedGameSnapshot.kt)과는
 * 다른 개념이다 — 그건 "진행 중인 대국 1개 이어하기" 전용이고, 이건 끝난 대국을 누적해
 * "최근 대국 목록"으로 탐색하기 위한 것이다(`OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md`
 * 6장).
 *
 * [humanColor]/[result]는 **사람 대 AI 대국**을 전제로 기록 시점에 미리 계산해 둔다(사람끼리
 * 또는 AI끼리 대국은 애초에 기록 대상이 아니다 — [runGameHistoryAppendIfCompleted] 참고).
 * [margin]은 [result]가 [GameHistoryResult.Win]/[GameHistoryResult.Loss]일 때만 값이 있을 수
 * 있다(기권·무승부는 집수 차가 없다).
 *
 * [moves]는 Phase 1에서는 항상 `null`이다(단순 목록 표시만 — 기보 재분석은 다음 단계) — 나중에
 * 채울 자리를 미리 비워둬서, 필드를 추가할 때 이미 저장된 데이터와의 마이그레이션 부담을
 * 줄이기 위함이다.
 */
data class GameHistoryEntry(
    val id: String,
    val playedAtMillis: Long,
    val boardSize: Int,
    val ruleset: Ruleset,
    val komi: Double,
    val handicapCount: Int,
    val playerSetup: PlayerSetup,
    val moveCount: Int,
    val humanColor: StoneColor,
    val result: GameHistoryResult,
    val margin: Double? = null,
    val moves: List<Move>? = null,
)
