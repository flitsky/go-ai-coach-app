package com.worksoc.goaicoach.ui.foundation

/** 프로세스의 [AppPlatform] — 애플리케이션이 만들어질 때 건다(`GoAiCoachApplication`). */
object ProcessAppPlatform {
    @Volatile
    var provider: (() -> AppPlatform)? = null
}

internal actual fun defaultAppPlatform(): AppPlatform =
    ProcessAppPlatform.provider?.invoke() ?: error("AppPlatform not installed — GoAiCoachApplication sets ProcessAppPlatform.provider")
