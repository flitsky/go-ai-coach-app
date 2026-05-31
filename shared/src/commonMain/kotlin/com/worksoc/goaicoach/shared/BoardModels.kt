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
    val capturedByBlack: Int = 0,
    val capturedByWhite: Int = 0,
    val koPoint: BoardCoordinate? = null,
    val koForbiddenFor: StoneColor? = null,
) {
    fun stoneAt(coordinate: BoardCoordinate): StoneColor? = stones[coordinate]

    fun isBoardFull(): Boolean = stones.size >= boardSize.value * boardSize.value

    fun capturedBy(player: StoneColor): Int =
        when (player) {
            StoneColor.Black -> capturedByBlack
            StoneColor.White -> capturedByWhite
        }

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
                require(move.coordinate != koPoint || move.player != koForbiddenFor) {
                    "Illegal ko recapture at ${move.coordinate.label(boardSize)}"
                }

                var nextStones = stones + (move.coordinate to move.player)
                val capturedStones = mutableSetOf<BoardCoordinate>()
                for (neighbor in move.coordinate.neighbors(boardSize)) {
                    if (nextStones[neighbor] == move.player.opponent) {
                        val group = nextStones.groupAt(neighbor, boardSize)
                        if (group.liberties.isEmpty()) {
                            capturedStones += group.stones
                        }
                    }
                }
                nextStones = nextStones - capturedStones

                val ownGroup = nextStones.groupAt(move.coordinate, boardSize)
                require(ownGroup.liberties.isNotEmpty()) {
                    "Suicide move is not allowed at ${move.coordinate.label(boardSize)}"
                }

                val nextKoPoint = nextKoPoint(capturedStones, ownGroup)

                copy(
                    nextPlayer = nextPlayer.opponent,
                    stones = nextStones,
                    moves = moves + move,
                    capturedByBlack = capturedByBlack + if (move.player == StoneColor.Black) capturedStones.size else 0,
                    capturedByWhite = capturedByWhite + if (move.player == StoneColor.White) capturedStones.size else 0,
                    koPoint = nextKoPoint,
                    koForbiddenFor = nextKoPoint?.let { move.player.opponent },
                )
            }

            is Move.Pass -> {
                require(move.player == nextPlayer) {
                    "Expected ${nextPlayer.label}, got ${move.player.label}"
                }
                copy(
                    nextPlayer = nextPlayer.opponent,
                    moves = moves + move,
                    koPoint = null,
                    koForbiddenFor = null,
                )
            }

            is Move.Resign -> {
                require(move.player == nextPlayer) {
                    "Expected ${nextPlayer.label}, got ${move.player.label}"
                }
                copy(moves = moves + move, koPoint = null, koForbiddenFor = null)
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

private data class BoardGroup(
    val stones: Set<BoardCoordinate>,
    val liberties: Set<BoardCoordinate>,
)

private fun Map<BoardCoordinate, StoneColor>.groupAt(
    start: BoardCoordinate,
    boardSize: BoardSize,
): BoardGroup {
    val color = requireNotNull(this[start]) {
        "Cannot collect a group from an empty point: ${start.label(boardSize)}"
    }
    val groupStones = mutableSetOf<BoardCoordinate>()
    val liberties = mutableSetOf<BoardCoordinate>()
    val pending = mutableListOf(start)
    var index = 0

    while (index < pending.size) {
        val current = pending[index++]
        if (!groupStones.add(current)) {
            continue
        }

        for (neighbor in current.neighbors(boardSize)) {
            when (this[neighbor]) {
                null -> liberties += neighbor
                color -> pending += neighbor
                else -> Unit
            }
        }
    }

    return BoardGroup(stones = groupStones, liberties = liberties)
}

private fun BoardCoordinate.neighbors(boardSize: BoardSize): List<BoardCoordinate> =
    buildList {
        if (row > 0) {
            add(copy(row = row - 1))
        }
        if (row < boardSize.value - 1) {
            add(copy(row = row + 1))
        }
        if (column > 0) {
            add(copy(column = column - 1))
        }
        if (column < boardSize.value - 1) {
            add(copy(column = column + 1))
        }
    }

private fun nextKoPoint(
    capturedStones: Set<BoardCoordinate>,
    ownGroup: BoardGroup,
): BoardCoordinate? =
    capturedStones.singleOrNull()
        ?.takeIf { ownGroup.stones.size == 1 && ownGroup.liberties.size == 1 }
