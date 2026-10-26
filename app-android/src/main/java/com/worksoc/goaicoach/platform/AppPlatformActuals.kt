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
    forceEeaConsentDebug = BuildConfig.FORCE_EEA_CONSENT_DEBUG,
)

actual fun localTimeOfDay(): LocalTimeOfDay = LocalTime.now().let { now -> LocalTimeOfDay(now.hour, now.minute, now.second) }

actual fun formatDeviceLocale(format: String, vararg args: Any?): String = String.format(format, *args)

actual fun formatRootLocale(format: String, vararg args: Any?): String = String.format(Locale.ROOT, format, *args)

actual fun debugLog(tag: String, message: String) {
    android.util.Log.i(tag, message)
}

actual val appIoDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

actual fun decodeText(bytes: ByteArray, charsetName: String?): String =
    String(bytes, charsetName?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8)
