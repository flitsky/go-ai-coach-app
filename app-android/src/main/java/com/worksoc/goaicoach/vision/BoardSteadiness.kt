package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.abs

/**
 * 라이브 카메라의 「판이 잡혀 멈췄는가」(백로그 #263 단계 4) — 멈추면 자동으로 찍는다.
 *
 * 미리보기 프레임마다 찾은 판의 네 모서리를 **프레임 크기로 나눈 비율 좌표**(0~1)로 넣는다. 최근 [holdMillis] 동안
 * 모서리가 [tolerance] 안에서만 움직였고 그 사이 판을 [minSightings]번 넘게 봤으면 [State.Steady]다. 판을 놓치면 처음부터 다시 센다.
 * ⚠️ 0.9초 · 3% · 3번은 스레드가 정한 값이다 — 폰에서 손떨림·찍히는 순간을 보고 고친다.
 */
internal class BoardSteadiness(
    private val holdMillis: Long = 900,
    private val tolerance: Float = 0.03f,
    private val minSightings: Int = 3,
) {
    enum class State { Searching, Found, Steady }

    companion object {
        /**
         * 판이 프레임의 이만큼(넓이 비율)은 덮어야 판으로 친다 — 사용자는 판을 비춘다. 에뮬레이터의 가상 거실에서 TV 화면의 체크무늬
         * 일부(프레임의 약 4%)를 판으로 잡았다(2026-10-13). ⚠️ 20%는 스레드가 정한 값이다.
         */
        const val MinAreaFraction = 0.2f

        /** 비율 좌표 사각형(TL TR BR BL)의 넓이 — 신발끈 공식. */
        fun areaOf(corners: List<PointF2D>): Float {
            var twice = 0f
            for (i in corners.indices) {
                val a = corners[i]
                val b = corners[(i + 1) % corners.size]
                twice += a.x * b.y - b.x * a.y
            }
            return abs(twice) / 2f
        }

        /** 판으로 칠 만큼 큰가. */
        fun isLargeEnough(corners: List<PointF2D>): Boolean = areaOf(corners) >= MinAreaFraction
    }

    private class Sighting(val at: Long, val corners: List<PointF2D>)

    private val sightings = ArrayDeque<Sighting>()

    /** 프레임 하나의 결과 — 판을 못 찾았으면 [corners]가 `null`. */
    fun feed(corners: List<PointF2D>?, nowMillis: Long): State {
        if (corners == null) {
            sightings.clear()
            return State.Searching
        }
        val latest = Sighting(nowMillis, corners)
        // 마지막 모서리에서 멀리 벗어난 옛 목격은 버린다 — 판을 옮겼다.
        while (sightings.isNotEmpty() && !close(sightings.first().corners, corners)) sightings.removeFirst()
        sightings.addLast(latest)
        while (sightings.size > 1 && nowMillis - sightings[1].at >= holdMillis) sightings.removeFirst()
        val span = nowMillis - sightings.first().at
        return if (sightings.size >= minSightings && span >= holdMillis) State.Steady else State.Found
    }

    fun reset() = sightings.clear()

    private fun close(a: List<PointF2D>, b: List<PointF2D>): Boolean =
        a.indices.all { abs(a[it].x - b[it].x) <= tolerance && abs(a[it].y - b[it].y) <= tolerance }
}
