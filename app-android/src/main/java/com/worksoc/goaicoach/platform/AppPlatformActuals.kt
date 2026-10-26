package com.worksoc.goaicoach.platform

import com.worksoc.goaicoach.BuildConfig
import java.time.LocalTime
import java.util.Locale

actual val isDebugBuild: Boolean = BuildConfig.DEBUG

actual val appBuildInfo: AppBuildInfo = AppBuildInfo(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    buildTime = BuildConfig.BUILD_TIME,
    buildType = BuildConfig.BUILD_TYPE,
    useTestAds = BuildConfig.USE_TEST_ADS,
)

actual fun localTimeOfDay(): LocalTimeOfDay = LocalTime.now().let { now -> LocalTimeOfDay(now.hour, now.minute, now.second) }

actual fun formatDeviceLocale(format: String, vararg args: Any?): String = String.format(format, *args)

actual fun formatRootLocale(format: String, vararg args: Any?): String = String.format(Locale.ROOT, format, *args)

actual fun debugLog(tag: String, message: String) {
    android.util.Log.i(tag, message)
}
