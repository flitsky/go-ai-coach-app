package com.worksoc.goaicoach

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * App-wide lifecycle hook — registered via `android:name` in
 * AndroidManifest.xml. The only job here is wiring
 * `ProcessLifecycleOwner` to [AppForegroundEvents]; feature-specific
 * logic (attendance check-in, etc.) subscribes to that event stream
 * instead of adding more observers here.
 *
 * ⚠️ **[ReleaseResetCoordinator]만 예외로 이벤트 스트림을 거치지 않고 여기서 직접, 그리고 가장
 * 먼저 돈다**(백로그 #63). 정식 릴리즈 초기화는 **다른 무엇도 읽거나 쓰기 전에** 끝나야 하기
 * 때문이다 — 출석 체크인이 먼저 돌면 그날 기록이 붙었다가 곧바로 지워져 사용자가 출석을 잃는다.
 */
class GoAiCoachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 순서가 중요하다 — 아래 둘보다 먼저다(KDoc 참고).
        ReleaseResetCoordinator(this).applyIfNeeded()
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundObserver)
        AttendanceCheckInCoordinator(this).start()
    }

    private object ForegroundObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            AppForegroundEvents.notifyForegrounded()
        }
    }
}
