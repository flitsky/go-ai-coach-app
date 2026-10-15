package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 인터넷 실물 사진 · 사용자 사진 중 정답을 매긴 것의 인식률(백로그 #263, 2026-10-14 · 15) — **앱과 같은 순서**(펴고 찾기 · 판마다 규칙 또는 신경망)로 잰다.
 *
 * 통과 기준은 정답 파일에 `gate: gated`인 사진만, 기준 이미지와 같은 값이다: 자동 ≥ 98% · 정답 모서리 ≥ 99.5% · 판 크기 일치.
 * 걸린 것: f17·f33(판 밖 번짐) · f19(큰 장면 속 작은 판) · 사용자가 「맞음」으로 확인한 실물 w08·w09·w10·w28·w33(비스듬한 다리 달린 판·빈 판 —
 * 격자 틀 후보) · 기보 그림 w41~w45·w47~w49(2026-10-14).
 * 나머지(f03 강한 원근의 돌 시차 · f22 시차 · f30 노란 조명 백돌 다섯)는 측정만 한다 — [BoardPhotoReportTest]의 `web.md`.
 * 사용자 사진(S22 Ultra — 사용자가 고른 22장)은 앱이 격자를 바로 찾고 판 전체를 맞힌 9장(o11·o13·o14·o17·o21·o33·o37·o39·o40 — 2026-10-15)이 걸렸다.
 * 나머지 13장 중 12장은 판 찾기가 틀린다(격자가 판 앞면까지 내려가거나 옆으로 밀림 · 판 크기 틀림 — `own.md`) · o26은 격자는 맞고 한 점 틀린다.
 *
 * ⚠️ 사진은 저장소에 없다(남의 사진 — 평가 갤러리 폴더 `test-fixtures/board-photos-web/`). 사진이나 원근 틀(`frames.json`)이 없으면 건너뛴다.
 */
class BoardPhotoWebTest {

    @Test
    fun gatedWebPhotosAreReadLikeTheApp() =
        gate(BoardPhotoSet.Web, "인터넷 사진·원근 틀이 없다 — test-fixtures/board-photos-web/ (engine-lab/vision/README.md 「평가 갤러리」)")

    @Test
    fun gatedOwnPhotosAreReadLikeTheApp() =
        gate(BoardPhotoSet.Own, "사용자 사진·원근 틀이 없다 — test-fixtures/board-photos-own/ (scripts/prepare-own-board-photos.py · engine-lab/vision/frames_for_dir.py)")

    private fun gate(set: BoardPhotoSet, missing: String) {
        val dir = File(RepoPaths.root, set.imageDir)
        val frames = BoardPhotoFixture.perspectiveFramesIn(dir)
        val lattices = BoardPhotoFixture.latticeFramesIn(dir)
        val fixtures = BoardPhotoFixture.set(set).filter { it.gated && it.hasImage }
        assumeTrue(missing, fixtures.isNotEmpty() && frames.isNotEmpty())
        fixtures.forEach { f ->
            val img = f.loadImageOnce()!!
            val n = f.boardSize.value
            fun accuracy(corners: BoardCornerPoints) =
                f.score(BoardPhotoFixture.readLikeTheApp(img, corners, f.boardSize)).first / (n * n).toFloat()
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
