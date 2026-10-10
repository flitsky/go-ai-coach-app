package com.worksoc.goaicoach.ui.setup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.engine.currentEngineDownloadTracker
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.guide.GuideBlockingOverlays
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * PAD on-demand 에셋 팩 다운로드 동안 표시되는 사용 가이드 카드 섹션 (백로그 #245 U-73, #247 U-74/U-75).
 *
 * ## 지키는 조건
 * 1. 팩을 실제로 받는 중이거나 상태가 발생했을 때만 표시 (모델이 이미 있는 기기에는 노출 0초)
 * 2. 실패/Wi-Fi 대기 시 숨지 않고 상태와 [다시 시도] / [모바일 데이터로 계속] 버튼 노출
 * 3. 완료 순간 읽던 카드를 빼앗지 않고 자연스럽게 유지하며, 사용자가 원할 때 닫기(X) 가능
 * 4. 팝업이 아니라 화면 안의 인라인 컴포저블로 배치 (출석/가이드 사슬 방해 금지)
 * 5. GoCoachApp 상태 훅 예산(42/42)을 침범하지 않고 이 컴포저블 내부에서 자체 캡슐화
 */
@Composable
internal fun EngineDownloadGuideSection(
    modifier: Modifier = Modifier,
) {
    val downloadStatus by currentEngineDownloadTracker().status.collectAsState()
    val isDismissed by currentEngineDownloadTracker().isDismissed.collectAsState()
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        if (activity != null) {
            currentEngineDownloadTracker().registerCellularConfirmationHandler {
                try {
                    val manager = AssetPackManagerFactory.getInstance(activity)
                    // 최신 권장 API인 showConfirmationDialog 우선 시도, 환경에 따라 showCellularDataConfirmation으로 안전 폴백
                    val task = try {
                        manager.showConfirmationDialog(activity)
                    } catch (_: NoSuchMethodError) {
                        manager.showCellularDataConfirmation(activity)
                    }
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
        onDispose {
            currentEngineDownloadTracker().registerCellularConfirmationHandler(null)
        }
    }

    // Idle 상태이면 렌더링하지 않는다
    if (downloadStatus is EngineDownloadStatus.Idle) {
        return
    }

    // 카드를 닫은 상태(isDismissed)
    if (isDismissed) {
        val status = downloadStatus
        val isMandatoryBlocked = when (status) {
            is EngineDownloadStatus.Failed -> !status.isHumanModelOnly
            is EngineDownloadStatus.WaitingForWifi -> !status.isHumanModelOnly
            else -> false
        }
        // 필수 주 모델 다운로드 실패 또는 Wi-Fi 대기 중 닫힌 경우, 사용자가 대국을 시작할 수 없는 상태이므로
        // 다시 카드를 열 수 있는 미니 안내 배너를 제공한다 (#247).
        if (isMandatoryBlocked) {
            val isFailed = status is EngineDownloadStatus.Failed
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.Corner8))
                    .clickable { currentEngineDownloadTracker().reopen() }
                    .border(
                        width = AppBorderWidth.Hairline,
                        color = if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(AppRadius.Corner8),
                    ),
                color = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space8),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isFailed) UiStringsDownloadGuide.failed(strings.language) else UiStringsDownloadGuide.waitingForWifi(strings.language),
                        fontSize = AppTextSize.Text12,
                        fontWeight = FontWeight.Medium,
                        color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (isFailed) UiStringsDownloadGuide.retry(strings.language) else UiStringsDownloadGuide.continueOnMobileData(strings.language),
                        fontSize = AppTextSize.Text12,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner12))
            .border(
                width = AppBorderWidth.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(AppRadius.Corner12),
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.Space12),
        ) {
            when (val status = downloadStatus) {
                is EngineDownloadStatus.Downloading -> {
                    val downloadedMb = status.bytesDownloaded / (1024 * 1024)
                    val totalMb = (status.totalBytesToDownload / (1024 * 1024)).coerceAtLeast(1L)
                    val progressFloat = (status.percentage / 100f).coerceIn(0f, 1f)
                    if (status.isHumanModelOnly) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = UiStringsDownloadGuide.preparingHumanModel(strings.language),
                                fontSize = AppTextSize.Text12,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { currentEngineDownloadTracker().dismiss() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = UiStringsDownloadGuide.close(strings.language),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.Space4))

                        Text(
                            text = "$downloadedMb MB / $totalMb MB (${status.percentage}%)",
                            fontSize = AppTextSize.Text12,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$downloadedMb MB / $totalMb MB (${status.percentage}%)",
                                fontSize = AppTextSize.Text12,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = { currentEngineDownloadTracker().dismiss() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = UiStringsDownloadGuide.close(strings.language),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.Space4))

                        // U-74: 다운로드 안내 (바둑 AI 엔진 다운로드 중 (197MB/99MB) · Wi-Fi 권장)
                        Text(
                            text = UiStringsDownloadGuide.downloadingNotice(strings.language),
                            fontSize = AppTextSize.Text12,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.Space6))

                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.Space10))
                }

                is EngineDownloadStatus.WaitingForWifi -> {
                    val downloadedMb = status.bytesDownloaded / (1024 * 1024)
                    val totalMb = (status.totalBytesToDownload / (1024 * 1024)).coerceAtLeast(1L)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = UiStringsDownloadGuide.waitingForWifi(strings.language),
                            fontSize = AppTextSize.Text12,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { currentEngineDownloadTracker().dismiss() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = UiStringsDownloadGuide.close(strings.language),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.Space4))

                    Text(
                        text = "$downloadedMb MB / $totalMb MB",
                        fontSize = AppTextSize.Text12,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.Space8))

                    // U-75 대안 B: 모바일 데이터로 계속 버튼 및 다시 시도 버튼
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
                    ) {
                        Button(
                            onClick = { currentEngineDownloadTracker().requestCellularConfirmation() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = RoundedCornerShape(AppRadius.Corner8),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(36.dp),
                        ) {
                            Text(
                                text = UiStringsDownloadGuide.continueOnMobileData(strings.language),
                                fontSize = AppTextSize.Text12,
                            )
                        }

                        OutlinedButton(
                            onClick = { currentEngineDownloadTracker().retry() },
                            shape = RoundedCornerShape(AppRadius.Corner8),
                            modifier = Modifier
                                .weight(0.8f)
                                .height(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = UiStringsDownloadGuide.retry(strings.language),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.Space4))
                            Text(
                                text = UiStringsDownloadGuide.retry(strings.language),
                                fontSize = AppTextSize.Text12,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.Space10))
                }

                is EngineDownloadStatus.Failed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = UiStringsDownloadGuide.failed(strings.language),
                            fontSize = AppTextSize.Text12,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { currentEngineDownloadTracker().retry() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                shape = RoundedCornerShape(AppRadius.Corner8),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = UiStringsDownloadGuide.retry(strings.language),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.Space4))
                                Text(text = UiStringsDownloadGuide.retry(strings.language), fontSize = AppTextSize.Text12)
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.Space6))
                            IconButton(
                                onClick = { currentEngineDownloadTracker().dismiss() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = UiStringsDownloadGuide.close(strings.language),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.Space10))
                }

                is EngineDownloadStatus.Completed -> {
                    val completedText = if (status.isHumanModelOnly) {
                        UiStringsDownloadGuide.humanModelReady(strings.language)
                    } else {
                        UiStringsDownloadGuide.aiReady(strings.language)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = completedText,
                            fontSize = AppTextSize.Text12,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { currentEngineDownloadTracker().dismiss() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = UiStringsDownloadGuide.close(strings.language),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AppSpacing.Space8))
                }

                else -> {}
            }

            // 가이드 카드 캐러셀
            DownloadGuideCardsCarousel(language = strings.language)
        }
    }
}

/**
 * 가이드 카드 3장을 넘겨볼 수 있는 공통 캐러셀 컴포넌트.
 * 로비 인라인 가이드 섹션과 설정 화면의 '가이드 보기' 팝업 다이얼로그에서 공통 사용된다.
 * 좌우 스와이프 제스처 및 화살표/도트/본문 탭을 통한 유연한 내비게이션을 지원한다.
 */
@Composable
internal fun DownloadGuideCardsCarousel(
    language: UiLanguage,
    modifier: Modifier = Modifier,
) {
    val cards = UiStringsDownloadGuide.cards(language)
    val totalCards = cards.size
    val pagerState = rememberPagerState(initialPage = 0) { totalCards }
    val scope = rememberCoroutineScope()
    val currentCard = cards[pagerState.currentPage.coerceIn(0, totalCards - 1)]

    Column(modifier = modifier.fillMaxWidth()) {
        // 가이드 카드 헤더 (제목 + 페이지 표시 및 이동 화살표)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentCard.title,
                fontSize = AppTextSize.Text14,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        scope.launch {
                            val prev = (pagerState.currentPage - 1 + totalCards) % totalCards
                            pagerState.animateScrollToPage(prev)
                        }
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = UiStringsDownloadGuide.previousGuide(language),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Text(
                    text = "${pagerState.currentPage + 1} / $totalCards",
                    fontSize = AppTextSize.Text12,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.Space4),
                )

                IconButton(
                    onClick = {
                        scope.launch {
                            val next = (pagerState.currentPage + 1) % totalCards
                            pagerState.animateScrollToPage(next)
                        }
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = UiStringsDownloadGuide.nextGuide(language),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space6))

        // 스와이프 가능한 가이드 본문 Pager (탭 시에도 다음 카드로 이동)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Text(
                text = cards[page].body,
                fontSize = AppTextSize.Text13,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val next = (pagerState.currentPage + 1) % totalCards
                            pagerState.animateScrollToPage(next)
                        }
                    },
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space10))

        // 하단 도트 인디케이터
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(totalCards) { index ->
                val isSelected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = AppSpacing.Space4)
                        .size(if (isSelected) 6.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            }
                        )
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                )
            }
        }
    }
}

/**
 * 설정 화면 등에서 언제든 사용 가이드 카드를 열어볼 수 있는 다이얼로그 (실기기 테스트 및 도움말).
 */
@Composable
internal fun DownloadGuideDialog(onClose: () -> Unit) {
    GuideBlockingOverlays.TrackWhileShown()
    val strings = LocalUiStrings.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(AppRadius.Corner16)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.Space20),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = UiStringsDownloadGuide.boardControlsGuide(strings.language),
                        fontSize = AppTextSize.Text16,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = UiStringsDownloadGuide.close(strings.language),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.Space16))

                Card(
                    shape = RoundedCornerShape(AppRadius.Corner12),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.padding(AppSpacing.Space16)) {
                        DownloadGuideCardsCarousel(language = strings.language)
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.Space16))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClose) {
                        Text(text = UiStringsDownloadGuide.close(strings.language))
                    }
                }
            }
        }
    }
}
