package com.worksoc.goaicoach.shared

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

enum class Ruleset {
    Chinese,
    Japanese,
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
) {
    fun stoneAt(coordinate: BoardCoordinate): StoneColor? = stones[coordinate]

    fun isBoardFull(): Boolean = stones.size >= boardSize.value * boardSize.value

    fun play(move: Move): GameState =
        when (move) {
            is Move.Play -> {
                require(move.player == nextPlayer) {
                    "Expected ${nextPlayer.label}, got ${move.player.label}"
                }
                require(move.coordinate.isInside(boardSize)) {
                    "${move.coordinate} is outside ${boardSize.value}x${boardSize.value}"
                }
                require(stoneAt(move.coordinate) == null) {
                    "${move.coordinate.label(boardSize)} is already occupied"
                }
                copy(
                    nextPlayer = nextPlayer.opponent,
                    stones = stones + (move.coordinate to move.player),
                    moves = moves + move,
                )
            }

            is Move.Pass -> {
                require(move.player == nextPlayer) {
                    "Expected ${nextPlayer.label}, got ${move.player.label}"
                }
                copy(nextPlayer = nextPlayer.opponent, moves = moves + move)
            }

            is Move.Resign -> {
                require(move.player == nextPlayer) {
                    "Expected ${nextPlayer.label}, got ${move.player.label}"
                }
                copy(moves = moves + move)
            }
        }

    companion object {
        fun empty(
            boardSize: BoardSize = BoardSize.Nine,
            ruleset: Ruleset = Ruleset.Chinese,
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
