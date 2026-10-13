package com.worksoc.goaicoach.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.ui.account.FirstDolGuideReplayDialog
import com.worksoc.goaicoach.ui.account.LocalOnlyDataNoticeDialog
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import com.worksoc.goaicoach.ui.setup.BoardControlsGuideDialog

/**
 * 설정의 **도움말 절**(백로그 #241, #245 → #256) — 「바둑판 조작법」, 「가이드 다시보기」와 「내 기록은 어떻게 보관되나요」.
 *
 * #256(사용자 2026-10-13 「A 정리형」)에서 앱 정보 카드 안의 **밑줄 링크**에서 **줄 목록**(›)으로 바꿨다 — 법적 고지 둘과 섞여
 * 다섯 줄의 밑줄이 한 덩어리로 읽혔고, 누를 표적도 글자 폭만큼 좁았다. 자리는 여전히 설정의 아래쪽이다(2026-10-09 결정).
 *
 * ⚠️ 상태를 스스로 든다 — `SettingsScreen.kt`의 상태는 원장으로 묶여 있다(`AdPrivacyOptionsRow`와 같은 이유).
 *   모든 창은 별도 윈도우(`Dialog`·`AlertDialog`)라 설정 화면의 뒤로가기에 지지 않는다.
 */
@Composable
internal fun SettingsHelpLinks() {
    val strings = LocalUiStrings.current
    var showDownloadGuide by remember { mutableStateOf(false) }
    var showGuideReplay by remember { mutableStateOf(false) }
    var showLocalOnlyDataNotice by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner12)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column {
            SettingsHelpRow(
                text = UiStringsDownloadGuide.boardControlsGuide(strings.language),
                onClick = { showDownloadGuide = true },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.Space16))
            SettingsHelpRow(
                text = strings.guideReplayAction,
                onClick = { showGuideReplay = true },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.Space16))
            SettingsHelpRow(
                text = strings.localOnlyDataNoticeTitle,
                onClick = { showLocalOnlyDataNotice = true },
            )
        }
    }

    if (showDownloadGuide) BoardControlsGuideDialog(onClose = { showDownloadGuide = false })
    if (showGuideReplay) FirstDolGuideReplayDialog(onClose = { showGuideReplay = false })
    if (showLocalOnlyDataNotice) LocalOnlyDataNoticeDialog(onClose = { showLocalOnlyDataNotice = false })
}

/** 도움말 한 줄 — 줄 전체가 눌린다(글자만 눌리면 표적이 좁다). */
@Composable
private fun SettingsHelpRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = AppTextSize.Text14,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
