package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D

/**
 * 원근을 걷어낸 틀 — 원본 사진의 [quad](TL TR BR BL)가 [width]×[height] 사진의 네 모서리로 펴진다(백로그 #263).
 * OpenCV가 격자선 두 방향의 소실점으로 찾는다([OpenCvPerspective]). 원근이 약하면 틀이 없다(`null`) — 똑바로 찍은 사진은 그대로 둔다.
 */
internal data class PerspectiveFrame(val quad: BoardCornerPoints, val width: Int, val height: Int)

/**
 * 원근을 걷어낸 사진에서 판을 찾는다 — 틀로 펴고 → [BoardLocator.locate] → 찾은 모서리를 원본 좌표로 되돌린다.
 *
 * 왜 따로 있는가: 지금의 격자 찾기는 회전(±20°)·판 옆 글자·여백은 견디지만 **원근(사다리꼴)** 은 못 견딘다(2026-10-13 측정 —
 * 사다리꼴 12% 변형에서 02 42.9% · 04 0%). 펴고 찾으면 02 100% · 04 98.6%였다. 펴기는 순수 Kotlin이라 JVM 테스트가 같은 길을 밟는다 —
 * OpenCV는 틀만 계산한다.
 *
 * ⚠️ 펴서 생긴 빈 곳은 [Backdrop](중간 회색)으로 채운다 — [BoardWarp.NoData](검정)로 두면 그 경계가 「선」처럼 보여 격자 찾기가 흔들렸다.
 */
internal object PerspectiveLocate {
    /** 책상 같은 중간 회색 — 기준 이미지 변형의 사진 밖과 같은 값. */
    private const val Backdrop = 0xFF8A8A8A.toInt()

    fun locate(source: PixelSource, frame: PerspectiveFrame): LocatedBoard? {
        val w = frame.width
        val h = frame.height
        if (w <= 0 || h <= 0) return null
        val rect = listOf(PointF2D(0f, 0f), PointF2D(w.toFloat(), 0f), PointF2D(w.toFloat(), h.toFloat()), PointF2D(0f, h.toFloat()))
        val toSource = BoardHomography.fit(rect, frame.quad.toList()) ?: return null
        val out = IntArray(w * h)
        val p = DoubleArray(2)
        val maxX = source.width - 1
        val maxY = source.height - 1
        for (y in 0 until h) {
            for (x in 0 until w) {
                toSource.mapInto(x + 0.5, y + 0.5, p)
                val sx = p[0].toFloat()
                val sy = p[1].toFloat()
                out[y * w + x] = if (sx >= 0f && sy >= 0f && sx <= maxX && sy <= maxY) BoardWarp.sampleBilinear(source, sx - 0.5f, sy - 0.5f) else Backdrop
            }
        }
        val located = BoardLocator.locate(ArrayPixelSource(w, h, out)) ?: return null
        val back = located.corners.toList().map { toSource.map(it.x.toDouble(), it.y.toDouble()) }
        return located.copy(corners = BoardCornerPoints(back[0], back[1], back[2], back[3]))
    }
}
