package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * 격자선 두 방향의 **소실점으로 원근을 걷어낼 틀**을 찾는다 — OpenCV(백로그 #263 단계 3).
 *
 * ⚠️ **실험실 `engine-lab/vision/lab/perspective.py`를 함수 단위로 옮긴 것이다** — 숫자(800px · 선분 4% · ±20° · 2° · 300회 · 0.06)를
 * 바꾸면 두 곳을 함께 바꾸고, 실험실의 `measure_perspective.py --export`로 `perspective-frames.json`을 다시 쓴다. 기기 계기 테스트
 * (`OpenCvPerspectiveDeviceTest`)가 둘이 같은 틀을 내는지 본다. 난수는 파이썬과 달라 RANSAC 표본이 다르지만, 최소제곱으로 다듬은 결과는 같은 자리에 온다.
 *
 * 1. 긴 변 800px · 회색 → LSD 선분, 짧은 것(돌 테두리의 호)은 버린다.
 * 2. 길이로 가중한 각도 히스토그램의 두 봉우리(45° 넘게 떨어진)로 무리를 나눈다.
 * 3. 무리마다 RANSAC 소실점 → 지지 직선의 최소제곱(SVD).
 * 4. 두 소실점을 잇는 소실선을 무한원으로 보내는 투시 변환(아핀까지 — 기울어짐은 격자 찾기가 견딘다).
 * 5. 원근이 약하면(소실선 세기 < 0.06) `null` — 똑바로 찍은 사진은 그대로 둔다(사다리꼴 12%는 0.13~0.20, 똑바로는 0.04 이하).
 */
internal object OpenCvPerspective {
    private const val WorkingMaxDim = 800
    private const val MinSegment = 0.04
    private const val FamilyWindowDegrees = 20.0
    private const val SupportDegrees = 2.0
    private const val RansacIterations = 300
    private const val WeakPerspective = 0.06
    private const val Seed = 263

    fun frameOf(bitmap: Bitmap): PerspectiveFrame? {
        if (!OpenCvRuntime.isReady) return null
        val rgba = Mat()
        val small = Mat()
        val gray = Mat()
        val lines = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            val w = rgba.cols()
            val h = rgba.rows()
            val scale = min(1.0, WorkingMaxDim.toDouble() / max(w, h))
            if (scale < 1.0) {
                Imgproc.resize(rgba, small, Size((w * scale).roundToInt().toDouble(), (h * scale).roundToInt().toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            } else {
                rgba.copyTo(small)
            }
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.createLineSegmentDetector(Imgproc.LSD_REFINE_STD).detect(gray, lines)
            val segments = segmentsOf(lines, MinSegment * max(gray.cols(), gray.rows()))
            val (first, second) = families(segments) ?: return null
            val random = Random(Seed)
            val v1 = vanishingPoint(first, random) ?: return null
            val v2 = vanishingPoint(second, random) ?: return null
            return frameFrom(v1, v2, gray.cols(), gray.rows(), scale, w, h)
        } finally {
            rgba.release()
            small.release()
            gray.release()
            lines.release()
        }
    }

    /** 선분 하나 — 두 끝점. */
    private class Segment(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
        val length = hypot(x2 - x1, y2 - y1)
        val angle = ((Math.toDegrees(atan2(y2 - y1, x2 - x1)) % 180.0) + 180.0) % 180.0
    }

    private fun segmentsOf(lines: Mat, minLength: Double): List<Segment> {
        if (lines.empty()) return emptyList()
        val raw = FloatArray(lines.rows() * lines.cols() * 4)
        lines.get(0, 0, raw)
        return (raw.indices step 4).map { i -> Segment(raw[i].toDouble(), raw[i + 1].toDouble(), raw[i + 2].toDouble(), raw[i + 3].toDouble()) }
            .filter { it.length >= minLength }
    }

    private fun families(segments: List<Segment>): Pair<List<Segment>, List<Segment>>? {
        if (segments.isEmpty()) return null
        val hist = DoubleArray(180)
        for (s in segments) {
            for (d in -3..3) hist[Math.floorMod((s.angle + d).roundToInt(), 180)] += s.length * (4 - abs(d))
        }
        val p1 = hist.indices.maxBy { hist[it] }
        val masked = hist.copyOf()
        for (d in -45..45) masked[Math.floorMod(p1 + d, 180)] = 0.0
        val p2 = masked.indices.maxBy { masked[it] }
        if (masked[p2] <= 0.0) return null
        fun member(peak: Int) = segments.filter { abs(((it.angle - peak + 90.0) % 180.0 + 180.0) % 180.0 - 90.0) <= FamilyWindowDegrees }
        return member(p1) to member(p2)
    }

    /** 동차좌표 직선(a, b, c) — a·x + b·y + c = 0, (a, b)를 단위 길이로. */
    private fun lineOf(s: Segment): DoubleArray {
        val l = cross(doubleArrayOf(s.x1, s.y1, 1.0), doubleArrayOf(s.x2, s.y2, 1.0))
        val n = hypot(l[0], l[1])
        return doubleArrayOf(l[0] / n, l[1] / n, l[2] / n)
    }

    private fun vanishingPoint(segments: List<Segment>, random: Random): DoubleArray? {
        if (segments.size < 3) return null
        val lines = segments.map(::lineOf)
        val minCos = cos(SupportDegrees * PI / 180.0)
        fun support(vp: DoubleArray): BooleanArray = BooleanArray(segments.size) { k ->
            val s = segments[k]
            val dx = (s.x2 - s.x1) / s.length
            val dy = (s.y2 - s.y1) / s.length
            var tx: Double
            var ty: Double
            if (abs(vp[2]) < 1e-9) {
                tx = vp[0]
                ty = vp[1]
            } else {
                tx = vp[0] / vp[2] - (s.x1 + s.x2) / 2
                ty = vp[1] / vp[2] - (s.y1 + s.y2) / 2
            }
            val tn = hypot(tx, ty) + 1e-12
            abs(tx / tn * dx + ty / tn * dy) >= minCos
        }
        var best: BooleanArray? = null
        var bestWeight = -1.0
        repeat(RansacIterations) {
            val i = random.nextInt(segments.size)
            var j = random.nextInt(segments.size - 1)
            if (j >= i) j++
            val vp = cross(lines[i], lines[j])
            val norm = kotlin.math.sqrt(vp[0] * vp[0] + vp[1] * vp[1] + vp[2] * vp[2])
            if (norm < 1e-12) return@repeat
            for (k in 0..2) vp[k] /= norm
            val m = support(vp)
            val weight = segments.indices.sumOf { if (m[it]) segments[it].length else 0.0 }
            if (weight > bestWeight) {
                best = m
                bestWeight = weight
            }
        }
        val mask = best ?: return null
        val chosen = segments.indices.filter { mask[it] }
        if (chosen.size < 3) return null
        // 최소제곱: 지지 직선들 l·v = 0 — 길이로 가중한 행렬의 가장 작은 특잇값의 오른쪽 특이벡터
        val a = Mat(chosen.size, 3, CvType.CV_64F)
        val wv = Mat()
        val u = Mat()
        val vt = Mat()
        try {
            chosen.forEachIndexed { row, k ->
                val l = lines[k]
                val weight = segments[k].length
                a.put(row, 0, l[0] * weight, l[1] * weight, l[2] * weight)
            }
            Core.SVDecomp(a, wv, u, vt)
            val v = DoubleArray(3) { vt.get(2, it)[0] }
            val n = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            return DoubleArray(3) { v[it] / n }
        } finally {
            a.release()
            wv.release()
            u.release()
            vt.release()
        }
    }

    /** 소실점 둘 → 원본에서 펼 사각형과 펼 크기. 작업 좌표는 원본의 [scale]배. */
    private fun frameFrom(v1: DoubleArray, v2: DoubleArray, sw: Int, sh: Int, scale: Double, w: Int, h: Int): PerspectiveFrame? {
        val longSide = max(sw, sh).toDouble()
        // 정규화 좌표(가운데 0, 긴 변 1)에서 계산해야 수치가 안정적이다
        val t = doubleArrayOf(1 / longSide, 0.0, -sw / 2 / longSide, 0.0, 1 / longSide, -sh / 2 / longSide, 0.0, 0.0, 1.0)
        val n1 = mulVec(t, v1)
        val n2 = mulVec(t, v2)
        val horizon = cross(n1, n2)
        if (abs(horizon[2]) < 1e-12) return null
        val hx = horizon[0] / horizon[2]
        val hy = horizon[1] / horizon[2]
        if (hypot(hx, hy) < WeakPerspective) return null
        val affine = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, hx, hy, 1.0)
        val s = doubleArrayOf(scale, 0.0, 0.0, 0.0, scale, 0.0, 0.0, 0.0, 1.0)
        val full = mulMat(mulMat(mulMat(invert(t) ?: return null, affine), t), s)
        val mapped = listOf(0.0 to 0.0, w.toDouble() to 0.0, w.toDouble() to h.toDouble(), 0.0 to h.toDouble()).map { (x, y) -> apply(full, x, y) }
        if (mapped.any { !it.first.isFinite() || !it.second.isFinite() }) return null
        val loX = mapped.minOf { it.first }
        val loY = mapped.minOf { it.second }
        val spanX = mapped.maxOf { it.first } - loX
        val spanY = mapped.maxOf { it.second } - loY
        val span = max(spanX, spanY)
        if (span <= 0 || span > 20 * max(w, h) * scale) return null // 소실선이 사진을 가로지른다 — 펴면 무한히 늘어난다
        val fit = max(w, h) / span
        val place = doubleArrayOf(fit, 0.0, -loX * fit, 0.0, fit, -loY * fit, 0.0, 0.0, 1.0)
        val toFlat = mulMat(place, full)
        val outW = ceil(spanX * fit).toInt()
        val outH = ceil(spanY * fit).toInt()
        val back = invert(toFlat) ?: return null
        val quad = listOf(0.0 to 0.0, outW.toDouble() to 0.0, outW.toDouble() to outH.toDouble(), 0.0 to outH.toDouble())
            .map { (x, y) -> apply(back, x, y).let { PointF2D(it.first.toFloat(), it.second.toFloat()) } }
        return PerspectiveFrame(BoardCornerPoints(quad[0], quad[1], quad[2], quad[3]), outW, outH)
    }

    // ── 3×3 행렬(행 우선)·동차 벡터 ────────────────────────────────────────────────────────

    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun mulVec(m: DoubleArray, v: DoubleArray) =
        DoubleArray(3) { r -> m[r * 3] * v[0] + m[r * 3 + 1] * v[1] + m[r * 3 + 2] * v[2] }

    private fun mulMat(a: DoubleArray, b: DoubleArray) =
        DoubleArray(9) { i -> (0..2).sumOf { k -> a[(i / 3) * 3 + k] * b[k * 3 + i % 3] } }

    private fun apply(m: DoubleArray, x: Double, y: Double): Pair<Double, Double> {
        val z = m[6] * x + m[7] * y + m[8]
        return (m[0] * x + m[1] * y + m[2]) / z to (m[3] * x + m[4] * y + m[5]) / z
    }

    private fun invert(m: DoubleArray): DoubleArray? {
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
        if (abs(det) < 1e-15) return null
        return doubleArrayOf(
            (m[4] * m[8] - m[5] * m[7]) / det, (m[2] * m[7] - m[1] * m[8]) / det, (m[1] * m[5] - m[2] * m[4]) / det,
            (m[5] * m[6] - m[3] * m[8]) / det, (m[0] * m[8] - m[2] * m[6]) / det, (m[2] * m[3] - m[0] * m[5]) / det,
            (m[3] * m[7] - m[4] * m[6]) / det, (m[1] * m[6] - m[0] * m[7]) / det, (m[0] * m[4] - m[1] * m[3]) / det,
        )
    }
}
