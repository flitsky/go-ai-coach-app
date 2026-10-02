package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 기준 이미지를 **일부러 거칠게** 바꾼 변형(백로그 #210) — 다섯 장에만 맞춘 검출기가 되지 않게 한다.
 * 실제 폰 사진은 기울고(회전), 사다리꼴로 찍히고(원근), 조명이 한쪽으로 쏠린다. 정답 모서리도 같은 변환으로 옮긴다.
 */
internal object BoardPhotoVariants {

    data class Variant(val name: String, val image: ArrayPixelSource, val corners: BoardCornerPoints)

    /** 사진 밖 배경 — 책상 같은 중간 회색. */
    private const val Backdrop = 0xFF8A8A8A.toInt()

    fun all(image: ArrayPixelSource, corners: BoardCornerPoints): List<Variant> = listOf(
        rotated(image, corners, 7.0),
        rotated(image, corners, -7.0),
        keystone(image, corners, 0.12f),
        lightingGradient(image, corners),
        scaled(image, corners, 0.6f),
        scaled(image, corners, 1.8f),
    )

    /** 원본 → 결과 좌표의 투시 변환 [forward]로 새 영상을 만든다(크기 [w]×[h]). */
    private fun warp(name: String, image: ArrayPixelSource, corners: BoardCornerPoints, w: Int, h: Int, forward: BoardHomography, backward: BoardHomography): Variant {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = backward.map(x + 0.5, y + 0.5)
                val v = BoardWarp.sampleBilinear(image, p.x - 0.5f, p.y - 0.5f)
                out[y * w + x] = if (v == BoardWarp.NoData) Backdrop else v
            }
        }
        val moved = corners.toList().map { forward.map(it.x.toDouble(), it.y.toDouble()) }
        return Variant(name, ArrayPixelSource(w, h, out), BoardCornerPoints(moved[0], moved[1], moved[2], moved[3]))
    }

    private fun quadMap(from: List<PointF2D>, to: List<PointF2D>) =
        requireNotNull(BoardHomography.fit(from, to)) to requireNotNull(BoardHomography.fit(to, from))

    private fun rect(w: Int, h: Int) = listOf(PointF2D(0f, 0f), PointF2D(w.toFloat(), 0f), PointF2D(w.toFloat(), h.toFloat()), PointF2D(0f, h.toFloat()))

    fun rotated(image: ArrayPixelSource, corners: BoardCornerPoints, degrees: Double): Variant {
        val a = Math.toRadians(degrees)
        val c = cos(a)
        val s = sin(a)
        val w0 = image.width.toDouble()
        val h0 = image.height.toDouble()
        val w = ceil(abs(w0 * c) + abs(h0 * s)).toInt()
        val h = ceil(abs(w0 * s) + abs(h0 * c)).toInt()
        val src = rect(image.width, image.height)
        val dst = src.map { p ->
            val x = p.x - w0 / 2
            val y = p.y - h0 / 2
            PointF2D((x * c - y * s + w / 2.0).toFloat(), (x * s + y * c + h / 2.0).toFloat())
        }
        val (fwd, bwd) = quadMap(src, dst)
        return warp("회전 ${degrees.roundToInt()}°", image, corners, w, h, fwd, bwd)
    }

    /** 위쪽 변을 [shrink]만큼 좁힌 사다리꼴 — 판을 앞쪽에서 비스듬히 내려다본 모양. */
    fun keystone(image: ArrayPixelSource, corners: BoardCornerPoints, shrink: Float): Variant {
        val w = image.width
        val h = image.height
        val inset = w * shrink / 2
        val dst = listOf(PointF2D(inset, 0f), PointF2D(w - inset, 0f), PointF2D(w.toFloat(), h.toFloat()), PointF2D(0f, h.toFloat()))
        val (fwd, bwd) = quadMap(rect(w, h), dst)
        return warp("사다리꼴 ${(shrink * 100).roundToInt()}%", image, corners, w, h, fwd, bwd)
    }

    fun scaled(image: ArrayPixelSource, corners: BoardCornerPoints, factor: Float): Variant {
        val w = max(1, (image.width * factor).roundToInt())
        val h = max(1, (image.height * factor).roundToInt())
        val (fwd, bwd) = quadMap(rect(image.width, image.height), rect(w, h))
        return warp("축척 ${factor}x", image, corners, w, h, fwd, bwd)
    }

    /** 왼쪽은 원래의 60%, 오른쪽은 110% 밝기 — 창가에서 한쪽으로 쏠린 조명. */
    fun lightingGradient(image: ArrayPixelSource, corners: BoardCornerPoints): Variant {
        val w = image.width
        val h = image.height
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val f = 0.6f + 0.5f * x / max(1, w - 1)
                val p = image.getPixel(x, y)
                fun ch(shift: Int) = (((p shr shift) and 0xFF) * f).roundToInt().coerceIn(0, 255)
                out[y * w + x] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        return Variant("조명 기울기", ArrayPixelSource(w, h, out), corners)
    }
}
