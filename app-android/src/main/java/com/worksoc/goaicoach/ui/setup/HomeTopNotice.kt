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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.application.guide.whatsNewPending
import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.engine.currentEngineDownloadTracker
import com.worksoc.goaicoach.persistence.WhatsNewStore
import com.worksoc.goaicoach.platform.AppUpdateStatus
import com.worksoc.goaicoach.platform.openStoreListing
import com.worksoc.goaicoach.platform.processAppUpdateCheck
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import com.worksoc.goaicoach.ui.l10n.WhatsNewCatalog
import com.worksoc.goaicoach.ui.l10n.WhatsNewRelease
import com.worksoc.goaicoach.ui.l10n.appUpdateAvailableLabelFor
import com.worksoc.goaicoach.ui.l10n.appUpdateShortActionLabelFor
import com.worksoc.goaicoach.ui.l10n.whatsNewHomeLineFor
import com.worksoc.goaicoach.ui.l10n.whatsNewOpenFor
import java.io.File

/** 홈 칩 줄 아래 한 줄 슬롯에 무엇을 그릴지(#248 → #255 → #257). */
internal enum class HomeTopNoticeKind { None, EngineDownload, WhatsNew, AppUpdate }

/**
 * 홈의 한 줄 슬롯 — 엔진 받기 안내와 앱 업데이트 알림이 한 자리를 나눠 쓴다. **둘의 순서는 여기서만 정한다**(#255).
 *
 * 규칙(사용자 2026-10-13): 엔진이 없고 새 버전이 있으면 **업데이트가 먼저**, 사용자가 「이대로 받기」를 고르면 **받기가 먼저**.
 * 앞의 경우는 엔진 상태가 이미 [EngineDownloadStatus.UpdateFirst]라 받기 줄이 곧 업데이트 권유다 — 그래서 받기 상태가
 * 움직이는 동안(받는 중 · Wi-Fi 대기 · 실패 · 업데이트 먼저)은 받기 줄을, 받기가 없거나 끝나면 업데이트 알림을 그린다.
 * 업데이트 알림은 **새 버전이 있을 때만** — 「최신입니다」·「스토어에서 확인」은 홈에서는 소음이라 설정에만 둔다.
 *
 * **「새로워진 점」(#257)은 받기 다음, 업데이트 알림 앞이다** — 스레드가 정한 차례(2026-10-13): 한 번 보면 끝나는 것이 먼저 서고,
 * 콜드 스타트마다 다시 뜨는 업데이트 알림은 그 뒤에 자기 차례를 얻는다.
 */
internal fun homeTopNoticeOf(
    engine: EngineDownloadStatus,
    update: AppUpdateStatus,
    updateDismissed: Boolean,
    whatsNewPending: Boolean = false,
): HomeTopNoticeKind = when {
    engine != EngineDownloadStatus.Idle && engine != EngineDownloadStatus.Completed -> HomeTopNoticeKind.EngineDownload
    whatsNewPending -> HomeTopNoticeKind.WhatsNew
    update == AppUpdateStatus.Available && !updateDismissed -> HomeTopNoticeKind.AppUpdate
    else -> HomeTopNoticeKind.None
}

/**
 * 아직 보지 않은 「새로워진 점」 묶음(#257) — 판정은 `whatsNewPending`(shared) 한 곳이다. 새로 설치한 사람은 지금 버전을 본 것으로 적어 둔다
 * (다음 업데이트부터 그 사이의 묶음이 뜬다). 디버그 빌드는 `files/debug-whats-new-preview`가 있으면 한 번(지우고) 가장 새 묶음을 띄운다 —
 * 개발 빌드의 versionCode는 아직 그 묶음보다 낮아 에뮬레이터로 볼 길이 이것뿐이다.
 */
internal fun initialWhatsNewPending(store: WhatsNewStore, filesDir: File): List<WhatsNewRelease> {
    if (BuildConfig.DEBUG) {
        val arm = File(filesDir, DebugWhatsNewPreviewFile)
        if (arm.isFile && arm.delete()) return WhatsNewCatalog.releases.sortedByDescending { it.versionCode }.take(1)
    }
    val lastSeen = store.lastSeenVersionCode()
    val fresh = lastSeen == null && store.isFreshInstall()
    if (fresh) store.markSeen(BuildConfig.VERSION_CODE)
    return whatsNewPending(
        available = WhatsNewCatalog.releases.map { it.versionCode },
        lastSeenVersionCode = lastSeen,
        currentVersionCode = BuildConfig.VERSION_CODE,
        isFreshInstall = fresh,
    ).mapNotNull(WhatsNewCatalog::release)
}

private const val DebugWhatsNewPreviewFile = "debug-whats-new-preview"

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
    // 「새로워진 점」(#257) — 열어 보거나 ✕로 닫으면 지금 버전을 본 것으로 적는다(그 버전에서는 다시 안 뜬다, 사용자 2026-10-13).
    val whatsNewStore = remember(context) { WhatsNewStore(context) }
    var whatsNew by remember(whatsNewStore) { mutableStateOf(initialWhatsNewPending(whatsNewStore, context.filesDir)) }
    var showWhatsNew by remember { mutableStateOf<List<WhatsNewRelease>>(emptyList()) }
    fun markWhatsNewSeen() {
        whatsNewStore.markSeen(BuildConfig.VERSION_CODE)
        whatsNew = emptyList()
    }
    if (showWhatsNew.isNotEmpty()) WhatsNewDialog(releases = showWhatsNew, onClose = { showWhatsNew = emptyList() })
    val language = LocalUiStrings.current.language
    when (homeTopNoticeOf(engine, update, dismissed, whatsNewPending = whatsNew.isNotEmpty())) {
        HomeTopNoticeKind.EngineDownload -> EngineDownloadStatusBanner(status = engine, modifier = modifier)
        HomeTopNoticeKind.WhatsNew -> HomeLineBanner(
            message = whatsNewHomeLineFor(language, whatsNew.first().versionName),
            action = whatsNewOpenFor(language),
            onClick = {
                showWhatsNew = whatsNew
                markWhatsNewSeen()
            },
            onDismiss = ::markWhatsNewSeen,
            modifier = modifier,
        )
        HomeTopNoticeKind.AppUpdate -> HomeLineBanner(
            message = appUpdateAvailableLabelFor(language),
            action = appUpdateShortActionLabelFor(language),
            onClick = { openStoreListing(context) },
            onDismiss = updateCheck::dismissNotice,
            modifier = modifier,
        )
        HomeTopNoticeKind.None -> Unit
    }
}

/**
 * 홈 한 줄(✕ 있음) — 새 버전(#255: 누르면 스토어, ✕는 이번 실행 동안만)과 새로워진 점(#257: 누르면 창, ✕도 본 것으로)이 같은 모양을 쓴다.
 * 받기 배너와 같은 모양에 ✕를 더했다.
 */
@Composable
private fun HomeLineBanner(
    message: String,
    action: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiStrings.current.language
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner8))
            .clickable(onClick = onClick)
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
                text = message,
                fontSize = AppTextSize.Text12,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(AppSpacing.Space8))
            Text(
                text = action,
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
