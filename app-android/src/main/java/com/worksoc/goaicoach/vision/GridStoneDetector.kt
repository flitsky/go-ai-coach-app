package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

interface PixelSource {
    val width: Int
    val height: Int
    fun getPixel(x: Int, y: Int): Int
}

class ArrayPixelSource(
    override val width: Int,
    override val height: Int,
    private val pixels: IntArray,
) : PixelSource {
    override fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]

    /** 화면에 보일 ARGB 배열 — 원본 사진 밖([BoardWarp.NoData])은 중간 회색으로. */
    fun toArgbArray(): IntArray = IntArray(pixels.size) { i -> if (pixels[i] == BoardWarp.NoData) 0xFF808080.toInt() else pixels[i] }

    companion object {
        /** 비트맵 픽셀을 한 번에 복사한다 — `Bitmap.getPixel`을 픽셀마다 부르면 느리다. */
        fun fromBitmap(bitmap: Bitmap): ArrayPixelSource {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return ArrayPixelSource(bitmap.width, bitmap.height, pixels)
        }
    }
}

/**
 * 반듯하게 편 판([RectifiedBoard])에서 교점마다 흑·백·빈 점을 가른다(백로그 #210에서 다시 썼다).
 *
 * ## 옛 방식이 틀린 이유 — 판 전체에 임계 **하나**
 * 교점 원형 샘플의 평균 밝기를 판 전체의 최소·중앙·최대에서 뽑은 임계 하나와 고정 상한(95/160)으로 갈랐다.
 * 정답 모서리를 줘도 실물 사진 75%, 번호 붙은 기보 66%였다(백로그 #210 기준선) — 그림자·조명이 판 위에서
 * 고르지 않고, 돌 위 숫자가 평균을 끌고, 인쇄 기보의 **백돌은 테두리뿐이라 밝기가 판과 같다.**
 *
 * ## 지금 방식 — 교점마다 **바로 옆 바탕**과 비교하고, **격자선이 보이는지**를 본다
 * - **바탕 기준**: 교점을 둘러싼 네 칸의 **한가운데**. 칸 중심은 이웃 네 교점에서 0.707칸 떨어져 있어
 *   어떤 돌(반지름 ≈ 0.48칸)도 덮지 못한다 — 그래서 돌이 아무리 빽빽해도 항상 바탕이다. 조명·그림자는 그 자리 것으로 맞춰진다.
 * - **돌 몸통**: 교점 둘레의 고리(0.14~0.36칸)에서 **격자선 띠를 뺀** 픽셀의 **중앙값** — 숫자·마지막 수 표시가 일부를 덮어도 중앙값은 돌 색이다.
 * - **격자선 팔**: 교점에서 네 방향으로 뻗는 선이 0.22~0.42칸 구간에서 **보이는가**. 빈 점은 선이 보이고, 돌(흑·백)은 덮는다 —
 *   **테두리뿐인 인쇄 백돌**을 바탕과 가르는 것이 이것이다.
 * - **테두리 고리**: 인쇄 백돌은 0.40~0.50칸에 검은 원 테두리가 있다.
 */
internal object GridStoneDetector {

    fun detect(board: RectifiedBoard): DetectedBoard {
        val analysis = Analysis(LumaImage.of(board.pixels), board.origin, board.step, board.boardSize.value)
        val n = board.boardSize.value
        val stones = mutableMapOf<BoardCoordinate, StoneColor>()
        for (row in 0 until n) {
            for (col in 0 until n) {
                classify(analysis.features(col, row))?.let { stones[BoardCoordinate(row = row, column = col)] = it }
            }
        }
        return DetectedBoard(boardSize = board.boardSize, stones = stones, confidence = 0.95f)
    }

    internal data class PointFeatures(
        /** 바탕(둘레 칸 중심) 밝기·채도 중앙값. */
        val backgroundLuma: Float,
        val backgroundSaturation: Float,
        /** 돌 몸통(격자선 띠를 뺀 고리) 밝기·채도 중앙값. */
        val bodyLuma: Float,
        val bodySaturation: Float,
        /** 판 안쪽으로 뻗는 팔 가운데 선이 보이는 비율(0~1). 판 밖으로 나가는 팔은 세지 않는다. */
        val visibleArmRatio: Float,
        /** 테두리 고리 위치에서 바탕보다 확실히 어두운 각도의 비율(0~1). */
        val ringDarkRatio: Float,
    ) {
        /** 바탕보다 얼마나 어두운가(0~1). */
        val darkContrast: Float get() = (backgroundLuma - bodyLuma) / max(backgroundLuma, 1f)

        /**
         * 바탕보다 얼마나 밝은가 — 남은 밝기 여유 대비(0~1).
         * ⚠️ 여유의 바닥을 30으로 둔다 — 흰 종이(254)에서는 여유가 1이라 밝기 1 차이가 "100% 밝다"가 됐다(04·05).
         */
        val brightContrast: Float get() = (bodyLuma - backgroundLuma) / max(255f - backgroundLuma, 30f)
    }

    internal fun classify(f: PointFeatures): StoneColor? = when {
        // 흑돌 — 바탕보다 확실히 어둡다. 흰 숫자가 일부를 덮어도 고리 중앙값은 검다.
        f.darkContrast > 0.40f -> StoneColor.Black
        // ── 강한 증거 둘: 격자선 팔은 보지 않는다 ──
        // 돌 위 **숫자의 획**이 팔 표본에 걸려 "선이 보인다"로 읽히고, 모서리가 1~2px만 어긋나도 그렇게 된다(03·05).
        // 색 있는 판(나무·화면)에서 채도가 판의 색을 거의 다 잃었고 어둡지 않다 — 빈 점(판 색 그대로)·흑돌(어둡다)일 수 없다.
        f.backgroundSaturation - f.bodySaturation > 0.25f && f.bodySaturation < 0.12f &&
            f.darkContrast < 0.35f -> StoneColor.White
        // 인쇄에서 원 테두리가 거의 한 바퀴 다 보인다 — 빈 교점의 고리 자리는 비어 있다.
        f.ringDarkRatio >= 0.85f && f.darkContrast < 0.40f && f.bodySaturation < 0.20f -> StoneColor.White
        // 실물·화면의 백돌 — 바탕(나무색)보다 밝거나 채도가 확 낮고, 격자선을 덮고, **그 자체로 무채색**이다.
        // ⚠️ 채도 조건이 없으면 그림자 진 칸 옆의 맨 나무(바탕보다 밝지만 채도는 더 높다)가 백돌로 읽힌다(01).
        (f.brightContrast > 0.22f || f.backgroundSaturation - f.bodySaturation > 0.16f) &&
            f.bodySaturation < max(0.25f, f.backgroundSaturation * 0.6f) &&
            f.visibleArmRatio < 0.5f && f.darkContrast < 0.15f -> StoneColor.White
        // 음영 진 백돌 — 화면·기보의 백돌은 회색 그러데이션이라 밝은 판보다 조금 어둡게 나온다(03 오른쪽 변).
        // 채도가 판보다 확 낮고 격자선을 완전히 덮으면 백돌이다. 흑돌(> 0.40)과는 겹치지 않는다.
        f.backgroundSaturation - f.bodySaturation > 0.25f && f.bodySaturation < 0.15f &&
            f.visibleArmRatio <= 0.25f && f.darkContrast < 0.30f -> StoneColor.White
        // 인쇄 백돌 — 바탕과 밝기가 같아도 테두리 고리가 또렷하다. 돌 위 숫자의 획이 격자선처럼 보여 팔 비율이
        // 0.5까지 오르므로 팔은 느슨하게 본다. 숫자가 몸통을 어둡게 끌어도 흑돌(> 0.40)까지는 안 간다.
        // ⚠️ 인쇄는 무채색이다 — 채도 조건이 없으면 실물 사진의 그림자 진 나무가 고리처럼 읽힌다(01).
        f.ringDarkRatio >= 0.70f && f.visibleArmRatio <= 0.5f && f.darkContrast < 0.40f &&
            f.bodySaturation < 0.20f -> StoneColor.White
        f.visibleArmRatio <= 0.25f && f.ringDarkRatio > 0.45f && f.darkContrast < 0.25f &&
            f.bodySaturation < 0.20f -> StoneColor.White
        else -> null
    }

    /**
     * 칸 중심 바탕 값을 한 번만 재 두고 교점마다 특징을 뽑는다.
     *
     * ⚠️ **바탕은 둘레 네 칸이 아니라 4×4 칸(±1.5칸)의 칸 중심 중앙값이다** — 실물 사진에서 흑돌 둘 사이 칸 중심에
     * **그림자**가 지면 네 칸 중앙값이 44까지 떨어져, 그 옆 맨 나무가 "바탕보다 밝은 백돌"로 읽혔다(기준 이미지 01).
     * 열여섯 칸이면 그림자 한두 칸이 중앙값을 못 움직이고, 조명의 판 위 기울기는 여전히 따라간다.
     */
    internal class Analysis(val image: LumaImage, val origin: Float, val step: Float, val n: Int) {
        private val cells = n - 1
        private val cellLuma = FloatArray(cells * cells)
        private val cellSat = FloatArray(cells * cells)

        init {
            val l = ArrayList<Float>()
            val s = ArrayList<Float>()
            for (r in 0 until cells) {
                for (c in 0 until cells) {
                    l.clear()
                    s.clear()
                    image.collectDisc(origin + (c + 0.5f) * step, origin + (r + 0.5f) * step, step * 0.14f, l, s)
                    cellLuma[r * cells + c] = median(l)
                    cellSat[r * cells + c] = median(s)
                }
            }
        }

        fun features(col: Int, row: Int): PointFeatures {
            val l = ArrayList<Float>()
            val s = ArrayList<Float>()
            // 교점 (col,row)를 둘러싼 4×4 칸: 칸 인덱스 col-2..col+1, row-2..row+1
            for (r in row - 2..row + 1) {
                for (c in col - 2..col + 1) {
                    if (r !in 0 until cells || c !in 0 until cells) continue
                    l += cellLuma[r * cells + c]
                    s += cellSat[r * cells + c]
                }
            }
            return measure(image, origin, step, n, col, row, median(l), median(s))
        }
    }

    private fun measure(
        image: LumaImage,
        origin: Float,
        step: Float,
        n: Int,
        col: Int,
        row: Int,
        backgroundLuma: Float,
        backgroundSaturation: Float,
    ): PointFeatures {
        val cx = origin + col * step
        val cy = origin + row * step
        // ⚠️ 판 테두리선은 굵다(인쇄 기보는 안쪽 선의 서너 배) — 변·귀 교점에서는 그 띠를 넓게 뺀다.
        // 안 그러면 테두리 먹이 몸통·고리에 섞여 빈 귀가 흑돌로, 테두리 고리가 백돌로 읽힌다(기준 이미지 04·05).
        val onEdgeColumn = col == 0 || col == n - 1
        val onEdgeRow = row == 0 || row == n - 1
        val bandX = step * if (onEdgeColumn) 0.16f else 0.07f
        val bandY = step * if (onEdgeRow) 0.16f else 0.07f

        // 돌 몸통: 고리에서 격자선 띠를 뺀다.
        val bodyLuma = ArrayList<Float>()
        val bodySat = ArrayList<Float>()
        val rIn = step * 0.14f
        val rOut = step * 0.36f
        val reach = rOut.toInt() + 1
        for (dy in -reach..reach) {
            for (dx in -reach..reach) {
                val d2 = (dx * dx + dy * dy).toFloat()
                if (d2 < rIn * rIn || d2 > rOut * rOut) continue
                if (abs(dx) < bandX || abs(dy) < bandY) continue
                val x = (cx + dx).roundToInt()
                val y = (cy + dy).roundToInt()
                if (!image.contains(x, y)) continue
                bodyLuma += image.luma(x, y)
                bodySat += image.saturation(x, y)
            }
        }

        // 격자선 팔: 판 안쪽으로 뻗는 것만.
        var armsInside = 0
        var armsVisible = 0
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for ((ux, uy) in directions) {
            if (col + ux !in 0 until n || row + uy !in 0 until n) continue
            armsInside++
            if (armVisible(image, cx, cy, ux, uy, step, backgroundLuma)) armsVisible++
        }

        // 테두리 고리: 격자선 방향(±15°)을 피해서 각도마다 0.40~0.50칸의 최소 밝기.
        var ringSamples = 0
        var ringDark = 0
        for (k in 0 until 48) {
            val angle = 2 * PI * k / 48
            val deg = (k * 7.5) % 90.0
            if (deg < 15.0 || deg > 75.0) continue
            var darkest = 255f
            var sampled = false
            var r = step * 0.40f
            while (r <= step * 0.50f) {
                val ox = r * cos(angle).toFloat()
                val oy = r * sin(angle).toFloat()
                // 테두리선 띠 안의 표본은 버린다(굵은 테두리가 고리처럼 보인다).
                val nearFrame = (onEdgeColumn && abs(ox) < step * 0.2f) || (onEdgeRow && abs(oy) < step * 0.2f)
                val x = (cx + ox).roundToInt()
                val y = (cy + oy).roundToInt()
                if (!nearFrame && image.contains(x, y)) {
                    darkest = min(darkest, image.luma(x, y))
                    sampled = true
                }
                r += 1f
            }
            if (!sampled) continue
            ringSamples++
            if ((backgroundLuma - darkest) / max(backgroundLuma, 1f) > 0.30f) ringDark++
        }

        return PointFeatures(
            backgroundLuma = backgroundLuma,
            backgroundSaturation = backgroundSaturation,
            bodyLuma = median(bodyLuma),
            bodySaturation = median(bodySat),
            visibleArmRatio = if (armsInside == 0) 0f else armsVisible.toFloat() / armsInside,
            ringDarkRatio = if (ringSamples == 0) 0f else ringDark.toFloat() / ringSamples,
        )
    }

    /**
     * 교점에서 ([ux], [uy]) 방향으로 0.22~0.42칸 구간에 **가는 어두운 선**이 이어지는가.
     * 위치마다 선에 수직으로 ±0.08칸 안에서 가장 어두운 곳을 찾고, 그보다 ±0.18칸 바깥 양옆이 밝으면(골짜기) 선이다.
     * 돌의 넓은 음영은 골짜기가 아니라서 선으로 잡히지 않는다.
     */
    private fun armVisible(
        image: LumaImage,
        cx: Float,
        cy: Float,
        ux: Int,
        uy: Int,
        step: Float,
        backgroundLuma: Float,
    ): Boolean {
        var positions = 0
        var hits = 0
        var t = 0.22f
        while (t <= 0.42f) {
            val px = cx + ux * t * step
            val py = cy + uy * t * step
            // 수직 방향
            val vx = -uy
            val vy = ux
            var darkest = Float.MAX_VALUE
            var darkestOffset = 0f
            var o = -0.08f * step
            while (o <= 0.08f * step) {
                val x = (px + vx * o).roundToInt()
                val y = (py + vy * o).roundToInt()
                if (image.contains(x, y)) {
                    val l = image.luma(x, y)
                    if (l < darkest) {
                        darkest = l
                        darkestOffset = o
                    }
                }
                o += 1f
            }
            val side = 0.18f * step
            val x1 = (px + vx * (darkestOffset - side)).roundToInt()
            val y1 = (py + vy * (darkestOffset - side)).roundToInt()
            val x2 = (px + vx * (darkestOffset + side)).roundToInt()
            val y2 = (py + vy * (darkestOffset + side)).roundToInt()
            if (darkest != Float.MAX_VALUE && image.contains(x1, y1) && image.contains(x2, y2)) {
                positions++
                val s1 = image.luma(x1, y1)
                val s2 = image.luma(x2, y2)
                val valley = (s1 + s2) / 2f - darkest
                // 골짜기가 바탕 대비 충분히 깊고, 양옆이 바탕 근처로 밝아야 한다(검은 돌 속의 골짜기는 제외).
                if (valley > 0.12f * backgroundLuma && min(s1, s2) > 0.6f * backgroundLuma) hits++
            }
            t += 0.04f
        }
        return positions > 0 && hits * 2 >= positions
    }

    private fun median(values: MutableList<Float>): Float {
        if (values.isEmpty()) return 0f
        values.sort()
        return values[values.size / 2]
    }
}

/** 밝기(BT.601)·채도를 한 번에 계산해 둔 영상. */
internal class LumaImage private constructor(
    val width: Int,
    val height: Int,
    private val lumaValues: FloatArray,
    private val saturationValues: FloatArray,
    private val valid: BooleanArray,
) {
    /** 영상 안이고, 원본 사진 밖([BoardWarp.NoData])이 아닌가. */
    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && valid[y * width + x]
    fun luma(x: Int, y: Int): Float = lumaValues[y * width + x]
    fun saturation(x: Int, y: Int): Float = saturationValues[y * width + x]

    fun collectDisc(cx: Float, cy: Float, radius: Float, luma: MutableList<Float>, saturation: MutableList<Float>) {
        val reach = radius.toInt() + 1
        for (dy in -reach..reach) {
            for (dx in -reach..reach) {
                if (dx * dx + dy * dy > radius * radius) continue
                val x = (cx + dx).roundToInt()
                val y = (cy + dy).roundToInt()
                if (!contains(x, y)) continue
                luma += luma(x, y)
                saturation += saturation(x, y)
            }
        }
    }

    companion object {
        fun of(source: PixelSource): LumaImage {
            val w = source.width
            val h = source.height
            val l = FloatArray(w * h)
            val s = FloatArray(w * h)
            val v = BooleanArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = source.getPixel(x, y)
                    v[y * w + x] = p != BoardWarp.NoData
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    l[y * w + x] = 0.299f * r + 0.587f * g + 0.114f * b
                    val hi = max(r, max(g, b))
                    val lo = min(r, min(g, b))
                    s[y * w + x] = if (hi > 0) (hi - lo).toFloat() / hi else 0f
                }
            }
            return LumaImage(w, h, l, s, v)
        }
    }
}
