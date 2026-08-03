package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Ruleset

@Composable
internal fun ScoringAndBoardSettingsPanel(
    ruleset: Ruleset,
    boardSize: BoardSize,
    handicapCount: Int,
    komi: Double = com.worksoc.goaicoach.shared.DefaultKomi,
    canChangeRuleset: Boolean = true,
    canChangeBoardSize: Boolean = true,
    canChangeHandicap: Boolean = true,
    canChangeKomi: Boolean = true,
    onRulesetChange: (Ruleset) -> Unit,
    onBoardSizeChange: (BoardSize) -> Unit,
    onHandicapCountChange: (Int) -> Unit,
    onKomiChange: (Double) -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingChoiceRow(
                label = strings.scoringRule,
                options = Ruleset.entries,
                selected = ruleset,
                enabled = canChangeRuleset,
                optionLabel = { rule -> strings.rulesetLabel(rule) },
                onSelected = onRulesetChange,
            )
            SettingChoiceRow(
                label = strings.komi,
                options = com.worksoc.goaicoach.shared.KomiOptions,
                selected = komi,
                enabled = canChangeKomi,
                optionLabel = { k -> strings.komiValueLabel(k) },
                onSelected = onKomiChange,
            )
            SettingChoiceRow(
                label = strings.boardSize,
                options = listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen),
                selected = boardSize,
                enabled = canChangeBoardSize,
                optionLabel = { "${it.value}x${it.value}" },
                onSelected = onBoardSizeChange,
            )
            // 접바둑 설정 행
            HandicapSettingRow(
                boardSize = boardSize,
                handicapCount = handicapCount,
                enabled = canChangeHandicap,
                onHandicapCountChange = onHandicapCountChange,
            )
        }
    }
}

/**
 * 접바둑 설정 UI.
 * [ - ] [ 접바둑 N점 ▼ ] [ + ] 레이아웃으로 구성됩니다.
 */
@Composable
private fun HandicapSettingRow(
    boardSize: BoardSize,
    handicapCount: Int,
    enabled: Boolean,
    onHandicapCountChange: (Int) -> Unit,
) {
    val strings = LocalUiStrings.current
    val maxHandicap = boardSize.maxHandicapCount

    // 접바둑 0점 = "없음", 그 외 N점 표시
    val displayText = if (handicapCount == 0) {
        strings.handicapNone
    } else {
        strings.handicapLabel(handicapCount)
    }

    // 드롭다운 옵션: 0 ~ maxHandicap (단, 1점 접바둑 없음)
    val options = listOf(0) + (2..maxHandicap).toList()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 레이블
        Text(
            text = strings.handicap,
            modifier = Modifier.weight(0.8f),
            fontWeight = FontWeight.SemiBold,
        )

        // - 버튼
        HandicapStepButton(
            symbol = "−",
            enabled = enabled && handicapCount > 0,
            onClick = {
                val prev = if (handicapCount <= 2) 0 else handicapCount - 1
                onHandicapCountChange(prev)
            },
        )

        // 중앙 드롭다운 버튼
        SetupDropdown(
            selectedText = displayText,
            enabled = enabled,
            modifier = Modifier.weight(1.4f),
            options = options,
            optionLabel = { option -> if (option == 0) strings.handicapNone else strings.handicapLabel(option) },
            onSelected = onHandicapCountChange,
        )

        // + 버튼
        HandicapStepButton(
            symbol = "+",
            enabled = enabled && handicapCount < maxHandicap,
            onClick = {
                val next = if (handicapCount == 0) 2 else handicapCount + 1
                onHandicapCountChange(next.coerceAtMost(maxHandicap))
            },
        )
    }
}

@Composable
private fun HandicapStepButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
