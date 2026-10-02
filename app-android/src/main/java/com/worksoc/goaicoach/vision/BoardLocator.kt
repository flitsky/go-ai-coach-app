package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 자동으로 찾은 판 — [corners]는 바깥 격자선 교차점(원본 픽셀). */
internal data class LocatedBoard(
    val corners: BoardCornerPoints,
    val boardSize: BoardSize,
    /** 0~1. 판 안 격자선의 세기 대비 판 밖의 세기로 잰 대략값. */
    val confidence: Float,
)

/**
 * 사진에서 바둑판 격자를 **스스로** 찾는다(백로그 #210 — 사용자 결정: 직접 구현).
 *
 * ## 격자선 + 돌, 둘 다 본다
 * 돌이 빽빽하면 격자선이 거의 안 보인다. 그런데 **돌은 언제나 교점 위에** 놓이므로, "돌 크기의 덩어리 중심"도
 * 격자선과 **같은 자리**에 봉우리를 만든다. 두 증거(가는 어두운 선 · 돌 크기 덩어리 중심)를 합친 투영 윤곽에서
 * - **기울기**: 선 지도만 여러 각도로 투영해 윤곽이 가장 뾰족한 각도(세로선 무리·가로선 무리를 **따로**).
 * - **원근**: 각도 ±5° 안에서 위치마다 가장 센 투영을 취한다 — 사다리꼴에서는 선마다 각도가 조금씩 다르다.
 * - **칸 간격**: 그 윤곽의 주기. **판의 범위**: 9·13·19줄이 연달아 세고 바깥은 약한 구간(판이 있는 영역으로 좁혀 한 번 더).
 * - 선마다 제 각도를 가진 직선의 교점으로 첫 격자를 만들고, [snapToGrid]가 교점을 증거 쪽으로 끌어당겨 마무리한다.
 *
 * ⚠️ **기울기를 기울기 방향 히스토그램으로 재지 않는다** — 돌은 원이라 경계 기울기가 모든 방향으로 고르게 퍼져서
 * 7° 돌린 판도 0°로 읽혔다. 원에 거의 반응하지 않는 "가는 어두운 선" 지도만으로 잰다.
 * ⚠️ **돌 가장자리(경계)는 위상 증거로 쓰지 않는다** — 경계는 교점에서 ±0.48칸, 즉 **반 칸 자리**에 몰려 위상을
 * 반 칸 밀어낸다. 경계 세기는 **주기**를 잴 때만 쓴다.
 * ⚠️ 선·돌 세기는 **그 자리 밝기 대비**로 잰다 — 한쪽이 어두운 조명에서 판보다 밝은 옆 UI 글자가 이기지 않게.
 *
 * 한계: 아주 비스듬히(강한 원근) 찍은 사진은 못 찾을 수 있다 — 수동 핀 + [refine]으로 받친다.
 */
internal object BoardLocator {

    /** 내부 처리 해상도(긴 변). 돌 하나가 10px 남짓이면 충분하다. */
    private const val WorkingMaxDim = 900

    private val sizes = listOf(19, 13, 9)

    fun locate(source: PixelSource): LocatedBoard? {
        val (gray, scale) = Gray.downscaled(source, WorkingMaxDim)
        val maps = EvidenceMaps(gray)
        val theta = rotationAngle(maps)
        val (pu, pv) = periods(maps, theta, gray) ?: return null
        if (pv / pu !in 0.75f..1.33f) return null
        maps.attachStones((pu + pv) / 2f)

        var lattice = findLattice(maps, theta, pu, pv, mask = null) ?: return null
        // 2차: **판이 있는 영역 안에서만** 다시 찾는다 — 판 옆 UI·제목 글자·좌표 표기가 격자선처럼 섞이지 않게
        // (기준 이미지 02: 판 위 제목줄이 한 줄로 끼어 판 전체가 한 칸 올라갔다).
        val n0 = lattice.n
        val mask = QuadMask(
            listOf(-0.6, -0.6, n0 - 0.4, -0.6, n0 - 0.4, n0 - 0.4, -0.6, n0 - 0.4).chunked(2).map { (x, y) -> lattice.homography.map(x, y) },
        )
        findLattice(maps, theta, lattice.pitchU, lattice.pitchV, mask)?.let { lattice = it }

        var fitted = snapToGrid(gray, maps.stone!!, lattice.n, lattice.homography)
        val shifted = adjustExtent(gray, lattice.n, fitted)
        if (shifted !== fitted) {
            fitted = snapToGrid(gray, maps.stone!!, lattice.n, shifted)
        }
        return LocatedBoard(cornersOf(fitted, lattice.n, 1f / scale), BoardSize(lattice.n), lattice.contrast.coerceIn(0f, 1f))
    }

    /**
     * 손으로 놓은(또는 대략 맞은) 모서리를 **가까운 격자에 붙인다** — 핀이 칸의 3분의 1쯤 어긋나도 맞춰 준다.
     * 그 모서리로 시작해 [snapToGrid]로 교점을 끌어당긴다. 모서리가 한 칸 가까이 움직였다면 엉뚱한 선에 붙은 것이라
     * 손으로 놓은 값을 그대로 둔다.
     */
    fun refine(source: PixelSource, approx: BoardCornerPoints, boardSize: BoardSize): BoardCornerPoints {
        val n = boardSize.value
        val (gray, scale) = Gray.downscaled(source, WorkingMaxDim)
        val start = BoardHomography.fit(unitCorners(n), approx.toList().map { it.scaled(scale) }) ?: return approx
        val pitch = localPitch(start, n / 2, n / 2)
        if (pitch < 3f) return approx
        val fitted = snapToGrid(gray, StoneResponse(gray, pitch), n, start)
        val refined = cornersOf(fitted, n, 1f / scale)
        val moved = refined.toList().zip(approx.toList()).maxOf { (a, b) -> hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) }
        return if (moved <= pitch / scale * 0.8f) refined else approx
    }

    // ---------------------------------------------------------------------------------------------
    // 격자 찾기

    private class Lattice(val n: Int, val homography: BoardHomography, val pitchU: Float, val pitchV: Float, val contrast: Float)

    /**
     * 기울기 [theta]로 투영한 선 + 돌 윤곽에서 판 크기·범위를 고르고, 판의 **위·아래 절반, 왼쪽·오른쪽 절반**을 따로 다시
     * 투영해 선마다 두 높이에서의 위치를 얻는다 — 그 두 점을 잇는 직선의 교점으로 첫 격자를 만든다(사다리꼴 원근이 여기서 들어간다).
     *
     * ⚠️ 선마다 각도를 따로 고르지 않는다 — 판 가장자리·돌 빽빽한 줄에서 각도가 흔들려 첫 격자를 비틀었다(01).
     * 절반 띠는 각각 반듯한 투영이라 흔들림이 없다.
     */
    private fun findLattice(maps: EvidenceMaps, theta: Float, pu: Float, pv: Float, mask: QuadMask?): Lattice? {
        val profU = maps.profile(Family.U, theta, mask)
        val profV = maps.profile(Family.V, theta, mask)
        val candidates = sizes.mapNotNull { n ->
            val ru = bestRun(profU, pu, n) ?: return@mapNotNull null
            val rv = bestRun(profV, pv, n) ?: return@mapNotNull null
            Triple(n, ru, rv)
        }
        val top = candidates.maxOfOrNull { it.second.score + it.third.score } ?: return null
        // ⚠️ **점수가 가장 높은 것의 90% 안이면 큰 판** — 19줄 판 한가운데의 돌 빽빽한 9줄은 평균 세기가 높아 19줄보다
        // 점수가 근소하게 높을 수 있다(02: 5.38 대 5.32). 진짜 9줄 판에서 19줄을 잡으면 판 밖으로 열 줄이 나가 크게 떨어진다.
        val (n, ru, rv) = candidates.filter { it.second.score + it.third.score >= top * 0.9f }.maxBy { it.first }

        val frame = maps.frame(theta)
        fun uOf(run: Run, k: Float) = maps.rhoMin + run.start + k * run.pitch
        // 반듯한 첫 격자(원근 없음)
        val uniform = BoardHomography.fit(
            unitCorners(n),
            listOf(
                frame.toImage(uOf(ru, 0f), uOf(rv, 0f)),
                frame.toImage(uOf(ru, n - 1f), uOf(rv, 0f)),
                frame.toImage(uOf(ru, n - 1f), uOf(rv, n - 1f)),
                frame.toImage(uOf(ru, 0f), uOf(rv, n - 1f)),
            ),
        ) ?: return null

        // 절반 띠: 세로선은 위·아래 절반에서, 가로선은 왼쪽·오른쪽 절반에서 각각 위치를 다시 잰다.
        val mid = (n - 1) / 2f
        fun band(k0: Float, k1: Float, j0: Float, j1: Float) =
            QuadMask(listOf(k0 to j0, k1 to j0, k1 to j1, k0 to j1).map { (k, j) -> uniform.map(k.toDouble(), j.toDouble()) })
        val topBand = band(-0.6f, n - 0.4f, -0.6f, mid)
        val bottomBand = band(-0.6f, n - 0.4f, mid, n - 0.4f)
        val leftBand = band(-0.6f, mid, -0.6f, n - 0.4f)
        val rightBand = band(mid, n - 0.4f, -0.6f, n - 0.4f)
        val tol = 0.5f
        val uTop = bestRun(maps.profile(Family.U, theta, topBand), ru.pitch, n, near = ru.start, tolerance = ru.pitch * tol, pitchSpread = 0.08f) ?: ru
        val uBottom = bestRun(maps.profile(Family.U, theta, bottomBand), ru.pitch, n, near = ru.start, tolerance = ru.pitch * tol, pitchSpread = 0.08f) ?: ru
        val vLeft = bestRun(maps.profile(Family.V, theta, leftBand), rv.pitch, n, near = rv.start, tolerance = rv.pitch * tol, pitchSpread = 0.08f) ?: rv
        val vRight = bestRun(maps.profile(Family.V, theta, rightBand), rv.pitch, n, near = rv.start, tolerance = rv.pitch * tol, pitchSpread = 0.08f) ?: rv
        // 띠 가운데의 높이·너비(반듯한 격자 기준)
        val vTop = uOf(rv, (n - 1) / 4f)
        val vBottom = uOf(rv, 3 * (n - 1) / 4f)
        val uLeft = uOf(ru, (n - 1) / 4f)
        val uRight = uOf(ru, 3 * (n - 1) / 4f)

        val gridPts = ArrayList<PointF2D>()
        val imgPts = ArrayList<PointF2D>()
        for (j in 0 until n) {
            for (k in 0 until n) {
                // 세로선 k: (uTop_k, vTop) — (uBottom_k, vBottom), 가로선 j: (uLeft, vLeft_j) — (uRight, vRight_j)
                val p = intersectSegments(
                    frame.toImage(uOf(uTop, k.toFloat()), vTop), frame.toImage(uOf(uBottom, k.toFloat()), vBottom),
                    frame.toImage(uLeft, uOf(vLeft, j.toFloat())), frame.toImage(uRight, uOf(vRight, j.toFloat())),
                ) ?: continue
                gridPts += PointF2D(k.toFloat(), j.toFloat())
                imgPts += p
            }
        }
        if (gridPts.size < 8) return null
        val h = BoardHomography.fit(gridPts, imgPts) ?: return null
        return Lattice(n, h, ru.pitch, rv.pitch, (ru.contrast + rv.contrast) / 2f)
    }

    /** 두 점을 지나는 직선 둘의 교점. 거의 나란하면 `null`. */
    private fun intersectSegments(a1: PointF2D, a2: PointF2D, b1: PointF2D, b2: PointF2D): PointF2D? {
        val d1x = a2.x - a1.x
        val d1y = a2.y - a1.y
        val d2x = b2.x - b1.x
        val d2y = b2.y - b1.y
        val den = d1x * d2y - d1y * d2x
        if (abs(den) < 1e-6f) return null
        val t = ((b1.x - a1.x) * d2y - (b1.y - a1.y) * d2x) / den
        return PointF2D(a1.x + t * d1x, a1.y + t * d1y)
    }

    /**
     * 판의 기울기(도) — 선 지도를 −20°~20°로 0.5°씩 투영해 **윤곽의 제곱합(뾰족함)**이 가장 큰 각도.
     * 선이 투영 방향과 나란할 때만 한 칸에 몰려 봉우리가 높아진다. 두 선 무리 중 **더 또렷한 쪽**의 각도 하나를 둘 다에 쓴다.
     *
     * ⚠️ 무리마다 따로 찾지 않는다 — 판 옆 바구니(바둑통)의 짜임 무늬가 가로선 무리를 −14°로 끌어간 적이 있다(01).
     * 사다리꼴 원근으로 선마다 각도가 조금씩 다른 것은 [findLattice]의 절반 띠 다시 재기가 받는다.
     */
    private fun rotationAngle(maps: EvidenceMaps): Float {
        fun curve(family: Family): List<Pair<Float, Double>> {
            val out = ArrayList<Pair<Float, Double>>()
            var a = -20f
            while (a <= 20f + 1e-3f) {
                out += a to maps.ridgeEnergy(family, a)
                a += 0.5f
            }
            return out
        }
        fun peakiness(c: List<Pair<Float, Double>>): Double = c.maxOf { it.second } / max(1e-9, c.sumOf { it.second } / c.size)
        val cu = curve(Family.U)
        val cv = curve(Family.V)
        val chosen = if (peakiness(cu) >= peakiness(cv)) cu else cv
        return chosen.maxBy { it.second }.first
    }

    // ---------------------------------------------------------------------------------------------
    // 주기

    /**
     * 두 축의 칸 간격. ⚠️ **두 값이 크게 다르면 주기가 더 또렷한 축을 믿고, 다른 축은 그 값의 0.75~1.33배 안에서만
     * 다시 찾는다** — 바둑판 칸은 거의 정사각이라 두 축이 그렇게 다를 수 없다. 한 축이 옆 UI 글자 줄·돌 사이 틈에 끌려
     * 절반·엉뚱한 주기를 내면(02 조명 기울기 7.7 대 18.5, 04 회전 8.2 대 15.9) 그 축만 바로잡힌다.
     */
    private fun periods(maps: EvidenceMaps, theta: Float, g: Gray): Pair<Float, Float>? {
        val profU = maps.periodProfile(Family.U, theta)
        val profV = maps.periodProfile(Family.V, theta)
        val eu = estimatePeriod(profU, g)
        val ev = estimatePeriod(profV, g)
        if (eu == null && ev == null) return null
        if (eu != null && ev != null && ev.first / eu.first in 0.8f..1.25f) return eu.first to ev.first
        val strongIsU = ev == null || (eu != null && eu.second >= ev.second)
        val strong = (if (strongIsU) eu else ev)!!.first
        val window = strong * 0.75f..strong * 1.33f
        val weak = estimatePeriod(if (strongIsU) profV else profU, g, window)?.first ?: strong
        return if (strongIsU) strong to weak else weak to strong
    }

    /**
     * 칸 간격(작업 해상도 px). 경계 + 선 윤곽의 자기상관에서 **첫 번째로 충분히 높은 봉우리**를 고르고
     * (2칸·3칸 배수 봉우리를 고르지 않게), k배 봉우리들로 소수점까지 다듬는다.
     */
    private fun estimatePeriod(profile: FloatArray, g: Gray, window: ClosedFloatingPointRange<Float>? = null): Pair<Float, Float>? {
        // ⚠️ 투영 윤곽은 회전을 받으려고 대각선 길이만큼 넉넉하다 — 아무 픽셀도 안 떨어진 양 끝을 잘라 낸다.
        // 남겨 두면 평균을 뺀 뒤 긴 음수 구간이 되어 자기상관을 흐린다(주기가 절반·엉뚱한 값으로 나왔다).
        val firstNonZero = profile.indexOfFirst { it != 0f }.takeIf { it >= 0 } ?: return null
        val lastNonZero = profile.indexOfLast { it != 0f }
        val sig = profile.copyOfRange(firstNonZero, lastNonZero + 1)
        val mean = sig.average().toFloat()
        for (i in sig.indices) sig[i] -= mean
        val dim = max(g.w, g.h)
        val minLag = window?.let { max(3, floor(it.start).toInt()) } ?: max(6, dim / 60)
        val maxLag = window?.let { min(sig.size / 3, ceil(it.endInclusive).toInt()) } ?: min(sig.size / 6, dim / 7)
        if (maxLag <= minLag + 2) return null
        val acMax = maxLag
        val ac = FloatArray(acMax + 2)
        val zero = sig.fold(0.0) { s, v -> s + v * v }.toFloat().takeIf { it > 0f } ?: return null
        for (lag in minLag..acMax + 1) {
            var s = 0.0
            for (i in 0 until sig.size - lag) s += sig[i] * sig[i + lag]
            ac[lag] = (s / zero).toFloat()
        }
        val peaks = (minLag + 1..maxLag).filter { ac[it] > ac[it - 1] && ac[it] >= ac[it + 1] && ac[it] > 0f }
        if (peaks.isEmpty()) return null
        val top = peaks.maxOf { ac[it] }
        // 창이 주어지면(다른 축이 알려 준 범위) 그 안의 가장 높은 봉우리, 아니면 첫 번째로 충분히 높은 봉우리.
        val first = if (window != null) peaks.maxBy { ac[it] } else peaks.first { ac[it] >= 0.6f * top }
        var estimate = subPixelPeak(ac, first)
        for (k in 2..4) {
            val center = (estimate * k).roundToInt()
            if (center + 2 > maxLag) break
            val window = (center - 2..center + 2).filter { it in minLag..maxLag }
            if (window.isEmpty()) break
            val p = window.maxBy { ac[it] }
            if (ac[p] < 0.3f * top) break
            estimate = subPixelPeak(ac, p) / k
        }
        return estimate to ac[first]
    }

    private fun subPixelPeak(a: FloatArray, i: Int): Float {
        if (i <= 0 || i >= a.size - 1) return i.toFloat()
        val d = a[i - 1] - 2 * a[i] + a[i + 1]
        return if (d == 0f) i.toFloat() else i + 0.5f * (a[i - 1] - a[i + 1]) / d
    }

    // ---------------------------------------------------------------------------------------------
    // 판의 범위(바깥 격자선)

    internal data class Run(
        /** 첫 격자선 위치(윤곽 인덱스). */
        val start: Float,
        val pitch: Float,
        val score: Float,
        /** 판 안 평균 대비 바깥 세기의 대비(0~1). */
        val contrast: Float,
    )

    /**
     * [n]줄이 연달아 세고 그 **바깥 한 줄씩은 약한** 구간을 찾는다. 간격은 추정값의 ±[pitchSpread] 안에서 같이 고른다(원근).
     * [near]가 있으면 시작 위치를 그 근처(±[tolerance])로 묶는다(절반 띠 다시 재기).
     */
    private fun bestRun(
        profile: FloatArray,
        pitchGuess: Float,
        n: Int,
        near: Float? = null,
        tolerance: Float = 0f,
        pitchSpread: Float = 0.04f,
    ): Run? {
        val pooled = maxPool(profile, max(1, (pitchGuess * 0.12f).roundToInt()))
        fun at(pos: Float): Float {
            val i = pos.roundToInt()
            return if (i in pooled.indices) pooled[i] else 0f
        }
        var best: Run? = null
        var pitch = pitchGuess * (1f - pitchSpread)
        while (pitch <= pitchGuess * (1f + pitchSpread)) {
            val span = (n - 1) * pitch
            var start = near?.let { max(0f, it - tolerance) } ?: 0f
            val end = min(profile.size - 1 - span, near?.let { it + tolerance } ?: Float.MAX_VALUE)
            while (start <= end) {
                var inside = 0f
                var weakest = Float.MAX_VALUE
                for (k in 0 until n) {
                    val v = at(start + k * pitch)
                    inside += v
                    weakest = min(weakest, v)
                }
                inside /= n
                val outside = max(at(start - pitch), at(start + n * pitch))
                // 가장 약한 안쪽 줄도 조금 반영한다 — 판 밖까지 걸친 구간을 막는다.
                val score = inside - 0.8f * outside + 0.2f * weakest
                if (best == null || score > best.score) {
                    val contrast = if (inside > 0f) 1f - outside / inside else 0f
                    best = Run(start, pitch, score, contrast)
                }
                start += 0.5f
            }
            pitch += pitchGuess * 0.005f
        }
        return best
    }

    private fun maxPool(a: FloatArray, radius: Int): FloatArray = FloatArray(a.size) { i ->
        var m = 0f
        for (j in max(0, i - radius)..min(a.size - 1, i + radius)) m = max(m, a[j])
        m
    }

    // ---------------------------------------------------------------------------------------------
    // 다듬기: 지금의 투시 변환에서 출발해 교점을 격자로 끌어당긴다(원근·회전·핀 어긋남을 함께 흡수)

    /**
     * 교점마다 세로선·가로선을 **그 자리의 선 방향 그대로** 법선 방향으로 조금씩 밀어 보며, 선 + 돌 증거가 가장 센 자리로
     * 옮긴다. 옮긴 교점 전체로 투시 변환을 다시 맞추고(크게 벗어난 점은 거른다), 창을 좁혀 세 번 되풀이한다.
     * ⚠️ 선을 하나씩 직선으로 맞추지 않는다 — 투시 변환 하나가 "모든 선이 곧고 한 소실점으로 모인다"는 제약을 대신한다.
     */
    private fun snapToGrid(g: Gray, stone: StoneResponse, n: Int, start: BoardHomography): BoardHomography {
        var h = start
        for (window in floatArrayOf(0.30f, 0.20f, 0.12f, 0.30f, 0.20f, 0.12f)) {
            val gridPts = ArrayList<PointF2D>()
            val imgPts = ArrayList<PointF2D>()
            for (j in 0 until n) {
                for (k in 0 until n) {
                    val p = h.map(k.toDouble(), j.toDouble())
                    val down = direction(h, k.toDouble(), j - 0.5, k.toDouble(), j + 0.5)
                    val right = direction(h, k - 0.5, j.toDouble(), k + 0.5, j.toDouble())
                    val pitch = localPitch(h, min(k, n - 2), min(j, n - 2))
                    // 세로선은 가로 방향(right)으로, 가로선은 세로 방향(down)으로 민다.
                    val tu = bestShift(g, stone, p, along = down, normal = right, search = pitch * window, half = pitch * 0.35f)
                    val tv = bestShift(g, stone, p, along = right, normal = down, search = pitch * window, half = pitch * 0.35f)
                    if (tu == null && tv == null) continue
                    val x = p.x + (tu ?: 0f) * right.first + (tv ?: 0f) * down.first
                    val y = p.y + (tu ?: 0f) * right.second + (tv ?: 0f) * down.second
                    gridPts += PointF2D(k.toFloat(), j.toFloat())
                    imgPts += PointF2D(x, y)
                }
            }
            if (gridPts.size < 8) break
            var next = BoardHomography.fit(gridPts, imgPts) ?: break
            repeat(2) {
                val keepG = ArrayList<PointF2D>()
                val keepI = ArrayList<PointF2D>()
                for (i in gridPts.indices) {
                    val q = next.map(gridPts[i].x.toDouble(), gridPts[i].y.toDouble())
                    val tol = localPitch(next, min(gridPts[i].x.toInt(), n - 2), min(gridPts[i].y.toInt(), n - 2)) * 0.15f
                    if (hypot((q.x - imgPts[i].x).toDouble(), (q.y - imgPts[i].y).toDouble()) <= tol) {
                        keepG += gridPts[i]
                        keepI += imgPts[i]
                    }
                }
                if (keepG.size >= max(8, gridPts.size / 3)) next = BoardHomography.fit(keepG, keepI) ?: next
            }
            h = next
        }
        return h
    }

    /**
     * 판의 범위를 **한 줄** 단위로 바로잡는다 — 격자 전체를 가로·세로로 −1·0·+1줄 옮긴 아홉 후보 중, 바깥 격자선 바로
     * **안쪽에는 가로지르는 선이 있고 바깥쪽에는 없는** 정도가 가장 큰 것을 고른다.
     *
     * ⚠️ **실물 판의 나무 가장자리**가 바깥 격자선에서 한 칸쯤 밖에 있고, 그 그림자가 가는 선처럼 보여 바깥 격자선으로
     * 잡혔다(기준 이미지 01: 판 전체가 한 칸 왼쪽·아래로). 격자선은 바깥 격자선에서 **끝나므로**, 진짜 바깥선 밖으로는
     * 가로지르는 선이 없고, 나무 가장자리 바로 안쪽(여백)에도 없다 — 이 차이로 가른다.
     */
    private fun adjustExtent(g: Gray, n: Int, h: BoardHomography): BoardHomography {
        fun shifted(du: Int, dv: Int): BoardHomography? = BoardHomography.fit(
            listOf(PointF2D(0f, 0f), PointF2D(n - 1f, 0f), PointF2D(n - 1f, n - 1f), PointF2D(0f, n - 1f)),
            listOf(h.map(du.toDouble(), dv.toDouble()), h.map(n - 1.0 + du, dv.toDouble()), h.map(n - 1.0 + du, n - 1.0 + dv), h.map(du.toDouble(), n - 1.0 + dv)),
        )
        var best = h
        var bestScore = terminationScore(g, n, h)
        for (du in -1..1) {
            for (dv in -1..1) {
                if (du == 0 && dv == 0) continue
                val c = shifted(du, dv) ?: continue
                val s = terminationScore(g, n, c)
                if (s > bestScore * 1.05f + 0.5f) {
                    bestScore = s
                    best = c
                }
            }
        }
        return best
    }

    /**
     * 네 변마다: 바깥 격자선 바로 안쪽(0.3~0.7칸)의 가로지르는 선 증거 − 바로 바깥쪽(−0.7~−0.3칸)의 증거, 그 합.
     * 증거는 그 자리 선 방향에 수직으로 잰 "가는 어두운 선"의 비율이다(돌이 덮은 자리는 0에 가깝다 — 안쪽만 줄 뿐 바깥을 키우지 않는다).
     */
    private fun terminationScore(g: Gray, n: Int, h: BoardHomography): Float {
        fun crossingLine(k: Double, j: Double, alongK: Boolean): Float {
            // alongK = true면 가로선(행 j)을, false면 세로선(열 k)을 그 자리에서 잰다.
            val p = h.map(k, j)
            val dir = if (alongK) direction(h, k - 0.2, j, k + 0.2, j) else direction(h, k, j - 0.2, k, j + 0.2)
            val nx = -dir.second
            val ny = dir.first
            val c = g.bilinear(p.x, p.y) ?: return 0f
            val a = g.bilinear(p.x - 2 * nx, p.y - 2 * ny) ?: return 0f
            val b = g.bilinear(p.x + 2 * nx, p.y + 2 * ny) ?: return 0f
            val side = (a + b) / 2f
            return max(0f, side - c) / max(side, 16f) * 100f
        }
        var score = 0f
        val offsets = doubleArrayOf(0.3, 0.4, 0.5, 0.6, 0.7)
        for (j in 0 until n) {
            for (t in offsets) {
                // 왼쪽 변(열 0)·오른쪽 변(열 n−1): 행 j의 가로선이 안쪽엔 있고 바깥엔 없어야 한다.
                score += crossingLine(t, j.toDouble(), true) - crossingLine(-t, j.toDouble(), true)
                score += crossingLine(n - 1 - t, j.toDouble(), true) - crossingLine(n - 1 + t, j.toDouble(), true)
                // 위쪽 변(행 0)·아래쪽 변(행 n−1): 열 j의 세로선.
                score += crossingLine(j.toDouble(), t, false) - crossingLine(j.toDouble(), -t, false)
                score += crossingLine(j.toDouble(), n - 1 - t, false) - crossingLine(j.toDouble(), n - 1 + t, false)
            }
        }
        score /= (n * offsets.size)

        // 여백 색: 바깥 격자선 바로 밖(0.38칸)은 **판 여백 — 안쪽과 같은 나무**여야 한다. 탁자·배경 색이면 그 "바깥선"은
        // 실은 판의 나무 가장자리다(01: 가장자리 그림자를 바깥선으로 잡았다). 인쇄는 안팎이 같은 종이라 아무 말도 안 한다.
        fun colorAt(k: Double, j: Double): Pair<Float, Float>? {
            val p = h.map(k, j)
            val l = g.bilinear(p.x, p.y) ?: return null
            return l to (g.saturationAt(p.x, p.y) ?: return null)
        }
        fun medianPair(list: List<Pair<Float, Float>>): Pair<Float, Float>? {
            if (list.isEmpty()) return null
            return list.map { it.first }.sorted()[list.size / 2] to list.map { it.second }.sorted()[list.size / 2]
        }
        var marginPenalty = 0f
        val m = n - 1.0
        for (side in 0 until 4) {
            val outside = ArrayList<Pair<Float, Float>>()
            val inside = ArrayList<Pair<Float, Float>>()
            for (j in 1 until n - 1) {
                val (ok, oj, ik, ij) = when (side) {
                    0 -> listOf(-0.38, j.toDouble(), 0.5, j - 0.5) // 왼쪽
                    1 -> listOf(m + 0.38, j.toDouble(), m - 0.5, j - 0.5) // 오른쪽
                    2 -> listOf(j.toDouble(), -0.38, j - 0.5, 0.5) // 위
                    else -> listOf(j.toDouble(), m + 0.38, j - 0.5, m - 0.5) // 아래
                }
                colorAt(ok, oj)?.let { outside += it }
                colorAt(ik, ij)?.let { inside += it }
            }
            val o = medianPair(outside) ?: continue
            val i = medianPair(inside) ?: continue
            marginPenalty += abs(o.first - i.first) / 48f + abs(o.second - i.second) / 0.15f
        }
        return score - 2f * marginPenalty
    }

    private fun direction(h: BoardHomography, x0: Double, y0: Double, x1: Double, y1: Double): Pair<Float, Float> {
        val a = h.map(x0, y0)
        val b = h.map(x1, y1)
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().takeIf { it > 1e-3f } ?: return 1f to 0f
        return dx / len to dy / len
    }

    /**
     * 점 [p]를 지나 [along] 방향으로 뻗은 선을 [normal] 방향으로 ±[search]만큼 밀어 보며, 선 위(±[half])의
     * 증거 합이 가장 큰 이동량. 증거가 고르게 약하면 `null`(그 교점은 이 방향으로 근거가 없다).
     * 증거 = 가는 어두운 선(법선 방향 ±2px보다 어두운 비율) + 돌 크기 덩어리 중심 반응.
     */
    private fun bestShift(
        g: Gray,
        stone: StoneResponse,
        p: PointF2D,
        along: Pair<Float, Float>,
        normal: Pair<Float, Float>,
        search: Float,
        half: Float,
    ): Float? {
        val (ax, ay) = along
        val (nx, ny) = normal
        var best = Float.NEGATIVE_INFINITY
        var bestT = 0f
        var total = 0f
        var count = 0
        var t = -search
        while (t <= search + 1e-3f) {
            var s = 0f
            var w = -half
            while (w <= half) {
                val x = p.x + nx * t + ax * w
                val y = p.y + ny * t + ay * w
                val c = g.bilinear(x, y)
                val l = g.bilinear(x - 2 * nx, y - 2 * ny)
                val r = g.bilinear(x + 2 * nx, y + 2 * ny)
                if (c != null && l != null && r != null) {
                    val side = (l + r) / 2f
                    s += max(0f, side - c) / max(side, 16f) * 100f
                }
                s += stone.at(x.roundToInt(), y.roundToInt())
                w += 1f
            }
            total += s
            count++
            if (s > best) {
                best = s
                bestT = t
            }
            t += 0.5f
        }
        if (count == 0) return null
        return if (best > (total / count) * 1.15f) bestT else null
    }

    // ---------------------------------------------------------------------------------------------
    // 공용

    private fun unitCorners(n: Int) =
        listOf(PointF2D(0f, 0f), PointF2D(n - 1f, 0f), PointF2D(n - 1f, n - 1f), PointF2D(0f, n - 1f))

    private fun cornersOf(h: BoardHomography, n: Int, toSource: Float) = BoardCornerPoints(
        topLeft = h.map(0.0, 0.0).scaled(toSource),
        topRight = h.map((n - 1).toDouble(), 0.0).scaled(toSource),
        bottomRight = h.map((n - 1).toDouble(), (n - 1).toDouble()).scaled(toSource),
        bottomLeft = h.map(0.0, (n - 1).toDouble()).scaled(toSource),
    )

    private fun localPitch(h: BoardHomography, k: Int, j: Int): Float {
        val p = h.map(k.toDouble(), j.toDouble())
        val q = h.map(k + 1.0, j.toDouble())
        val r = h.map(k.toDouble(), j + 1.0)
        return ((hypot((q.x - p.x).toDouble(), (q.y - p.y).toDouble()) + hypot((r.x - p.x).toDouble(), (r.y - p.y).toDouble())) / 2).toFloat()
    }

    private fun PointF2D.scaled(f: Float) = PointF2D(x * f, y * f)

    private fun quantile(a: FloatArray, q: Float): Float {
        if (a.isEmpty()) return 0f
        val sorted = a.sortedArray()
        return sorted[((sorted.size - 1) * q).toInt()]
    }

    // ---------------------------------------------------------------------------------------------
    // 증거 지도와 투영

    /** U = 세로선 무리(가로로 늘어선다), V = 가로선 무리(세로로 늘어선다). */
    internal enum class Family { U, V }

    /** 판이 있는 볼록 사각형 — 2차 투영은 그 안의 픽셀만 센다. */
    private class QuadMask(private val pts: List<PointF2D>) {
        fun contains(x: Float, y: Float): Boolean {
            var sign = 0
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
                val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
                if (s != 0) {
                    if (sign == 0) sign = s else if (s != sign) return false
                }
            }
            return true
        }
    }

    /** 영상 중심을 원점으로 [angle]만큼 돌린 좌표계 — u = (x−cx)cos + (y−cy)sin, v = −(x−cx)sin + (y−cy)cos. */
    internal class RotatedFrame(private val cx: Float, private val cy: Float, angle: Float) {
        private val c = cos(angle * PI.toFloat() / 180f)
        private val s = sin(angle * PI.toFloat() / 180f)
        fun toImage(u: Float, v: Float) = PointF2D(cx + u * c - v * s, cy + u * s + v * c)
    }

    /**
     * 증거 지도 셋: 가는 어두운 선(등방성, 그 자리 밝기 대비), 경계 세기(주기 전용), 돌 크기 덩어리 중심(칸 간격을 안 뒤에).
     * 투영 좌표: U 무리는 ρ = (x−cx)cos a + (y−cy)sin a, V 무리는 ρ = −(x−cx)sin a + (y−cy)cos a.
     */
    private class EvidenceMaps(val g: Gray) {
        val w = g.w
        val h = g.h
        private val cx = w / 2f
        private val cy = h / 2f
        val rhoMin = -ceil(hypot(cx.toDouble(), cy.toDouble())).toFloat() - 2f
        private val size = (-2 * rhoMin).toInt() + 1

        /** 가는 어두운 선 — 둘레 8점(거리 2)의 평균보다 가운데가 어두운 비율. 큰 돌 속은 고르게 어두워 반응이 없다. */
        val ridge = FloatArray(w * h)

        var stone: StoneResponse? = null
            private set

        /** 기울기 추정용: 선 지도에서 센 픽셀만(속도). */
        private val strongRidge: IntArray

        init {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val c = g.at(x, y)
                    var sum = 0f
                    for ((dx, dy) in Ring8) sum += g.at(x + dx, y + dy)
                    val around = sum / 8f
                    ridge[y * w + x] = max(0f, around - c) / max(around, 16f) * 100f
                }
            }
            val cut = quantile(ridge, 0.85f)
            strongRidge = ridge.indices.filter { ridge[it] > cut && ridge[it] > 2f }.toIntArray()
        }

        fun attachStones(pitch: Float) {
            stone = StoneResponse(g, pitch)
        }

        private fun rho(family: Family, x: Float, y: Float, cosA: Float, sinA: Float): Float =
            if (family == Family.U) (x - cx) * cosA + (y - cy) * sinA else -(x - cx) * sinA + (y - cy) * cosA

        fun ridgeEnergy(family: Family, angle: Float): Double {
            val a = angle * PI.toFloat() / 180f
            val c = cos(a)
            val s = sin(a)
            val prof = FloatArray(size)
            for (i in strongRidge) {
                val x = (i % w).toFloat()
                val y = (i / w).toFloat()
                val r = (rho(family, x, y, c, s) - rhoMin).roundToInt()
                if (r in prof.indices) prof[r] += ridge[i]
            }
            return prof.fold(0.0) { e, v -> e + v.toDouble() * v }
        }

        /**
         * 주기 추정용 윤곽 — 그 무리의 **법선 방향** 기울기 세기(±1px) + 가는 선(±2px), 각자 최댓값으로 맞춰 더한다(절대값).
         */
        fun periodProfile(family: Family, angle: Float): FloatArray {
            val a = angle * PI.toFloat() / 180f
            val nx = if (family == Family.U) cos(a) else -sin(a)
            val ny = if (family == Family.U) sin(a) else cos(a)
            val e = project(family, angle, null) { i ->
                val x = (i % w).toFloat()
                val y = (i / w).toFloat()
                val c = g.v[i]
                abs((g.bilinear(x + nx, y + ny) ?: c) - (g.bilinear(x - nx, y - ny) ?: c))
            }
            val r = project(family, angle, null) { i ->
                val x = (i % w).toFloat()
                val y = (i / w).toFloat()
                val c = g.v[i]
                val l = g.bilinear(x - 2 * nx, y - 2 * ny) ?: c
                val rr = g.bilinear(x + 2 * nx, y + 2 * ny) ?: c
                max(0f, (l + rr) / 2f - c)
            }
            val me = e.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val mr = r.maxOrNull()?.takeIf { it > 0f } ?: 1f
            return FloatArray(size) { i -> e[i] / me + r[i] / mr }
        }

        /** 기울기 [angle]로 투영한 선 + 돌 윤곽(각자 95분위로 맞춰 더한다). [mask] 안의 픽셀만. 인덱스 ↔ ρ = i + [rhoMin]. */
        fun profile(family: Family, angle: Float, mask: QuadMask?): FloatArray {
            val st = stone!!
            val ridgeQ = ridgeQ95
            val stoneQ = st.quantile95().takeIf { it > 0f } ?: 1f
            return project(family, angle, mask) { ridge[it] / ridgeQ + st.atIndex(it) / stoneQ }
        }

        private val ridgeQ95: Float by lazy { quantile(ridge, 0.95f).takeIf { it > 0f } ?: 1f }

        /** 기울기 [angle]의 (u, v) ↔ 영상 좌표. u는 U 무리의 ρ, v는 V 무리의 ρ다. */
        fun frame(angle: Float) = RotatedFrame(cx, cy, angle)

        private inline fun project(family: Family, angle: Float, mask: QuadMask?, value: (Int) -> Float): FloatArray {
            val a = angle * PI.toFloat() / 180f
            val c = cos(a)
            val s = sin(a)
            val prof = FloatArray(size)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (mask != null && !mask.contains(x.toFloat(), y.toFloat())) continue
                    val r = (rho(family, x.toFloat(), y.toFloat(), c, s) - rhoMin).roundToInt()
                    if (r in prof.indices) prof[r] += value(y * w + x)
                }
            }
            return prof
        }

        companion object {
            private val Ring8 = listOf(-2 to -2, 0 to -2, 2 to -2, -2 to 0, 2 to 0, -2 to 2, 0 to 2, 2 to 2)
        }
    }

    /**
     * 돌 크기 덩어리 중심 반응 — |작은 흐림 − 큰 흐림|을 큰 흐림 밝기로 나눈 비율. 흑돌(주변보다 어둡다)·백돌(밝다)
     * 모두 중심에서 커지고, 가는 격자선은 작은 흐림에서 이미 지워져 반응이 작다.
     */
    internal class StoneResponse(g: Gray, pitch: Float) {
        private val w = g.w
        private val h = g.h
        private val values: FloatArray

        init {
            val small = g.boxBlurred(max(1, (pitch * 0.18f).roundToInt()))
            val large = g.boxBlurred(max(2, (pitch * 0.55f).roundToInt()))
            values = FloatArray(w * h) { i -> abs(small.v[i] - large.v[i]) / max(large.v[i], 16f) * 100f }
        }

        fun at(x: Int, y: Int): Float = if (x in 0 until w && y in 0 until h) values[y * w + x] else 0f
        fun atIndex(i: Int): Float = values[i]
        fun quantile95(): Float = quantile(values, 0.95f)
    }

    /** 밝기 영상(작업 해상도). [sat]은 채도(0~1) — 판 여백과 탁자를 가를 때만 쓴다(흐린 사본에는 없다). */
    internal class Gray(val w: Int, val h: Int, val v: FloatArray, val sat: FloatArray? = null) {
        fun at(x: Int, y: Int): Float = v[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]

        /** 가장 가까운 픽셀의 채도. 영상 밖이거나 채도가 없으면 `null`. */
        fun saturationAt(x: Float, y: Float): Float? {
            val s = sat ?: return null
            val xi = x.roundToInt()
            val yi = y.roundToInt()
            return if (xi in 0 until w && yi in 0 until h) s[yi * w + xi] else null
        }

        /** 영상 밖이면 `null`. */
        fun bilinear(x: Float, y: Float): Float? {
            if (x < 0f || y < 0f || x > w - 1 || y > h - 1) return null
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val x1 = min(x0 + 1, w - 1)
            val y1 = min(y0 + 1, h - 1)
            val fx = x - x0
            val fy = y - y0
            val top = v[y0 * w + x0] + (v[y0 * w + x1] - v[y0 * w + x0]) * fx
            val bottom = v[y1 * w + x0] + (v[y1 * w + x1] - v[y1 * w + x0]) * fx
            return top + (bottom - top) * fy
        }

        /** 상자 흐림 세 번(가우스 근사), 반지름 [r]. */
        fun boxBlurred(r: Int): Gray {
            var cur = v.copyOf()
            repeat(3) {
                cur = blurPass(cur, w, h, r, horizontal = true)
                cur = blurPass(cur, w, h, r, horizontal = false)
            }
            return Gray(w, h, cur)
        }

        companion object {
            fun of(source: PixelSource): Gray {
                val out = FloatArray(source.width * source.height)
                val sat = FloatArray(source.width * source.height)
                for (y in 0 until source.height) {
                    for (x in 0 until source.width) {
                        val p = source.getPixel(x, y)
                        out[y * source.width + x] = if (p == BoardWarp.NoData) 255f else luma(p)
                        sat[y * source.width + x] = saturation((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
                    }
                }
                return Gray(source.width, source.height, out, sat)
            }

            /** 긴 변이 [maxDim] 이하가 되도록 정수배 상자 평균으로 줄인다. 반환값의 두 번째는 축척(작업/원본). */
            fun downscaled(source: PixelSource, maxDim: Int): Pair<Gray, Float> {
                val f = max(1, ceil(max(source.width, source.height).toFloat() / maxDim).toInt())
                if (f == 1) return of(source) to 1f
                val w = source.width / f
                val h = source.height / f
                val out = FloatArray(w * h)
                val sat = FloatArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        var r = 0
                        var gg = 0
                        var b = 0
                        for (dy in 0 until f) {
                            for (dx in 0 until f) {
                                val p = source.getPixel(x * f + dx, y * f + dy)
                                r += (p shr 16) and 0xFF
                                gg += (p shr 8) and 0xFF
                                b += p and 0xFF
                            }
                        }
                        val k = f * f
                        out[y * w + x] = 0.299f * r / k + 0.587f * gg / k + 0.114f * b / k
                        sat[y * w + x] = saturation(r / k, gg / k, b / k)
                    }
                }
                return Gray(w, h, out, sat) to 1f / f
            }

            private fun saturation(r: Int, g: Int, b: Int): Float {
                val hi = max(r, max(g, b))
                val lo = min(r, min(g, b))
                return if (hi > 0) (hi - lo).toFloat() / hi else 0f
            }

            private fun luma(p: Int): Float =
                0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)

            private fun blurPass(src: FloatArray, w: Int, h: Int, r: Int, horizontal: Boolean): FloatArray {
                val out = FloatArray(src.size)
                val len = if (horizontal) w else h
                val lines = if (horizontal) h else w
                val window = 2 * r + 1
                for (line in 0 until lines) {
                    fun idx(i: Int): Int = if (horizontal) line * w + i.coerceIn(0, w - 1) else i.coerceIn(0, h - 1) * w + line
                    var sum = 0f
                    for (i in -r..r) sum += src[idx(i)]
                    for (i in 0 until len) {
                        out[if (horizontal) line * w + i else i * w + line] = sum / window
                        sum += src[idx(i + r + 1)] - src[idx(i - r)]
                    }
                }
                return out
            }
        }
    }
}
