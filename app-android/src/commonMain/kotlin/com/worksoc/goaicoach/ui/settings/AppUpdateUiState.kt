package com.worksoc.goaicoach.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.worksoc.goaicoach.platform.AppUpdateStatus
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.foundation.rememberStoreListingLauncher
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.appUpToDateLabelFor
import com.worksoc.goaicoach.ui.l10n.appUpdateActionLabelFor
import com.worksoc.goaicoach.ui.l10n.appUpdateAvailableLabelFor
import com.worksoc.goaicoach.ui.l10n.appUpdateCheckStoreLabelFor

/**
 * 설정 화면이 열릴 때 프로세스의 답을 읽는다(#53 → #255). 묻는 일과 판정은 `platform/PlayAppUpdate.kt` 한 곳이다 —
 * 홈 알림·엔진 받기 전의 확인과 같은 답을 쓴다(프로세스마다 한 번 묻는다).
 *
 * ⚠️ **화면을 막지 않는다**(사용자 지시). `LaunchedEffect`로 비동기로 묻고 답이 오면 그때 줄이 나타난다.
 * 실패는 조용히 [AppUpdateStatus.Unknown]이 된다 — 업데이트 확인 실패로 설정 화면이 경고를 띄우면 그건 소음이다.
 */
@Composable
internal fun rememberAppUpdateStatus(): AppUpdateStatus {
    val platform = LocalAppPlatform.current
    val check = remember(platform) { platform.appUpdate }
    val status by check.status.collectAsState()
    LaunchedEffect(check) { check.ensureChecked() }
    return status
}

/**
 * 버전 정보 카드 아래에 붙는 업데이트 줄(백로그 #53).
 *
 * ⚠️ **버전 텍스트와 붙여 놓지 말 것.** 그 텍스트에는 **10번 두드리면 개발자 모드가 켜지는**
 * 숨은 제스처가 있다(`SettingsScreen.kt`) — 업데이트 버튼을 그 옆이나 위에 붙이면 오탭 한 번이
 * 개발자 모드를 연다. 그래서 개인정보처리방침 링크를 사이에 두고 **맨 아래 별도 행**으로 둔다.
 *
 * 조회 중에는 **아무것도 그리지 않는다** — 설정에 들어올 때마다 줄이 나타났다 바뀌면 소음이다.
 */
@Composable
internal fun AppUpdateRow(status: AppUpdateStatus, modifier: Modifier = Modifier) {
    if (status == AppUpdateStatus.Checking) return
    val openStoreListing = rememberStoreListingLauncher()
    val language = LocalUiStrings.current.language
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(AppSpacing.Space10))
        when (status) {
            AppUpdateStatus.Available -> {
                Text(
                    text = appUpdateAvailableLabelFor(language),
                    fontSize = AppTextSize.Text12,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(AppSpacing.Space6))
                // 새 버전이 있을 때만 버튼을 쓴다 — 나머지 상태는 알림이지 할 일이 아니다.
                Button(onClick = { openStoreListing() }) {
                    Text(appUpdateActionLabelFor(language), fontSize = AppTextSize.Text13)
                }
            }

            AppUpdateStatus.UpToDate -> Text(
                text = appUpToDateLabelFor(language),
                fontSize = AppTextSize.Text12,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            // 확인하지 못한 경우의 폴백(선택지 ⓒ). 개인정보처리방침 링크와 같은 모양이라
            // "누를 수 있는 것"임이 이 화면 안에서 이미 학습돼 있다.
            AppUpdateStatus.Unknown -> Text(
                text = appUpdateCheckStoreLabelFor(language),
                fontSize = AppTextSize.Text12,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { openStoreListing() },
            )

            AppUpdateStatus.Checking -> Unit // 위에서 이미 걸렀다.
        }
    }
}
