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
            if (!ScanStability.isConfident(sure, f.boardSize)) failures += "${f.key} 정답 모서리인데 흔들림 ${sure.size}개"

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
}
