package com.worksoc.goaicoach.application.gamehistory

/**
 * 리플레이로 **무엇을 남길지**의 정책(백로그 #151).
 *
 * ## U-35 — 무료 대국에서도 손실집수를 남긴다 (2026-09-18 사용자)
 * 착수 평가(형세·추천)의 **표시**는 프리미엄 게이팅을 그대로 둔다. 다만 **계산과 저장**은
 * 게이팅과 무관하게 한다 — 안 하면 다시보기의 실착 지표가 사실상 구독자 전용이 되고,
 * 무료로 둔 대국은 **나중에 구독해도 되살릴 수 없다**(그 순간의 국면 분석이 사라진다).
 *
 * ## ⚠️ 이것은 공짜가 아니다 — 무엇을 치르는지 알고 켤 것
 * 켜면 **사람 차례마다 사전 분석이 한 번 더 돈다.** 예전에는 "추천 수" 토글이 꺼져 있으면
 * 그 분석을 통째로 건너뛰었다. 다만 비용은 다음 이유로 작다:
 * - 이 목적의 탐색은 이미 **가장 얕은 설정**이다(`TurnAnalysisPurpose.HumanMoveReview` →
 *   `fastCandidateAnalysis(candidateCount = 1)`).
 * - 엔진이 바쁘면 **미뤄진다**(`TopMoveAnalysisDeferral`) — AI 착수를 밀어내지 않는다.
 * - 판이 끝났거나 엔진이 안 떴으면 애초에 돌지 않는다.
 *
 * ⚠️ **되돌리려면 이 상수 하나만 `false`로** 두면 예전 동작으로 정확히 돌아간다.
 */
object ReplayRecordingPolicy {
    const val RecordMoveEvaluations: Boolean = true
}
