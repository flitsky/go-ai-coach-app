package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 라이브 카메라는 프레임을 긴 변 360px로 줄여 판을 찾는다(백로그 #263 — `LiveBoardAnalyzer`). 그 크기에서도 판을 찾는가 —
 * 모서리가 0.75칸 안이면 미리보기 위 테두리로 충분하다(안내용이다 — 정밀 인식은 찍은 사진으로 다시 한다. 02가 0.55칸).
 *
 * 라이브에서는 사용자가 판으로 화면을 채운다 — 그래서 기준 이미지를 **판 둘레로 잘라**(판이 폭·높이의 약 75%) 줄인다.
 * 사진 전체를 그대로 줄이면 01(판이 폭의 절반)·02(옆에 중계 패널)·05(도면 둘)는 한 칸이 10px 아래로 떨어져 못 찾는다(2026-10-13).
 */
class LiveLocateScaleTest {

    @Test
    fun boardsAreFoundAt360px() {
        val failures = mutableListOf<String>()
        var anyImage = false
        BoardPhotoFixture.all().forEach { f ->
            val img = f.image ?: return@forEach
            anyImage = true
            // 판 둘레로 자른다 — 판이 잘린 사진의 약 75%가 되게(사방으로 판 폭의 1/6씩), 사진 밖은 판 밖 회색으로.
            val xs = f.corners.toList().map { it.x }
            val ys = f.corners.toList().map { it.y }
            val margin = maxOf(xs.max() - xs.min(), ys.max() - ys.min()) / 6f
            val left = xs.min() - margin
            val top = ys.min() - margin
            val cropW = xs.max() - xs.min() + 2 * margin
            val cropH = ys.max() - ys.min() + 2 * margin
            val factor = 360f / max(cropW, cropH)
            val w = (cropW * factor).roundToInt()
            val h = (cropH * factor).roundToInt()
            val small = ArrayPixelSource(w, h, IntArray(w * h) { i ->
                val sx = left + (i % w + 0.5f) / factor
                val sy = top + (i / w + 0.5f) / factor
                if (sx < 0 || sy < 0 || sx > img.width - 1 || sy > img.height - 1) 0xFF8A8A8A.toInt() else BoardWarp.sampleBilinear(img, sx - 0.5f, sy - 0.5f)
            })
            val located = BoardLocator.locate(small)
            fun moved(p: PointF2D) = PointF2D((p.x - left) * factor, (p.y - top) * factor)
            val answer = BoardCornerPoints(moved(f.corners.topLeft), moved(f.corners.topRight), moved(f.corners.bottomRight), moved(f.corners.bottomLeft))
            val cell = hypot(answer.topRight.x - answer.topLeft.x, answer.topRight.y - answer.topLeft.y) / (f.boardSize.value - 1)
            val error = located?.corners?.toList()?.zip(answer.toList())?.maxOf { (p, q) -> hypot(p.x - q.x, p.y - q.y) }?.div(cell)
            println("${f.key} ${w}x$h: ${if (located == null) "못 찾음" else "모서리 오차 ${"%.2f".format(error)}칸 · ${located.boardSize.value}줄"}")
            if (located == null || located.boardSize != f.boardSize || error!! > 0.75f) failures += "${f.key} 360px에서 판을 제대로 못 찾았다"
        }
        assumeTrue("기준 이미지가 없다 — scripts/fetch-board-photo-fixtures.sh", anyImage)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
