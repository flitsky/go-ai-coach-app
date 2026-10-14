package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import java.io.File
import java.text.Normalizer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * 정답이 없는 사진 묶음을 **앱과 같은 순서로** 인식해 결과를 남긴다(백로그 #263) — 사람이 눈으로 평가하는 갤러리의 재료.
 *
 * 순서: [BoardCandidates] — (실험실이 계산한 원근 틀이 있으면) 펴고 찾기 → 확실하지 않으면 그대로 찾은 것·늘려 찾은 것과 견주기 → 돌 · 흔들림.
 * 앱의 `AndroidBoardVisionScanner`와 같다 — 원근 틀만 OpenCV 대신 실험실 기록(`frames.json`, `engine-lab/vision/frames_for_dir.py`)을 쓴다.
 * 결과는 `app-android/build/reports/board-photos/gallery-<폴더>.json` → `engine-lab/vision/render_gallery.py`가 HTML로 그린다.
 * 통과 기준을 거는 테스트가 아니다. 폴더가 없으면 아무것도 안 한다.
 */
class BoardPhotoGalleryTest {

    private val folders = listOf("test-fixtures/board-photos-web", "test-fixtures/board-photos-own")

    /**
     * 사진 옆에 놓인 인식 결과 그림(`engine-lab/vision/export_results.py`가 쓰는 `<사진 이름>_인식결과.png`)은 사진이 아니다.
     * ⚠️ 실험실 `lab/fixtures.py`의 `RESULT_SUFFIX`와 같은 글자여야 한다. 맥 Finder가 한글 이름을 NFD로 바꿔 둘 수 있어 NFC로 맞춰 본다.
     */
    private val ResultSuffix = "_인식결과"

    private fun isPhoto(f: File): Boolean = f.extension.lowercase() in setOf("jpg", "jpeg", "png") &&
        !Normalizer.normalize(f.nameWithoutExtension, Normalizer.Form.NFC).endsWith(ResultSuffix)

    @Test
    fun recognizeEveryPhotoInTheGalleryFolders() {
        folders.map { File(RepoPaths.root, it) }.filter { it.isDirectory }.forEach { dir ->
            val frames = BoardPhotoFixture.perspectiveFramesIn(dir)
            val lattices = BoardPhotoFixture.latticeFramesIn(dir)
            val results = JSONArray()
            dir.listFiles { f -> isPhoto(f) }.orEmpty().sortedBy { it.name }.forEach { file ->
                val img = BoardPhotoFixture.loadPixels(file) ?: return@forEach
                results.put(recognize(file.name, img, frames[file.name], lattices[file.name]))
            }
            val out = File(RepoPaths.root, "app-android/build/reports/board-photos").apply { mkdirs() }.resolve("gallery-${dir.name}.json")
            out.writeText(results.toString(1))
            println("갤러리 ${dir.name}: ${results.length()}장 → $out")
        }
    }

    private fun recognize(name: String, img: ArrayPixelSource, frame: PerspectiveFrame?, lattice: LatticeFrame?): JSONObject {
        val started = System.nanoTime()
        val reading = BoardCandidates.locate(img, frame) { lattice }
        val out = JSONObject().put("file", name).put("width", img.width).put("height", img.height)
            .put("perspective", reading?.kind in setOf(BoardCandidates.Kind.Flattened, BoardCandidates.Kind.Stretched, BoardCandidates.Kind.Lattice))
            .put("candidate", reading?.kind?.name ?: JSONObject.NULL)
            .put("located", reading != null)
        if (reading == null) return out.put("ms", (System.nanoTime() - started) / 1_000_000)
        val located = reading.located
        val size = located.boardSize
        val n = size.value
        val stones = reading.detected.stones
        val grid = JSONArray()
        for (row in 0 until n) {
            grid.put((0 until n).joinToString("") { col -> when (stones[BoardCoordinate(row = row, column = col)]) { StoneColor.Black -> "B"; StoneColor.White -> "W"; null -> "." } })
        }
        return out.put("boardSize", n)
            .put("corners", JSONArray(located.corners.toList().map { JSONArray(listOf(it.x.toDouble(), it.y.toDouble())) }))
            .put("grid", grid)
            .put("black", stones.values.count { it == StoneColor.Black })
            .put("white", stones.values.count { it == StoneColor.White })
            .put("unstable", JSONArray(reading.unstable.map { JSONArray(listOf(it.row, it.column)) }))
            .put("confident", reading.confident)
            .put("plausible", ScanStability.isPlausible(stones, size))
            .put("coverage", ScanStability.coverage(located.corners, img.width, img.height).toDouble())
            .put("ms", (System.nanoTime() - started) / 1_000_000)
    }
}
