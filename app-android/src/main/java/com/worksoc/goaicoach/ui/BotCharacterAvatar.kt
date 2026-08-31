package com.worksoc.goaicoach.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.R
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4계층(External Integration) — 공용 계층의 [BotCharacter.avatarRef] 문자열을 Android 그림
 * 리소스로 옮기는 유일한 지점(백로그 #48).
 *
 * ⚠️ **`Resources.getIdentifier`를 쓰지 않는다.** 그건 이름을 실행 중에 찾는 방식이라
 * R8이 리소스를 지웠는지 알 수 없고, `playInternal`에도 R8이 켜져 있는 지금(#47)은 조용히
 * 깨지는 길이다. 아래처럼 **명시적으로 적어 두면** 리소스가 참조된 것으로 잡혀 안전하고,
 * 캐릭터를 추가했는데 그림을 빠뜨리면 `BotCharacterAvatarTest`가 잡는다.
 *
 * 모르는 참조는 `null`이다 — 그림이 아직 없는 캐릭터도 이름·설명만으로 정상 동작해야 한다.
 */
@DrawableRes
internal fun botAvatarRes(character: BotCharacter): Int? =
    when (character.avatarRef) {
        "bot_fast_beginner_1" -> R.drawable.bot_fast_beginner_1
        "bot_fast_beginner_2" -> R.drawable.bot_fast_beginner_2
        "bot_fast_beginner_3" -> R.drawable.bot_fast_beginner_3
        "bot_fast_beginner_4" -> R.drawable.bot_fast_beginner_4
        "bot_fast_beginner_5" -> R.drawable.bot_fast_beginner_5
        else -> null
    }

/**
 * 조각 진행도(백로그 #50). [required]조각 중 [acquired]조각을 모았다는 뜻이고, 그만큼이
 * 12시부터 **시계방향으로** 원래 색을 되찾는다.
 *
 * ⚠️ **조각 경로 캐릭터에만 있다.** 5종 중 광고 조각으로 열리는 2·4단계만 해당하고,
 * 출석으로 열리는 3·5단계는 부분 진행률이라는 것이 아예 없어 `null`이다(아래 주석 참고).
 */
internal data class ShardReveal(val acquired: Int, val required: Int)

/**
 * 이 캐릭터에 부분 공개가 있는가. **조각 경로 캐릭터만 있다** — 출석 해금(3·5단계)은 부분
 * 진행이라는 개념이 없어 `null`이고, [BotCharacterAvatar]가 그때는 통째로 흑백을 그린다.
 *
 * 픽커(#49)와 출석 도장판(#57)이 같이 쓴다. 두 곳이 각자 `as? BotUnlockSource.AdShards`를
 * 적어 두면 조각 경로 판정이 갈라질 수 있어 한 군데로 모았다.
 */
internal fun shardRevealOf(character: BotCharacter, acquiredShards: Int): ShardReveal? =
    (character.unlockSource as? BotUnlockSource.AdShards)
        ?.let { source -> ShardReveal(acquired = acquiredShards, required = source.required) }

/**
 * [acquired]/[required]에 해당하는 부채꼴 각도. **순수 함수라 단위 테스트로 고정한다** —
 * 이 항목에서 실제로 틀리기 쉬운 곳은 그림이 아니라 이 계산이다.
 *
 * 방어적으로 잘라낸다: 조각이 요구치를 넘거나(보상이 중복 지급되는 등) [required]가 0 이하인
 * 값이 흘러들어도 각도가 한 바퀴를 넘거나 0으로 나누는 일이 없어야 한다.
 */
internal fun shardSweepDegrees(acquired: Int, required: Int): Float {
    if (required <= 0 || acquired <= 0) return 0f
    if (acquired >= required) return 360f
    return 360f * acquired / required
}

/**
 * 캐릭터 아바타 한 장. 그림이 없으면 같은 크기의 빈 원판을 그려 **레이아웃이 흔들리지 않게**
 * 한다 — 캐러셀(#49)에서 한 칸만 높이가 달라지는 것을 막기 위함이다.
 *
 * 세 가지 상태를 그린다:
 * - [available] → 원래 색 그대로.
 * - 잠겼고 [reveal]이 `null` → 통째로 흑백(출석 해금 캐릭터. 부분 진행이라는 개념이 없다).
 * - 잠겼고 [reveal]이 있음 → 흑백 위에 모은 조각만큼 **12시부터 시계방향으로** 원본이 드러나고,
 *   조각 경계마다 카드 바탕색 선을 그어 **쪼개진 티가 나게** 한다(#50).
 *   · ⚠️ 경계선이 이 연출의 숨은 핵심이다. 선이 없으면 **0조각인 카드와 출석 잠금 카드가
 *     똑같이 통짜 흑백으로 보여** 구분이 안 된다. 선이 있으면 0조각이어도 "다섯 쪽짜리인데
 *     아직 하나도 없다"가 읽힌다.
 *
 * ⚠️ 흐림(`Modifier.blur`)은 쓰지 않았다 — API 31+ 전용이라 `minSdk`가 26인 이 앱에서는
 * 26~30 기기에서 **아무 일도 일어나지 않는다**(조용히 무시된다). 흑백만으로 구분한다.
 *
 * [seamColor]는 이 아바타가 얹히는 바탕색이다. 기본값은 픽커 카드의 `surface`이고, 다른 색
 * 위에 놓는 호출부는 자기 배경을 넘겨야 이음선이 배경과 어긋나지 않는다(#57에서 드러났다).
 */
@Composable
internal fun BotCharacterAvatar(
    character: BotCharacter,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    available: Boolean = true,
    reveal: ShardReveal? = null,
    seamColor: Color = MaterialTheme.colorScheme.surface,
) {
    val res = botAvatarRes(character)
    // ⚠️ 조각 경계선은 **아바타가 놓인 바탕색**으로 그어야 한다 — 선이 아니라 틈으로 보여야
    // "조각 났다"가 된다. 기본값이 `surface`인 것은 픽커 카드가 그 색이기 때문이고, 다른 색
    // 위에 얹는 곳(출석 도장판의 칸, #57)은 자기 배경색을 넘겨야 흰 거미줄이 되지 않는다.
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (res == null) {
            Box(
                Modifier
                    .size(size)
                    .background(placeholderColor),
            )
        } else {
            val painter = painterResource(res)
            Canvas(Modifier.size(size)) {
                if (available) {
                    drawAvatar(painter)
                    return@Canvas
                }
                drawAvatar(painter, GreyscaleFilter)
                if (reveal == null) return@Canvas
                val sweep = shardSweepDegrees(reveal.acquired, reveal.required)
                if (sweep > 0f) {
                    clipPath(shardSectorPath(sweep)) { drawAvatar(painter) }
                }
                drawShardSeams(reveal.required, seamColor)
            }
        }
    }
}

private fun DrawScope.drawAvatar(painter: Painter, colorFilter: ColorFilter? = null) {
    with(painter) { draw(size, colorFilter = colorFilter) }
}

/**
 * 12시에서 시작해 시계방향으로 [sweepDegrees]만큼 열린 부채꼴. 반지름을 넉넉히 잡는다 —
 * 바깥으로 삐져나온 부분은 호출부의 원형 클립이 잘라내므로, 모자라서 귀퉁이가 비는 것보다 낫다.
 */
private fun DrawScope.shardSectorPath(sweepDegrees: Float): Path {
    val center = this.center
    return Path().apply {
        moveTo(center.x, center.y)
        arcTo(
            rect = Rect(center = center, radius = size.maxDimension),
            startAngleDegrees = StartAngleDegrees,
            sweepAngleDegrees = sweepDegrees,
            forceMoveTo = false,
        )
        close()
    }
}

/** 조각 경계마다 바탕색 틈을 낸다. 한 조각짜리는 나눌 것이 없으므로 아무것도 그리지 않는다. */
private fun DrawScope.drawShardSeams(pieces: Int, color: Color) {
    if (pieces < 2) return
    val center = this.center
    val radius = size.minDimension / 2f
    repeat(pieces) { index ->
        val radians = (StartAngleDegrees + index * 360f / pieces) * PI.toFloat() / 180f
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + radius * cos(radians), center.y + radius * sin(radians)),
            strokeWidth = size.minDimension * SeamWidthRatio,
        )
    }
}

/** Compose 각도는 3시가 0도라, 12시에서 시작하려면 -90도다. */
private const val StartAngleDegrees = -90f

/** 아바타 지름 대비 틈 두께. 84dp에서 약 1.7dp로, 작게 줄여도 뭉개지지 않는 선. */
private const val SeamWidthRatio = 0.02f

/** 채도 0 행렬. 매번 만들면 리컴포지션마다 새 객체가 생기므로 한 번만 만들어 둔다. */
private val GreyscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
