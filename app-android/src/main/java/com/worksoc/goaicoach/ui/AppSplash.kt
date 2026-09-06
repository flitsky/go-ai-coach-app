package com.worksoc.goaicoach.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * 4계층(External Integration) — 앱 기동 직후 **1초 동안** 캐릭터 5장이 부채꼴로 펼쳐졌다
 * 다시 모이는 브랜드 모먼트(백로그 #125).
 *
 * ## ⚠️ 이것은 #123의 대책이 아니다
 * 크래시가 나던 자리는 *무엇을 그리는가* 가 아니라 `installDecor`→`generateLayout`,
 * 즉 **창의 decor를 세우는 순간**이었다. 스플래시를 넣어도 그 경로는 똑같이 지난다.
 * 안정성을 준 것은 [MainActivity]의 **호출 순서**다(백로그 함정 35번). 두 가지를 한 덩어리로
 * 읽지 말 것.
 *
 * ## ⚠️ 이 1초로 엔진이 준비되지는 않는다 — 로비 게이트를 지울 근거가 아니다
 * 최초 실행은 약 100MB 모델 압축 해제가 돌아 **몇 초**가 걸린다. 그러므로
 * `GameSetupLobby`의 엔진 준비 게이트(`engineNotReadyToStart`, 백로그 #101 0단계)는
 * 스플래시가 생겨도 **그대로 남아야 한다.** 이 1초가 주는 것은 *"준비 완료"* 가 아니라
 * **1초만큼의 선행**이고, 그 값은 모델이 이미 풀린 **두 번째 실행부터** 크다.
 *
 * ## 왜 별도 Activity도, `core-splashscreen`도 아닌가
 * - ⚠️ **별도 `SplashActivity`는 창을 하나 더 세운다** — #123이 죽던 그 경로를 한 번 더 지난다.
 * - **시스템 스플래시(API 31+)는 아이콘 하나(정적 drawable 또는 AVD)만 그린다.** 그림 5장을
 *   거기서 표현할 방법이 없으므로 `androidx.core:core-splashscreen`을 넣어도 얻는 것은
 *   이음매 처리뿐인데, 그 이음매는 테마의 `android:windowBackground`를 이 화면의 배경과
 *   같은 색으로 맞추면 사라진다(`styles.xml` 참고). **그래서 새 의존성이 없다.**
 *
 * ## ⚠️ 글자를 넣지 않는다
 * 앱 이름을 여기 적는 순간 **4개 언어 × 함정 26번의 "그물 밖 자리"** 가 하나 더 생긴다.
 * 브랜드는 시스템 스플래시의 런처 아이콘이 이미 말하고, 여기는 그림만 말한다.
 *
 * ⚠️ **화면 회전으로 다시 재생되면 안 된다** — [rememberSaveable]로 끝난 상태를 들고 간다.
 * 프로세스가 죽고 새로 뜨면 그때는 다시 재생되는 것이 맞다.
 */
@Composable
internal fun AppSplash(durationMillis: Int = SplashDurationMillis) {
    var finished by rememberSaveable { mutableStateOf(false) }
    if (finished) return

    val progress = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        finished = true
    }

    val t = progress.value
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .graphicsLayer { alpha = splashAlpha(t) }
            .background(AppLightColorScheme.background)
            // ⚠️ **터치를 반드시 먹어야 한다** — 아래 홈은 이미 컴포즈돼 있어서, 막지 않으면
            //    스플래시를 보는 1초 동안 누른 것이 홈으로 새어 들어간다.
            .pointerInput(Unit) { detectTapGestures { finished = true } },
        contentAlignment = Alignment.Center,
    ) {
        // ⚠️ **부채 폭을 화면 폭에 맞춰 조인다.** 5장이 전부 펼쳐지려면 `4 × 간격 + 카드`가 필요한데
        // (기본값으로 344dp) 좁은 기기에서는 양끝이 잘린다. 잘린 카드는 "고장"으로 읽히고,
        // **에뮬레이터 한 대만 보면 절대 드러나지 않는다**(함정 21과 같은 부류 — 폭이 모자란 것을
        // 배치로 감추지 말고 애초에 들어가게 만든다).
        val gaps = (AllBotAvatarRes.size - 1).coerceAtLeast(1)
        val spacing = minOf(FanSpacing, ((maxWidth - CardSize - FanEdgeMargin * 2) / gaps).coerceAtLeast(0.dp))
        AllBotAvatarRes.forEachIndexed { index, avatar ->
            val spread = splashSpread(index, t)
            val settle = splashSettle(t)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val fromCenter = index - (AllBotAvatarRes.size - 1) / 2f
                        translationX = fromCenter * spacing.toPx() * spread
                        translationY = -abs(fromCenter) * FanLift.toPx() * spread
                        rotationZ = fromCenter * FanAngleDegrees * spread
                        val s = StackScale + (1f - StackScale) * settle
                        scaleX = s
                        scaleY = s
                    }
                    .size(CardSize)
                    .shadow(CardElevation, CircleShape)
                    .clip(CircleShape)
                    .background(AppLightColorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 1초. ⚠️ 페이드아웃까지 **포함한** 총 길이다 — 여기에 뭔가를 더 얹지 말 것. */
internal const val SplashDurationMillis: Int = 1000

private val CardSize = 96.dp
private val CardElevation = 6.dp
private val FanSpacing = 62.dp
private val FanEdgeMargin = 12.dp
private val FanLift = 10.dp
private const val FanAngleDegrees = 27f
private const val StackScale = 0.84f

// 구간(진행도 0~1 기준). ⚠️ 숫자를 만질 때는 **겹침을 유지할 것** — 구간 사이에 틈이 생기면
// 카드가 한 프레임 멈춰 "끊긴 것"으로 보인다.
//
// ⚠️ **실기로 한 번 조정했다**(2026-09-06). 첫 값은 ⓐ 부채가 너무 좁아 5장이 거의 겹쳐 보였고
// (`FanSpacing` 52→62dp, 각도 24→27°), ⓑ 모인 뒤 **200ms 가까이 정지한 채로 남았다**
// (collapse 종료 0.78 → 0.80, 페이드아웃 0.88 → 0.86으로 당겨 죽는 구간을 없앴다).
// **프레임을 뽑아 보기 전에는 둘 다 안 보였다** — 숫자만 읽어서는 판단할 수 없는 종류다.
private const val FadeInEnd = 0.07f
private const val FanStartBase = 0.05f
private const val FanStagger = 0.03f
private const val FanRise = 0.26f
private const val FanCollapseStart = 0.58f
private const val FanCollapseSpan = 0.22f
private const val SettleStart = 0.72f
private const val SettleSpan = 0.14f
private const val FadeOutStart = 0.86f

/** 들어올 때는 빠르게, 나갈 때는 마지막 12%에서. */
private fun splashAlpha(t: Float): Float = when {
    t < FadeInEnd -> t / FadeInEnd
    t > FadeOutStart -> 1f - (t - FadeOutStart) / (1f - FadeOutStart)
    else -> 1f
}

/** 0이면 가운데 스택, 1이면 완전히 펼쳐진 부채꼴. 카드마다 [FanStagger]만큼 늦게 출발한다. */
private fun splashSpread(index: Int, t: Float): Float {
    val start = FanStartBase + index * FanStagger
    val rise = FastOutSlowInEasing.transform(((t - start) / FanRise).coerceIn(0f, 1f))
    val fall = FastOutSlowInEasing.transform(((t - FanCollapseStart) / FanCollapseSpan).coerceIn(0f, 1f))
    return rise * (1f - fall)
}

/** 다시 모인 뒤 스택이 제 크기로 커지는 마무리. */
private fun splashSettle(t: Float): Float =
    FastOutSlowInEasing.transform(((t - SettleStart) / SettleSpan).coerceIn(0f, 1f))
