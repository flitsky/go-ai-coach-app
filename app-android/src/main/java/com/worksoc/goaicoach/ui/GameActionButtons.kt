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
internal val ActionButtonContainerColor = Color(0xFFE2F0E7)
internal val ActionButtonContentColor = Color(0xFF205B3E)

@Composable
internal fun ToggleActionButton(
    action: GameActionButtonState,
    label: String,
    onEvent: (GameUiEvent) -> Unit,
    modifier: Modifier = Modifier
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
            ToggleActionButtonContent(label = label, isOn = true)
        }
    } else {
        OutlinedButton(
            onClick = { onEvent(action.event) },
            enabled = action.enabled,
            modifier = toggleModifier,
            shape = ActionButtonShape,
            contentPadding = ActionButtonContentPadding,
            border = ActionButtonBorder,
        ) {
            ToggleActionButtonContent(label = label, isOn = false)
        }
    }
}

@Composable
internal fun SingleActionButton(
    action: GameActionButtonState,
    label: String,
    onEvent: (GameUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionButton(
        onClick = { onEvent(action.event) },
        enabled = action.enabled,
        modifier = modifier,
        label = label,
    )
}

@Composable
internal fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    ) {
        ActionButtonText(label)
    }
}

@Composable
private fun ToggleActionButtonContent(
    label: String,
    isOn: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ActionButtonText(label)
        ToggleStateBadge(isOn = isOn)
    }
}

@Composable
private fun ToggleStateBadge(isOn: Boolean) {
    val containerColor = if (isOn) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isOn) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = if (isOn) "ON" else "OFF",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
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
