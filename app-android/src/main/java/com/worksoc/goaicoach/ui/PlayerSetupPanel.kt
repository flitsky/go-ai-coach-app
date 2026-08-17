package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.presentation.PlayerSetupSideUiState
import com.worksoc.goaicoach.presentation.PlayerSetupUiState
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            SeatControllerPill(
                label = strings.controllerLabel(SeatController.Human),
                selected = side.controller == SeatController.Human,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.seatControllerPill(state.color, SeatController.Human)),
                onClick = { onSideChange(side.copy(controller = SeatController.Human)) },
            )
            SeatControllerPill(
                label = strings.controllerLabel(SeatController.Ai),
                selected = side.controller == SeatController.Ai,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.seatControllerPill(state.color, SeatController.Ai)),
                onClick = { onSideChange(side.copy(controller = SeatController.Ai)) },
            )
        }
        // AI 선택 시에만 노출되는 난이도 드롭다운 — "AI" 버튼 아래쪽에서 파생된 것처럼 우측 정렬.
        // 2026-08-18부터 그룹(빠른초급/초급/중급/고급)→단계의 2뎁스 선택을 없애고,
        // 빠른 초급의 5단계(초보~초고수)만 노출하는 1뎁스 드롭다운으로 간소화했다 —
        // 초심자 진입 난이도를 낮추는 게 목적. 초급/중급/고급 그룹은 코드는 남아있지만
        // 이 화면에서는 완전히 숨겼다(대체 진입 경로는 docs/engine-research의
        // FAST_BEGINNER_FIVE_TIER_REDESIGN_PLAN 문서 로드맵 참고).
        if (side.controller == SeatController.Ai) {
            val fastBeginnerLevel = if (side.playLevel.group == PlayLevelGroup.FastBeginner) {
                side.playLevel.safeLevel
            } else {
                1
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                SetupDropdown(
                    selectedText = strings.fastBeginnerTierLabel(fastBeginnerLevel),
                    enabled = enabled,
                    modifier = Modifier.width(112.dp),
                    options = (1..PlayLevelGroup.FastBeginner.maxLevel).toList(),
                    optionLabel = { level -> strings.fastBeginnerTierLabel(level) },
                    onSelected = { level ->
                        onSideChange(
                            side.copy(
                                playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = level),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SeatControllerPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}


