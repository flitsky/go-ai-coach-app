package com.worksoc.goaicoach.platform

import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import platform.Foundation.NSBundle
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSDate

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary

/** iOS가 처음 나가는 판의 versionCode(1.8.4). 한 번 출시하면 바꾸지 않는다. */
private const val IosFirstReleaseVersionCode = 10804

/** Google 공식 시험 광고의 게시자 번호 — `app-ios/Config/Ads.xcconfig`의 시험 ID가 모두 이것으로 시작한다. */
private const val GoogleTestAdsPublisher = "ca-app-pub-3940256099942544"

/**
 * 번들의 판 이름 · 번호. 빌드 시각은 Info.plist의 `GoCoachBuildTime`(없으면 빈 칸).
 * 시험 광고 여부는 번들의 AdMob 앱 ID로 판단한다(#296 — 출시 구성은 `Ads.local.xcconfig`의 실제 ID, 없으면 시험 ID) — 진단 줄 `test-ads`가 실제와 맞게.
 */
actual val appBuildInfo: AppBuildInfo = run {
    val info = NSBundle.mainBundle.infoDictionary
    AppBuildInfo(
        versionName = info?.get("CFBundleShortVersionString") as? String ?: "",
        versionCode = (info?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 0,
        buildTime = info?.get("GoCoachBuildTime") as? String ?: "",
        buildType = if (isDebugBuild) "debug" else "release",
        useTestAds = (info?.get("GADApplicationIdentifier") as? String)?.startsWith(GoogleTestAdsPublisher) ?: true,
        // iOS 첫 판(사용자 2026-10-27: 안드로이드와 같은 번호 1.8.4로 시작) — 「새로워진 점」은 이보다 뒤의 묶음부터.
        firstReleaseVersionCode = IosFirstReleaseVersionCode,
    )
}

actual fun localTimeOfDay(): LocalTimeOfDay {
    val parts = NSCalendar.currentCalendar.components(
        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
        fromDate = NSDate(),
    )
    return LocalTimeOfDay(hour = parts.hour.toInt(), minute = parts.minute.toInt(), second = parts.second.toInt())
}

actual fun formatDeviceLocale(format: String, vararg args: Any?): String = formatAscii(format, args)

actual fun formatRootLocale(format: String, vararg args: Any?): String = formatAscii(format, args)

/**
 * 이 앱이 쓰는 `String.format` 지시자만 아는 서식기 — `%d` · `%s` · `%f`, 인자 번호(`%2$s`), 플래그 `0`/`-`, 너비, 정밀도, `%%`.
 * 모르는 지시자는 터뜨린다 — 조용히 틀린 글자를 그리는 것보다 낫다(공용 문자열 테스트가 안드로이드와 견준다).
 */
internal fun formatAscii(format: String, args: Array<out Any?>): String {
    val out = StringBuilder()
    var nextArg = 0
    var i = 0
    while (i < format.length) {
        val c = format[i]
        if (c != '%') {
            out.append(c)
            i++
            continue
        }
        var j = i + 1
        if (format.getOrNull(j) == '%') {
            out.append('%')
            i = j + 1
            continue
        }
        if (format.getOrNull(j) == 'n') {
            out.append('\n')
            i = j + 1
            continue
        }
        val digitsStart = j
        while (j < format.length && format[j].isDigit()) j++
        var argIndex: Int? = null
        if (j > digitsStart && format.getOrNull(j) == '$') {
            argIndex = format.substring(digitsStart, j).toInt() - 1
            j++
        } else {
            j = digitsStart
        }
        var zeroPad = false
        var leftAlign = false
        while (j < format.length && format[j] in "0-+ #") {
            if (format[j] == '0') zeroPad = true
            if (format[j] == '-') leftAlign = true
            j++
        }
        val widthStart = j
        while (j < format.length && format[j].isDigit()) j++
        val width = if (j > widthStart) format.substring(widthStart, j).toInt() else 0
        var precision: Int? = null
        if (format.getOrNull(j) == '.') {
            j++
            val precisionStart = j
            while (j < format.length && format[j].isDigit()) j++
            precision = format.substring(precisionStart, j).toIntOrNull() ?: 0
        }
        val conversion = format.getOrNull(j) ?: throw IllegalArgumentException("format ends inside a specifier: $format")
        val arg = args.getOrNull(argIndex ?: nextArg++)
        val body = when (conversion) {
            'd' -> (arg as Number).toLong().toString()
            's' -> arg.toString()
            'f' -> fixedPoint((arg as Number).toDouble(), precision ?: 6)
            else -> throw IllegalArgumentException("unsupported conversion %$conversion in $format")
        }
        out.append(padded(body, width, zeroPad && conversion != 's', leftAlign))
        i = j + 1
    }
    return out.toString()
}

private fun padded(body: String, width: Int, zeroPad: Boolean, leftAlign: Boolean): String {
    if (body.length >= width) return body
    val fill = width - body.length
    return when {
        leftAlign -> body + " ".repeat(fill)
        zeroPad && body.startsWith("-") -> "-" + "0".repeat(fill) + body.substring(1)
        zeroPad -> "0".repeat(fill) + body
        else -> " ".repeat(fill) + body
    }
}

/** `%.Nf` — 반올림은 절반 올림(자바의 `String.format`과 같은 방향; 이진 표현의 경계값은 다를 수 있다). */
private fun fixedPoint(value: Double, precision: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    val scale = 10.0.pow(precision)
    val scaled = floor(abs(value) * scale + 0.5).toLong()
    val negative = value < 0 && scaled != 0L
    val digits = scaled.toString().padStart(precision + 1, '0')
    val whole = digits.dropLast(precision)
    val fraction = digits.takeLast(precision)
    val text = if (precision == 0) whole else "$whole.$fraction"
    return if (negative) "-$text" else text
}

actual fun debugLog(tag: String, message: String) {
    println("$tag: $message")
}

actual val appIoDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
