package com.worksoc.goaicoach.ui.foundation

internal actual fun defaultAppPlatform(): AppPlatform =
    error("AppPlatform not provided — MainViewController provides LocalAppPlatform")
