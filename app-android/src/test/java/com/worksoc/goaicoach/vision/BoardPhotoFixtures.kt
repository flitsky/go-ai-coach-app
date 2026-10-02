package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import java.io.File

/**
 * 바둑판 사진 인식 기준 이미지와 그 정답(백로그 #210).
 *
 * ⚠️ **이미지는 저장소에 없다** — 남의 사진이고 저장소가 공개라서다. `scripts/fetch-board-photo-fixtures.sh`가
 * `test-fixtures/board-photos/`로 받는다. 없는 기계에서는 [image]가 `null`이고, 테스트는 실패가 아니라 **건너뛴다**.
 * 정답(`src/test/resources/board-photos/NN.txt`)은 사람이 이미지를 보고 손으로 뽑은 것이라 커밋돼 있다.
 */
internal data class BoardPhotoFixture(
    val key: String,
    val imageName: String,
    val boardSize: BoardSize,
    val corners: BoardCornerPoints,
    /** `true`면 통과 기준(자동 ≥ 98%, 핀 수동 ≥ 99.5%)이 걸린다. `false`(인쇄 기보 04·05)는 측정만 한다. */
    val gated: Boolean,
    /** 위에서 아래로 n줄, `B`·`W`·`.` */
    val truth: List<String>,
) {
    val image: ArrayPixelSource? by lazy { loadImage(imageName) }

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

        fun all(): List<BoardPhotoFixture> = keys.map(::load)

        private fun load(key: String): BoardPhotoFixture {
            val text = requireNotNull(BoardPhotoFixture::class.java.classLoader!!.getResource("board-photos/$key.txt")) {
                "정답 파일이 없다: board-photos/$key.txt"
            }.readText()
            val meta = mutableMapOf<String, String>()
            val grid = mutableListOf<String>()
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
                val colon = line.indexOf(": ")
                if (colon > 0 && !line.all { it in "BW." }) meta[line.substring(0, colon)] = line.substring(colon + 2) else grid += line
            }
            val size = meta.getValue("size").toInt()
            val pts = meta.getValue("corners").split(" ").map { pair ->
                val (x, y) = pair.split(",").map(String::toFloat)
                PointF2D(x, y)
            }
            check(grid.size == size && grid.all { it.length == size }) { "$key 정답 격자 모양이 틀렸다" }
            return BoardPhotoFixture(
                key = key,
                imageName = meta.getValue("image"),
                boardSize = BoardSize(size),
                corners = BoardCornerPoints(pts[0], pts[1], pts[2], pts[3]),
                gated = meta.getValue("gate") == "gated",
                truth = grid,
            )
        }

        /**
         * ⚠️ **`javax.imageio`를 리플렉션으로 부른다** — 앱 모듈의 단위 테스트는 `android.jar`로 컴파일되는데 거기엔
         * `javax.imageio`가 없다. 테스트를 **돌리는** JVM(JDK 17)에는 있으므로 실행 시점에 찾는다. Robolectric을 들이는 것보다 가볍다.
         */
        private fun loadImage(name: String): ArrayPixelSource? {
            val file = File(RepoPaths.root, "test-fixtures/board-photos/$name")
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
