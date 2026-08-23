package com.worksoc.goaicoach

import android.content.Context
import com.worksoc.goaicoach.application.attendance.AttendanceCheckInRequest
import com.worksoc.goaicoach.application.attendance.runAttendanceCheckIn
import com.worksoc.goaicoach.application.attendance.runAttendanceRewardGrant
import com.worksoc.goaicoach.persistence.AttendanceStore
import com.worksoc.goaicoach.persistence.PremiumStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [AppForegroundEvents]를 구독해 매 foreground 이벤트마다 출석 체크인을 실행/저장하고, 이어서
 * 아직 지급되지 않은 출석 보상을 지급한다(현재 내용이 확정된 보상은 1일차 "무르기 무제한"뿐 —
 * `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` 4.2·4.4절).
 *
 * 지급 판정/저장 로직 자체는 `shared`의 `runAttendanceRewardGrant`가 갖고 있고, 여기서는
 * 안드로이드 저장소 어댑터 두 개를 연결하는 배선만 한다. 이 클래스는 Compose 트리 밖
 * (`Application.onCreate` → 백그라운드 코루틴)에서 도는데, 프리미엄 클레임이 UI에 의존하지
 * 않는 진입점(`runPremiumFeatureClaim`)으로 분리돼 있어 그대로 호출할 수 있다.
 */
internal class AttendanceCheckInCoordinator(context: Context) {
    private val store = AttendanceStore(context)
    private val premiumStore = PremiumStateStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            AppForegroundEvents.events.collect {
                val result = runAttendanceCheckIn(
                    request = AttendanceCheckInRequest(nowEpochMillis = System.currentTimeMillis()),
                    store = store,
                )
                runAttendanceRewardGrant(
                    state = result.state,
                    attendanceStore = store,
                    premiumStore = premiumStore,
                )
            }
        }
    }
}
