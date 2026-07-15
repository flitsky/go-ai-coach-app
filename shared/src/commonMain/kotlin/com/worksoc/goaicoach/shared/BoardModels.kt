package com.worksoc.goaicoach.shared

const val DefaultKomi = 6.5

data class BoardSize(val value: Int) {
    init {
        require(value in SUPPORTED_SIZES) { "Unsupported board size: $value" }
    }

    /** 이 바둑판에서 접바둑으로 설정 가능한 최대 돌 개수 */
    val maxHandicapCount: Int
        get() = when (value) {
            19 -> 9
            else -> 5 // 9x9, 13x13
        }

    /**
     * 접바둑 돌을 화점 기준으로 배치할 좌표 목록을 반환합니다.
     * 순서: 우상귀 → 좌하귀 → 우하귀 → 좌상귀 → 천원 → (19x19) 좌변 → 우변 → 하변 → 상변
     *
     * @param count 접바둑 돌 개수 (2~maxHandicapCount)
     */
    fun handicapStonePositions(count: Int): List<BoardCoordinate> {
        require(count in 0..maxHandicapCount) {
            "접바둑 개수 $count 은 이 바둑판(${value}x${value})에서 지원하지 않습니다."
        }
        if (count == 0) return emptyList()

        // 화점의 오프셋: 9x9=2, 13x13=3, 19x19=3
        val near = when (value) {
            9 -> 2
            else -> 3
        }
        val far = value - 1 - near
        val mid = value / 2

        // 19x19 변 가운데 좌표 (6~9점용)
        val sidePositions: List<BoardCoordinate> = if (value == 19) {
            listOf(
                BoardCoordinate(mid, near),  // 좌변 가운데
                BoardCoordinate(mid, far),   // 우변 가운데
                BoardCoordinate(far, mid),   // 하변 가운데
                BoardCoordinate(near, mid),  // 상변 가운데
            )
        } else {
            emptyList()
        }

        // 순서: 우상귀, 좌하귀, 우하귀, 좌상귀, 천원, 변 가운데들
        val allPositions = listOf(
            BoardCoordinate(near, far),  // 우상귀
            BoardCoordinate(far, near),  // 좌하귀
            BoardCoordinate(far, far),   // 우하귀
            BoardCoordinate(near, near), // 좌상귀
            BoardCoordinate(mid, mid),   // 천원 (정 가운데)
        ) + sidePositions

        return allPositions.take(count)
    }

    companion object {
        private val SUPPORTED_SIZES = setOf(9, 13, 19)

        val Nine = BoardSize(9)
        val Thirteen = BoardSize(13)
        val Nineteen = BoardSize(19)

        fun supported(): List<BoardSize> = listOf(Nine, Thirteen, Nineteen)
    }
}

enum class Ruleset(
    val scoringLabel: String,
    val katagoName: String,
) {
    Chinese(scoringLabel = "Area", katagoName = "chinese"),
    Japanese(scoringLabel = "Territory", katagoName = "japanese"),
    ;

    fun toggled(): Ruleset =
        when (this) {
            Chinese -> Japanese
            Japanese -> Chinese
        }
}

enum class StoneColor(val label: String) {
    Black("Black"),
    White("White"),
    ;

    val opponent: StoneColor
        get() = when (this) {
            Black -> White
            White -> Black
        }
}

data class BoardCoordinate(
    val row: Int,
    val column: Int,
) {
    init {
        require(row >= 0) { "row must be zero or greater" }
        require(column >= 0) { "column must be zero or greater" }
    }

    fun isInside(boardSize: BoardSize): Boolean =
        row < boardSize.value && column < boardSize.value

    fun label(boardSize: BoardSize): String {
        require(isInside(boardSize)) { "Coordinate is outside ${boardSize.value}x${boardSize.value}" }
        val columns = "ABCDEFGHJKLMNOPQRSTUVWXYZ"
        return "${columns[column]}${boardSize.value - row}"
    }

    companion object {
        private const val GTP_COLUMNS = "ABCDEFGHJKLMNOPQRSTUVWXYZ"

        fun fromLabel(
            label: String,
            boardSize: BoardSize,
        ): BoardCoordinate {
            require(label.length >= 2) { "Invalid coordinate label: $label" }

            val column = GTP_COLUMNS.indexOf(label.first().uppercaseChar())
            require(column >= 0) { "Invalid coordinate column: $label" }

            val rowNumber = label.drop(1).toIntOrNull()
            require(rowNumber != null) { "Invalid coordinate row: $label" }

            val coordinate = BoardCoordinate(
                row = boardSize.value - rowNumber,
                column = column,
            )
            require(coordinate.isInside(boardSize)) {
                "Coordinate $label is outside ${boardSize.value}x${boardSize.value}"
            }
            return coordinate
        }
    }
}

fun BoardSize.allCoordinates(): Sequence<BoardCoordinate> =
    sequence {
        for (row in 0 until value) {
            for (column in 0 until value) {
                yield(BoardCoordinate(row, column))
            }
        }
    }

sealed interface Move {
    val player: StoneColor

    data class Play(
        override val player: StoneColor,
        val coordinate: BoardCoordinate,
    ) : Move

    data class Pass(
        override val player: StoneColor,
    ) : Move

    data class Resign(
        override val player: StoneColor,
    ) : Move
}

data class GameState(
    val boardSize: BoardSize,
    val ruleset: Ruleset,
    val nextPlayer: StoneColor,
    val stones: Map<BoardCoordinate, StoneColor>,
    val moves: List<Move>,
    val capturedByBlack: Int = 0,
    val capturedByWhite: Int = 0,
    val koPoint: BoardCoordinate? = null,
    val koForbiddenFor: StoneColor? = null,
    val handicapCount: Int = 0,
) {
    fun stoneAt(coordinate: BoardCoordinate): StoneColor? = stones[coordinate]

    fun isBoardFull(): Boolean = stones.size >= boardSize.value * boardSize.value

    fun hasConsecutivePasses(): Boolean =
        moves.takeLast(2).let { lastMoves ->
            lastMoves.size == 2 && lastMoves.all { it is Move.Pass }
        }

    fun capturedBy(player: StoneColor): Int =
        when (player) {
            StoneColor.Black -> capturedByBlack
            StoneColor.White -> capturedByWhite
        }

    fun play(move: Move): GameState = BoardRules.play(this, move)

    companion object {
        fun empty(
            boardSize: BoardSize = BoardSize.Nine,
            ruleset: Ruleset = Ruleset.Japanese,
            nextPlayer: StoneColor = StoneColor.Black,
        ): GameState =
            GameState(
                boardSize = boardSize,
                ruleset = ruleset,
                nextPlayer = nextPlayer,
                stones = emptyMap(),
                moves = emptyList(),
                handicapCount = 0,
            )

        /**
         * 접바둑 초기 상태를 생성합니다.
         * - handicapCount == 0: 일반 대국 (흑 선착)
         * - handicapCount >= 2: 흑돌을 화점에 미리 배치하고 백이 먼저 둠
         *
         * @param handicapCount 접바둑 돌 개수 (0 또는 2~maxHandicapCount)
         */
        fun withHandicap(
            boardSize: BoardSize,
            ruleset: Ruleset,
            handicapCount: Int,
        ): GameState {
            if (handicapCount <= 0) return empty(boardSize, ruleset)

            val positions = boardSize.handicapStonePositions(handicapCount)
            val stones = positions.associateWith { StoneColor.Black }
            // 접바둑에서는 흑이 미리 놓고 백이 먼저 둠
            return GameState(
                boardSize = boardSize,
                ruleset = ruleset,
                nextPlayer = StoneColor.White,
                stones = stones,
                moves = emptyList(),
                handicapCount = handicapCount,
            )
        }
    }
}

fun Move.describe(boardSize: BoardSize): String =
    when (this) {
        is Move.Play -> "${player.label} ${coordinate.label(boardSize)}"
        is Move.Pass -> "${player.label} pass"
        is Move.Resign -> "${player.label} resign"
    }
