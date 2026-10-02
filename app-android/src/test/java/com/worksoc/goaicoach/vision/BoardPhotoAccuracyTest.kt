package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 바둑판 사진 인식률 — 기준 이미지 다섯 장(백로그 #210, 통과 기준은 사용자 확정 2026-10-02).
 *
 * - **정답 모서리**(사람이 잰 바깥 격자선 교차점)로 돌 판정: 01·02·03 ≥ 99.5%
 * - **자동 인식**(모서리를 스스로 찾고 돌 판정): 01·02·03 ≥ 98%, 판 크기도 맞아야 한다
 * - **핀이 0.3칸씩 어긋난 경우**(모서리마다 다른 방향) → [BoardLocator.refine]으로 붙인 뒤: 01·02·03 ≥ 99.5%
 * - 인쇄 기보 04·05는 사용자 결정대로 **측정만** 하되, 처음 잰 값(지금 모두 100%)을 **기준선**으로 걸어 둔다(98%).
 *
 * ⚠️ 이미지가 없는 기계(이미지는 저장소에 없다 — `scripts/fetch-board-photo-fixtures.sh`)에서는 **건너뛴다**.
 * 2026-10-02 처음 맞췄을 때: 옛 검출기는 정답 모서리로도 01 74.8% · 03 66.2%였고, 자동 인식은 없었다.
 */
class BoardPhotoAccuracyTest {

    private val fixtures = BoardPhotoFixture.all()

    private fun image(f: BoardPhotoFixture): ArrayPixelSource {
        val img = f.image
        assumeTrue("기준 이미지가 없다 — scripts/fetch-board-photo-fixtures.sh를 먼저 돌릴 것(${f.imageName})", img != null)
        return img!!
    }

    private fun accuracy(f: BoardPhotoFixture, image: ArrayPixelSource, corners: BoardCornerPoints): Pair<Float, List<String>> {
        val stones = GridStoneDetector.detect(BoardWarp.rectify(image, corners, f.boardSize)).stones
        val (correct, wrong) = f.score(stones)
        return correct.toFloat() / (f.boardSize.value * f.boardSize.value) to wrong
    }

    private fun gate(f: BoardPhotoFixture, gated: Float, baseline: Float) = if (f.gated) gated else baseline

    @Test
    fun manualCornersClassifyTheStones() {
        fixtures.forEach { f ->
            val (acc, wrong) = accuracy(f, image(f), f.corners)
            assertTrue("${f.key} 정답 모서리 인식률 ${"%.1f".format(acc * 100)}% — 틀린 점 $wrong", acc >= gate(f, 0.995f, 0.98f))
        }
    }

    @Test
    fun automaticDetectionFindsTheBoard() {
        fixtures.forEach { f ->
            val img = image(f)
            val located = BoardLocator.locate(img)
            assertTrue("${f.key} 판을 못 찾았다", located != null)
            assertEquals("${f.key} 판 크기", f.boardSize, located!!.boardSize)
            val (acc, wrong) = accuracy(f, img, located.corners)
            assertTrue(
                "${f.key} 자동 인식률 ${"%.1f".format(acc * 100)}% — 모서리 ${located.corners} 정답 ${f.corners} 틀린 점 $wrong",
                acc >= gate(f, 0.98f, 0.98f),
            )
        }
    }

    @Test
    fun misplacedPinsSnapBackToTheGrid() {
        fixtures.forEach { f ->
            val img = image(f)
            val c = f.corners
            val step = (hypot(c.topRight.x - c.topLeft.x, c.topRight.y - c.topLeft.y) / (f.boardSize.value - 1)) * 0.3f
            val pins = BoardCornerPoints(
                PointF2D(c.topLeft.x + step, c.topLeft.y - step),
                PointF2D(c.topRight.x + step, c.topRight.y + step),
                PointF2D(c.bottomRight.x - step, c.bottomRight.y + step),
                PointF2D(c.bottomLeft.x - step, c.bottomLeft.y - step),
            )
            val refined = BoardLocator.refine(img, pins, f.boardSize)
            val (acc, wrong) = accuracy(f, img, refined)
            assertTrue("${f.key} 핀 붙이기 뒤 인식률 ${"%.1f".format(acc * 100)}% — 틀린 점 $wrong", acc >= gate(f, 0.995f, 0.98f))
        }
    }
}
