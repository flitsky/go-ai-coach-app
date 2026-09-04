package com.worksoc.goaicoach.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.application.botcharacter.BotCharacter

/**
 * 캐릭터를 새로 얻었을 때의 축전 팝업(백로그 #69).
 *
 * ## 그림은 새로 그리지 않는다 — #50의 조각 공개를 재생한다
 * [BotCharacterAvatar]는 이미 흑백 위에 12시부터 시계방향으로 부채꼴을 열어 컬러를 드러내는
 * 연출을 갖고 있다(#50, 픽커 카드와 출석 도장판이 함께 쓴다). 그것을 **0에서 필요 수까지 한 번
 * 재생한 뒤 컬러로 확정**하면 *"모으던 조각이 캐릭터가 됐다"* 가 그림 하나로 읽힌다. 새 자산이
 * 0이고, 폰트 배율·저사양 기기에서 따로 검증할 것도 늘지 않는다(2026-09-03 사용자 확정).
 *
 * ⚠️ **출석 해금 캐릭터(3·5단계)에는 조각이라는 개념이 없다** — [shardRevealOf]가 `null`을
 * 돌려주므로 그때는 스윕 없이 컬러 아바타가 그대로 뜬다. 이 팝업은 두 경로를 모두 받으므로
 * 그 분기를 없애면 출석 획득에서 흑백이 그대로 남는다.
 *
 * ## 닫기는 "아무 곳이나 탭"이다 — 세 경로가 같은 함수로 간다
 * 버튼을 두지 않는다(사용자 요청). 그래서 [Dialog]를 쓰고([androidx.compose.material3.AlertDialog]가
 * 아니다) **전면 탭 · 바깥 탭 · 뒤로 가기 셋이 모두 [onDismiss]** 로 간다. 출석 Claim 팝업이
 * "어떻게 닫든 같은 경로"를 쓰는 것과 같은 이유다 — 닫는 방법에 따라 결과가 달라지면 숨은
 * 분기가 생긴다.
 *
 * ⚠️ **글자가 든 상자에 고정 `dp` 높이를 주지 않는다**(함정 9번). 이 팝업은 캐러셀이 아니므로
 * `heightIn(min = …)` 처방이 그대로 통한다 — 배율이 커지면 상자가 함께 자란다.
 */
@Composable
internal fun BotCharacterAcquiredDialog(
    character: BotCharacter,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val reveal = shardRevealOf(character, acquiredShards = 0)
    // 조각 경로면 0 → 필요 수로 한 번 쓸어 준다. 출석 해금은 잴 것이 없어 곧바로 완성이다.
    val target = reveal?.required?.toFloat() ?: 0f
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = RevealDurationMillis, easing = LinearEasing),
        label = "botAcquiredReveal",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                // ⚠️ 표면 전체가 탭 대상이다. 물결 효과는 끈다 — 버튼이 아니라 "아무 곳이나"라서,
                // 특정 지점이 눌린 것처럼 보이면 오히려 버튼을 찾게 된다.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BotCharacterAvatar(
                    character = character,
                    size = AvatarSize,
                    // 스윕이 끝나야 컬러로 확정된다 — 그 전까지는 부채꼴만 컬러다.
                    available = reveal == null || sweep >= target,
                    reveal = reveal?.copy(acquired = sweep.toInt()),
                )
                Text(
                    text = strings.botAcquiredTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = strings.botCharacterLabel(character),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = strings.botCharacterDescription(character),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = DescriptionMinHeight),
                )
                Text(
                    text = strings.botAcquiredDismissHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val RevealDurationMillis = 900
private val AvatarSize = 112.dp

/**
 * 설명 줄의 **바닥값**. 캐릭터마다 설명 길이가 달라 팝업이 연달아 뜰 때(밀린 회차가 캐릭터 둘을
 * 한 번에 주는 경우) 높이가 출렁이는 것을 막는다.
 *
 * ⚠️ 고정 높이가 아니라 `heightIn(min = …)`이다 — 고정하면 배율 1.3배에서 아랫줄이 잘린다(함정 9번).
 */
private val DescriptionMinHeight = 40.dp
