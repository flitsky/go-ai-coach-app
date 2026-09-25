package com.worksoc.goaicoach.application.attendance

data class AttendanceCheckInRequest(val nowEpochMillis: Long)

/**
 * 6계층(Session & Continuity) — [store]에서 현재 출석 상태를 읽고, 오늘 첫 방문이면 카운트를 올려
 * 저장까지 완료한다. 앱의 foreground 이벤트(콜드 스타트 포함)마다 호출하도록 설계됐다.
 * 어떤 보상을 줄지는 이 함수의 책임이 아니다 — 반환값이 [AttendanceCheckInResult.CheckedIn]이면
 * 그 [AttendanceCheckInResult.CheckedIn.rewardTier]를 보고 실제 지급은 호출부가 판단한다.
 *
 * ⚠️ 읽기와 쓰기를 [AttendanceStorePort.update] 한 번으로 한다(refactor backlog #21) — 이 함수는
 * IO 스레드(`AttendanceCheckInCoordinator`)에서도 돌아, 메인 스레드의 Claim과 겹칠 수 있다.
 */
fun runAttendanceCheckIn(
    request: AttendanceCheckInRequest,
    store: AttendanceStorePort,
): AttendanceCheckInResult {
    var result: AttendanceCheckInResult? = null
    store.update { current ->
        val checkIn = current.checkIn(request.nowEpochMillis)
        result = checkIn
        (checkIn as? AttendanceCheckInResult.CheckedIn)?.state
    }
    return checkNotNull(result) { "AttendanceStorePort.update must invoke its transform exactly once" }
}
