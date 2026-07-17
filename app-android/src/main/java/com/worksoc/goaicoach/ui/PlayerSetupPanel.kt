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
import com.worksoc.goaicoach.shared.SearchTimeLimit
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
            MaximumSearchTimeLimitRow(
                selected = settings.limit,
                enabled = enabled,
                onSelected = { limit -> onSettingsChange(settings.withLimit(limit)) },
            )
        }
    }
}

@Composable
private fun MaximumSearchTimeLimitRow(
    selected: SearchTimeLimit,
    enabled: Boolean,
    onSelected: (SearchTimeLimit) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.maximumSearchTimeLimit,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        SetupDropdown(
            selectedText = strings.searchTimeLimitLabel(selected),
            enabled = enabled,
            modifier = Modifier.weight(1f),
            options = SearchTimeLimit.entries,
            optionLabel = strings::searchTimeLimitLabel,
            onSelected = onSelected,
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


