package com.worksoc.goaicoach.application.attendance

const val MillisPerUtcDay: Long = 24L * 60L * 60L * 1000L

/**
 * [epochMillis]를 UTC 하루 인덱스(1970-01-01 UTC = 0)로 변환한다. 앱이 실제로 호출할 값은
 * 항상 "지금"(1970년 이후, 양수)이므로 평범한 정수 나눗셈이 내림 나눗셈과 같다 — java.time/
 * kotlinx-datetime 없이도 정확하다.
 */
fun utcDayIndex(epochMillis: Long): Long = epochMillis / MillisPerUtcDay

/** [state]는 어느 분기든 "체크인 처리 후 현재 유효한 출석 상태"다 — 보상 지급 판정처럼 분기와
 * 무관하게 최신 상태만 필요한 호출부가 `when`으로 분해하지 않아도 되게 부모에 올려 뒀다. */
sealed class AttendanceCheckInResult {
    abstract val state: AttendanceState

    /** 오늘 첫 방문. [state]는 카운트가 이미 반영된 새 상태, [rewardTier]는 방금 도달한 회차(= attendanceCount). */
    data class CheckedIn(override val state: AttendanceState, val rewardTier: Int) : AttendanceCheckInResult()

    /** 같은 UTC 날짜 안에서의 재실행 — 상태 변화 없음. */
    data class AlreadyCheckedInToday(override val state: AttendanceState) : AttendanceCheckInResult()
}

/**
 * 5계층(App Service) — cold start/foreground 복귀마다 호출하는 순수 체크인 판정 함수.
 * 연속 출석 요구가 없다: 며칠을 건너뛰어도 다음 방문에서 그냥 attendanceCount가 1 증가한다
 * (스트릭 리셋 로직 자체가 없음 — `260823-260830_OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN.md`
 * 4.1절). 같은 UTC 날짜 안에서는 몇 번을 다시 열어도 카운트가 오르지 않는다.
 */
fun AttendanceState.checkIn(nowEpochMillis: Long): AttendanceCheckInResult {
    val today = utcDayIndex(nowEpochMillis)
    if (lastCheckInUtcDay == today) {
        return AttendanceCheckInResult.AlreadyCheckedInToday(this)
    }
    val nextCount = attendanceCount + 1
    return AttendanceCheckInResult.CheckedIn(
        state = copy(attendanceCount = nextCount, lastCheckInUtcDay = today),
        rewardTier = nextCount,
    )
}

/**
 * [tier](= 그날의 `attendanceCount`)가 실제로 보상을 지급하는 회차인지. 1~7일차는 매일,
 * 8일차부터는 7의 배수(14, 21, 28, ...)에서만 지급한다 — 킥오프 플랜 4.1절 "보상 티어 매핑".
 * 어떤 보상을 줄지(콘텐츠)는 이 함수의 범위 밖이다 — 1일차 외에는 아직 미확정(4.2절).
 */
fun isRewardedTier(tier: Int): Boolean = tier in 1..7 || (tier > 7 && tier % 7 == 0)
