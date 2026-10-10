package com.worksoc.goaicoach.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/**
 * PAD on-demand 에셋 팩 다운로드 동안 표시되는 사용 가이드 카드 섹션 (백로그 #245 U-73).
 *
 * ## 지키는 조건
 * 1. 팩을 실제로 받는 중이거나 상태가 발생했을 때만 표시 (모델이 이미 있는 기기에는 노출 0초)
 * 2. 실패/Wi-Fi 대기 시 숨지 않고 상태와 [다시 시도] 버튼 노출
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

    // Idle 상태이거나 사용자가 카드를 닫았으면 렌더링하지 않는다
    if (downloadStatus is EngineDownloadStatus.Idle || isDismissed) {
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
                    val statusTitle = if (status.isHumanModelOnly) {
                        UiStringsDownloadGuide.preparingHumanModel(strings.language)
                    } else {
                        strings.engineNotReadyToStart
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = statusTitle,
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

                    Button(
                        onClick = { currentEngineDownloadTracker().retry() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        shape = RoundedCornerShape(AppRadius.Corner8),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = UiStringsDownloadGuide.retry(strings.language),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.Space6))
                        Text(text = UiStringsDownloadGuide.retry(strings.language), fontSize = AppTextSize.Text12)
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
 */
@Composable
internal fun DownloadGuideCardsCarousel(
    language: UiLanguage,
    modifier: Modifier = Modifier,
) {
    val cards = UiStringsDownloadGuide.cards(language)
    val totalCards = cards.size
    var cardIndex by remember { mutableIntStateOf(0) }
    val currentCard = cards[cardIndex.coerceIn(0, totalCards - 1)]

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
                    onClick = { cardIndex = (cardIndex - 1 + totalCards) % totalCards },
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
                    text = "${cardIndex + 1} / $totalCards",
                    fontSize = AppTextSize.Text12,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.Space4),
                )

                IconButton(
                    onClick = { cardIndex = (cardIndex + 1) % totalCards },
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

        // 가이드 본문 텍스트 (탭 시 다음 카드로 이동)
        AnimatedContent(
            targetState = currentCard.body,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "GuideCardBody",
        ) { body ->
            Text(
                text = body,
                fontSize = AppTextSize.Text13,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { cardIndex = (cardIndex + 1) % totalCards },
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
                val isSelected = index == cardIndex
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
                        .clickable { cardIndex = index },
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
