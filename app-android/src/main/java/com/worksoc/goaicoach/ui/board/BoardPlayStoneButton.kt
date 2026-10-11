package com.worksoc.goaicoach.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.ui.designsystem.GameStatusPalette

// 착수 칸의 **돌 버튼**(백로그 #143·#223). 대국 화면(`ui.play`의 `PlaySlot`)과 바둑판 조작법 시연(#249)이 함께 쓴다 —
// 시연이 따로 그리면 실제 버튼을 고칠 때 시연만 옛 모양으로 남는다. 판의 돌 그리기만 쓰므로 판 패키지에 둔다(#249에서 옮겼다).

/** 돌 버튼의 세 얼굴 — 대국의 착수 칸(`PlaySlot`)의 KDoc. */
internal enum class PlayStoneFace { ConfirmOff, ConfirmOn, CommitMove }

/**
 * 지금 돌 버튼이 어느 얼굴인가. 가착수는 착수 확인에서만 생기지만(바로 착수는 탭이 곧 착수다), 그 약속에 기대지 않고
 * **모드를 먼저 본다** — 착수 확인이 꺼져 있는데 「착수」 확정 버튼이 뜨는 일은 없어야 한다.
 */
internal fun playStoneFaceOf(isDirectPlay: Boolean, hasTentativeMove: Boolean): PlayStoneFace = when {
    isDirectPlay -> PlayStoneFace.ConfirmOff
    hasTentativeMove -> PlayStoneFace.CommitMove
    else -> PlayStoneFace.ConfirmOn
}

/** 착수 확인이 **꺼져 있을 때** 돌의 투명도 — 판의 가착수 돌이 깜빡이는 범위(0.35~0.65) 안에서, 켜진 돌과 한눈에 갈리게 낮은 쪽. */
private const val PlayStoneOffAlpha = 0.4f

/** 꺼져 있을 때 글자의 투명도 — 돌만 흐리고 글자가 진하면 켜진 것으로 읽힌다. */
private const val PlayStoneOffLabelAlpha = 0.45f

/** 착수 확정 얼굴에서 돌을 두르는 고리의 굵기. */
private val PlayStoneCommitRingWidth = 3.dp

/**
 * 돌 버튼 한 개. 돌은 **판의 돌과 같은 그리기**를 쓴다(`drawStone`·`drawGhostStone`) — 따로 그리면 판의 돌 모양을
 * 고칠 때 이 버튼만 옛 모양으로 남는다. 색은 누구 차례든 **흰 돌**이다(사용자 지시) — 글자를 얹을 바탕이라서다.
 *
 * ⚠️ 돌을 그리는 `drawBehind`가 `clip`보다 **앞**이다 — 돌의 그림자는 원 밖으로 조금 나가고, 잘리는 것은 누름 물결뿐이어야 한다.
 */
@Composable
internal fun PlayStoneButton(
    face: PlayStoneFace,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val isCommit = face == PlayStoneFace.CommitMove
    val ringColor = MaterialTheme.colorScheme.primary
    val ringWidth = with(LocalDensity.current) { PlayStoneCommitRingWidth.toPx() }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .drawBehind {
                val radius = size.minDimension / 2f
                if (face == PlayStoneFace.ConfirmOff) {
                    drawGhostStone(center = center, radius = radius, stone = StoneColor.White, alpha = PlayStoneOffAlpha)
                } else {
                    drawStone(center = center, radius = radius, stone = StoneColor.White, isGameEnded = false)
                }
                // 확정 얼굴은 "지금 누르면 둔다"를 고리로 말한다 — 글자만 바뀌면 모드 버튼인 줄 알고 누른다.
                if (isCommit) {
                    drawCircle(color = ringColor, radius = radius - ringWidth / 2f, center = center, style = Stroke(width = ringWidth))
                }
            }
            .clip(CircleShape)
            // 글자가 고정이라 켜짐·꺼짐을 접근성 도구에는 **스위치의 값**으로 알린다. 확정 얼굴만 그냥 버튼이다.
            .then(
                if (isCommit) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier.toggleable(
                        value = face == PlayStoneFace.ConfirmOn,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = { onClick() },
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // 라벨이 두 줄(`착수\n확인`)이다 — 줄바꿈은 문자열이 갖고 있다.
            text = label,
            style = if (isCommit) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when (face) {
                PlayStoneFace.CommitMove -> ringColor
                PlayStoneFace.ConfirmOn -> GameStatusPalette.SeatLabel
                PlayStoneFace.ConfirmOff -> GameStatusPalette.SeatLabel.copy(alpha = PlayStoneOffLabelAlpha)
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
