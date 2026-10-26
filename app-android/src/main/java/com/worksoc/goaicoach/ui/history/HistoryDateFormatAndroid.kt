package com.worksoc.goaicoach.ui.history

import com.worksoc.goaicoach.ui.l10n.UiLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual fun formatHistoryMonthDay(epochMillis: Long, language: UiLanguage): String = dateFormat(language).format(Date(epochMillis))

/**
 * 언어별 날짜 표기 어순이 달라 [UiLanguage]마다 다른 패턴/로케일을 쓴다(연도는 생략 — 목록용 짧은 표기).
 *
 * ⚠️ **시각(HH:mm)을 뺐다**(백로그 #151, 2026-09-18 사용자). 한 줄에 다섯 조각이 들어가는데
 * 분 단위까지 붙으면 폭만 먹고 고르는 데 도움이 안 된다 — 같은 날 여러 판은 **차례로 놓인다.**
 */
private fun dateFormat(language: UiLanguage): SimpleDateFormat =
    when (language) {
        UiLanguage.Korean -> SimpleDateFormat("M.d", Locale.KOREAN)
        UiLanguage.English -> SimpleDateFormat("MMM d", Locale.ENGLISH)
        UiLanguage.Japanese -> SimpleDateFormat("M月d日", Locale.JAPANESE)
        UiLanguage.ChineseSimplified -> SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE)
    }
