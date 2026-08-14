package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.presentation.KaTrainUxOptions

@Composable
internal fun KaTrainUxMenuButton(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
) {
    val strings = LocalUiStrings.current
    OutlinedButton(
        onClick = { onMenuExpandedChange(!menuExpanded) },
    ) {
        Text(if (menuExpanded) strings.close else "\u2630")
    }
}

@Composable
internal fun KaTrainUxMenuPanel(
    options: KaTrainUxOptions,
    onOptionsChange: (KaTrainUxOptions) -> Unit,
) {
    val strings = LocalUiStrings.current
    val premium = LocalPremiumUiState.current
    var showPremiumUpsellDialog by remember { mutableStateOf(false) }
    val columnGap = (LocalConfiguration.current.screenWidthDp * 0.05f).dp

    PremiumUpsellDialogHost(
        visible = showPremiumUpsellDialog,
        onDismiss = { showPremiumUpsellDialog = false },
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OptionSwitchCell(
                    label = strings.directPlay,
                    checked = options.isDirectPlayEnabled,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(isDirectPlayEnabled = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                OptionSwitchCell(
                    label = strings.lastMoveRing,
                    checked = options.showLastMoveRing,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showLastMoveRing = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OptionSwitchCell(
                    label = strings.moveNumbers,
                    checked = options.showMoveNumbers,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showMoveNumbers = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                // 착수 평가: 착수한 돌의 품질 색상 표시 여부. 기본 꺼짐 — 사용자가 의도적으로
                // 켤 때만 노출한다. 향후 평가 신뢰도/방식이 점진적으로 고도화될 예정인 기능.
                // 프리미엄 전용 — 비활성 상태에서는 흐리게 표시하고 탭하면 업셀 팝업을 띄운다.
                // 판정은 FeatureAccessPolicy(6계층)에 위임 — GamePlaySection.kt의 형세보기/추천수와
                // 같은 정책 소스를 공유한다(이 기능엔 클레임 경로가 없어 동작은 이전과 동일).
                val moveReviewAllowed = premium.resolve(FeatureId.MoveReview) is FeatureAccess.Allowed
                OptionSwitchCell(
                    label = strings.moveReviewToggle,
                    checked = options.showMoveReview && moveReviewAllowed,
                    modifier = Modifier.weight(1f).alpha(if (moveReviewAllowed) 1f else 0.5f),
                    onCheckedChange = {
                        if (moveReviewAllowed) {
                            onOptionsChange(options.copy(showMoveReview = it))
                        } else {
                            showPremiumUpsellDialog = true
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OptionSwitchCell(
                    label = strings.coordinates,
                    checked = options.showCoordinates,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showCoordinates = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OptionSwitchCell(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

