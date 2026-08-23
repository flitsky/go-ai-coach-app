package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

/**
 * 완료된 대국 한 판의 기록. [SavedGameSnapshot](../savedgame/SavedGameSnapshot.kt)과는
 * 다른 개념이다 — 그건 "진행 중인 대국 1개 이어하기" 전용이고, 이건 끝난 대국을 누적해
 * "최근 대국 목록"으로 탐색하기 위한 것이다(`OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md`
 * 6장).
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
    val winner: StoneColor?,
    val margin: Double?,
    val moves: List<Move>? = null,
)
