package com.worksoc.goaicoach.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import com.worksoc.goaicoach.ui.foundation.findActivity

@Composable
internal actual fun ApplySystemBarsAppearance(dark: Boolean, windowBackground: Color) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            // 창 바탕도 같은 색으로 — 컴포즈가 아직 덮지 않은 순간(키보드로 창이 줄 때 등)에 반대색이 비치지 않게.
            window.setBackgroundDrawable(windowBackground.toArgb().toDrawable())
        }
    }
}
