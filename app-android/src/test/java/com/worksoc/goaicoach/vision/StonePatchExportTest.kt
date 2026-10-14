package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 신경망 돌 판정의 **학습 데이터**를 내보낸다(백로그 #263 단계 5) — 통과 기준을 거는 테스트가 아니다. 사진이 없으면 건너뛴다.
 * `make test`에서는 돌지 않는다 — `./gradlew :app-android:testDebugUnitTest --tests '*StonePatchExportTest*' -PexportStonePatches`.
 *
 * 정답이 있는 사진(기준 이미지 · 인터넷 정답 · 사용자 사진)마다 판을 펴고 교점 조각([StonePatches] — 앱이 판정할 때와 **같은 함수**)을 잘라
 * `engine-lab/vision/runs/patches/`(저장소 밖)에 쓴다. 실험실 `stone_net.py`가 읽어 학습한다.
 *
 * 판 하나에서 여러 벌: 정답 모서리 · 앱이 찾은 모서리(앱과 같은 길 — 판 크기가 맞고 규칙 판정이 90% 넘게 맞을 때만, 즉 같은 격자일 때) ·
 * 모서리를 ±[JitterCells]칸 흔든 [Jitters]벌(판 찾기·핀의 오차를 흉내) · 기준 이미지의 변형(돌리기·기울이기·조명·크기).
 * 조각마다 규칙 판정([GridStoneDetector])도 함께 적는다 — 실험실이 같은 조각에서 신경망과 규칙을 견준다.
 *
 * 묶음(`group`)은 같은 대국이면 같다(w10 = w08) — 실험실이 묶음 단위로 나눠 검증해야 같은 대국이 학습과 검증에 함께 들어가지 않는다.
 * 사용자 사진은 **같은 날 찍은 것끼리** 한 묶음이다(`sources.json`의 찍은 날짜) — 같은 저녁이면 같은 판·조명·이어지는 수순이라,
 * 나눠 넣으면 신경망이 처음 보는 사진이 아니게 된다.
 */
class StonePatchExportTest {

    private val out = File(RepoPaths.root, "engine-lab/vision/runs/patches")
    private val manifest = JSONArray()

    @Test
    fun exportPatchesForTraining() {
        assumeTrue("-PexportStonePatches를 줄 때만 돈다", System.getProperty("exportStonePatches") == "true")
        val fixtures = BoardPhotoSet.entries.flatMap { set -> BoardPhotoFixture.set(set) }.filter { it.hasImage }
        assumeTrue("정답 있는 사진이 없다", fixtures.isNotEmpty())
        out.deleteRecursively()
        out.mkdirs()
        val res = File(requireNotNull(javaClass.classLoader!!.getResource("board-photos")).toURI())
        val pressFrames = BoardPhotoFixture.perspectiveFramesIn(res, "perspective-frames.json")
        val pressLattices = BoardPhotoFixture.latticeFramesIn(res)
        val folderFrames = HashMap<BoardPhotoSet, Pair<Map<String, PerspectiveFrame>, Map<String, LatticeFrame>>>()
        fixtures.forEach { f ->
            val img = f.loadImageOnce()!!
            val group = groupOf(f)
            val key = "${f.set.name.lowercase()}-${f.key}"
            write(key, group, "truth", f, img, f.corners)
            val (frame, lattice) = if (f.set == BoardPhotoSet.Press) {
                pressFrames[f.key] to pressLattices[f.key]
            } else {
                val (fr, la) = folderFrames.getOrPut(f.set) {
                    val dir = File(RepoPaths.root, f.set.imageDir)
                    BoardPhotoFixture.perspectiveFramesIn(dir) to BoardPhotoFixture.latticeFramesIn(dir)
                }
                fr[f.imageName] to la[f.imageName]
            }
            BoardPhotoFixture.locateLikeTheApp(img, frame, lattice)?.takeIf { it.boardSize == f.boardSize }?.let { located ->
                val stones = GridStoneDetector.detect(BoardWarp.rectify(img, located.corners, f.boardSize)).stones
                if (f.score(stones).first >= 0.9f * f.boardSize.value * f.boardSize.value) write(key, group, "auto", f, img, located.corners)
            }
            val random = Random(key.hashCode())
            repeat(Jitters) { k -> write(key, group, "jitter$k", f, img, jitter(f.corners, f.boardSize.value, random)) }
            if (f.set == BoardPhotoSet.Press) {
                BoardPhotoVariants.all(img, f.corners).forEach { v -> write(key, group, "var-${v.name}", f, v.image, v.corners) }
            }
        }
        File(out, "manifest.json").writeText(manifest.toString(1))
        println("교점 조각 ${manifest.length()}벌 → $out")
    }

    private fun groupOf(f: BoardPhotoFixture): String {
        if (f.set == BoardPhotoSet.Own) return "own-${ownDays[f.key] ?: f.key}"
        // 같은 대국이면 같은 묶음 — `same-as`가 가리키는 정답(w10 → w08)
        val text = javaClass.classLoader!!.getResource("${f.set.answerDir}/${f.key}.txt")!!.readText()
        val base = Regex("""(?m)^same-as:\s*(\S+)""").find(text)?.groupValues?.get(1) ?: f.key
        return "${f.set.name.lowercase()}-$base"
    }

    /** 사용자 사진 키 → 찍은 날(`2026-08-26`) — `scripts/prepare-own-board-photos.py`가 적은 `sources.json`. */
    private val ownDays: Map<String, String> by lazy {
        val file = File(RepoPaths.root, "${BoardPhotoSet.Own.imageDir}/sources.json")
        if (!file.exists()) return@lazy emptyMap()
        val list = JSONArray(file.readText())
        (0 until list.length()).map { list.getJSONObject(it) }
            .associate { it.getString("key") to it.getString("taken").take(10).replace(':', '-') }
    }

    private fun jitter(c: BoardCornerPoints, n: Int, random: Random): BoardCornerPoints {
        val pts = c.toList()
        val cell = (0 until 4).map { i -> pts[i].distanceTo(pts[(i + 1) % 4]) }.average().toFloat() / (n - 1)
        fun move(p: PointF2D) = PointF2D(
            p.x + (random.nextFloat() * 2 - 1) * JitterCells * cell,
            p.y + (random.nextFloat() * 2 - 1) * JitterCells * cell,
        )
        return BoardCornerPoints(move(pts[0]), move(pts[1]), move(pts[2]), move(pts[3]))
    }

    private fun PointF2D.distanceTo(o: PointF2D) = kotlin.math.hypot((x - o.x).toDouble(), (y - o.y).toDouble())

    private fun write(key: String, group: String, variant: String, f: BoardPhotoFixture, img: ArrayPixelSource, corners: BoardCornerPoints) {
        val n = f.boardSize.value
        val board = BoardWarp.rectify(img, corners, f.boardSize)
        val patches = StonePatches.extract(board)
        val rules = GridStoneDetector.detect(board).stones
        // 규칙이 흔들리는 교점(모서리를 0.1칸 흔들면 답이 바뀐다 — 앱의 「확인 필요」)
        val unstable = ScanStability.unstablePoints(img, corners, f.boardSize, rules)
        val name = "${key}__${variant}.bin".replace(Regex("[^A-Za-z0-9._%-]"), "_")
        val per = StonePatches.Channels * StonePatches.Size * StonePatches.Size
        DataOutputStream(File(out, name).outputStream().buffered()).use { s ->
            val head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            head.put("STP1".toByteArray()).putInt(n * n).putInt(StonePatches.Size)
            s.write(head.array())
            val rec = ByteBuffer.allocate(5 + 12 + per).order(ByteOrder.LITTLE_ENDIAN)
            for (row in 0 until n) {
                for (col in 0 until n) {
                    val idx = row * n + col
                    rec.clear()
                    // 정답 「모름」(?)은 3 — 실험실이 학습·채점에서 뺀다
                    rec.put((if (f.isUnknown(col, row)) 3 else code(f.truthAt(col, row))).toByte())
                    rec.put(code(rules[BoardCoordinate(row = row, column = col)]).toByte())
                    rec.put(row.toByte()).put(col.toByte())
                    // 다섯째 바이트: 판 크기(아래 7비트) + 흔들림(맨 위 비트)
                    rec.put((n or if (BoardCoordinate(row = row, column = col) in unstable) 0x80 else 0).toByte())
                    patches.background[idx].forEach { rec.putFloat(it) }
                    val size2 = StonePatches.Size * StonePatches.Size
                    for (c in 0 until 3) {
                        for (i in 0 until size2) rec.put(patches.value(idx, c, i).coerceIn(0, 255).toByte())
                    }
                    s.write(rec.array())
                }
            }
        }
        manifest.put(JSONObject().put("file", name).put("key", key).put("group", group).put("variant", variant).put("n", n).put("set", f.set.name))
    }

    private fun code(c: StoneColor?) = when (c) {
        null -> 0
        StoneColor.Black -> 1
        StoneColor.White -> 2
    }

    private companion object {
        const val Jitters = 4
        const val JitterCells = 0.2f
    }
}
