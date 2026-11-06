package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 종이 질감 바탕(백로그 #303) — 미색 바탕에 아주 옅은 점과 짧은 섬유 결을 흩뿌린다. 그림 파일 없이 그려 밝은 · 어두운 구성 모두 같은 코드다(색은 [PaperPalette]).
 *
 * 결은 **늘 같은 자리**다 — 씨앗([seed])이 고정이라 다시 그려도 출렁이지 않고, 크기가 바뀔 때만 다시 계산한다(`drawWithCache`).
 * 개수는 넓이에 비례하되 상한이 있어(큰 화면에서도 수백 개) 그리는 값이 작다.
 * 튀지 않게 하는 것이 목적이다(사용자: *"현재 ux에서 너무 튀지는 않게"*) — 결의 진하기는 [PaperPalette.Grain]의 알파 하나로 조인다.
 */
internal fun Modifier.paperTexture(base: Color, grain: Color, seed: Int = PaperSeed): Modifier =
    drawWithCache {
        val random = Random(seed)
        val area = size.width * size.height
        val speckRadius = 0.7.dp.toPx()
        val fiberLength = 5.dp.toPx()
        val fiberStroke = 0.6.dp.toPx()
        val speckCount = (area / (14.dp.toPx() * 14.dp.toPx())).toInt().coerceIn(24, 600)
        val specks = List(speckCount) {
            Triple(Offset(random.nextFloat() * size.width, random.nextFloat() * size.height), 0.5f + random.nextFloat(), 0.35f + random.nextFloat() * 0.65f)
        }
        val fibers = List(speckCount / 5) {
            val start = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            // 종이 섬유는 대체로 가로로 눕는다 — ±25°.
            val angle = (random.nextFloat() - 0.5f) * 0.9f
            val length = fiberLength * (0.5f + random.nextFloat())
            Triple(start, start + Offset(cos(angle) * length, sin(angle) * length), 0.4f + random.nextFloat() * 0.6f)
        }
        onDrawBehind {
            drawRect(base)
            specks.forEach { (center, scale, strength) ->
                drawCircle(grain.copy(alpha = grain.alpha * strength), radius = speckRadius * scale, center = center)
            }
            fibers.forEach { (start, end, strength) ->
                drawLine(grain.copy(alpha = grain.alpha * strength), start, end, strokeWidth = fiberStroke)
            }
        }
    }

private const val PaperSeed = 303
