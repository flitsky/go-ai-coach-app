package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 핀 화면을 건너뛸 확신도(백로그 #263 — [ScanStability]): 정답 모서리로 읽은 판은 **확실**, 모서리를 한 줄 안쪽으로 잡은 판(판을 잘못 찾은
 * 가장 흔한 꼴 — 바깥 한 줄을 놓친다)은 **불확실**해야 한다. 기준 이미지가 없으면 건너뛴다.
 */
class ScanStabilityTest {

    @Test
    fun answerCornersAreConfidentAndAnInsetBoardIsNot() {
        val failures = mutableListOf<String>()
        var anyImage = false
        BoardPhotoFixture.all().forEach { f ->
            val img = f.image ?: return@forEach
            anyImage = true
            val right = GridStoneDetector.detect(BoardWarp.rectify(img, f.corners, f.boardSize)).stones
            val sure = ScanStability.unstablePoints(img, f.corners, f.boardSize, right)
            // 04는 한 칸이 15px인 신문 인쇄본이라 정답 모서리로도 25~47개가 흔들린다 — 핀 화면으로 가는 것이 맞다(ConfidentRatio의 KDoc).
            if (f.key != "04" && !ScanStability.isConfident(sure, f.boardSize)) failures += "${f.key} 정답 모서리인데 흔들림 ${sure.size}개"
            // 핀 건너뛰기의 나머지 조건(그럴듯함 · 크기 · 사방 여백)도 바른 판을 막지 않아야 한다 — 04·05는 신문 도면을 격자선에 바짝(2~7px)
            // 잘라 낸 사진이라 판이 더 이어지는지 알 수 없다(핀 화면이 맞다).
            if (f.key !in setOf("04", "05") && !ScanStability.canSkipPins(sure, right, f.boardSize, f.corners, img.width, img.height)) {
                failures += "${f.key} 정답 모서리인데 핀 화면을 건너뛰지 못한다(그럴듯함 ${ScanStability.isPlausible(right, f.boardSize)} · " +
                    "크기 ${"%.2f".format(ScanStability.coverage(f.corners, img.width, img.height))} · 여백 ${ScanStability.showsMarginAllAround(f.corners, f.boardSize, img.width, img.height)})"
            }

            val n = f.boardSize.value
            val c = f.corners
            fun towards(p: PointF2D, q: PointF2D) = PointF2D(p.x + (q.x - p.x) / (n - 1), p.y + (q.y - p.y) / (n - 1))
            // 모서리마다 대각선 맞은편 쪽으로 한 칸 — 판을 한 줄씩 작게 잡은 꼴(크기 n은 그대로라 칸 간격이 어긋난다)
            val inset = BoardCornerPoints(towards(c.topLeft, c.bottomRight), towards(c.topRight, c.bottomLeft), towards(c.bottomRight, c.topLeft), towards(c.bottomLeft, c.topRight))
            val wrong = GridStoneDetector.detect(BoardWarp.rectify(img, inset, f.boardSize)).stones
            val shaky = ScanStability.unstablePoints(img, inset, f.boardSize, wrong)
            println("${f.key}: 정답 모서리 흔들림 ${sure.size} · 한 줄 안쪽 ${shaky.size}")
            if (ScanStability.isConfident(shaky, f.boardSize)) failures += "${f.key} 한 줄 어긋난 판인데 확실하다고 봤다(흔들림 ${shaky.size}개)"
        }
        assumeTrue("기준 이미지가 없다 — scripts/fetch-board-photo-fixtures.sh", anyImage)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** 갤러리 f30처럼 한쪽 색을 통째로 놓친 판은 흔들리지 않아도 말이 안 된다. */
    @Test
    fun aBoardMissingAllOfOneColorIsNotPlausible() {
        val n = com.worksoc.goaicoach.shared.domain.BoardSize.Nineteen
        val blackOnly = (0 until 40).associate { i -> com.worksoc.goaicoach.shared.domain.BoardCoordinate(row = (i / 10) * 2, column = (i % 10) * 2) to com.worksoc.goaicoach.shared.domain.StoneColor.Black }
        assertTrue(!ScanStability.isPlausible(blackOnly, n))
        assertTrue(ScanStability.isColorImbalanced(blackOnly))
        val mixed = blackOnly.mapValues { (c, _) -> if (c.column % 4 == 0) com.worksoc.goaicoach.shared.domain.StoneColor.White else com.worksoc.goaicoach.shared.domain.StoneColor.Black }
        assertTrue(ScanStability.isPlausible(mixed, n))
    }
}
