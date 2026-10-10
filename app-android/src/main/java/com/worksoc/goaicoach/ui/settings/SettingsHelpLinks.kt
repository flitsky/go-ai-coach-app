package com.worksoc.goaicoach.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import com.worksoc.goaicoach.ui.account.FirstDolGuideReplayDialog
import com.worksoc.goaicoach.ui.account.LocalOnlyDataNoticeDialog
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import com.worksoc.goaicoach.ui.setup.DownloadGuideDialog

/**
 * 설정 하단의 **도움말 링크들**(백로그 #241, #245) — 「바둑판 조작법」, 「가이드 다시보기」와 「내 기록은 어떻게 보관되나요」.
 *
 * ⚠️ 상태를 스스로 든다 — `SettingsScreen.kt`는 상태 훅 여유가 0이다(`AdPrivacyOptionsRow`와 같은 이유).
 *   모든 창은 별도 윈도우(`Dialog`·`AlertDialog`)라 설정 화면의 뒤로가기에 지지 않는다.
 */
@Composable
internal fun SettingsHelpLinks() {
    val strings = LocalUiStrings.current
    var showDownloadGuide by remember { mutableStateOf(false) }
    var showGuideReplay by remember { mutableStateOf(false) }
    var showLocalOnlyDataNotice by remember { mutableStateOf(false) }

    SettingsFooterLink(
        text = UiStringsDownloadGuide.boardControlsGuide(strings.language),
        onClick = { showDownloadGuide = true },
    )
    Spacer(modifier = Modifier.height(AppSpacing.Space8))
    SettingsFooterLink(
        text = strings.guideReplayAction,
        onClick = { showGuideReplay = true },
    )
    Spacer(modifier = Modifier.height(AppSpacing.Space8))
    SettingsFooterLink(
        text = strings.localOnlyDataNoticeTitle,
        onClick = { showLocalOnlyDataNotice = true },
    )
    Spacer(modifier = Modifier.height(AppSpacing.Space8))

    if (showDownloadGuide) DownloadGuideDialog(onClose = { showDownloadGuide = false })
    if (showGuideReplay) FirstDolGuideReplayDialog(onClose = { showGuideReplay = false })
    if (showLocalOnlyDataNotice) LocalOnlyDataNoticeDialog(onClose = { showLocalOnlyDataNotice = false })
}

/** 설정 하단의 밑줄 링크 — 오픈소스 라이선스·개인정보처리방침 줄과 같은 모습이다. */
@Composable
private fun SettingsFooterLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = AppTextSize.Text12,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
