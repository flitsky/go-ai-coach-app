package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.presentation.GameActionButtonState
import com.worksoc.goaicoach.presentation.GameUiEvent

internal val ActionButtonMinHeight = 48.dp
internal val ActionButtonShape = RoundedCornerShape(16.dp)
internal val ActionButtonBorder
    @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
internal val ActionButtonContentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
internal val ActionButtonContainerColor
    @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
internal val ActionButtonContentColor
    @Composable get() = MaterialTheme.colorScheme.primary

@Composable
internal fun ToggleActionButton(
    action: GameActionButtonState,
    label: String,
    onEvent: (GameUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    premiumLocked: Boolean = false,
) {
    val isOn = action.isFilled
    val toggleModifier = modifier
        .height(ActionButtonMinHeight)
        .semantics(mergeDescendants = true) {
            role = Role.Switch
            stateDescription = if (isOn) "ON" else "OFF"
        }

    if (action.isFilled) {
        Button(
            onClick = { onEvent(action.event) },
            enabled = action.enabled,
            modifier = toggleModifier,
            shape = ActionButtonShape,
            contentPadding = ActionButtonContentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            ToggleActionButtonContent(label = label)
        }
    } else {
        OutlinedButton(
            onClick = { onEvent(action.event) },
            enabled = action.enabled,
            modifier = toggleModifier,
            shape = ActionButtonShape,
            contentPadding = ActionButtonContentPadding,
            border = if (premiumLocked) PremiumLockedBorder else ActionButtonBorder,
        ) {
            ToggleActionButtonContent(label = label)
        }
    }
}

@Composable
internal fun SingleActionButton(
    action: GameActionButtonState,
    label: String,
    onEvent: (GameUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    premiumLocked: Boolean = false,
) {
    ActionButton(
        onClick = { onEvent(action.event) },
        enabled = action.enabled,
        modifier = modifier,
        label = label,
        premiumLocked = premiumLocked,
    )
}

@Composable
internal fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    premiumLocked: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(ActionButtonMinHeight),
        shape = ActionButtonShape,
        contentPadding = ActionButtonContentPadding,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = ActionButtonContainerColor,
            contentColor = ActionButtonContentColor,
        ),
        border = if (premiumLocked) PremiumLockedBorder else null,
    ) {
        ActionButtonText(label)
    }
}

/**
 * 형세 보기·추천 수 버튼의 내용. **ON/OFF 뱃지를 두지 않는다**(2026-08-29 사용자 확정) —
 * 이 버튼들은 이제 상태 토글이 아니라 **1회성 동작**이라, 상태 표기가 오히려 "켜 두면 계속
 * 갱신된다"는 잘못된 기대를 준다. 매 수마다 보는 방식은 대국 메뉴의 별도 옵션이 담당한다.
 * 채워진 배경(켜짐)은 지금 표시 중인지를 알리는 용도로만 남긴다.
 */
@Composable
private fun ToggleActionButtonContent(label: String) {
    ActionButtonText(label)
}


@Composable
private fun ActionButtonText(label: String) {
    Text(
        text = label,
        maxLines = 1,
        softWrap = false,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
