package com.worksoc.goaicoach.ios

import com.worksoc.goaicoach.AppForegroundEvents
import com.worksoc.goaicoach.application.attendance.AttendanceCheckInRequest
import com.worksoc.goaicoach.application.attendance.runAttendanceCheckIn
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.ui.foundation.AppPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillEnterForegroundNotification

private var started = false

/**
 * 프로세스 시작에 한 번(백로그 #294 — 이식 계획 S29) — 안드로이드 `GoAiCoachApplication.onCreate`의 짝. 지금 하는 일:
 * 앞으로 올 때마다 `AppForegroundEvents`를 알리고(콜드 스타트 포함 — 안드로이드 `ProcessLifecycleOwner.ON_START`와 같다), 그때마다 출석을
 * 확인한다(`AttendanceCheckInCoordinator`와 같은 일). 배경음악을 켠다([IosBackgroundMusic]).
 * 하지 않는 것: 정식 릴리스 초기화(#63 — iOS에는 지울 베타 기록이 없다) · 개발자 모드 주기 초기화(#99 — 시험용 빌드 몫, 실기 시험 때 다시 본다).
 */
internal fun runIosAppStartup(platform: AppPlatform) {
    if (started) return
    started = true
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // UNDISPATCHED — 아래 콜드 스타트 알림보다 구독이 먼저 걸려야 한다(버퍼만 있고 재생이 없는 흐름이다).
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        AppForegroundEvents.events.collect {
            runAttendanceCheckIn(
                request = AttendanceCheckInRequest(nowEpochMillis = currentEpochMillis()),
                store = platform.stores.attendance,
            )
        }
    }
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationWillEnterForegroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ -> AppForegroundEvents.notifyForegrounded() }
    IosBackgroundMusic(platform.stores.backgroundMusic).start()
    // 콜드 스타트도 「앞으로 왔다」다(안드로이드 `ProcessLifecycleOwner.ON_START`가 콜드 스타트에도 온다).
    scope.launch(Dispatchers.Main) { AppForegroundEvents.notifyForegrounded() }
}
