package com.worksoc.goaicoach.ui.setup

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.engine.currentEngineDownloadTracker
import com.worksoc.goaicoach.platform.AppUpdateStatus
import com.worksoc.goaicoach.platform.openStoreListing
import com.worksoc.goaicoach.platform.processAppUpdateCheck
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import com.worksoc.goaicoach.ui.l10n.appUpdateAvailableLabelFor
import com.worksoc.goaicoach.ui.l10n.appUpdateShortActionLabelFor

/** 홈 칩 줄 아래 한 줄 슬롯에 무엇을 그릴지(#248 → #255). */
internal enum class HomeTopNoticeKind { None, EngineDownload, AppUpdate }

/**
 * 홈의 한 줄 슬롯 — 엔진 받기 안내와 앱 업데이트 알림이 한 자리를 나눠 쓴다. **둘의 순서는 여기서만 정한다**(#255).
 *
 * 규칙(사용자 2026-10-13): 엔진이 없고 새 버전이 있으면 **업데이트가 먼저**, 사용자가 「이대로 받기」를 고르면 **받기가 먼저**.
 * 앞의 경우는 엔진 상태가 이미 [EngineDownloadStatus.UpdateFirst]라 받기 줄이 곧 업데이트 권유다 — 그래서 받기 상태가
 * 움직이는 동안(받는 중 · Wi-Fi 대기 · 실패 · 업데이트 먼저)은 받기 줄을, 받기가 없거나 끝나면 업데이트 알림을 그린다.
 * 업데이트 알림은 **새 버전이 있을 때만** — 「최신입니다」·「스토어에서 확인」은 홈에서는 소음이라 설정에만 둔다.
 */
internal fun homeTopNoticeOf(
    engine: EngineDownloadStatus,
    update: AppUpdateStatus,
    updateDismissed: Boolean,
): HomeTopNoticeKind = when {
    engine != EngineDownloadStatus.Idle && engine != EngineDownloadStatus.Completed -> HomeTopNoticeKind.EngineDownload
    update == AppUpdateStatus.Available && !updateDismissed -> HomeTopNoticeKind.AppUpdate
    else -> HomeTopNoticeKind.None
}

/**
 * 홈 화면의 한 줄 안내(#248 엔진 받기 · #255 앱 업데이트). 셸이 홈의 `topNotice` 슬롯으로 넘긴다.
 * 엔진 받기는 앱을 켜는 순간 시작되므로(`createEngineBootstrap`) 첫 화면에서 알리고, 받기가 없거나 끝나면 업데이트 알림 자리다.
 * 그릴 것이 없으면 아무것도 그리지 않는다 — 엔진이 있고 최신인 기기에는 노출 0초.
 */
@Composable
internal fun HomeTopNotice(modifier: Modifier = Modifier) {
    RegisterCellularConfirmationHandler()
    val context = LocalContext.current
    val updateCheck = remember(context) { processAppUpdateCheck(context) }
    LaunchedEffect(updateCheck) { updateCheck.ensureChecked() }
    val engine by currentEngineDownloadTracker().status.collectAsState()
    val update by updateCheck.status.collectAsState()
    val dismissed by updateCheck.noticeDismissed.collectAsState()
    when (homeTopNoticeOf(engine, update, dismissed)) {
        HomeTopNoticeKind.EngineDownload -> EngineDownloadStatusBanner(status = engine, modifier = modifier)
        HomeTopNoticeKind.AppUpdate -> AppUpdateHomeBanner(
            onUpdate = { openStoreListing(context) },
            onDismiss = updateCheck::dismissNotice,
            modifier = modifier,
        )
        HomeTopNoticeKind.None -> Unit
    }
}

/** 새 버전 한 줄 — 받기 배너와 같은 모양에 ✕를 더했다. 줄을 누르면 스토어로, ✕는 이번 실행 동안만 닫는다. */
@Composable
private fun AppUpdateHomeBanner(
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiStrings.current.language
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner8))
            .clickable(onClick = onUpdate)
            .border(
                width = AppBorderWidth.Hairline,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(AppRadius.Corner8),
            ),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(start = AppSpacing.Space12, top = AppSpacing.Space4, bottom = AppSpacing.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = appUpdateAvailableLabelFor(language),
                fontSize = AppTextSize.Text12,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(AppSpacing.Space8))
            Text(
                text = appUpdateShortActionLabelFor(language),
                fontSize = AppTextSize.Text12,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = UiStringsDownloadGuide.close(language),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
