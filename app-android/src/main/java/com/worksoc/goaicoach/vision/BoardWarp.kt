package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 반듯하게 편 판 — 격자선 `k`(0 ≤ k < n)는 `x = origin + k * step`(세로선), `y = origin + k * step`(가로선)에 있다.
 */
internal class RectifiedBoard(
    val pixels: ArrayPixelSource,
    val boardSize: BoardSize,
    val origin: Float,
    val step: Float,
)

/**
 * 모서리 넷(바깥 격자선 교차점)을 기준으로 판을 반듯하게 편다(백로그 #210).
 *
 * ⚠️ **바깥 격자선 밖으로 여백을 둔다**([MarginCells]칸). 옛 변환은 모서리를 출력 가장자리에 딱 붙여서
 * **변·귀의 돌이 절반·4분의 1만** 남았고, 그 반쪽 샘플이 오판을 냈다. 여백이 있으면 가장자리 돌도 통째로 보인다.
 */
internal object BoardWarp {
    /** 칸 하나의 출력 픽셀 수 — 19줄이면 판이 (18 + 2·여백) × 40 ≈ 780px. */
    const val CellPx: Int = 40

    /** 바깥 격자선 밖 여백(칸 단위). 0.5칸이면 가장자리 돌(반지름 ≈ 0.48칸)이 겨우 들어오므로 조금 더 둔다. */
    const val MarginCells: Float = 0.75f

    /** 원본 사진 밖이라 값이 없는 픽셀(알파 0). */
    const val NoData: Int = 0

    fun rectify(
        source: PixelSource,
        corners: BoardCornerPoints,
        boardSize: BoardSize,
        cellPx: Int = CellPx,
        marginCells: Float = MarginCells,
    ): RectifiedBoard {
        val n = boardSize.value
        val origin = marginCells * cellPx
        val span = ((n - 1) * cellPx).toFloat()
        val size = (span + 2 * origin).roundToInt()
        val grid = listOf(
            PointF2D(origin, origin),
            PointF2D(origin + span, origin),
            PointF2D(origin + span, origin + span),
            PointF2D(origin, origin + span),
        )
        val toSource = requireNotNull(BoardHomography.fit(grid, corners.toList())) {
            "모서리 넷이 퇴화했다(세 점이 한 줄) — 핀을 다시 놓아야 한다"
        }
        val out = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val p = toSource.map(x + 0.5, y + 0.5)
                out[y * size + x] = sampleBilinear(source, p.x - 0.5f, p.y - 0.5f)
            }
        }
        return RectifiedBoard(ArrayPixelSource(size, size, out), boardSize, origin, cellPx.toFloat())
    }

    /**
     * 원본 밖이면 [NoData](알파 0)를 돌려준다 — 판이 사진 끝에 걸친 경우.
     *
     * ⚠️ **가장자리 픽셀로 늘려 채우지 않는다** — 그러면 사진 가장자리에 걸린 굵은 테두리선이 바깥으로 복제돼
     * 빈 귀가 흑돌로 읽혔다(기준 이미지 04·05). 검출기는 [NoData]를 표본에서 뺀다.
     */
    internal fun sampleBilinear(source: PixelSource, x: Float, y: Float): Int {
        val maxX = source.width - 1
        val maxY = source.height - 1
        if (x < -0.5f || y < -0.5f || x > maxX + 0.5f || y > maxY + 0.5f) return NoData
        val cx = x.coerceIn(0f, maxX.toFloat())
        val cy = y.coerceIn(0f, maxY.toFloat())
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val x1 = minOf(x0 + 1, maxX)
        val y1 = minOf(y0 + 1, maxY)
        val fx = cx - x0
        val fy = cy - y0
        val p00 = source.getPixel(x0, y0)
        val p10 = source.getPixel(x1, y0)
        val p01 = source.getPixel(x0, y1)
        val p11 = source.getPixel(x1, y1)
        fun channel(shift: Int): Int {
            val c00 = (p00 shr shift) and 0xFF
            val c10 = (p10 shr shift) and 0xFF
            val c01 = (p01 shr shift) and 0xFF
            val c11 = (p11 shr shift) and 0xFF
            val top = c00 + (c10 - c00) * fx
            val bottom = c01 + (c11 - c01) * fx
            return (top + (bottom - top) * fy).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
