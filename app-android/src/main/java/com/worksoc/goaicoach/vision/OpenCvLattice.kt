package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * **격자 자체로** 판을 펴고 잘라 낼 틀을 찾는다 — 비스듬히·작게 찍힌 실물 판용(백로그 #263).
 *
 * ⚠️ **실험실 `engine-lab/vision/lab/lattice.py`를 함수 단위로 옮긴 것이다** — 숫자를 바꾸면 두 곳을 함께 바꾸고, `measure_perspective.py --export`
 * (기준 이미지·변형)와 `frames_for_dir.py`(사진 폴더)로 `lattice-frames.json`을 다시 쓴다. 기기 계기 테스트(`OpenCvLatticeDeviceTest`)가 둘이 같은 틀을 내는지 본다.
 * 무작위가 없다(긴 선분 쌍을 모두 본다) · 반올림은 파이썬처럼 짝수 쪽으로([Math.rint]) · 음수 나머지는 파이썬처럼([floorMod]).
 *
 * [OpenCvPerspective]는 사진 전체에서 가장 센 두 방향으로 소실점을 잡는다 — 판 옆면·다다미·벽의 선이 더 세면 엉뚱한 방향을 펴고(갤러리 w07·w08),
 * 편 뒤에도 칸이 납작하거나 판이 사진의 일부라 격자 찾기가 엉뚱한 곳을 잡았다(w09·w13·w33). 여기서는 **고르게 늘어선 선 무리 둘**을 찾는다 —
 * 바둑판은 같은 간격의 선이 열 몇 줄씩 두 방향으로 있다. 다다미 이음매·판 옆면 모서리는 그렇지 않다.
 *
 * 1. 긴 변 1000px · 회색 → LSD 선분(짧은 돌 테두리 호는 버림).
 * 2. 소실점: 가장 긴 선분 [TopSegments]개의 쌍마다 교점 → 각도 [SupportDegrees]° 안으로 지나는 선분 길이 합. 가장 센 것부터 지지 선분을 빼며 [MaxVanishingPoints]개.
 * 3. 소실점 두 개씩: 소실선을 무한원으로 보내 두 무리를 각각 평행하게 → 무리마다 선 위치 히스토그램의 자기상관 = 칸 간격과 **고른 정도**. 둘 다 고른 쌍.
 * 4. 칸 간격·방향으로 격자 좌표 → 사진 좌표를 바로 만든다(칸이 정사각, 줄이 축과 나란하게). 찾은 줄 범위 + 여유를 틀로 잘라 낸다.
 */
internal object OpenCvLattice {
    private const val WorkingMaxDim = 1000
    private const val MinSegment = 0.012
    private const val TopSegments = 80
    private const val PairMaxDegrees = 25.0
    private const val SupportDegrees = 1.0
    private const val MaxVanishingPoints = 4
    private const val MinCell = 0.008
    private const val MaxCell = 0.12
    private const val MarginCells = 3.0
    private const val WeightCap = 2.0
    private const val OutLongSide = 900.0

    fun frameOf(bitmap: Bitmap): LatticeFrame? {
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
                Imgproc.resize(rgba, small, Size(Math.rint(w * scale), Math.rint(h * scale)), 0.0, 0.0, Imgproc.INTER_AREA)
            } else {
                rgba.copyTo(small)
            }
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.createLineSegmentDetector(Imgproc.LSD_REFINE_STD).detect(gray, lines)
            val sw = gray.cols()
            val sh = gray.rows()
            val segments = segmentsOf(lines, MinSegment * max(sw, sh))
            if (segments.size < 12) return null
            return find(segments, sw, sh, scale)
        } finally {
            rgba.release()
            small.release()
            gray.release()
            lines.release()
        }
    }

    /** 선분(정규화 좌표 — 가운데 0, 긴 변 1). */
    private class Segment(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
        val length = hypot(x2 - x1, y2 - y1)
        val dx = (x2 - x1) / length
        val dy = (y2 - y1) / length
        val mx = (x1 + x2) / 2
        val my = (y1 + y2) / 2
        val angle = floorMod(Math.toDegrees(atan2(dy, dx)), 180.0)
        val line: DoubleArray = cross(doubleArrayOf(x1, y1, 1.0), doubleArrayOf(x2, y2, 1.0)).let { l ->
            val n = hypot(l[0], l[1])
            doubleArrayOf(l[0] / n, l[1] / n, l[2] / n)
        }
    }

    private fun segmentsOf(lines: Mat, minLength: Double): List<DoubleArray> {
        if (lines.empty()) return emptyList()
        val raw = FloatArray(lines.rows() * lines.cols() * 4)
        lines.get(0, 0, raw)
        return (raw.indices step 4).map { i -> doubleArrayOf(raw[i].toDouble(), raw[i + 1].toDouble(), raw[i + 2].toDouble(), raw[i + 3].toDouble()) }
            .filter { hypot(it[2] - it[0], it[3] - it[1]) >= minLength }
    }

    private fun supports(vp: DoubleArray, s: Segment): Boolean {
        var tx: Double
        var ty: Double
        if (abs(vp[2]) < 1e-12) {
            tx = vp[0]
            ty = vp[1]
        } else {
            tx = vp[0] / vp[2] - s.mx
            ty = vp[1] / vp[2] - s.my
        }
        val tn = hypot(tx, ty) + 1e-12
        tx /= tn
        ty /= tn
        return abs(tx * s.dx + ty * s.dy) >= cos(Math.toRadians(SupportDegrees))
    }

    /** 소실점과 그 지지 선분 표시 — 가장 센 것부터 [MaxVanishingPoints]개. 무작위 없이 긴 선분 쌍을 모두 본다. */
    private fun vanishingPoints(segs: List<Segment>): List<Pair<DoubleArray, BooleanArray>> {
        val free = BooleanArray(segs.size) { true }
        val out = ArrayList<Pair<DoubleArray, BooleanArray>>()
        repeat(MaxVanishingPoints) {
            val idx = segs.indices.filter { free[it] }
            if (idx.size < 4) return out
            val top = idx.sortedByDescending { segs[it].length }.take(TopSegments)
            var best: BooleanArray? = null
            var bestWeight = 0.0
            for (a in top.indices) {
                for (b in a + 1 until top.size) {
                    val i = top[a]
                    val j = top[b]
                    val d = abs(floorMod(segs[i].angle - segs[j].angle + 90.0, 180.0) - 90.0)
                    if (d > PairMaxDegrees) continue
                    val vp = cross(segs[i].line, segs[j].line)
                    val n = norm3(vp)
                    if (n < 1e-12) continue
                    for (k in 0..2) vp[k] /= n
                    val m = BooleanArray(segs.size) { free[it] && supports(vp, segs[it]) }
                    var weight = 0.0
                    for (k in segs.indices) if (m[k]) weight += segs[k].length
                    if (weight > bestWeight) {
                        best = m
                        bestWeight = weight
                    }
                }
            }
            val chosen = best ?: return out
            if (chosen.count { it } < 4) return out
            val vp = leastSquaresPoint(segs.indices.filter { chosen[it] }.map { segs[it] })
            val m = BooleanArray(segs.size) { free[it] && supports(vp, segs[it]) }
            out += vp to m
            for (k in segs.indices) if (m[k]) free[k] = false
        }
        return out
    }

    /** 지지 직선들 l·v = 0 — 길이로 가중한 행렬의 가장 작은 특잇값의 오른쪽 특이벡터. */
    private fun leastSquaresPoint(chosen: List<Segment>): DoubleArray {
        val a = Mat(chosen.size, 3, CvType.CV_64F)
        val wv = Mat()
        val u = Mat()
        val vt = Mat()
        try {
            chosen.forEachIndexed { row, s -> a.put(row, 0, s.line[0] * s.length, s.line[1] * s.length, s.line[2] * s.length) }
            Core.SVDecomp(a, wv, u, vt)
            val v = DoubleArray(3) { vt.get(2, it)[0] }
            val n = norm3(v)
            return DoubleArray(3) { v[it] / n }
        } finally {
            a.release()
            wv.release()
            u.release()
            vt.release()
        }
    }

    /** 소실선을 무한원으로 보내는 3×3(행 우선). 소실선이 사진에 걸치거나 아주 가까우면 `null` — 펴면 무한히 늘어난다. */
    private fun horizonAffine(v1: DoubleArray, v2: DoubleArray, corners: List<Pair<Double, Double>>): DoubleArray? {
        val hz = cross(v1, v2)
        if (hypot(hz[0], hz[1]) < 1e-9 * abs(hz[2]) || abs(hz[2]) < 1e-12) {
            if (abs(hz[2]) < 1e-12) return null
            return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        }
        val a = hz[0] / hz[2]
        val b = hz[1] / hz[2]
        if (corners.any { (x, y) -> x * a + y * b + 1.0 <= 0.15 }) return null
        return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, a, b, 1.0)
    }

    /** 한 무리를 아핀으로 편 뒤의 방향 · 법선 · 선마다 (법선 거리, 무게). */
    private class Family(val direction: DoubleArray, val normal: DoubleArray, val pos: DoubleArray, val weights: DoubleArray)

    private fun familyLines(segs: List<Segment>, mask: BooleanArray, haff: DoubleArray): Family {
        val ps = ArrayList<DoubleArray>()
        val qs = ArrayList<DoubleArray>()
        val lens = ArrayList<Double>()
        for (k in segs.indices) {
            if (!mask[k]) continue
            val s = segs[k]
            val p = apply(haff, s.x1, s.y1)
            val q = apply(haff, s.x2, s.y2)
            val ln = hypot(q[0] - p[0], q[1] - p[1])
            if (ln <= 1e-9) continue
            ps += p
            qs += q
            lens += ln
        }
        val n = lens.size
        val ux = DoubleArray(n) { (qs[it][0] - ps[it][0]) / lens[it] }
        val uy = DoubleArray(n) { (qs[it][1] - ps[it][1]) / lens[it] }
        // 방향의 부호를 맞춘 가중 평균 — 기준은 가장 긴 선분
        val r = lens.indices.maxBy { lens[it] }
        var sx = 0.0
        var sy = 0.0
        for (i in 0 until n) {
            val sign = Math.signum(ux[i] * ux[r] + uy[i] * uy[r])
            sx += ux[i] * sign * lens[i]
            sy += uy[i] * sign * lens[i]
        }
        val dn = hypot(sx, sy)
        val direction = doubleArrayOf(sx / dn, sy / dn)
        val normal = doubleArrayOf(-direction[1], direction[0])
        val pos = DoubleArray(n) { ((ps[it][0] + qs[it][0]) / 2) * normal[0] + ((ps[it][1] + qs[it][1]) / 2) * normal[1] }
        // 무게 = 길이, 단 중앙값의 [WeightCap]배까지 — 판 테두리·다다미 이음매처럼 긴 선 몇 개가 격자선 열 몇 줄을 덮지 않게(w08·w09)
        val cap = WeightCap * median(lens.toDoubleArray())
        val weights = DoubleArray(n) { min(lens[it], cap) }
        return Family(direction, normal, pos, weights)
    }

    /** 선 위치들의 고른 간격 — (간격, 고른 정도 0~1). 위치 히스토그램의 자기상관 첫 봉우리. */
    private fun period(pos: DoubleArray, wts: DoubleArray): Pair<Double, Double>? {
        if (pos.size < 6) return null
        val lo = pos.min()
        val hi = pos.max()
        if (hi - lo < 4 * MinCell) return null
        val binw = MinCell / 8
        val nb = ((hi - lo) / binw).toInt() + 3
        val raw = DoubleArray(nb)
        for (i in pos.indices) raw[((pos[i] - lo) / binw).toInt() + 1] += wts[i]
        val kernel = doubleArrayOf(1.0, 2.0, 3.0, 2.0, 1.0).map { it / 9.0 }
        val hist = DoubleArray(nb) { i -> (-2..2).sumOf { d -> if (i + d in 0 until nb) raw[i + d] * kernel[d + 2] else 0.0 } }
        val mean = hist.average()
        for (i in hist.indices) hist[i] -= mean
        val zero = hist.sumOf { it * it }
        if (zero <= 0) return null
        val lmin = (MinCell / binw).toInt()
        val lmax = min((MaxCell / binw).toInt(), nb / 4)
        if (lmax <= lmin + 2) return null
        val acLen = min(nb, lmax * 4 + 4)
        val ac = DoubleArray(acLen) { k -> (0 until nb - k).sumOf { hist[it] * hist[it + k] } / zero }
        val peaks = (lmin + 1 until lmax).filter { k -> ac[k] > ac[k - 1] && ac[k] >= ac[k + 1] && ac[k] > 0 }
        if (peaks.isEmpty()) return null
        val top = peaks.maxOf { ac[it] }
        val k = peaks.first { ac[it] >= 0.6 * top }
        var est = k.toDouble()
        for (mult in 2..4) {
            val c = Math.rint(est * mult).toInt()
            if (c + 2 >= nb || c > lmax * 4) break
            val p = (c - 2..c + 2).maxBy { ac[it] }
            if (ac[p] < 0.3 * top) break
            est = p.toDouble() / mult
        }
        return est * binw to ac[k]
    }

    /** 선들이 실제로 있는 줄 번호 범위(가장 무거운 덩어리) — 위상 · 첫 줄 · 끝 줄. */
    private class Run(val phase: Double, val first: Int, val last: Int) {
        val lines get() = last - first + 1
    }

    private fun run(pos: DoubleArray, wts: DoubleArray, period: Double): Run? {
        var sinSum = 0.0
        var cosSum = 0.0
        for (i in pos.indices) {
            val a = 2 * PI * pos[i] / period
            sinSum += wts[i] * sin(a)
            cosSum += wts[i] * cos(a)
        }
        val phase = atan2(sinSum, cosSum) / (2 * PI) * period
        val kw = sortedMapOf<Int, Double>()
        var good = 0
        for (i in pos.indices) {
            val t = (pos[i] - phase) / period
            val k = Math.rint(t).toInt()
            if (abs(t - k) < 0.2) {
                good++
                kw[k] = (kw[k] ?: 0.0) + wts[i]
            }
        }
        if (good < 4) return null
        // 기준은 「흔한 줄」의 무게(상위 4분의 1 경계) — 가장 무거운 줄을 기준으로 하면 판 테두리 한 줄에 격자선이 다 묻혔다
        val ref = percentile(kw.values.toDoubleArray(), 75.0)
        val keep = kw.keys.filter { kw.getValue(it) >= 0.25 * ref }
        // 한두 줄 빈 곳은 잇고 그보다 긴 틈에서 덩어리를 나눈다 — 가장 무거운 덩어리(같으면 앞의 것)
        val blocks = ArrayList<MutableList<Int>>()
        var cur = mutableListOf(keep[0])
        for (k in keep.drop(1)) {
            if (k - cur.last() <= 3) cur += k else {
                blocks += cur
                cur = mutableListOf(k)
            }
        }
        blocks += cur
        var block = blocks[0]
        var blockWeight = block.sumOf { kw.getValue(it) }
        for (b in blocks.drop(1)) {
            val wsum = b.sumOf { kw.getValue(it) }
            if (wsum > blockWeight) {
                block = b
                blockWeight = wsum
            }
        }
        return Run(phase, block.first(), block.last())
    }

    private class Best(val score: Double, val haff: DoubleArray, val fa: Family, val fb: Family, val pa: Double, val pb: Double, val ra: Run, val rb: Run)

    private fun find(raw: List<DoubleArray>, sw: Int, sh: Int, scale: Double): LatticeFrame? {
        val s = max(sw, sh).toDouble()
        val segs = raw.map { Segment((it[0] - sw / 2.0) / s, (it[1] - sh / 2.0) / s, (it[2] - sw / 2.0) / s, (it[3] - sh / 2.0) / s) }
        val vps = vanishingPoints(segs)
        val corners = listOf(-sw / 2.0 to -sh / 2.0, sw / 2.0 to -sh / 2.0, sw / 2.0 to sh / 2.0, -sw / 2.0 to sh / 2.0).map { (x, y) -> x / s to y / s }
        var best: Best? = null
        for (a in vps.indices) {
            for (b in a + 1 until vps.size) {
                val haff = horizonAffine(vps[a].first, vps[b].first, corners) ?: continue
                val fa = familyLines(segs, vps[a].second, haff)
                val fb = familyLines(segs, vps[b].second, haff)
                // 두 무리가 너무 비슷한 방향이면 같은 무리를 둘로 나눈 것이다
                if (abs(fa.direction[0] * fb.direction[0] + fa.direction[1] * fb.direction[1]) > cos(Math.toRadians(30.0))) continue
                val pa = period(fa.pos, fa.weights) ?: continue
                val pb = period(fb.pos, fb.weights) ?: continue
                val ra = run(fa.pos, fa.weights, pa.first) ?: continue
                val rb = run(fb.pos, fb.weights, pb.first) ?: continue
                if (ra.lines < 5 || rb.lines < 5) continue
                val score = sqrt(pa.second * pb.second) * sqrt(min(ra.lines, 21).toDouble() * min(rb.lines, 21))
                if (best == null || score > best.score) best = Best(score, haff, fa, fb, pa.first, pb.first, ra, rb)
            }
        }
        val chosen = best ?: return null
        return frameFrom(chosen, sw, sh, s, scale)
    }

    /** 격자 좌표(k = 무리 A의 줄, j = 무리 B의 줄) → 원본 픽셀로 틀을 만든다. */
    private fun frameFrom(best: Best, sw: Int, sh: Int, s: Double, scale: Double): LatticeFrame? {
        val nA = best.fa.normal
        val nB = best.fb.normal
        val det = nA[0] * nB[1] - nA[1] * nB[0]
        if (abs(det) < 1e-12) return null
        val back = invert(best.haff) ?: return null
        fun gridToImage(k: Double, j: Double): DoubleArray {
            val ra = best.ra.phase + k * best.pa
            val rb = best.rb.phase + j * best.pb
            // [nA; nB] x = [ra, rb]
            val x = (nB[1] * ra - nA[1] * rb) / det
            val y = (-nB[0] * ra + nA[0] * rb) / det
            val p = apply(back, x, y)
            return doubleArrayOf((p[0] * s + sw / 2.0) / scale, (p[1] * s + sh / 2.0) / scale)
        }
        val k0 = best.ra.first.toDouble()
        val k1 = best.ra.last.toDouble()
        val j0 = best.rb.first.toDouble()
        val j1 = best.rb.last.toDouble()
        // 찾은 줄 수가 표준 판(9·13·19)보다 적으면 그만큼 여유를 더 둔다 — 가장자리 줄을 덜 셌을 수 있다(w28)
        val mk = MarginCells + shortfall(best.ra.lines)
        val mj = MarginCells + shortfall(best.rb.lines)
        val quad = listOf(gridToImage(k0 - mk, j0 - mj), gridToImage(k1 + mk, j0 - mj), gridToImage(k1 + mk, j1 + mj), gridToImage(k0 - mk, j1 + mj))
        val inner = listOf(gridToImage(k0, j0), gridToImage(k1, j0), gridToImage(k1, j1), gridToImage(k0, j1))
        if (quad.any { p -> !p[0].isFinite() || !p[1].isFinite() }) return null
        var order = intArrayOf(0, 1, 2, 3)
        // 거울상이면 B 쪽을 뒤집는다(시계 방향 TL TR BR BL)
        val area = (0..3).sumOf { i -> quad[i][0] * quad[(i + 1) % 4][1] - quad[(i + 1) % 4][0] * quad[i][1] }
        if (area < 0) order = intArrayOf(1, 0, 3, 2)
        // 사진의 위쪽이 틀의 위쪽이 되게 — TL→TR이 가장 오른쪽을 향하는 순서로 돌린다(같으면 앞의 것)
        val q = order.map { quad[it] }
        var rot = 0
        var rotBest = Double.NEGATIVE_INFINITY
        for (r in 0..3) {
            val a = q[r]
            val b = q[(r + 1) % 4]
            val v = (b[0] - a[0]) / (hypot(b[0] - a[0], b[1] - a[1]) + 1e-9)
            if (v > rotBest) {
                rotBest = v
                rot = r
            }
        }
        order = IntArray(4) { order[(it + rot) % 4] }
        val outQuad = order.map { quad[it] }
        val outInner = order.map { inner[it] }
        val alongK = (order[0] in setOf(0, 3) && order[1] in setOf(1, 2)) || (order[0] in setOf(1, 2) && order[1] in setOf(0, 3))
        val spanK = k1 - k0 + 2 * mk
        val spanJ = j1 - j0 + 2 * mj
        val cols = if (alongK) spanK else spanJ
        val rows = if (alongK) spanJ else spanK
        val lineCols = if (alongK) best.ra.lines else best.rb.lines
        val lineRows = if (alongK) best.rb.lines else best.ra.lines
        val cell = OutLongSide / max(cols, rows)
        val width = Math.rint(cols * cell).toInt()
        val height = Math.rint(rows * cell).toInt()
        val quadPts = outQuad.map { PointF2D(it[0].toFloat(), it[1].toFloat()) }
        val rect = listOf(PointF2D(0f, 0f), PointF2D(width.toFloat(), 0f), PointF2D(width.toFloat(), height.toFloat()), PointF2D(0f, height.toFloat()))
        val toFrame = BoardHomography.fit(quadPts, rect) ?: return null
        val innerFrame = outInner.map { toFrame.map(it[0], it[1]) }
        return LatticeFrame(
            PerspectiveFrame(BoardCornerPoints(quadPts[0], quadPts[1], quadPts[2], quadPts[3]), width, height),
            lineCols,
            lineRows,
            BoardCornerPoints(innerFrame[0], innerFrame[1], innerFrame[2], innerFrame[3]),
        )
    }

    /** 표준 판 크기(9·13·19) 중 [lines] 이상인 가장 작은 것까지 모자란 줄 수(19를 넘으면 0). */
    private fun shortfall(lines: Int): Int = listOf(9, 13, 19).firstOrNull { lines <= it }?.let { it - lines } ?: 0

    // ── 파이썬과 같은 수치 ────────────────────────────────────────────────────────────────

    /** 파이썬의 `%`(나누는 수의 부호를 따른다). */
    private fun floorMod(x: Double, m: Double): Double = x - floor(x / m) * m

    /** numpy `median` — 짝수 개면 가운데 둘의 평균. */
    private fun median(a: DoubleArray): Double {
        val s = a.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    /** numpy `percentile`(선형 보간). */
    private fun percentile(a: DoubleArray, q: Double): Double {
        val s = a.sorted()
        val idx = q / 100.0 * (s.size - 1)
        val lo = floor(idx).toInt()
        val hi = min(lo + 1, s.size - 1)
        return s[lo] + (idx - lo) * (s[hi] - s[lo])
    }

    private fun norm3(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun apply(m: DoubleArray, x: Double, y: Double): DoubleArray {
        val z = m[6] * x + m[7] * y + m[8]
        return doubleArrayOf((m[0] * x + m[1] * y + m[2]) / z, (m[3] * x + m[4] * y + m[5]) / z)
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
