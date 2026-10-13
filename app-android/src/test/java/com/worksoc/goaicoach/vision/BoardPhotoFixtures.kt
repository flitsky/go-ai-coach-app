package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import java.io.File

/**
 * 바둑판 사진 묶음 — 정답은 커밋된 리소스 폴더에, 이미지는 저장소 밖 폴더에 있다(둘 다 공개 저장소에 올릴 수 없는 사진이다).
 *
 * - [Press] 언론사·위키 기준 이미지 다섯 장(백로그 #210) — `scripts/fetch-board-photo-fixtures.sh`가 받는다.
 * - [Own] 사용자가 폰으로 찍은 실물 판(백로그 #263 단계 2) — `scripts/prepare-own-board-photos.py`가 앱처럼 세우고 줄여 둔다.
 */
internal enum class BoardPhotoSet(val answerDir: String, val imageDir: String) {
    Press("board-photos", "test-fixtures/board-photos"),
    Own("board-photos-own", "test-fixtures/board-photos-own"),
}

/**
 * 바둑판 사진 인식 기준 이미지와 그 정답(백로그 #210 · #263).
 *
 * ⚠️ **이미지는 저장소에 없다** — 남의 사진이고 저장소가 공개라서다. 없는 기계에서는 [image]가 `null`이고, 테스트는 실패가 아니라 **건너뛴다**.
 * 정답(`src/test/resources/<묶음>/` 아래의 `.txt`)은 사람이 이미지를 보고 손으로 뽑은 것이라 커밋돼 있다.
 * 한 국면을 여러 각도·조명으로 찍은 사진은 정답 파일에 `same-as: <다른 정답의 키>`로 판 크기·배치를 물려받는다 — 모서리는 사진마다 다르다.
 */
internal data class BoardPhotoFixture(
    val set: BoardPhotoSet,
    val key: String,
    val imageName: String,
    val boardSize: BoardSize,
    val corners: BoardCornerPoints,
    /** `true`면 통과 기준(자동 ≥ 98%, 핀 수동 ≥ 99.5%)이 걸린다. `false`(인쇄 기보 04·05)는 측정만 한다. */
    val gated: Boolean,
    /** 위에서 아래로 n줄, `B`·`W`·`.` */
    val truth: List<String>,
) {
    val image: ArrayPixelSource? by lazy { loadImage(set, imageName) }

    fun truthAt(col: Int, row: Int): StoneColor? = when (truth[row][col]) {
        'B' -> StoneColor.Black
        'W' -> StoneColor.White
        else -> null
    }

    /** 맞은 교점 수와 틀린 교점 목록(`열,행 정답→검출`). */
    fun score(stones: Map<BoardCoordinate, StoneColor>): Pair<Int, List<String>> {
        val n = boardSize.value
        var correct = 0
        val wrong = mutableListOf<String>()
        for (row in 0 until n) {
            for (col in 0 until n) {
                val expected = truthAt(col, row)
                val actual = stones[BoardCoordinate(row = row, column = col)]
                if (expected == actual) correct++ else wrong += "($col,$row) ${expected.symbol()}→${actual.symbol()}"
            }
        }
        return correct to wrong
    }

    companion object {
        private val keys = listOf("01", "02", "03", "04", "05")

        /** 기준 이미지 다섯 장(#210) — 인식률 테스트의 통과 기준이 걸린 묶음. */
        fun all(): List<BoardPhotoFixture> = keys.map { load(BoardPhotoSet.Press, it, emptyMap()) }

        /** 묶음의 정답 전부(파일 이름순). 정답 폴더가 없으면 빈 목록 — 사용자 사진(#263)은 아직 없을 수 있다. */
        fun set(set: BoardPhotoSet): List<BoardPhotoFixture> {
            if (set == BoardPhotoSet.Press) return all()
            val url = BoardPhotoFixture::class.java.classLoader!!.getResource(set.answerDir) ?: return emptyList()
            val files = File(url.toURI()).listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }
            val loaded = LinkedHashMap<String, BoardPhotoFixture>()
            // `same-as`가 앞선 정답을 가리키도록 두 번 돈다 — 가리키는 쪽이 이름순으로 앞이 아닐 수도 있다.
            repeat(2) { files.forEach { f -> runCatching { loaded.getOrPut(f.nameWithoutExtension) { load(set, f.nameWithoutExtension, loaded) } } } }
            check(loaded.size == files.size) { "${set.answerDir} 정답 중 읽지 못한 것: ${files.map { it.nameWithoutExtension } - loaded.keys}" }
            return loaded.values.toList()
        }

        private fun load(set: BoardPhotoSet, key: String, known: Map<String, BoardPhotoFixture>): BoardPhotoFixture {
            val text = requireNotNull(BoardPhotoFixture::class.java.classLoader!!.getResource("${set.answerDir}/$key.txt")) {
                "정답 파일이 없다: ${set.answerDir}/$key.txt"
            }.readText()
            val meta = mutableMapOf<String, String>()
            val grid = mutableListOf<String>()
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
                val colon = line.indexOf(": ")
                if (colon > 0 && !line.all { it in "BW." }) meta[line.substring(0, colon)] = line.substring(colon + 2) else grid += line
            }
            val base = meta["same-as"]?.let { requireNotNull(known[it]) { "$key: same-as $it 를 먼저 읽어야 한다" } }
            val size = base?.boardSize?.value ?: meta.getValue("size").toInt()
            val truth = base?.truth ?: grid.toList()
            val pts = meta.getValue("corners").split(" ").map { pair ->
                val (x, y) = pair.split(",").map(String::toFloat)
                PointF2D(x, y)
            }
            check(truth.size == size && truth.all { it.length == size }) { "$key 정답 격자 모양이 틀렸다" }
            return BoardPhotoFixture(
                set = set,
                key = key,
                imageName = meta.getValue("image"),
                boardSize = BoardSize(size),
                corners = BoardCornerPoints(pts[0], pts[1], pts[2], pts[3]),
                gated = meta["gate"] == "gated",
                truth = truth,
            )
        }

        /**
         * ⚠️ **`javax.imageio`를 리플렉션으로 부른다** — 앱 모듈의 단위 테스트는 `android.jar`로 컴파일되는데 거기엔
         * `javax.imageio`가 없다. 테스트를 **돌리는** JVM(JDK 17)에는 있으므로 실행 시점에 찾는다. Robolectric을 들이는 것보다 가볍다.
         */
        private fun loadImage(set: BoardPhotoSet, name: String): ArrayPixelSource? {
            val file = File(RepoPaths.root, "${set.imageDir}/$name")
            if (!file.isFile) return null
            val imageIo = Class.forName("javax.imageio.ImageIO")
            val img = imageIo.getMethod("read", File::class.java).invoke(null, file) ?: return null
            val type = img.javaClass
            val width = type.getMethod("getWidth").invoke(img) as Int
            val height = type.getMethod("getHeight").invoke(img) as Int
            val int = Int::class.javaPrimitiveType
            val pixels = type.getMethod("getRGB", int, int, int, int, IntArray::class.java, int, int)
                .invoke(img, 0, 0, width, height, null, 0, width) as IntArray
            return ArrayPixelSource(width, height, pixels)
        }

        private fun StoneColor?.symbol(): Char = when (this) {
            StoneColor.Black -> 'B'
            StoneColor.White -> 'W'
            null -> '.'
        }
    }
}
