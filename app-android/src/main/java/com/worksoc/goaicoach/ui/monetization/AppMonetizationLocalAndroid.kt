package com.worksoc.goaicoach.ui.monetization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.AndroidAppMonetization
import com.worksoc.goaicoach.platform.AppMonetization
import com.worksoc.goaicoach.ui.foundation.findActivity

@Composable
internal actual fun rememberAppMonetization(): AppMonetization {
    // ⚠️ 창(Dialog · AlertDialog) 안의 LocalContext는 액티비티가 아니다 — 그대로 넘기면 ☰ 대국 메뉴에서 연 업셀의 가격이
    // 늘 「결제 화면에서」였다(`as? Activity`가 null). 액티비티로 풀어 넘긴다(QA-19b, 2026-11-23).
    val context = LocalContext.current
    return remember(context) { AndroidAppMonetization(context.findActivity() ?: context) }
}
