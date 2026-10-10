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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.engine.currentEngineDownloadTracker
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide

/**
 * PAD on-demand 에셋 팩 다운로드 동안 표시되는 사용 가이드 카드 섹션 (백로그 #245 U-73).
 *
 * ## 지키는 조건
 * 1. 팩을 실제로 받는 중이거나 상태가 발생했을 때만 표시 (모델이 이미 있는 기기에는 노출 0초)
 * 2. 실패/Wi-Fi 대기 시 숨지 않고 상태와 [다시 시도] 버튼 노출
 * 3. 완료 순간 읽던 카드를 빼앗지 않고 자연스럽게 유지
 * 4. 팝업이 아니라 화면 안의 인라인 컴포저블로 배치 (출석/가이드 사슬 방해 금지)
 * 5. GoCoachApp 상태 훅 예산(42/42)을 침범하지 않고 이 컴포저블 내부에서 자체 캡슐화
 */
@Composable
internal fun EngineDownloadGuideSection(
    modifier: Modifier = Modifier,
) {
    val downloadStatus by currentEngineDownloadTracker().status.collectAsState()
    val strings = LocalUiStrings.current

    // Idle 상태이면 아무것도 표시하지 않는다
    if (downloadStatus is EngineDownloadStatus.Idle) {
        return
    }

    var cardIndex by remember { mutableIntStateOf(0) }
    val totalCards = 3

    val currentCard = UiStringsDownloadGuide.cardFor(strings.language, cardIndex)

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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.engineNotReadyToStart,
                            fontSize = AppTextSize.Text12,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "$downloadedMb MB / $totalMb MB (${status.percentage}%)",
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

                    Spacer(modifier = Modifier.height(AppSpacing.Space12))
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
                        )
                        Text(
                            text = "$downloadedMb MB / $totalMb MB",
                            fontSize = AppTextSize.Text12,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

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

                    Spacer(modifier = Modifier.height(AppSpacing.Space12))
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
                        )
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
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.Space12))
                }

                is EngineDownloadStatus.Completed -> {
                    // 완료 시에도 읽던 카드는 상단 상태 라벨만 완료로 변경하고 유지
                    Text(
                        text = "✓ AI 준비 완료",
                        fontSize = AppTextSize.Text12,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.Space8))
                }

                else -> {
                    // Idle 등
                }
            }

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
                            contentDescription = "Previous guide",
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
                            contentDescription = "Next guide",
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
}
