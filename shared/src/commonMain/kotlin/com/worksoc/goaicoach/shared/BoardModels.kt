package com.worksoc.goaicoach.shared

const val DefaultKomi = 6.5

data class BoardSize(val value: Int) {
    init {
        require(value in SUPPORTED_SIZES) { "Unsupported board size: $value" }
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
            )
    }
}

fun Move.describe(boardSize: BoardSize): String =
    when (this) {
        is Move.Play -> "${player.label} ${coordinate.label(boardSize)}"
        is Move.Pass -> "${player.label} pass"
        is Move.Resign -> "${player.label} resign"
    }
