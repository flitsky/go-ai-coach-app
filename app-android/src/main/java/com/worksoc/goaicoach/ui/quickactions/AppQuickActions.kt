package com.worksoc.goaicoach.ui.quickactions

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.persistence.BackgroundMusicStore
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.backgroundMusicLabelFor

/**
 * 「빠른 버튼」 묶음 — 화면마다 **맨 윗줄 오른쪽 끝**에 놓는 같은 버튼들(백로그 #270, 사용자 2026-10-19:
 * *"대국중이던 어느 화면이던 글로벌 기능을 빠르게 켜고 끄는게 주 목적"*).
 *
 * ## 새 줄을 만들지 않는다
 * 상태바 아래에 띠를 따로 두면 모든 화면이 세로를 내준다. 대국 화면은 판 아닌 부분이 이미 426dp(`PhoneLayoutNonBoardHeightDp`)라
 * 폭 360dp급 폰에서는 띠 높이만큼 **판이 작아진다** — 보조 기능이 대국을 깎지 않는다. 떠 있는 버튼도 물렸다 — 오른쪽 위는
 * 화면마다 이미 주인(대국 ☰ · 홈 ⚙)이 있다. 그래서 화면마다 **이미 있는 윗줄**에 붙인다(사용자 확정 2026-10-19).
 *
 * ## 화면은 이것을 놓기만 한다
 * 묶음에 버튼을 더하면 모든 화면에 함께 생긴다. 지금은 배경음악 켬/끔 하나다 — 「어디서든 켜고 끌」 전역 기능이 그것뿐이고,
 * 빈 자리는 두지 않는다(빈 버튼은 고장처럼 보인다).
 * 놓인 곳: 홈 · 대국 준비 · 대국(폰·넓은 배치) · 대국 기록 · 다시보기 · 기보 보기(목록·판) · 학습(허브·영상·단원) · 사진 분석(촬영) · 내 정보.
 * 설정 화면에는 놓지 않는다 — 같은 값의 스위치(「소리」 절)가 있다.
 *
 * @param tint 화면의 머리말 색. 사진 분석의 촬영 화면처럼 어두운 바탕이면 그 화면의 글자색을 넘긴다.
 * @param musicToggleModifier 배경음악 버튼에만 거는 것 — 홈이 업데이트 안내(코치마크)의 대상 자리를 알리는 데 쓴다.
 */
@Composable
internal fun AppQuickActions(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    musicToggleModifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BackgroundMusicQuickToggle(tint = tint, modifier = musicToggleModifier)
    }
}

/**
 * 배경음악 켬/끔 — 설정 › 소리의 「앱 배경음악」과 **같은 값**이다(`BackgroundMusicStore`). 트는 일은 앱 전체의 플레이어가
 * 저장소의 바뀜을 듣고 한다(`BackgroundMusicCoordinator`) — 이 버튼은 저장만 한다(설정 패널과 같은 선).
 * 값은 **흐름으로** 읽는다 — 설정에서 바꾸고 돌아왔을 때 옛 모양으로 남지 않게.
 */
@Composable
private fun BackgroundMusicQuickToggle(tint: Color, modifier: Modifier) {
    val context = LocalContext.current
    val store = remember(context) { BackgroundMusicStore(context) }
    val isEnabled by remember(store) { store.enabledChanges() }.collectAsState(initial = store.isEnabled())
    IconToggleButton(
        checked = isEnabled,
        onCheckedChange = { checked -> store.setEnabled(checked) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isEnabled) MusicNoteIcon else MusicOffIcon,
            contentDescription = backgroundMusicLabelFor(LocalUiStrings.current.language),
            tint = if (isEnabled) tint else tint.copy(alpha = OffAlpha),
        )
    }
}

/** 끈 모양은 사선만으로도 읽히지만, 흐리게 한 번 더 — 켬/끔을 모양과 농도 둘로 알린다(색만으로 가르지 않는다). */
private const val OffAlpha = 0.55f

/**
 * 음표 · 사선 그은 음표. ⚠️ 이 앱은 아이콘 **코어 세트만** 의존해(확장 세트는 APK를 크게 불린다 — 홈 `GameHistoryBoardIcon`의 KDoc)
 * 확장 세트의 `MusicNote`·`MusicOff`를 쓸 수 없다 — 그 두 모양의 경로(Material Icons, Apache 2.0 — 코어 세트와 같은 출처)를
 * 확장 세트가 쓰는 **같은 빌더**(`materialIcon` · `materialPath`)로 옮겨 그린다. 채움색은 빌더가 정하고 `Icon`의 `tint`가 덮는다.
 * 이모지가 아닌 이유는 홈 카드 아이콘과 같다(#181: 기기·글꼴마다 크기·모양이 갈린다).
 */
private val MusicNoteIcon: ImageVector = materialIcon(name = "QuickActions.MusicNote") {
    materialPath {
        moveTo(12.0f, 3.0f)
        verticalLineToRelative(10.55f)
        curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2.0f, -0.55f)
        curveToRelative(-2.21f, 0.0f, -4.0f, 1.79f, -4.0f, 4.0f)
        reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
        reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
        verticalLineTo(7.0f)
        horizontalLineToRelative(4.0f)
        verticalLineTo(3.0f)
        horizontalLineToRelative(-6.0f)
        close()
    }
}

private val MusicOffIcon: ImageVector = materialIcon(name = "QuickActions.MusicOff") {
    materialPath {
        moveTo(4.27f, 3.0f)
        lineTo(3.0f, 4.27f)
        lineToRelative(9.0f, 9.0f)
        verticalLineToRelative(0.28f)
        curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2.0f, -0.55f)
        curveToRelative(-2.21f, 0.0f, -4.0f, 1.79f, -4.0f, 4.0f)
        reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
        reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
        verticalLineToRelative(-1.73f)
        lineTo(19.73f, 21.0f)
        lineTo(21.0f, 19.73f)
        lineTo(4.27f, 3.0f)
        close()
        moveTo(14.0f, 7.0f)
        horizontalLineToRelative(4.0f)
        verticalLineTo(3.0f)
        horizontalLineToRelative(-6.0f)
        verticalLineToRelative(5.18f)
        lineToRelative(2.0f, 2.0f)
        close()
    }
}
