package com.worksoc.goaicoach.ui.setup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.engine.currentEngineDownloadTracker
import com.worksoc.goaicoach.platform.openStoreListing
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import com.worksoc.goaicoach.ui.l10n.appDownloadWithoutUpdateLabelFor
import com.worksoc.goaicoach.ui.l10n.appUpdateBeforeEngineLabelFor
import com.worksoc.goaicoach.ui.l10n.appUpdateShortActionLabelFor

/**
 * 받기 상태 한 줄 배너 — 홈([HomeTopNotice])과, 대국 설정에서 카드를 닫은 뒤의 미니 배너가 함께 쓴다.
 * 받는 중이면 진행률을, Wi-Fi 대기·실패면 그 동작(「모바일 데이터로 계속」·「다시 시도」)을 보이고, 탭하면 바로 실행한다.
 * 업데이트를 먼저 권하는 중(#255)이면 「업데이트」 — 탭하면 스토어로 간다(「이대로 받기」는 두 버튼 카드 [EngineUpdateFirstCard]에만).
 * 그 밖의 상태(Idle·Completed)는 그리지 않는다. 언제 보일지는 호출부가 정한다.
 */
@Composable
internal fun EngineDownloadStatusBanner(
    status: EngineDownloadStatus,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val language = strings.language
    val context = LocalContext.current
    val isFailed = status is EngineDownloadStatus.Failed
    val isConfirmationRequired = status is EngineDownloadStatus.WaitingForWifi && status.isConfirmationRequired
    val accent = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val (message, action) = when (status) {
        is EngineDownloadStatus.Downloading -> Pair(
            UiStringsDownloadGuide.downloadingNotice(language),
            "${status.percentage}%",
        )
        is EngineDownloadStatus.WaitingForWifi -> Pair(
            if (isConfirmationRequired) UiStringsDownloadGuide.requiresConfirmation(language) else UiStringsDownloadGuide.waitingForWifi(language),
            if (isConfirmationRequired) UiStringsDownloadGuide.openConfirmation(language) else UiStringsDownloadGuide.continueOnMobileData(language),
        )
        is EngineDownloadStatus.Failed -> Pair(
            UiStringsDownloadGuide.failed(language),
            UiStringsDownloadGuide.retry(language),
        )
        EngineDownloadStatus.UpdateFirst -> Pair(
            appUpdateBeforeEngineLabelFor(language),
            appUpdateShortActionLabelFor(language),
        )
        EngineDownloadStatus.Idle, EngineDownloadStatus.Completed -> return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner8))
            .clickable(enabled = status !is EngineDownloadStatus.Downloading) {
                // 배너의 동작 이름(「다시 시도」·「모바일 데이터로 계속」)대로 바로 실행한다 — 카드만 열면 같은 버튼을 두 번 누르게 된다.
                val tracker = currentEngineDownloadTracker()
                when (status) {
                    is EngineDownloadStatus.Failed -> tracker.retry()
                    is EngineDownloadStatus.WaitingForWifi -> {
                        tracker.reopen()
                        tracker.requestCellularConfirmation()
                    }
                    EngineDownloadStatus.UpdateFirst -> openStoreListing(context)
                    else -> Unit
                }
            }
            .border(
                width = AppBorderWidth.Hairline,
                color = accent.copy(alpha = 0.5f),
                shape = RoundedCornerShape(AppRadius.Corner8),
            ),
        color = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    ) {
        Column(modifier = Modifier.padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space8)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    fontSize = AppTextSize.Text12,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(AppSpacing.Space8))
                Text(
                    text = action,
                    fontSize = AppTextSize.Text12,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (status is EngineDownloadStatus.Downloading) {
                Spacer(modifier = Modifier.height(AppSpacing.Space6))
                LinearProgressIndicator(
                    progress = { (status.percentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 받기 전에 업데이트를 권하는 카드(#255) — 엔진 준비 화면과 대국 설정의 받기 카드가 함께 쓴다.
 * 「업데이트」가 기본(채운 버튼 · 스토어로), 「이대로 받기」는 작은 글자 버튼이다(사용자 2026-10-13: 둘 다 둔다).
 */
@Composable
internal fun EngineUpdateFirstCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val language = LocalUiStrings.current.language
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner12))
            .border(AppBorderWidth.Hairline, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(AppRadius.Corner12)),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    ) {
        Column(modifier = Modifier.padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space12)) {
            Text(
                text = appUpdateBeforeEngineLabelFor(language),
                fontSize = AppTextSize.Text14,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(AppSpacing.Space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { currentEngineDownloadTracker().skipUpdate() }) {
                    Text(appDownloadWithoutUpdateLabelFor(language), fontSize = AppTextSize.Text13)
                }
                Spacer(modifier = Modifier.width(AppSpacing.Space8))
                Button(onClick = { openStoreListing(context) }, shape = RoundedCornerShape(AppRadius.Corner8)) {
                    Text(appUpdateShortActionLabelFor(language), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 「모바일 데이터로 계속」이 띄울 Play 공식 승인창(`showConfirmationDialog`)을 이 화면이 떠 있는 동안 등록한다.
 * 그 창은 Activity가 있어야 열리므로 트래커가 아니라 화면이 등록한다 — 배너가 있는 화면마다 부른다.
 */
@Composable
internal fun RegisterCellularConfirmationHandler() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        val handler: () -> Unit = {
            if (activity != null) {
                try {
                    val manager = AssetPackManagerFactory.getInstance(activity)
                    val task = manager.showConfirmationDialog(activity)
                    task.addOnSuccessListener { resultCode ->
                        android.util.Log.d("EngineDownloadGuide", "Cellular confirmation result: $resultCode")
                    }.addOnFailureListener { exception ->
                        android.util.Log.w("EngineDownloadGuide", "Cellular confirmation dialog failed", exception)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("EngineDownloadGuide", "Cannot show cellular confirmation dialog", e)
                }
            }
        }
        currentEngineDownloadTracker().registerCellularConfirmationHandler(handler)
        onDispose {
            currentEngineDownloadTracker().unregisterCellularConfirmationHandler(handler)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
