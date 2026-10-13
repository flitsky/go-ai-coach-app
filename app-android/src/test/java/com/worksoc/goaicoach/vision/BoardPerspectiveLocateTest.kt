package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 원근을 걷어내고 판 찾기(백로그 #263 단계 3) — 실험실이 OpenCV로 잰 틀(`board-photos/perspective-frames.json`)로 펴서 찾는다.
 *
 * 틀은 `engine-lab/vision/measure_perspective.py --export`가 쓴다(숫자뿐이라 커밋). 앱은 같은 계산을 [OpenCvPerspective]로 하고,
 * 기기 계기 테스트가 두 결과가 맞는지 본다 — 여기서는 **펴고 찾는 쪽**(순수 Kotlin)을 잰다.
 * 기준선(2026-10-13): 사다리꼴 12% 변형 — 직접 01 33.8% · 02 42.9% · 03 100% · 04 0% · 05 0% → 펴고 찾으면 69.3 · 100 · 100 · 98.6 · 0%.
 * 05는 한 사진에 도면이 둘이라 원근과 무관하게 못 찾는다(#211 카드의 ⚠️).
 */
class BoardPerspectiveLocateTest {

    private val frames: JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader!!.getResource("board-photos/perspective-frames.json")) { "perspective-frames.json이 없다" }.readText(),
    )

    private fun frameOf(key: String): PerspectiveFrame? {
        val f = frames.optJSONObject(key) ?: return null
        val q = f.getJSONArray("quad")
        val pts = (0 until 4).map { i -> q.getJSONArray(i).let { PointF2D(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
        val size = f.getJSONArray("size")
        return PerspectiveFrame(BoardCornerPoints(pts[0], pts[1], pts[2], pts[3]), size.getInt(0), size.getInt(1))
    }

    /** 실험실은 원근이 센 사진만 편다 — 똑바로 찍은 사진(원본·회전·조명·축척)에는 틀이 없어야 한다. */
    @Test
    fun onlyTheKeystoneVariantsGetAFrame() {
        val withFrame = frames.keys().asSequence().filter { frames.optJSONObject(it) != null }.toSortedSet()
        assertEquals((1..5).map { "0$it 사다리꼴 12%" }.toSortedSet(), withFrame)
    }

    @Test
    fun keystoneVariantsAreFoundAfterFlattening() {
        val floor = mapOf("01" to 0.69f, "02" to 1f, "03" to 1f, "04" to 0.98f)
        val failures = mutableListOf<String>()
        var anyImage = false
        BoardPhotoFixture.all().forEach { f ->
            val image = f.image ?: return@forEach
            anyImage = true
            val v = BoardPhotoVariants.keystone(image, f.corners, 0.12f)
            val frame = requireNotNull(frameOf("${f.key} ${v.name}")) { "${f.key} ${v.name} 틀이 없다" }
            val n = f.boardSize.value
            fun accuracy(corners: BoardCornerPoints?) =
                corners?.let { f.score(GridStoneDetector.detect(BoardWarp.rectify(v.image, it, f.boardSize)).stones).first / (n * n).toFloat() } ?: 0f
            val direct = accuracy(BoardLocator.locate(v.image)?.takeIf { it.boardSize == f.boardSize }?.corners)
            val flattened = accuracy(PerspectiveLocate.locate(v.image, frame)?.takeIf { it.boardSize == f.boardSize }?.corners)
            println("${f.key} ${v.name}: 직접 ${"%.1f".format(direct * 100)}% · 펴고 찾기 ${"%.1f".format(flattened * 100)}%")
            if (flattened < direct) failures += "${f.key} 펴고 찾기가 직접보다 나쁘다(${"%.1f".format(flattened * 100)} < ${"%.1f".format(direct * 100)})"
            floor[f.key]?.let { if (flattened < it) failures += "${f.key} 펴고 찾기 ${"%.1f".format(flattened * 100)}% < ${"%.1f".format(it * 100)}%" }
        }
        assumeTrue("기준 이미지가 없다 — scripts/fetch-board-photo-fixtures.sh", anyImage)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
