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

/**
 * 설정 하단의 **도움말 링크 둘**(백로그 #241, 2026-10-09 사용자) — 「가이드 다시보기」와 「내 기록은 어떻게 보관되나요」.
 * 둘 다 마이 페이지 맨 아래에 있던 것이다. 마이 페이지는 *"내가 모은 것"* 을 보는 자리이고, 도움말과 데이터 안내를
 * 찾는 곳은 설정이다(바로 아래의 오픈소스 라이선스·개인정보처리방침과 같은 줄에 선다).
 *
 * ⚠️ **가이드 다시보기의 진입점은 여기 하나다** — 2026-09의 *"마이페이지에만, 설정에는 넣지 않는다"*(ⓑ)를 사용자가 뒤집었다.
 * ⚠️ 상태를 스스로 든다 — `SettingsScreen.kt`는 상태 훅 여유가 0이다(`AdPrivacyOptionsRow`와 같은 이유).
 *   두 창 모두 별도 윈도우(`Dialog`·`AlertDialog`)라 설정 화면의 뒤로가기에 지지 않는다.
 */
@Composable
internal fun SettingsHelpLinks() {
    val strings = LocalUiStrings.current
    var showGuideReplay by remember { mutableStateOf(false) }
    var showLocalOnlyDataNotice by remember { mutableStateOf(false) }

    SettingsFooterLink(text = strings.guideReplayAction, onClick = { showGuideReplay = true })
    Spacer(modifier = Modifier.height(AppSpacing.Space8))
    SettingsFooterLink(text = strings.localOnlyDataNoticeTitle, onClick = { showLocalOnlyDataNotice = true })
    Spacer(modifier = Modifier.height(AppSpacing.Space8))

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
