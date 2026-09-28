package com.worksoc.goaicoach.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.worksoc.goaicoach.ui.designsystem.AppElevation
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings

@Composable
internal fun GameMenuActionsPanel(
    onCopyLog: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Corner8),
        tonalElevation = AppElevation.Level1,
        shadowElevation = AppElevation.Level0,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.Space12),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCopyLog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.copyLog, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
