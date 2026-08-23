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
 */
class GoAiCoachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundObserver)
    }

    private object ForegroundObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            AppForegroundEvents.notifyForegrounded()
        }
    }
}
