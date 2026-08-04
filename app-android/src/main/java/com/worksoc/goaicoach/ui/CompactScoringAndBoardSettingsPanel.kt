package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.KomiOptions
import com.worksoc.goaicoach.shared.Ruleset

/**
 * 대국 설정 화면의 "콤팩트" 레이아웃 — 계가/덤/바둑판 크기/접바둑을 2x2 드롭다운 그리드로
 * 압축해 스크롤 없이 한 화면에 보이게 한다. [ScoringAndBoardSettingsPanel](심플 레이아웃)과
 * 기능은 동일하고(같은 GameUiEvent를 그대로 재사용), 시각적 밀도만 다르다 — 어느 것을 쓸지는
 * [GameSetupLobby]가 저장된 `GameSetupUxMode`로 분기한다.
 */
@Composable
internal fun CompactScoringAndBoardSettingsPanel(
    ruleset: Ruleset,
    boardSize: BoardSize,
    handicapCount: Int,
    komi: Double,
    onRulesetChange: (Ruleset) -> Unit,
    onBoardSizeChange: (BoardSize) -> Unit,
    onHandicapCountChange: (Int) -> Unit,
    onKomiChange: (Double) -> Unit,
) {
    val strings = LocalUiStrings.current
    val handicapOptions = listOf(0) + (2..boardSize.maxHandicapCount).toList()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1행: 계가 방식 / 덤
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactSettingDropdownCell(
                modifier = Modifier.weight(1f),
                valueText = strings.compactRulesetLabel(ruleset),
                options = Ruleset.entries,
                optionLabel = strings::compactRulesetLabel,
                onSelected = onRulesetChange,
            )
            CompactSettingDropdownCell(
                modifier = Modifier.weight(1f),
                valueText = strings.compactKomiLabel(komi),
                options = KomiOptions,
                optionLabel = strings::komiValueLabel,
                onSelected = onKomiChange,
            )
        }
        // 2행: 바둑판 크기 / 접바둑
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactSettingDropdownCell(
                modifier = Modifier.weight(1f),
                valueText = strings.compactBoardSizeLabel(boardSize),
                options = listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen),
                optionLabel = { size -> "${size.value}x${size.value}" },
                onSelected = onBoardSizeChange,
            )
            CompactSettingDropdownCell(
                modifier = Modifier.weight(1f),
                valueText = strings.compactHandicapLabel(handicapCount),
                options = handicapOptions,
                optionLabel = strings::compactHandicapValueLabel,
                onSelected = onHandicapCountChange,
            )
        }
    }
}

/**
 * 콤팩트 그리드 한 칸 — 버튼 텍스트 자체가 "라벨 (값)" 형태라 셀 위 별도 작은 라벨을
 * 두지 않는다(버튼 하나만 봐도 무엇을 어떤 값으로 설정했는지 알 수 있게). 탭하면
 * [DropdownMenu]로 다른 값을 고를 수 있다.
 */
@Composable
private fun <T> CompactSettingDropdownCell(
    valueText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = valueText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "▾",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
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
