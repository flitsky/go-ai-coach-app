package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.abs

/**
 * 평면 투시 변환(3x3, `h[8] = 1`) — 순수 Kotlin(백로그 #210).
 *
 * ⚠️ **안드로이드 `Matrix.setPolyToPoly`를 쓰지 않는다** — 그건 JVM 단위 테스트에서 돌지 않아서, 기준 이미지로
 * 인식률을 재는 테스트가 **실제 경로를 밟지 못한다.** 같은 계산을 앱과 테스트가 함께 쓰게 하려고 직접 둔다.
 */
internal class BoardHomography private constructor(private val h: DoubleArray) {

    fun map(x: Double, y: Double): PointF2D {
        val w = h[6] * x + h[7] * y + 1.0
        return PointF2D(
            ((h[0] * x + h[1] * y + h[2]) / w).toFloat(),
            ((h[3] * x + h[4] * y + h[5]) / w).toFloat(),
        )
    }

    companion object {
        /**
         * [from]의 점을 [to]의 점으로 보내는 변환. 점이 넷이면 정확히, 넷보다 많으면 최소제곱으로 맞춘다.
         * 퇴화(세 점이 한 줄 등)면 `null`.
         */
        fun fit(from: List<PointF2D>, to: List<PointF2D>): BoardHomography? {
            require(from.size == to.size && from.size >= 4) { "점은 같은 개수, 넷 이상이어야 한다" }
            // 정규방정식 AᵀA h = Aᵀb (8x8). 점 넷이면 A가 정방이라 해와 같다.
            val ata = Array(8) { DoubleArray(8) }
            val atb = DoubleArray(8)
            fun accumulate(row: DoubleArray, rhs: Double) {
                for (i in 0 until 8) {
                    atb[i] += row[i] * rhs
                    for (j in 0 until 8) ata[i][j] += row[i] * row[j]
                }
            }
            for (k in from.indices) {
                val x = from[k].x.toDouble()
                val y = from[k].y.toDouble()
                val u = to[k].x.toDouble()
                val v = to[k].y.toDouble()
                accumulate(doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u), u)
                accumulate(doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v), v)
            }
            val solution = solve(ata, atb) ?: return null
            return BoardHomography(solution + 1.0)
        }

        /** 부분 피벗 가우스 소거. 특이하면 `null`. */
        private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            val m = Array(n) { i -> a[i].copyOf() + b[i] }
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
                if (abs(m[pivot][col]) < 1e-12) return null
                val tmp = m[col]
                m[col] = m[pivot]
                m[pivot] = tmp
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = m[r][col] / m[col][col]
                    if (f == 0.0) continue
                    for (c in col until n + 1) m[r][c] -= f * m[col][c]
                }
            }
            return DoubleArray(n) { i -> m[i][n] / m[i][i] }
        }
    }
}
