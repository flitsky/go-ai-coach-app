package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 인터넷 실물 사진 중 정답을 매긴 것의 인식률(백로그 #263, 2026-10-14) — **앱과 같은 순서**(펴고 찾기)로 잰다.
 *
 * 통과 기준은 정답 파일에 `gate: gated`인 사진만, 기준 이미지와 같은 값이다: 자동 ≥ 98% · 정답 모서리 ≥ 99.5% · 판 크기 일치.
 * 지금 걸린 것: f17·f33 — 격자가 판 밖 탁자로 2~3줄 나가던 사진(판이 아닌 칸으로 범위 바로잡기 — `BoardLocator.leaveForeign`),
 * f19 — 큰 장면 속 작은 9줄 판(펴면 틀이 판을 일그러뜨려 그대로 찾은 후보가 뽑힌다 — [BoardCandidates]).
 * 나머지(f03 강한 원근의 돌 시차 · f22 시차 · f30 노란 조명 백돌 다섯)는 측정만 한다 — [BoardPhotoReportTest]의 `web.md`.
 *
 * ⚠️ 사진은 저장소에 없다(남의 사진 — 평가 갤러리 폴더 `test-fixtures/board-photos-web/`). 사진이나 원근 틀(`frames.json`)이 없으면 건너뛴다.
 */
class BoardPhotoWebTest {

    @Test
    fun gatedWebPhotosAreReadLikeTheApp() {
        val dir = File(RepoPaths.root, BoardPhotoSet.Web.imageDir)
        val frames = BoardPhotoFixture.perspectiveFramesIn(dir)
        val lattices = BoardPhotoFixture.latticeFramesIn(dir)
        val fixtures = BoardPhotoFixture.set(BoardPhotoSet.Web).filter { it.gated && it.image != null }
        assumeTrue("인터넷 사진·원근 틀이 없다 — test-fixtures/board-photos-web/ (engine-lab/vision/README.md 「평가 갤러리」)", fixtures.isNotEmpty() && frames.isNotEmpty())
        fixtures.forEach { f ->
            val img = f.image!!
            val n = f.boardSize.value
            fun accuracy(corners: BoardCornerPoints) =
                f.score(GridStoneDetector.detect(BoardWarp.rectify(img, corners, f.boardSize)).stones).first / (n * n).toFloat()
            val located = BoardPhotoFixture.locateLikeTheApp(img, frames[f.imageName], lattices[f.imageName])
            assertTrue("${f.key} 판을 못 찾았다", located != null)
            assertEquals("${f.key} 판 크기", f.boardSize, located!!.boardSize)
            val auto = accuracy(located.corners)
            val manual = accuracy(f.corners)
            assertTrue("${f.key} 자동 인식률 ${"%.1f".format(auto * 100)}%", auto >= 0.98f)
            assertTrue("${f.key} 정답 모서리 인식률 ${"%.1f".format(manual * 100)}%", manual >= 0.995f)
        }
    }
}
