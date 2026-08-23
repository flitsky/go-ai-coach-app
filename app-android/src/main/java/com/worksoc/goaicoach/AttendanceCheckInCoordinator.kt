package com.worksoc.goaicoach

import android.content.Context
import com.worksoc.goaicoach.application.attendance.AttendanceCheckInRequest
import com.worksoc.goaicoach.application.attendance.runAttendanceCheckIn
import com.worksoc.goaicoach.persistence.AttendanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [AppForegroundEvents]를 구독해 매 foreground 이벤트마다 출석 체크인을 실행하고 저장한다.
 * 어떤 보상을 지급할지는 다루지 않는다 — 상태를 최신으로 유지하는 것까지만 책임진다(그 다음
 * 단계는 `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` 4.4절 이후 항목).
 */
internal class AttendanceCheckInCoordinator(context: Context) {
    private val store = AttendanceStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            AppForegroundEvents.events.collect {
                runAttendanceCheckIn(
                    request = AttendanceCheckInRequest(nowEpochMillis = System.currentTimeMillis()),
                    store = store,
                )
            }
        }
    }
}
