package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import java.io.File
import kotlin.math.hypot
import org.junit.Test

/**
 * 바둑판 사진 인식의 **측정표**(백로그 #263 단계 2) — 통과 기준을 거는 테스트가 아니다. 묶음마다 사진 한 장에 한 줄:
 * 판을 스스로 찾았나 · 판 크기 · 모서리가 몇 칸 어긋났나 · 자동 인식률 · 정답 모서리로 잰 인식률 · 틀린 종류(흑→빈 …).
 *
 * 엔진을 바꾸기 전과 뒤(OpenCV · 학습한 신경망)를 **같은 사진으로** 견주는 기준선이다. 결과는 표준 출력과
 * `app-android/build/reports/board-photos/<묶음>.md`에 남는다. 통과 기준은 [BoardPhotoAccuracyTest]·[BoardPhotoRobustnessTest]가 건다.
 * 시간 칸은 JVM 값이라 기기 속도가 아니다(함정 86) — 같은 기계에서 앞뒤를 견줄 때만 쓴다.
 *
 * 이미지가 없는 묶음은 건너뛴다(사진은 저장소 밖이다 — [BoardPhotoSet]).
 */
class BoardPhotoReportTest {

    @Test
    fun measureEverySetThatHasImages() {
        BoardPhotoSet.entries.forEach { set ->
            val fixtures = BoardPhotoFixture.set(set).filter { it.image != null }
            if (fixtures.isEmpty()) return@forEach
            val frames = BoardPhotoFixture.perspectiveFramesIn(File(RepoPaths.root, set.imageDir))
            write(set.name, fixtures.map { measure(it, it.key, it.image!!, it.corners, frames[it.imageName]) })
            if (set == BoardPhotoSet.Press) {
                // 같은 사진을 돌리고·기울이고·줄인 변형(#210) — 알려진 약점이 여기 있다(자동 인식이 놓치는 사다리꼴·축소).
                val variants = fixtures.flatMap { f -> BoardPhotoVariants.all(f.image!!, f.corners).map { v -> measure(f, "${f.key} ${v.name}", v.image, v.corners) } }
                write("${set.name}-variants", variants)
            }
        }
    }

    private fun write(name: String, rows: List<Row>) {
        val report = render(name, rows)
        println(report)
        File(RepoPaths.root, "app-android/build/reports/board-photos").apply { mkdirs() }
            .resolve("${name.lowercase()}.md").writeText(report)
    }

    private data class Row(
        val key: String,
        val size: Int,
        val located: Boolean,
        val sizeMatches: Boolean,
        val cornerErrorCells: Float?,
        val autoAccuracy: Float?,
        val manualAccuracy: Float,
        val errorKinds: Map<String, Int>,
        val locateMillis: Long,
        /** [LocatedBoard.confidence] — 판 안 격자선의 세기 대비. 핀 화면을 건너뛸지 가를 후보(#263 단계 6). */
        val confidence: Float?,
        /** 자동 모서리로 읽은 판에서, 샘플 위치를 0.1칸 흔들면 답이 바뀌는 교점 수(#263 단계 6 — 확신도 후보). */
        val unstable: Int?,
        /** 그 흔들리는 교점 가운데 실제로 틀린 것 / 실제로 틀린 교점 수. */
        val unstableWrong: String?,
    )

    /** [f]는 정답 배치, [img]·[corners]는 잴 사진과 그 사진의 정답 모서리(변형이면 변형된 모서리). [frame]이 있으면 펴고 찾는다. */
    private fun measure(f: BoardPhotoFixture, key: String, img: ArrayPixelSource, corners: BoardCornerPoints, frame: PerspectiveFrame? = null): Row {
        val n = f.boardSize.value
        val cell = cellSize(corners, n)
        val started = System.nanoTime()
        val located = BoardPhotoFixture.locateLikeTheApp(img, frame)
        val locateMillis = (System.nanoTime() - started) / 1_000_000
        val sizeMatches = located?.boardSize == f.boardSize
        val cornerError = located?.takeIf { sizeMatches }?.let { worstCornerError(it.corners, corners) / cell }
        val autoAccuracy = located?.takeIf { sizeMatches }?.let { accuracy(f, detect(f, img, it.corners)) }
        val manual = detect(f, img, corners)
        return Row(
            key = key,
            size = n,
            located = located != null,
            sizeMatches = sizeMatches,
            cornerErrorCells = cornerError,
            autoAccuracy = autoAccuracy,
            manualAccuracy = accuracy(f, manual),
            errorKinds = errorKinds(f, manual),
            locateMillis = locateMillis,
            confidence = located?.confidence,
            unstable = located?.takeIf { sizeMatches }?.let { unstablePoints(f, img, it.corners).size },
            unstableWrong = located?.takeIf { sizeMatches }?.let { loc ->
                val auto = detect(f, img, loc.corners)
                val wrong = (0 until n * n).map { BoardCoordinate(row = it / n, column = it % n) }
                    .filter { c -> f.truthAt(c.column, c.row) != auto[c] }.toSet()
                "${(unstablePoints(f, img, loc.corners) intersect wrong).size}/${wrong.size}"
            },
        )
    }

    /** [ScanStability] — 앱의 「확인 필요」·핀 건너뛰기와 같은 계산. */
    private fun unstablePoints(f: BoardPhotoFixture, img: ArrayPixelSource, corners: BoardCornerPoints): Set<BoardCoordinate> =
        ScanStability.unstablePoints(img, corners, f.boardSize, detect(f, img, corners))

    private fun detect(f: BoardPhotoFixture, img: ArrayPixelSource, corners: BoardCornerPoints) =
        GridStoneDetector.detect(BoardWarp.rectify(img, corners, f.boardSize)).stones

    private fun accuracy(f: BoardPhotoFixture, stones: Map<BoardCoordinate, StoneColor>): Float =
        f.score(stones).first.toFloat() / (f.boardSize.value * f.boardSize.value)

    /** 틀린 교점을 종류로 센다 — `B→.`(흑을 놓침) · `.→W`(빈 점을 백으로) … */
    private fun errorKinds(f: BoardPhotoFixture, stones: Map<BoardCoordinate, StoneColor>): Map<String, Int> {
        val kinds = sortedMapOf<String, Int>()
        val n = f.boardSize.value
        for (row in 0 until n) {
            for (col in 0 until n) {
                val expected = f.truthAt(col, row)
                val actual = stones[BoardCoordinate(row = row, column = col)]
                if (expected != actual) kinds.merge("${expected.symbol()}→${actual.symbol()}", 1, Int::plus)
            }
        }
        return kinds
    }

    private fun cellSize(c: BoardCornerPoints, n: Int): Float =
        listOf(c.topLeft to c.topRight, c.bottomLeft to c.bottomRight, c.topLeft to c.bottomLeft, c.topRight to c.bottomRight)
            .map { (a, b) -> hypot(b.x - a.x, b.y - a.y) }.average().toFloat() / (n - 1)

    private fun worstCornerError(a: BoardCornerPoints, b: BoardCornerPoints): Float =
        a.toList().zip(b.toList()).maxOf { (p, q) -> hypot(p.x - q.x, p.y - q.y) }

    private fun render(name: String, rows: List<Row>): String = buildString {
        appendLine("## $name — ${rows.size}장")
        appendLine()
        appendLine("| 사진 | 줄 | 판 찾음 | 확신도 | 흔들림 교점 | 흔들림∩틀림/틀림 | 모서리 오차(칸) | 자동 인식률 | 정답 모서리 인식률 | 틀린 종류(정답 모서리) | locate ms(JVM) |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        rows.forEach { r ->
            val found = when {
                !r.located -> "✗"
                !r.sizeMatches -> "크기 틀림"
                else -> "○"
            }
            appendLine(
                "| ${r.key} | ${r.size} | $found | ${r.confidence?.let { "%.2f".format(it) } ?: "-"} | ${r.unstable ?: "-"} | ${r.unstableWrong ?: "-"} | " +
                    "${r.cornerErrorCells?.let { "%.2f".format(it) } ?: "-"} | " +
                    "${r.autoAccuracy?.let(::pct) ?: "-"} | ${pct(r.manualAccuracy)} | " +
                    "${r.errorKinds.entries.joinToString(" ") { "${it.key} ${it.value}" }.ifEmpty { "-" }} | ${r.locateMillis} |",
            )
        }
        appendLine()
        val perfectAuto = rows.count { (it.autoAccuracy ?: 0f) >= 1f }
        val perfectManual = rows.count { it.manualAccuracy >= 1f }
        appendLine("판 전체가 맞은 사진: 자동 $perfectAuto/${rows.size} · 정답 모서리 $perfectManual/${rows.size}")
    }

    private fun pct(v: Float) = "%.1f%%".format(v * 100)

    private fun StoneColor?.symbol(): Char = when (this) {
        StoneColor.Black -> 'B'
        StoneColor.White -> 'W'
        null -> '.'
    }
}
