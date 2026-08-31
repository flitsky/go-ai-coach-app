package com.worksoc.goaicoach.application.attendance

/**
 * 6계층(Session & Continuity) — 출석 체크인 상태. [lastCheckInUtcDay]는 포맷된 날짜 문자열이
 * 아니라 UTC 자정 기준 "에폭 이후 며칠째"를 나타내는 정수 하루 인덱스다([utcDayIndex] 참고).
 * `shared`는 플랫폼 독립적이어야 하는데(`ARCHITECTURE.md` 7계층 원칙) 이 프로젝트엔 아직
 * 날짜 파싱/포맷 라이브러리(kotlinx-datetime 등)가 없어서, 문자열 대신 정수 비교만으로
 * "오늘 이미 체크인했는가"를 판정할 수 있는 이 표현을 택했다.
 *
 * [claimedTiers]는 어떤 회차(= 그날의 [attendanceCount])의 보상이 실제로 지급 완료됐는지
 * 기록하는 원장이다 — 체크인 판정([AttendanceCheckInResult]) 자체와는 별개 축으로, 보상을
 * 실제로 지급하는 쪽(예: 1일차 "무르기 무제한" 자동 지급)이 지급 후에 채운다.
 */
data class AttendanceState(
    val attendanceCount: Int = 0,
    val lastCheckInUtcDay: Long? = null,
    val claimedTiers: Set<Int> = emptySet(),
) {
    fun isTierClaimed(tier: Int): Boolean = tier in claimedTiers

    fun withTierClaimed(tier: Int): AttendanceState = copy(claimedTiers = claimedTiers + tier)
}
