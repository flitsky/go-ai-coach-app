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

/**
 * 5계층(App Service) — **개발자 테스트용**으로 "오늘 아직 체크인하지 않은" 상태로 되감는다
 * (백로그 #71). 다음 체크인이 하루를 진행시키므로 출석일이 하나 오른다.
 *
 * ## ⚠️ 시계를 앞으로 돌리는 방식은 성립하지 않는다 — 그래서 저장값을 되감는다
 * 출석 판정에 들어오는 벽시계는 **`AppClock`을 거치지 않는다.** app-android의 두 진입점
 * (`AttendanceCheckInCoordinator`, `AttendanceRewardClaimDialog`)이 `System.currentTimeMillis()`를
 * 직접 읽고, `AppClock.currentEpochMillis()`는 포트가 아니라 최상위 함수다. 게다가 전역 오프셋을
 * 만들면 프리미엄 만료·대국 타이머·기록 타임스탬프까지 함께 오염된다(직접 호출이 20곳이다).
 * **되감기는 시간을 읽을 필요가 없다** — [AttendanceState.lastCheckInUtcDay]만 비우면 된다.
 *
 * ## ⚠️ [AttendanceState.attendanceCount]는 건드리지 않는다
 * 증가는 [checkIn]의 책임이다. 여기서 같이 올리면 **체크인 경로가 둘이 되어** 하나를 고칠 때
 * 다른 하나가 남는다 — 그 종류의 이중 경로가 #66에서 어떻게 끝났는지 보면 된다.
 *
 * @return 되감은 새 상태. 이미 "오늘 안 함" 상태였으면 아무것도 하지 않고 `null`.
 */
fun runAttendanceDevDayRewind(store: AttendanceStorePort): AttendanceState? {
    val current = store.load()
    if (current.lastCheckInUtcDay == null) return null
    val next = current.copy(lastCheckInUtcDay = null)
    store.save(next)
    return next
}
