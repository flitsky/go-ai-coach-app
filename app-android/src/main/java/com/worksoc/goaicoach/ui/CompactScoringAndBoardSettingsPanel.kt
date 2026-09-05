package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
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
 * 압축해 스크롤 없이 한 화면에 보이게 한다.
 *
 * ⚠️ **이제 이 패널이 유일한 선택지다.** [GameSetupLobby]와 설정 화면이 함께 이것만 그린다 —
 * 분기하던 `GameSetupUxMode`는 #73이, 짝이던 심플 레이아웃(`ScoringAndBoardSettingsPanel`)은
 * #76이 지웠다(2026-09-05).
 *
 * ## ⚠️ 판 크기·접바둑은 진행 중 대국에서 잠긴다(백로그 #75, 2026-09-05 사용자 결정)
 * 심플 레이아웃이 갖고 있던 게이팅을 이 패널에 **다시 만들었다**(#73·#76이 그 표현을 지웠고,
 * 그때 되살릴지가 미정이었다). 사용자 결정의 근거: *"굳이 대국 중에 다른 대국 설정이 필요한가,
 * 나중에 대국 시작할 때 하면 될 일"* — **로비가 그 자리이고, 그쪽은 잠그지 않는다.**
 *
 * ⚠️ **계가 방식과 덤은 잠그지 않는다.** 원래 심플 레이아웃도 그 둘만 묶었다 — 판의 **모양**을
 * 바꾸는 것과 **셈법**을 바꾸는 것은 진행 중 대국에 미치는 뜻이 다르다.
 *
 * ⚠️ **잠글지 판정하는 조건을 여기서 인라인으로 쓰지 말 것** —
 * [com.worksoc.goaicoach.application.preferences.isBoardSetupLockedDuringGame]가 갖고 있다.
 * `isGameEnded` 하나로 판단하면 **대국을 한 번도 하지 않은 사용자에게도 잠긴다.**
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
    /** 판 크기·접바둑을 바꿀 수 있는가. 로비는 항상 `true`(늘 대국 시작 전이다), 설정 화면은 #75의 판정을 넘긴다. */
    canChangeBoardShape: Boolean = true,
) {
    val strings = LocalUiStrings.current
    val handicapOptions = listOf(0) + (2..boardSize.maxHandicapCount).toList()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1행: 계가 방식 / 덤
        Row(
            // ⚠️ **`IntrinsicSize.Min`으로 묶는다**(백로그 #107). 아래 칸 글자가 두 줄로 접히면
            // 그 칸만 높아져 짝이 어긋난다 — 출석판이 같은 처방으로 고쳤다(#64 ⓐ, 함정 9번).
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactSettingDropdownCell(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                valueText = strings.compactRulesetLabel(ruleset),
                options = Ruleset.entries,
                optionLabel = strings::compactRulesetLabel,
                onSelected = onRulesetChange,
            )
            CompactSettingDropdownCell(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                valueText = strings.compactKomiLabel(komi),
                options = KomiOptions,
                optionLabel = strings::komiValueLabel,
                onSelected = onKomiChange,
            )
        }
        // 2행: 바둑판 크기 / 접바둑
        Row(
            // ⚠️ **`IntrinsicSize.Min`으로 묶는다**(백로그 #107). 아래 칸 글자가 두 줄로 접히면
            // 그 칸만 높아져 짝이 어긋난다 — 출석판이 같은 처방으로 고쳤다(#64 ⓐ, 함정 9번).
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactSettingDropdownCell(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                valueText = strings.compactBoardSizeLabel(boardSize),
                options = listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen),
                optionLabel = { size -> "${size.value}x${size.value}" },
                onSelected = onBoardSizeChange,
                enabled = canChangeBoardShape,
            )
            CompactSettingDropdownCell(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                valueText = strings.compactHandicapLabel(handicapCount),
                options = handicapOptions,
                optionLabel = strings::compactHandicapValueLabel,
                onSelected = onHandicapCountChange,
                enabled = canChangeBoardShape,
            )
        }
        // ⚠️ **잠근 이유를 반드시 말한다.** 눌러도 안 열리는 칸을 이유 없이 두면 고장으로 읽힌다 —
        // 사용자가 이 항목을 만든 계기도 *"바꿨는데 왜 그대로지"* 라는 어긋남이었다.
        if (!canChangeBoardShape) {
            Text(
                text = strings.boardShapeLockedDuringGame,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
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
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 행이 `IntrinsicSize.Min`이므로 배경도 그 높이를 채워야 짝이 나란히 보인다.
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                // ⚠️ `clickable(enabled = false)`로 둔다 — 아예 빼면 **잠긴 칸이 부모의 클릭을
                // 대신 받는다.** 여기서는 부모가 스크롤이라 잠긴 칸을 눌렀을 때 엉뚱하게 반응한다.
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = valueText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    // 잠긴 것이 **보여야** 한다 — 색만 흐리게 하고 값은 그대로 읽히게 둔다.
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)
                },
                // ⚠️ **1줄이면 큰 글꼴에서 값이 잘린다** — 글꼴 크기가 정식 설정이 되면서
                // (#106) 일반 사용자가 1.3배에 닿게 됐고, 실기에서 `바둑판 (13…` 으로 잘렸다.
                // 고정 높이를 주지 않으므로 접혀도 칸이 함께 자란다(함정 9번).
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "▾",
                fontSize = 12.sp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = DisabledAlpha)
                },
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

/** 잠긴 칸의 불투명도. 값은 계속 읽혀야 하므로 완전히 흐리게 하지 않는다. */
private const val DisabledAlpha = 0.38f
