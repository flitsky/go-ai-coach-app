package com.worksoc.goaicoach.vision

import kotlin.math.max
import kotlin.math.min

/**
 * 교점 조각 — 신경망 돌 판정([StoneNet])의 입력(백로그 #263 단계 5).
 *
 * 반듯하게 편 판([RectifiedBoard])에서 교점마다 ±[HalfCells]칸을 [Size]×[Size] RGB로 잘라 낸다. 학습용 내보내기(`StonePatchExportTest`)와
 * 앱의 판정이 **같은 함수**를 쓴다 — 그래야 맥에서 배운 것이 폰에서 같은 값을 본다.
 *
 * 정규화: 교점을 둘러싼 4×4 칸의 **칸 한가운데 색**(돌이 덮지 못하는 자리 — [GridStoneDetector]와 같은 생각)의 채널별 중앙값을 바탕으로 빼고
 * [Scale]로 나눈다. 조명이 판 위에서 고르지 않아도 「바탕보다 밝다/어둡다/채도가 빠졌다」가 같은 값으로 들어간다. 사진 밖([BoardWarp.NoData])은 0(= 바탕).
 */
internal object StonePatches {
    /** 조각 한 변(px). */
    const val Size = 24

    /** 교점에서 조각 끝까지(칸). 1.5칸 폭 — 비스듬한 사진에서 돌이 떠 보이는 만큼(최대 0.4칸)도 담긴다. */
    const val HalfCells = 0.75f

    const val Channels = 3

    /** 정규화 나눗수(밝기 단위). */
    const val Scale = 64f

    /** 칸 한가운데 표본의 반지름(칸) — [GridStoneDetector]와 같다. */
    private const val CenterRadiusCells = 0.14f

    /** 판 하나의 교점 조각들 — 행 우선(row * n + col), 조각마다 채널 우선 [Channels × Size × Size]. */
    class Patches(val n: Int, val raw: Array<IntArray>, val background: Array<FloatArray>) {
        /** 조각의 채널 [c](0 R · 1 G · 2 B) 값 [i] — 사진 밖은 바탕을 반올림한 값(학습 데이터 내보내기와 같다). */
        fun value(index: Int, c: Int, i: Int): Int {
            val p = raw[index][i]
            return if (p == BoardWarp.NoData) Math.round(background[index][c]) else (p shr (16 - 8 * c)) and 0xFF
        }

        /** 신경망 입력 — (값 − 바탕) ÷ [Scale], 채널 우선. 사진 밖은 바탕과 거의 같다(0 근처). */
        fun normalized(index: Int): FloatArray {
            val bg = background[index]
            val out = FloatArray(Channels * Size * Size)
            for (c in 0 until Channels) {
                for (i in 0 until Size * Size) out[c * Size * Size + i] = (value(index, c, i) - bg[c]) / Scale
            }
            return out
        }
    }

    fun extract(board: RectifiedBoard): Patches {
        val n = board.boardSize.value
        val src = board.pixels
        val cells = n - 1
        // 칸 한가운데 색(채널별 중앙값)
        val cellColor = Array(cells * cells) { FloatArray(3) }
        val r = CenterRadiusCells * board.step
        val reach = r.toInt() + 1
        val chans = Array(3) { ArrayList<Float>() }
        for (cr in 0 until cells) {
            for (cc in 0 until cells) {
                chans.forEach { it.clear() }
                val cx = board.origin + (cc + 0.5f) * board.step
                val cy = board.origin + (cr + 0.5f) * board.step
                for (dy in -reach..reach) {
                    for (dx in -reach..reach) {
                        if (dx * dx + dy * dy > r * r) continue
                        val x = Math.round(cx + dx)
                        val y = Math.round(cy + dy)
                        if (x !in 0 until src.width || y !in 0 until src.height) continue
                        val p = src.getPixel(x, y)
                        if (p == BoardWarp.NoData) continue
                        chans[0] += ((p shr 16) and 0xFF).toFloat()
                        chans[1] += ((p shr 8) and 0xFF).toFloat()
                        chans[2] += (p and 0xFF).toFloat()
                    }
                }
                for (c in 0..2) cellColor[cr * cells + cc][c] = median(chans[c])
            }
        }
        val raw = Array(n * n) { IntArray(Size * Size) }
        val background = Array(n * n) { FloatArray(3) }
        val span = 2 * HalfCells * board.step
        for (row in 0 until n) {
            for (col in 0 until n) {
                val idx = row * n + col
                // 바탕: 교점을 둘러싼 4×4 칸(판 밖 칸은 뺀다)
                for (c in 0..2) chans[c].clear()
                for (cr in max(0, row - 2)..min(cells - 1, row + 1)) {
                    for (cc in max(0, col - 2)..min(cells - 1, col + 1)) {
                        for (c in 0..2) chans[c] += cellColor[cr * cells + cc][c]
                    }
                }
                for (c in 0..2) background[idx][c] = median(chans[c])
                val cx = board.origin + col * board.step
                val cy = board.origin + row * board.step
                val px = raw[idx]
                for (j in 0 until Size) {
                    val y = cy - HalfCells * board.step + (j + 0.5f) * span / Size
                    for (i in 0 until Size) {
                        val x = cx - HalfCells * board.step + (i + 0.5f) * span / Size
                        px[j * Size + i] = BoardWarp.sampleBilinear(src, x - 0.5f, y - 0.5f)
                    }
                }
            }
        }
        return Patches(n, raw, background)
    }

    private fun median(values: MutableList<Float>): Float {
        if (values.isEmpty()) return 0f
        values.sort()
        return values[values.size / 2]
    }
}
