package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.AiEngineChoice
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.presentation.PlayerSetupSideUiState
import com.worksoc.goaicoach.presentation.PlayerSetupUiState
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.SearchTimeProfile
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun PlayerSetupPanel(
    state: PlayerSetupUiState,
    enabled: Boolean,
    onPlayerSetupChange: (PlayerSetup) -> Unit,
    onAutoPlayDelayChange: (AutoPlayDelaySetting) -> Unit,
) {
    val strings = LocalUiStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.playerSetup, fontWeight = FontWeight.SemiBold)
            PlayerSetupSideRow(
                state = state.black,
                enabled = enabled,
                onSideChange = { side -> onPlayerSetupChange(state.setup.updateSide(StoneColor.Black, side)) },
            )
            PlayerSetupSideRow(
                state = state.white,
                enabled = enabled,
                onSideChange = { side -> onPlayerSetupChange(state.setup.updateSide(StoneColor.White, side)) },
            )
            if (state.showAutoPlayDelay) {
                AutoPlayDelayRow(
                    selected = state.autoPlayDelaySetting,
                    onSelected = onAutoPlayDelayChange,
                )
            }
        }
    }
}

@Composable
internal fun SearchTimeSettingsPanel(
    settings: SearchTimeSettings,
    benchmarkAverages: Map<Int, Double>,
    enabled: Boolean,
    onSettingsChange: (SearchTimeSettings) -> Unit,
) {
    val strings = LocalUiStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.searchTime, fontWeight = FontWeight.SemiBold)
            SearchTimeCapRow(
                timeCapEnabled = settings.timeCapEnabled,
                enabled = enabled,
                onSelected = { timeCapEnabled ->
                    onSettingsChange(settings.withTimeCapEnabled(timeCapEnabled))
                },
            )
            SearchTimeProfile.entries.forEach { profile ->
                SearchTimeRow(
                    profile = profile,
                    selectedMillis = settings.millisFor(profile),
                    recommendedAverageMs = benchmarkAverages[profile.visits],
                    enabled = enabled && settings.timeCapEnabled,
                    onSelectedMillis = { millis ->
                        onSettingsChange(settings.withMillis(profile, millis))
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchTimeCapRow(
    timeCapEnabled: Boolean,
    enabled: Boolean,
    onSelected: (Boolean) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.timeCap,
            modifier = Modifier.weight(0.52f),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (timeCapEnabled) strings.timeCapOn else strings.timeCapOff,
            modifier = Modifier.weight(1.12f),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        SetupDropdown(
            selectedText = if (timeCapEnabled) "On" else "Off",
            enabled = enabled,
            modifier = Modifier.weight(0.9f),
            options = listOf(true, false),
            optionLabel = { selected -> if (selected) "On" else "Off" },
            onSelected = onSelected,
        )
    }
}

@Composable
private fun SearchTimeRow(
    profile: SearchTimeProfile,
    selectedMillis: Long,
    recommendedAverageMs: Double?,
    enabled: Boolean,
    onSelectedMillis: (Long) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.searchProfileLabel(profile),
            modifier = Modifier.weight(0.52f),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${strings.recommendedPrefix}[${strings.timeRecommendationLabel(recommendedAverageMs)}]",
            modifier = Modifier.weight(1.12f),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        SetupDropdown(
            selectedText = strings.secondsLabel(selectedMillis),
            enabled = enabled,
            modifier = Modifier.weight(0.9f),
            options = profile.optionMillis,
            optionLabel = { millis -> strings.secondsLabel(millis) },
            onSelected = onSelectedMillis,
        )
    }
}

@Composable
private fun AutoPlayDelayRow(
    selected: AutoPlayDelaySetting,
    onSelected: (AutoPlayDelaySetting) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.autoDelay,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        SetupDropdown(
            selectedText = strings.autoPlayDelayLabel(selected),
            enabled = true,
            modifier = Modifier.weight(1f),
            options = AutoPlayDelaySetting.entries,
            optionLabel = { setting -> strings.autoPlayDelayLabel(setting) },
            onSelected = onSelected,
        )
    }
}

@Composable
private fun PlayerSetupSideRow(
    state: PlayerSetupSideUiState,
    enabled: Boolean,
    onSideChange: (SidePlayerSetup) -> Unit,
) {
    val strings = LocalUiStrings.current
    val side = state.side
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.colorLabel(state.color),
                modifier = Modifier.weight(0.38f),
                fontWeight = FontWeight.SemiBold,
            )
            SetupDropdown(
                selectedText = strings.controllerLabel(side.controller),
                enabled = enabled,
                modifier = Modifier.weight(1f),
                options = SeatController.entries,
                optionLabel = { controller -> strings.controllerLabel(controller) },
                onSelected = { controller ->
                    onSideChange(side.copy(controller = controller))
                },
            )
            when (side.controller) {
                SeatController.Human -> {
                    SetupStaticBox(
                        text = "-",
                        modifier = Modifier.weight(1.08f),
                    )
                    SetupStaticBox(
                        text = "-",
                        modifier = Modifier.weight(0.8f),
                    )
                }

                SeatController.Ai -> {
                    SetupDropdown(
                        selectedText = strings.playLevelGroupLabel(side.playLevel.group),
                        enabled = enabled,
                        modifier = Modifier.weight(1.08f),
                        options = PlayLevelGroup.entries,
                        optionLabel = { group -> strings.playLevelGroupLabel(group) },
                        onSelected = { group ->
                            onSideChange(side.copy(playLevel = side.playLevel.withGroup(group)))
                        },
                    )
                    SetupDropdown(
                        selectedText = strings.levelLabel(side.playLevel.safeLevel),
                        enabled = enabled,
                        modifier = Modifier.weight(0.8f),
                        options = (1..side.playLevel.group.maxLevel).toList(),
                        optionLabel = { level -> strings.levelLabel(level) },
                        onSelected = { level ->
                            onSideChange(side.copy(playLevel = side.playLevel.withLevel(level)))
                        },
                    )
                }
            }
        }
        if (side.controller == SeatController.Ai) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.engine,
                    modifier = Modifier.weight(0.38f),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                SetupDropdown(
                    selectedText = state.aiEngineLabel,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    options = AiEngineChoice.entries,
                    optionLabel = { it.label },
                    onSelected = { engineChoice ->
                        onSideChange(side.copy(aiEngine = engineChoice))
                    },
                )
                Text(
                    text = state.aiDetailText,
                    modifier = Modifier.weight(1.88f),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun <T> SetupDropdown(
    selectedText: String,
    enabled: Boolean,
    modifier: Modifier,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedText, maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
internal fun <T> SettingDropdownRow(
    label: String,
    selectedText: String,
    enabled: Boolean,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.8f),
            fontWeight = FontWeight.SemiBold,
        )
        SetupDropdown(
            selectedText = selectedText,
            enabled = enabled,
            modifier = Modifier.weight(1.4f),
            options = options,
            optionLabel = optionLabel,
            onSelected = onSelected,
        )
    }
}

@Composable
private fun SetupStaticBox(
    text: String,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier,
    ) {
        Text(text, maxLines = 1)
    }
}
