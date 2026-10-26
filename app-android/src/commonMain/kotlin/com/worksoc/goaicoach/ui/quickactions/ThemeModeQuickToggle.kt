package com.worksoc.goaicoach.ui.quickactions

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.worksoc.goaicoach.platform.AppNotice
import com.worksoc.goaicoach.platform.NoticeHandle
import com.worksoc.goaicoach.shared.policy.ThemeMode
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.themeModeLabelFor
import com.worksoc.goaicoach.ui.l10n.themeModeQuickToggleNoticeFor
import com.worksoc.goaicoach.ui.l10n.themeModeTitleFor

/**
 * 화면 모드 — 아이콘 하나가 **지금 고른 모드**를 보이고, 누를 때마다 다음 모드로 간다(백로그 #275, 사용자 2026-10-21:
 * *"해 > 달 > 컴퓨터모양? > 시계"*). 차례는 [ThemeMode.nextOnQuickToggle] 한 곳이다.
 *
 * 설정 › 「화면 모드」와 **같은 값**이다(`ThemeModeStore`) — 바꾸면 앱의 맨 바깥 테마(`ui/settings/AppTheme.kt`)가 그 흐름을 듣고 다시
 * 그린다. 이 버튼은 저장만 한다(배경음악 버튼과 같은 선). 값은 흐름으로 읽는다 — 설정에서 바꾸고 돌아와도 옛 모양으로 남지 않게.
 *
 * 누르면 짧은 안내(토스트)로 새 모드의 이름을 알린다 — 폰 · 시계 아이콘만으로는 무엇인지 처음에 알기 어렵다. 밝은지 어두운지는 화면 색이
 * 이미 보이므로 아이콘에 따로 얹지 않는다.
 *
 * ⚠️ **빠르게 여러 번 눌러도 마지막 모드 하나만 알린다**(폰 확인 2026-10-21 — 토스트가 줄을 서서 하나씩 차례로 떴다). 앞의 안내를 내리고
 * 새로 띄운다([LatestNotice]). 다음 모드도 화면에 보이는 값이 아니라 **저장소의 지금 값**에서 고른다 — 흐름이 다시 그리기 전에 또 누르면
 * 화면의 값은 아직 앞의 것이라 같은 모드를 두 번 고른다(저장은 메모리에 곧바로 반영된다).
 */
@Composable
internal fun ThemeModeQuickToggle(tint: Color, modifier: Modifier = Modifier) {
    val platform = LocalAppPlatform.current
    val language = LocalUiStrings.current.language
    val store = remember(platform) { platform.stores.themeMode }
    val mode by remember(store) { store.modeChanges() }.collectAsState(initial = remember(store) { store.load() })
    val notice = remember(platform) { LatestNotice(platform.notice) }
    IconButton(
        onClick = {
            val next = store.load().nextOnQuickToggle
            store.save(next)
            notice.show(themeModeQuickToggleNoticeFor(language, next))
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = mode.quickToggleIcon,
            contentDescription = "${themeModeTitleFor(language)}: ${themeModeLabelFor(language, mode)}",
            tint = tint,
        )
    }
}

/** 마지막 안내 하나만 남기는 토스트 — 새로 띄우기 전에 앞의 것을 내린다(시스템은 앱의 토스트를 차례로 줄 세운다). */
private class LatestNotice(private val notice: AppNotice) {
    private var shown: NoticeHandle? = null

    fun show(text: String) {
        shown?.cancel()
        shown = notice.show(text)
    }
}

/** 해(밝게) · 달(어둡게) · 폰(시스템 따라가기 — 이 폰의 설정이라는 뜻) · 시계(밤 시간 자동). */
private val ThemeMode.quickToggleIcon: ImageVector
    get() = when (this) {
        ThemeMode.Light -> SunIcon
        ThemeMode.Dark -> MoonIcon
        ThemeMode.System -> PhoneIcon
        ThemeMode.NightHours -> ClockIcon
    }

/*
 * 네 모양의 경로는 Material Icons(Apache 2.0 — 코어 세트와 같은 출처)의 `LightMode` · `DarkMode` · `Smartphone` · `Schedule`을 옮겨 그린 것이다.
 * 확장 세트를 의존하지 않는 이유와 빌더는 [AppQuickActions]의 음표와 같다. 이모지가 아닌 이유도 같다(#181).
 */
private val SunIcon: ImageVector = materialIcon(name = "QuickActions.ThemeLight") {
    materialPath {
        moveTo(12.0f, 7.0f)
        curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
        reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
        reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
        reflectiveCurveTo(14.76f, 7.0f, 12.0f, 7.0f)
        lineTo(12.0f, 7.0f)
        close()
        moveTo(2.0f, 13.0f)
        lineToRelative(2.0f, 0.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        lineToRelative(-2.0f, 0.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveTo(1.45f, 13.0f, 2.0f, 13.0f)
        close()
        moveTo(20.0f, 13.0f)
        lineToRelative(2.0f, 0.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        lineToRelative(-2.0f, 0.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveTo(19.45f, 13.0f, 20.0f, 13.0f)
        close()
        moveTo(11.0f, 2.0f)
        verticalLineToRelative(2.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineTo(2.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveTo(11.0f, 1.45f, 11.0f, 2.0f)
        close()
        moveTo(11.0f, 20.0f)
        verticalLineToRelative(2.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(-2.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        curveTo(11.45f, 19.0f, 11.0f, 19.45f, 11.0f, 20.0f)
        close()
        moveTo(5.99f, 4.58f)
        curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
        lineToRelative(1.06f, 1.06f)
        curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0.0f)
        reflectiveCurveToRelative(0.39f, -1.03f, 0.0f, -1.41f)
        lineTo(5.99f, 4.58f)
        close()
        moveTo(18.36f, 16.95f)
        curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
        lineToRelative(1.06f, 1.06f)
        curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0.0f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
        lineTo(18.36f, 16.95f)
        close()
        moveTo(19.42f, 5.99f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
        curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
        lineToRelative(-1.06f, 1.06f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
        reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0.0f)
        lineTo(19.42f, 5.99f)
        close()
        moveTo(7.05f, 18.36f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
        curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
        lineToRelative(-1.06f, 1.06f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
        reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0.0f)
        lineTo(7.05f, 18.36f)
        close()
    }
}

private val MoonIcon: ImageVector = materialIcon(name = "QuickActions.ThemeDark") {
    materialPath {
        moveTo(12.0f, 3.0f)
        curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
        reflectiveCurveToRelative(4.03f, 9.0f, 9.0f, 9.0f)
        reflectiveCurveToRelative(9.0f, -4.03f, 9.0f, -9.0f)
        curveToRelative(0.0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
        curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
        curveToRelative(-2.98f, 0.0f, -5.4f, -2.42f, -5.4f, -5.4f)
        curveToRelative(0.0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
        curveTo(12.92f, 3.04f, 12.46f, 3.0f, 12.0f, 3.0f)
        lineTo(12.0f, 3.0f)
        close()
    }
}

private val PhoneIcon: ImageVector = materialIcon(name = "QuickActions.ThemeSystem") {
    materialPath {
        moveTo(17.0f, 1.01f)
        lineTo(7.0f, 1.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(18.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(10.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(3.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -1.99f, -2.0f, -1.99f)
        close()
        moveTo(17.0f, 19.0f)
        horizontalLineTo(7.0f)
        verticalLineTo(5.0f)
        horizontalLineToRelative(10.0f)
        verticalLineToRelative(14.0f)
        close()
    }
}

private val ClockIcon: ImageVector = materialIcon(name = "QuickActions.ThemeNightHours") {
    materialPath {
        moveTo(11.99f, 2.0f)
        curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
        curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
        reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
        reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
        reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
        reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
        close()
        moveTo(12.5f, 7.0f)
        horizontalLineTo(11.0f)
        verticalLineToRelative(6.0f)
        lineToRelative(5.25f, 3.15f)
        lineToRelative(0.75f, -1.23f)
        lineToRelative(-4.5f, -2.67f)
        close()
    }
}
