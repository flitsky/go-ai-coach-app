package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.presentation.GameActionButtonState
import com.worksoc.goaicoach.presentation.GameUiEvent

internal val ActionButtonMinHeight = 48.dp
internal val ActionButtonShape = RoundedCornerShape(16.dp)
internal val ActionButtonBorder
    @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
// 가로 4dp는 오타가 아니다(#29). 3분할 행의 버튼 폭은 360dp 화면에서 104dp인데, 좌우 8dp씩을
// 떼면 글자에 88dp만 남는다. 옛 일본어 라벨 `新しい対局を開始`은 전각 8자 = 92dp라 배율
// 1.0배에서도 넘쳤다. 4dp면 96dp가 되고, 라벨을 `新規対局`(46dp)으로 함께 줄여 이 행은
// 2.0배까지 버틴다 — 둘 중 하나만으로는 부족했다.
//
// 짧은 라벨(`기권`/`통과`)의 겉모습은 바뀌지 않는다 — M3 `Button` 내부는
// `Arrangement.Center`라 남는 여백이 어차피 양쪽으로 갈리기 때문이다. 실제로 넓어지는 건
// 넘치던 라벨뿐이다.
//
// ⚠️ 1행(형세·추천 수)은 이 패딩으로 152dp를 받지만 그래도 2.0배에서는 잘린다
//    (`추천 수 보…(5)`). 그건 이 상수가 아니라 라벨 길이의 문제이고, 잔량 표기는 별도
//    `Text`라 언제나 살아남는다(#27).
internal val ActionButtonContentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
internal val ActionButtonContainerColor
    @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
internal val ActionButtonContentColor
    @Composable get() = MaterialTheme.colorScheme.primary

@Composable
internal fun ToggleActionButton(
    action: GameActionButtonState,
    label: String,
    mark: String? = null,
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
            ToggleActionButtonContent(label = label, mark = mark)
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
            ToggleActionButtonContent(label = label, mark = mark)
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
private fun ToggleActionButtonContent(label: String, mark: String? = null) {
    ActionButtonText(label = label, mark = mark)
}


/**
 * 버튼 라벨. **잔량 표기([mark])는 이름과 분리해 먼저 자리를 잡는다**(#27).
 *
 * 예전에는 `(3)`/`(∞)`/`(⏱)`가 한 문자열의 꼬리였는데, `softWrap = false` + 기본
 * `TextOverflow.Clip` 조합은 폭 제약을 무시한 채 측정한 뒤 부모가 오른쪽을 잘라낸다 — 그래서
 * **가장 먼저 사라지는 것이 하필 잔량**이었다. `Row`는 가중치 없는 자식을 먼저 측정하므로,
 * 표기를 따로 떼면 그것은 언제나 온전히 그려지고 모자란 폭은 이름 쪽이 말줄임으로 흡수한다.
 *
 * 표기가 없는 버튼(기권·통과·무르기)도 `Clip` 대신 말줄임을 쓴다 — 글자가 뭉텅 잘리는 것보다
 * "…"이 낫다.
 */
@Composable
private fun ActionButtonText(label: String, mark: String? = null) {
    val labelStyle = MaterialTheme.typography.labelSmall
    if (mark == null) {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = labelStyle,
            fontWeight = FontWeight.SemiBold,
        )
        return
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = labelStyle,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = " ($mark)",
            maxLines = 1,
            softWrap = false,
            style = labelStyle,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
