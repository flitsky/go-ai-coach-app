package com.worksoc.goaicoach.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * **좌표 표기 골든 — 정수 좌표와 `D4` 같은 문자열 사이의 번역표를 글자 그대로 고정한다**(refactor backlog #36).
 *
 * ## 왜 왕복 테스트로는 모자라는가
 * 좌표 문자열은 저장 포맷이다 — 이어하기(`active_game_snapshot`)와 대국 기록의 `moves[].coordinate`,
 * 번들 기보, 분석 캐시 키(`analysisFingerprint`)가 전부 [BoardCoordinate.label]로 쓰이고
 * [BoardCoordinate.fromLabel]로 읽힌다. 그런데 지금까지의 테스트는 *encode→decode* 왕복뿐이라
 * **두 함수가 함께 바뀌는 변경**(알파벳에 `I`를 넣기, 행 원점을 위아래로 뒤집기)은 왕복이 그대로
 * 맞아 초록이었다. 그 사이 옛 저장분은 다른 점으로 읽히거나 판 밖이 돼 조용히 버려진다.
 *
 * 그래서 여기서는 **기대값을 문자열 리터럴로**, **입력을 정수 생성자 `BoardCoordinate(row, column)`로**
 * 만든다. 입력을 `fromLabel`로 만들면 같은 드리프트가 입력과 기대값 양쪽에 똑같이 들어가 상쇄된다.
 *
 * ## 여기서 고정하는 것
 * - 9·13·19 판의 모서리, `I` 건너뛰기 경계(`H`→`J`), 자주 쓰는 점을 양방향 표로.
 * - 세 판의 **611점 전부**(81 + 169 + 361)를 리터럴 알파벳과 명시적 행 규칙으로 대조하고 왕복한다.
 * - 거부되는 표기와 그 예외 타입. [BoardCoordinate.fromLabelOrNull]은 같은 입력에서 `null`이다.
 * - **현행 관대 파싱**(소문자 `e5`, 앞자리 0 `E05`, 부호 `E+5`) — 옛 저장분을 읽는 규칙이므로 "정리"하면 안 된다.
 * - 열 알파벳 [BoardCoordinate.ColumnLetters]와 [BoardSize.columnLabels].
 */
class BoardCoordinateNotationGoldenTest {

    @Test
    fun notationTableIsGoldenInBothDirections() {
        val failures = NotationTable.mapNotNull { (boardSize, coordinate, label) ->
            val written = coordinate.label(boardSize)
            val read = BoardCoordinate.fromLabel(label, boardSize)
            val readOrNull = BoardCoordinate.fromLabelOrNull(label, boardSize)
            when {
                written != label -> "${boardSize.value}x${boardSize.value} $coordinate → \"$written\" (기대 \"$label\")"
                read != coordinate -> "${boardSize.value}x${boardSize.value} \"$label\" → $read (기대 $coordinate)"
                readOrNull != coordinate -> "${boardSize.value}x${boardSize.value} \"$label\" → OrNull $readOrNull (기대 $coordinate)"
                else -> null
            }
        }

        assertEquals(emptyList<String>(), failures, "좌표 표기 골든 표가 어긋났다:\n" + failures.joinToString("\n"))
    }

    /**
     * 611점 전부를 **테스트가 따로 쥔 리터럴 알파벳**과 명시적 행 규칙(`판 크기 - row`)으로 대조한다.
     * 표가 고른 몇 점만이 아니라 모든 점에서 대칭 드리프트를 잡는다.
     */
    @Test
    fun everyPointOfEverySupportedBoardMatchesTheLiteralRuleAndRoundTrips() {
        var checked = 0
        val failures = mutableListOf<String>()
        for (size in listOf(9, 13, 19)) {
            val boardSize = BoardSize(size)
            val seen = mutableSetOf<String>()
            for (row in 0 until size) {
                for (column in 0 until size) {
                    val coordinate = BoardCoordinate(row, column)
                    val expected = "${ExpectedColumnLetters[column]}${size - row}"
                    val label = coordinate.label(boardSize)
                    if (label != expected) failures += "${size}x$size $coordinate → \"$label\" (기대 \"$expected\")"
                    if (!seen.add(label)) failures += "${size}x$size \"$label\"이 두 번 나왔다"
                    if (BoardCoordinate.fromLabel(label, boardSize) != coordinate) {
                        failures += "${size}x$size \"$label\"이 $coordinate 로 돌아오지 않는다"
                    }
                    if (BoardCoordinate.fromLabelOrNull(label, boardSize) != coordinate) {
                        failures += "${size}x$size \"$label\"을 fromLabelOrNull이 $coordinate 로 읽지 않는다"
                    }
                    checked++
                }
            }
        }

        assertEquals(emptyList<String>(), failures, "좌표 표기 전수 대조가 어긋났다:\n" + failures.take(20).joinToString("\n"))
        assertEquals(81 + 169 + 361, checked)
    }

    /** 거부되는 표기 — [BoardCoordinate.fromLabel]은 [IllegalArgumentException], OrNull은 `null`. */
    @Test
    fun rejectedLabelsThrowIllegalArgumentAndReadAsNull() {
        val failures = RejectedLabels.mapNotNull { (boardSize, label) ->
            val orNull = BoardCoordinate.fromLabelOrNull(label, boardSize)
            val failure = runCatching { BoardCoordinate.fromLabel(label, boardSize) }.exceptionOrNull()
            when {
                orNull != null -> "${boardSize.value}x${boardSize.value} \"$label\"을 fromLabelOrNull이 $orNull 로 읽었다"
                failure == null -> "${boardSize.value}x${boardSize.value} \"$label\"을 fromLabel이 받아들였다"
                failure !is IllegalArgumentException ->
                    "${boardSize.value}x${boardSize.value} \"$label\"이 ${failure::class.simpleName}로 터졌다"
                else -> null
            }
        }

        assertEquals(emptyList<String>(), failures, "좌표 거부 골든 표가 어긋났다:\n" + failures.joinToString("\n"))
    }

    /**
     * **현행 관대 파싱** — 소문자 열, 앞자리 0, `+` 부호를 받아들인다. 저장분을 이 규칙으로 읽어 왔으므로
     * 이 표가 빨개지는 변경은 "정리"가 아니라 **옛 이어하기·대국 기록을 버리는 변경**이다.
     */
    @Test
    fun currentLenientParsingIsPinned() {
        val lenient = listOf(
            Triple(BoardSize.Nine, "e5", BoardCoordinate(4, 4)),
            Triple(BoardSize.Nine, "E05", BoardCoordinate(4, 4)),
            Triple(BoardSize.Nine, "E+5", BoardCoordinate(4, 4)),
            Triple(BoardSize.Nine, "j9", BoardCoordinate(0, 8)),
            Triple(BoardSize.Nineteen, "q16", BoardCoordinate(3, 15)),
            Triple(BoardSize.Nineteen, "t01", BoardCoordinate(18, 18)),
        )

        val failures = lenient.mapNotNull { (boardSize, label, expected) ->
            val read = runCatching { BoardCoordinate.fromLabel(label, boardSize) }.getOrNull()
            val readOrNull = BoardCoordinate.fromLabelOrNull(label, boardSize)
            if (read == expected && readOrNull == expected) null else "\"$label\" → $read / OrNull $readOrNull (기대 $expected)"
        }

        assertEquals(emptyList<String>(), failures, "관대 파싱 골든이 어긋났다:\n" + failures.joinToString("\n"))
    }

    @Test
    fun labelOfAnOutsidePointThrowsIllegalArgument() {
        assertFailsWith<IllegalArgumentException> { BoardCoordinate(9, 0).label(BoardSize.Nine) }
        assertFailsWith<IllegalArgumentException> { BoardCoordinate(0, 9).label(BoardSize.Nine) }
        assertFailsWith<IllegalArgumentException> { BoardCoordinate(0, 19).label(BoardSize.Nineteen) }
    }

    @Test
    fun columnAlphabetIsGoldenAndAgreesWithLabel() {
        assertEquals(ExpectedColumnLetters, BoardCoordinate.ColumnLetters)
        assertEquals("ABCDEFGHJ".toList(), BoardSize.Nine.columnLabels())
        assertEquals("ABCDEFGHJKLMN".toList(), BoardSize.Thirteen.columnLabels())
        assertEquals("ABCDEFGHJKLMNOPQRST".toList(), BoardSize.Nineteen.columnLabels())

        for (boardSize in BoardSize.supported()) {
            val fromLabel = (0 until boardSize.value).map { column ->
                BoardCoordinate(row = 0, column = column).label(boardSize).first()
            }
            assertEquals(fromLabel, boardSize.columnLabels(), "${boardSize.value}x${boardSize.value} 열 이름이 label과 다르다")
        }
    }

    private data class NotationCase(
        val boardSize: BoardSize,
        val coordinate: BoardCoordinate,
        val label: String,
    )

    private companion object {
        /** 테스트가 따로 쥔 알파벳 — 프로덕션 상수를 가져오면 그 상수가 바뀔 때 기대값도 함께 바뀐다. */
        const val ExpectedColumnLetters = "ABCDEFGHJKLMNOPQRSTUVWXYZ"

        val NotationTable = listOf(
            // 9x9 — 네 모서리, 천원, I 건너뛰기 경계(H=7, J=8)
            NotationCase(BoardSize.Nine, BoardCoordinate(0, 0), "A9"),
            NotationCase(BoardSize.Nine, BoardCoordinate(8, 0), "A1"),
            NotationCase(BoardSize.Nine, BoardCoordinate(0, 8), "J9"),
            NotationCase(BoardSize.Nine, BoardCoordinate(8, 8), "J1"),
            NotationCase(BoardSize.Nine, BoardCoordinate(4, 4), "E5"),
            NotationCase(BoardSize.Nine, BoardCoordinate(8, 7), "H1"),
            NotationCase(BoardSize.Nine, BoardCoordinate(6, 2), "C3"),
            // 13x13 — 네 모서리, 천원, 접바둑 2점 화점, 번들 기보의 첫 세 수
            NotationCase(BoardSize.Thirteen, BoardCoordinate(0, 0), "A13"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(12, 0), "A1"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(0, 12), "N13"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(12, 12), "N1"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(6, 6), "G7"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(3, 9), "K10"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(9, 3), "D4"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(3, 8), "J10"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(10, 2), "C3"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(9, 2), "C4"),
            NotationCase(BoardSize.Thirteen, BoardCoordinate(3, 2), "C10"),
            // 19x19 — 네 모서리, 화점, I 건너뛰기 경계
            NotationCase(BoardSize.Nineteen, BoardCoordinate(0, 0), "A19"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(18, 0), "A1"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(0, 18), "T19"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(18, 18), "T1"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(3, 15), "Q16"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(15, 3), "D4"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(9, 9), "K10"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(9, 8), "J10"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(11, 7), "H8"),
            NotationCase(BoardSize.Nineteen, BoardCoordinate(11, 8), "J8"),
        )

        val RejectedLabels = listOf(
            BoardSize.Nine to "I5", // I는 없는 열
            BoardSize.Nine to "i5",
            BoardSize.Nine to "A0", // 0선은 없다
            BoardSize.Nine to "A10", // 9x9의 10선
            BoardSize.Nine to "K1", // 9x9의 10번째 열
            BoardSize.Nine to "E-1",
            BoardSize.Nine to "A", // 한 글자
            BoardSize.Nine to "5E",
            BoardSize.Nine to " E5",
            BoardSize.Nine to "E5 ",
            BoardSize.Nineteen to "U1", // 19x19의 20번째 열
            BoardSize.Nineteen to "T20",
            BoardSize.Nineteen to "Z1",
            BoardSize.Nineteen to "",
            BoardSize.Nineteen to "pass",
            BoardSize.Nineteen to "resign",
        )
    }
}
