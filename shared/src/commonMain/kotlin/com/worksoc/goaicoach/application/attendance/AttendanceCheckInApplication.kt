package com.worksoc.goaicoach.application.attendance

data class AttendanceCheckInRequest(val nowEpochMillis: Long)

/**
 * 5계층(App Service) — [store]에서 현재 출석 상태를 읽고, 오늘 첫 방문이면 카운트를 올려
 * 저장까지 완료한다. 앱의 foreground 이벤트(콜드 스타트 포함)마다 호출하도록 설계됐다.
 * 어떤 보상을 줄지는 이 함수의 책임이 아니다 — 반환값이 [AttendanceCheckInResult.CheckedIn]이면
 * 그 [AttendanceCheckInResult.CheckedIn.rewardTier]를 보고 실제 지급은 호출부가 판단한다.
 */
fun runAttendanceCheckIn(
    request: AttendanceCheckInRequest,
    store: AttendanceStorePort,
): AttendanceCheckInResult {
    val current = store.load()
    val result = current.checkIn(request.nowEpochMillis)
    if (result is AttendanceCheckInResult.CheckedIn) {
        store.save(result.state)
    }
    return result
}
