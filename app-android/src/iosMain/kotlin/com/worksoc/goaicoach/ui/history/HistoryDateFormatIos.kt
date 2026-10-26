package com.worksoc.goaicoach.ui.history

import com.worksoc.goaicoach.ui.l10n.UiLanguage
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970

/** 안드로이드 `SimpleDateFormat`과 같은 패턴 · 언어(`HistoryDateFormatAndroid.kt`). */
internal actual fun formatHistoryMonthDay(epochMillis: Long, language: UiLanguage): String {
    val (pattern, locale) = when (language) {
        UiLanguage.Korean -> "M.d" to "ko"
        UiLanguage.English -> "MMM d" to "en"
        UiLanguage.Japanese -> "M月d日" to "ja"
        UiLanguage.ChineseSimplified -> "M月d日" to "zh_CN"
    }
    val formatter = NSDateFormatter().apply {
        dateFormat = pattern
        this.locale = NSLocale(localeIdentifier = locale)
    }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}
