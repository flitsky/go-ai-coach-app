package com.worksoc.goaicoach.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 스플래시 후보 9종(백로그 #126). **말로 조율하는 대신 다 그려 놓고 눈으로 고르기 위한 것**이다 —
 * 개발자 모드 1차 섹션의 `1`~`9` 버튼이 각각을 전체 화면으로 재생한다.
 *
 * ## ⚠️ 이것은 임시 도구다
 * 하나가 정해지면 [AppSplash]의 기본값으로 박고, **이 목록과 미리보기를 남길지 지울지 판단해야
 * 한다** — 개발자 섹션은 `release` 빌드에도 그대로 실리기 때문이다(함정 11번). 남기는 쪽을
 * 택하더라도 그것은 **결정**이어야지 잊어서 남는 것이면 안 된다.
 *
 * ## 번호가 곧 이름이다
 * ⚠️ 후보 이름 9개를 4개 언어에 넣으면 `UiStrings`에 36줄이 늘어난다. **고르고 나면 역할이 끝나는
 * 화면**에 그 값을 치르지 않기로 했다 — 버튼은 숫자만 쓰고, 무엇이 무엇인지는 아래 KDoc이 든다.
 *
 * ## 길이는 후보마다 다르다
 * 연출이 요구하는 시간이 다르기 때문이다. ⚠️ **[RadialStepBurst]의 3.5초는 사용자가 "그린 것을 다
 * 볼 수 있게" 일부러 길게 잡은 값이고 최종값이 아니다** — 고른 뒤 1초대로 줄이는 것이 예정돼 있다.
 */
internal enum class SplashVariant(val number: Int, val durationMillis: Int) {

    /**
     * **①방사형 스텝 확대 — 사용자가 직접 설계한 안**(2026-09-06).
     *
     * 캐릭터 하나가 **10%에서 시작해 `0.03초마다 5%씩`** 커져 100%가 되고(18스텝 = 0.54초),
     * 그 뒤 **계속 더 커지면서 같은 틱마다 알파를 10%씩** 깎아 사라진다(10스텝 = 0.30초).
     * 5장이 **0.5초 간격**으로 차례로 시작하며 자리는 **9시 → 12시 → 3시 → 6시(시계방향), 마지막은
     * 정가운데**다.
     *
     * ⚠️ **스텝은 일부러 이산적이다.** *"0.03초마다 5%씩"* 이 스펙이므로 부드럽게 보간하면
     * 의도한 질감이 사라진다. [stepIndex]가 그 이산화를 맡는다.
     */
    RadialStepBurst(1, 3500),

    /** **②부채 셔플** — 백로그 #125에서 만든 것. 5장이 카드처럼 펼쳐졌다 한 장으로 모인다. */
    CardFan(2, 1200),

    /**
     * **③궤도 확산 — [Orbit]을 거꾸로 돌린 것**(2026-09-06 사용자 지시).
     *
     * 가운데 겹쳐 있던 5장이 **바깥으로 퍼지며** 고리를 이룬다. ⑥이 모이는 연출이라면 이쪽은
     * 피어나는 연출이고, **속도는 ⑥의 1.5배**다(⑥의 2.0초짜리 움직임을 [OrbitBloomAnimMillis]
     * 안에 끝낸다).
     *
     * ⚠️ **끝난 뒤에는 마지막 프레임에서 멈춰 있는다** — 남은 시간을 빈 화면으로 보내지 않는다.
     * 그 정지가 이 후보의 절반이므로 [OrbitBloomAnimMillis]보다 **길이를 넉넉히 잡아야** 뜻이 산다.
     */
    OrbitBloom(3, 2000),

    /**
     * **④궤도 확산 → 줌 소멸**(2026-09-06 사용자 지시. **지금 앱이 쓰는 것**).
     *
     * 앞 1.5초는 ③과 같다 — 가운데서 피어나 고리를 이루고 멈춘다. 그다음 **그 자리 그대로에서**
     * ⑦처럼 한 장씩 빠르게 커지며, 더 커질 때 알파가 빠져 사라진다.
     *
     * ⚠️ **⑦과 다른 것은 시작 위치 하나뿐이다** — ⑦은 화면 중앙에서 시작하지만 이쪽은
     * **③이 마지막에 그려 둔 고리 위 제자리**에서 시작한다.
     * ⚠️ **원판(coin)을 쓰지 않는다.** 원판을 4배로 키우면 흰 원이 화면을 덮어 **다섯 번의 흰
     * 섬광**이 된다. 참조로 삼은 ⑦도 그림만 쓴다.
     */
    OrbitBloomBurst(4, 3500),

    /** **⑤순차 상승 페이드** — 아래에서 하나씩 떠오른다. Material의 stagger enter. */
    StaggerRise(5, 1500),

    /** **⑥궤도 수렴** — 원 궤도를 돌며 점점 안쪽으로 모여 한 점이 된다. */
    Orbit(6, 2000),

    /** **⑦줌 스루** — 한 장씩 화면을 가득 채우며 앞으로 지나간다. iOS식 전환의 느낌. */
    ZoomThrough(7, 2000),

    /** **⑧카드 플립** — Y축으로 뒤집히며 차례로 나타난다. */
    CardFlip(8, 1800),

    /** **⑨파도 스케일** — 가로 일렬 5장이 파도처럼 순차로 부풀었다 가라앉는다. */
    Wave(9, 1600),
    ;

    companion object {
        /**
         * 지금 앱이 실제로 쓰는 후보(2026-09-06 사용자 선택). ⚠️ **최종 선택이 아니다** —
         * 고르고 나면 **1초대로 줄이는 조정이 예정돼 있다**(백로그 #126).
         */
        val Current: SplashVariant = OrbitBloomBurst

        fun ofNumber(number: Int): SplashVariant? = entries.firstOrNull { it.number == number }
    }
}

/**
 * 후보 하나를 그린다. [elapsedMillis]는 재생 시작으로부터의 경과이고, 각 후보가 자기 방식으로
 * 해석한다(전체 진행도로 쓰기도 하고, [SplashVariant.RadialStepBurst]처럼 **절대 시간**을 그대로
 * 쓰기도 한다 — 그쪽 스펙이 `0.03초`·`0.5초` 같은 실제 시간으로 적혀 있기 때문이다).
 */
@Composable
internal fun SplashScene(
    variant: SplashVariant,
    elapsedMillis: Int,
    avatars: List<Int> = AllBotAvatarRes,
    modifier: Modifier = Modifier,
) {
    val t = (elapsedMillis.toFloat() / variant.durationMillis).coerceIn(0f, 1f)
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // ⚠️ **반지름을 가로에만 매지 말 것** — 세로가 짧은 기기(가로 모드·폴더블 펼침)에서 위아래
        // 캐릭터가 화면 밖으로 나간다.
        val radius = minOf(w, h) * 0.26f
        avatars.forEachIndexed { index, res ->
            val f = transformFor(variant, index, avatars.size, t, elapsedMillis, radius, w, h)
            if (f.alpha > 0.001f) {
                SplashCoin(
                    res = res,
                    size = f.size,
                    coin = variant.usesCoin,
                    modifier = Modifier.graphicsLayer {
                        translationX = f.translationX
                        translationY = f.translationY
                        scaleX = f.scale
                        scaleY = f.scale
                        alpha = f.alpha
                        rotationZ = f.rotationZ
                        rotationY = f.rotationY
                        cameraDistance = 12f * density
                    },
                )
            }
        }
    }
}

/** 카드처럼 원판에 얹을 것인가, 그림만 쓸 것인가. 연출의 성격이 갈리는 자리다. */
private val SplashVariant.usesCoin: Boolean
    get() = when (this) {
        SplashVariant.RadialStepBurst, SplashVariant.ZoomThrough, SplashVariant.OrbitBloomBurst -> false
        else -> true
    }

@Composable
private fun SplashCoin(res: Int, size: Dp, coin: Boolean, modifier: Modifier) {
    val base = modifier.size(size)
    if (coin) {
        Box(
            modifier = base
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(AppLightColorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    } else {
        Image(painterResource(res), null, base, contentScale = ContentScale.Fit)
    }
}

private data class CoinFrame(
    val size: Dp,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val rotationZ: Float = 0f,
    val rotationY: Float = 0f,
)

private val CoinSize = 96.dp
private val BigSize = 168.dp

/**
 * `0.03초마다 한 스텝`의 이산화. ⚠️ **반올림이 아니라 내림이다** — 스텝이 실제로 계단처럼
 * 보여야 [SplashVariant.RadialStepBurst]의 스펙과 같다.
 */
private fun stepIndex(elapsedMillis: Int, startMillis: Int, tickMillis: Int = 30): Int =
    floor((elapsedMillis - startMillis).coerceAtLeast(0).toFloat() / tickMillis).toInt()

private fun easeInOut(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun transformFor(
    variant: SplashVariant,
    index: Int,
    count: Int,
    t: Float,
    elapsedMillis: Int,
    radius: Float,
    width: Float,
    height: Float,
): CoinFrame {
    val fromCenter = index - (count - 1) / 2f
    return when (variant) {
        SplashVariant.RadialStepBurst -> radialStepBurst(index, elapsedMillis, radius)
        SplashVariant.CardFan -> cardFan(fromCenter, index, t, width)
        SplashVariant.OrbitBloom -> orbitBloom(index, count, t, radius)
        SplashVariant.OrbitBloomBurst -> orbitBloomBurst(index, count, t, elapsedMillis, bloomRadius(radius, width))
        SplashVariant.StaggerRise -> staggerRise(fromCenter, index, t, height, width, count)
        SplashVariant.Orbit -> orbit(index, count, t, radius)
        SplashVariant.ZoomThrough -> zoomThrough(index, count, t)
        SplashVariant.CardFlip -> cardFlip(fromCenter, index, t, width, count)
        SplashVariant.Wave -> wave(fromCenter, index, t, width, count)
    }
}

// ── ① 방사형 스텝 확대 (사용자 안) ─────────────────────────────────────────────
//
// 자리: 1번 9시, 2번 12시, 3번 3시, 4번 6시(시계방향), 5번 정가운데.
private val RadialDirections = listOf(
    -1f to 0f,   // 9시
    0f to -1f,   // 12시
    1f to 0f,    // 3시
    0f to 1f,    // 6시
    0f to 0f,    // 정가운데
)

private const val RadialStartStaggerMillis = 500
private const val RadialTickMillis = 30
private const val RadialScaleStart = 0.10f
private const val RadialScaleStep = 0.05f
private const val RadialGrowSteps = 18   // 10% → 100%
private const val RadialFadeSteps = 10   // 알파 100% → 0%, 10%씩

private fun radialStepBurst(index: Int, elapsedMillis: Int, radius: Float): CoinFrame {
    val start = index * RadialStartStaggerMillis
    val step = stepIndex(elapsedMillis, start, RadialTickMillis)
    if (step < 0) return CoinFrame(size = BigSize, alpha = 0f)
    val scale = RadialScaleStart + RadialScaleStep * step
    // 커지는 구간이 끝나면 **계속 커지면서** 알파가 틱마다 10%씩 빠진다.
    val fadeStep = (step - RadialGrowSteps).coerceAtLeast(0)
    val alpha = (1f - 0.10f * fadeStep).coerceIn(0f, 1f)
    if (fadeStep > RadialFadeSteps) return CoinFrame(size = BigSize, alpha = 0f)
    val (dx, dy) = RadialDirections[index % RadialDirections.size]
    return CoinFrame(
        size = BigSize,
        translationX = dx * radius,
        translationY = dy * radius,
        scale = scale,
        alpha = alpha,
    )
}

// ── ② 부채 셔플 (#125) ────────────────────────────────────────────────────────
private fun cardFan(fromCenter: Float, index: Int, t: Float, width: Float): CoinFrame {
    val start = 0.05f + index * 0.03f
    val rise = easeInOut(((t - start) / 0.26f).coerceIn(0f, 1f))
    val fall = easeInOut(((t - 0.58f) / 0.22f).coerceIn(0f, 1f))
    val spread = rise * (1f - fall)
    val settle = easeInOut(((t - 0.72f) / 0.14f).coerceIn(0f, 1f))
    val spacing = minOf(width * 0.16f, width * 0.22f)
    return CoinFrame(
        size = CoinSize,
        translationX = fromCenter * spacing * spread,
        translationY = -abs(fromCenter) * width * 0.026f * spread,
        scale = 0.84f + 0.16f * settle,
        alpha = splashEnvelope(t),
        rotationZ = fromCenter * 27f * spread,
    )
}

// ── ⑤ 순차 상승 페이드 ────────────────────────────────────────────────────────
private fun staggerRise(fromCenter: Float, index: Int, t: Float, height: Float, width: Float, count: Int): CoinFrame {
    val start = index * 0.10f
    val p = easeInOut(((t - start) / 0.34f).coerceIn(0f, 1f))
    return CoinFrame(
        size = CoinSize,
        translationX = fromCenter * spacingFor(width, count),
        translationY = (1f - p) * height * 0.10f,
        scale = 0.90f + 0.10f * p,
        alpha = p * splashEnvelope(t),
    )
}

// ── ⑥ 궤도 수렴 / ③ 궤도 확산 / ④ 궤도 확산 → 줌 소멸 ───────────────────────
//
// 셋이 **같은 기하**를 공유한다. `u`가 0이면 활짝 펼쳐진 고리, 1이면 가운데로 모인 상태다.
// ⑥은 `u`를 0→1로(모임), ③·④는 1→0으로(피어남) 흘려보낸다.
private const val OrbitTurns = 1.15f
private val Tau = 2f * PI.toFloat()

private fun orbitAt(index: Int, count: Int, u: Float, radius: Float): CoinFrame {
    val angle = (index.toFloat() / count) * Tau + easeInOut(u) * OrbitTurns * Tau
    val spread = 1f - easeInOut(((u - 0.35f) / 0.5f).coerceIn(0f, 1f))
    return CoinFrame(
        size = CoinSize,
        translationX = cos(angle) * radius * spread,
        translationY = sin(angle) * radius * spread,
        scale = 0.72f + 0.28f * spread,
    )
}

private fun orbit(index: Int, count: Int, t: Float, radius: Float): CoinFrame =
    orbitAt(index, count, t, radius).copy(alpha = splashEnvelope(t))

/**
 * ③ — ⑥의 **1.5배 속도**로 거꾸로 돌린다. ⑥의 2.0초짜리 움직임이 [OrbitBloomAnimMillis] 안에
 * 끝나고, 그 뒤로는 `u`가 0에 붙어 **마지막 프레임에서 멈춰 있는다.**
 */
private const val OrbitBloomAnimMillis = 1333   // = 2000 / 1.5

private fun orbitBloom(index: Int, count: Int, t: Float, radius: Float): CoinFrame {
    val played = (t * SplashVariant.OrbitBloom.durationMillis / OrbitBloomAnimMillis).coerceAtMost(1f)
    return orbitAt(index, count, 1f - played, radius).copy(alpha = splashEnvelope(t))
}

/**
 * ④ — 앞 [BloomHoldUntilMillis]까지는 ③ 그대로(피어나고 멈춤), 그 뒤로 **각자 제자리에서**
 * ⑦처럼 한 장씩 커지며 사라진다.
 *
 * ⚠️ **⑦과 다른 것은 시작 위치 하나뿐**이므로 확대·소멸 곡선은 [zoomThrough]와 같은 모양으로
 * 둔다. 여기서 곡선을 따로 손보면 *"⑦ 그대로"* 라는 전제가 조용히 깨진다.
 */
private const val BloomHoldUntilMillis = 1500
private const val BurstSlotMillis = 400

private fun orbitBloomBurst(index: Int, count: Int, t: Float, elapsedMillis: Int, radius: Float): CoinFrame {
    val played = (elapsedMillis.toFloat() / OrbitBloomAnimMillis).coerceAtMost(1f)
    val ring = orbitAt(index, count, 1f - played, radius).copy(size = BurstSize)
    val burstStart = BloomHoldUntilMillis + index * BurstSlotMillis
    if (elapsedMillis < burstStart) return ring.copy(alpha = splashEnvelope(t))
    val p = ((elapsedMillis - burstStart).toFloat() / BurstSlotMillis).coerceIn(0f, 1f)
    return ring.copy(
        scale = ring.scale * (1f + p * p * BurstScaleGain),
        alpha = if (p < BurstFadeStart) 1f else ((1f - (p - BurstFadeStart) / (1f - BurstFadeStart))).coerceIn(0f, 1f),
    )
}

private val BurstSize = 132.dp

/**
 * ④의 고리는 ⑥보다 **넓어야 한다.** 기본 반지름은 96dp 원판을 전제로 잡힌 값인데 ④는 [BurstSize]
 * 짜리 그림을 늘어놓으므로, 그대로 쓰면 다섯이 서로 심하게 겹쳐 고리로 읽히지 않는다.
 *
 * ⚠️ **넓히되 화면 밖으로 내보내지 말 것** — 그림 반쪽과 여백을 뺀 값으로 상한을 둔다.
 * 그림 폭을 화면 폭의 비율로 어림하는 이유는 이 함수에 밀도가 없기 때문이고, 어림이어도
 * **상한의 방향이 안전한 쪽**이라 문제가 되지 않는다.
 */
private fun bloomRadius(radius: Float, width: Float): Float {
    val figure = width * 0.32f
    return minOf(radius * 1.35f, width / 2f - figure / 2f - width * 0.03f).coerceAtLeast(radius)
}
private const val BurstScaleGain = 3.4f
private const val BurstFadeStart = 0.55f

// ── ⑦ 줌 스루 ─────────────────────────────────────────────────────────────────
private fun zoomThrough(index: Int, count: Int, t: Float): CoinFrame {
    val slot = 1f / count
    val p = ((t - index * slot) / slot).coerceIn(0f, 1f)
    if (p <= 0f || p >= 1f) return CoinFrame(size = BigSize, alpha = 0f)
    return CoinFrame(
        size = BigSize,
        scale = 0.35f + p * p * 2.6f,
        // 앞으로 다가오다 마지막 30%에서 스쳐 지나간다.
        alpha = if (p < 0.7f) (p / 0.35f).coerceAtMost(1f) else (1f - (p - 0.7f) / 0.3f),
    )
}

// ── ⑧ 카드 플립 ───────────────────────────────────────────────────────────────
private fun cardFlip(fromCenter: Float, index: Int, t: Float, width: Float, count: Int): CoinFrame {
    val start = index * 0.11f
    val p = easeInOut(((t - start) / 0.36f).coerceIn(0f, 1f))
    return CoinFrame(
        size = CoinSize,
        translationX = fromCenter * spacingFor(width, count),
        scale = 0.86f + 0.14f * p,
        // 90°를 지나기 전에는 뒷면이라 보이지 않는 것이 자연스럽다.
        alpha = if (p < 0.5f) 0f else splashEnvelope(t),
        rotationY = -180f * (1f - p),
    )
}

// ── ⑨ 파도 스케일 ─────────────────────────────────────────────────────────────
private fun wave(fromCenter: Float, index: Int, t: Float, width: Float, count: Int): CoinFrame {
    val phase = (t * 2f - index * 0.14f).coerceAtLeast(0f)
    val bump = if (phase >= 1f) 0f else sin(phase * PI.toFloat()) * 0.30f
    return CoinFrame(
        size = CoinSize,
        translationX = fromCenter * spacingFor(width, count),
        translationY = -bump * width * 0.10f,
        scale = 0.90f + bump,
        alpha = splashEnvelope(t),
    )
}

/**
 * ⚠️ **가로 일렬 배치의 간격은 화면 폭에서 계산한다.** 고정 dp를 쓰면 좁은 기기에서 양끝이
 * 잘리는데, 잘린 카드는 "고장"으로 읽히고 **에뮬레이터 한 대만 보면 드러나지 않는다**(함정 21).
 */
private fun spacingFor(width: Float, count: Int): Float {
    val gaps = (count - 1).coerceAtLeast(1)
    return (width * 0.86f - width * 0.22f) / gaps
}

/** 모든 후보가 공유하는 들어오고 나가는 봉투. 각 후보가 자기 페이드를 또 만들지 않게 한다. */
private fun splashEnvelope(t: Float): Float = when {
    t < 0.06f -> t / 0.06f
    t > 0.90f -> 1f - (t - 0.90f) / 0.10f
    else -> 1f
}
