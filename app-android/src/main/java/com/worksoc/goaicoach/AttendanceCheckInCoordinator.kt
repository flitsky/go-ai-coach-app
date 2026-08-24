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
 * [AppForegroundEvents]를 구독해 매 foreground 이벤트마다 출석 체크인을 실행/저장한다.
 *
 * ⚠️ **보상 지급은 여기서 하지 않는다.** 보상이 자동 지급에서 Claim 방식으로 바뀌면서
 * (`OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` 5.1절, 백로그 #14) 지급 경로는
 * 사용자가 Claim 버튼을 누르는 `ui/AttendanceRewardClaimDialog.kt` 하나뿐이다 — 여기서 미리
 * 지급해 버리면 팝업에 보여줄 것이 남지 않는다. 이 클래스가 계속 필요한 이유는 **앱을 켜기만
 * 하고 팝업을 닫아도 출석 자체는 기록돼야** 하기 때문이다(체크인과 지급은 별개 축 —
 * `AttendanceState.claimedTiers` 참고).
 *
 * 체크인 판정/저장 로직 자체는 `shared`의 `runAttendanceCheckIn`이 갖고 있고, 여기서는 안드로이드
 * 저장소 어댑터를 연결하는 배선만 한다. Compose 트리 밖(`Application.onCreate` → 백그라운드
 * 코루틴)에서 도는데, 체크인이 UI에 의존하지 않는 순수 진입점이라 그대로 호출할 수 있다.
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
