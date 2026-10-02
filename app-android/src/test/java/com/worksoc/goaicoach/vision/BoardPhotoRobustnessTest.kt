package com.worksoc.goaicoach.vision

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 기준 이미지를 거칠게 바꾼 변형(회전 ±7° · 사다리꼴 12% · 조명 기울기 · 0.6/1.8배)에서의 인식률 — **래칫**이다(백로그 #210).
 *
 * 다섯 장에만 맞춘 검출기가 되지 않게 하려고 만들었다. 2026-10-02 기준 30개 중 아래 [passing] 19개가 자동 인식 98% 이상이다 —
 * 그것들이 **뒤로 가지 않게** 묶고, 나머지는 실패해도 숫자만 찍는다(나아지면 [passing]에 올릴 것).
 * 정답 모서리로의 돌 판정은 변형 30개 모두 98% 이상이어야 한다 — 04를 0.6배로 줄인 것(판 한 칸이 9px)만 뺀다.
 *
 * 아직 안 되는 것(2026-10-02): 실물 사진 01의 +7° 회전·사다리꼴·0.6배, 화면 캡처 02의 사다리꼴, 인쇄 04의 사다리꼴·0.6배,
 * 도면이 둘인 05의 회전·사다리꼴·축척. 실제 폰 사진으로 실기 확인하며 다음에 넓힌다.
 */
class BoardPhotoRobustnessTest {

    private val passing = setOf(
        "01 회전 -7°", "01 조명 기울기", "01 축척 1.8x",
        "02 회전 7°", "02 회전 -7°", "02 조명 기울기", "02 축척 0.6x", "02 축척 1.8x",
        "03 회전 7°", "03 회전 -7°", "03 사다리꼴 12%", "03 조명 기울기", "03 축척 0.6x", "03 축척 1.8x",
        "04 회전 7°", "04 회전 -7°", "04 조명 기울기", "04 축척 1.8x",
        "05 조명 기울기",
    )

    private val manualExempt = setOf("04 축척 0.6x")

    @Test
    fun variantsDoNotRegress() {
        val failures = mutableListOf<String>()
        var anyImage = false
        BoardPhotoFixture.all().forEach { f ->
            val image = f.image ?: return@forEach
            anyImage = true
            BoardPhotoVariants.all(image, f.corners).forEach { v ->
                val name = "${f.key} ${v.name}"
                val total = (f.boardSize.value * f.boardSize.value).toFloat()
                val manual = f.score(GridStoneDetector.detect(BoardWarp.rectify(v.image, v.corners, f.boardSize)).stones).first / total
                if (name !in manualExempt && manual < 0.98f) failures += "$name 정답 모서리 ${"%.1f".format(manual * 100)}%"
                val located = BoardLocator.locate(v.image)
                val auto = if (located != null && located.boardSize == f.boardSize) {
                    f.score(GridStoneDetector.detect(BoardWarp.rectify(v.image, located.corners, located.boardSize)).stones).first / total
                } else {
                    0f
                }
                println("변형 $name: 자동 ${"%.1f".format(auto * 100)}% · 정답 모서리 ${"%.1f".format(manual * 100)}%")
                if (name in passing && auto < 0.98f) failures += "$name 자동 ${"%.1f".format(auto * 100)}% (래칫 98%)"
            }
        }
        assumeTrue("기준 이미지가 없다 — scripts/fetch-board-photo-fixtures.sh", anyImage)
        assertTrue("변형 인식률이 뒤로 갔다: $failures", failures.isEmpty())
    }
}
