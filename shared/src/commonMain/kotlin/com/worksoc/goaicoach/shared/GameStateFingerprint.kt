package com.worksoc.goaicoach.shared

fun GameState.analysisFingerprint(): String =
    buildString {
        append("size=").append(boardSize.value)
        append("|rules=").append(ruleset.name)
        append("|next=").append(nextPlayer.name)
        append("|capturedB=").append(capturedByBlack)
        append("|capturedW=").append(capturedByWhite)
        append("|ko=").append(koPoint?.label(boardSize) ?: "none")
        append("|koFor=").append(koForbiddenFor?.name ?: "none")
        append("|stones=")
        stones.entries
            .sortedWith(compareBy({ it.key.row }, { it.key.column }))
            .forEach { (coordinate, color) ->
                append(coordinate.label(boardSize)).append(':').append(color.name.first()).append(',')
            }
        append("|moves=")
        moves.forEach { move ->
            append(move.describe(boardSize)).append(';')
        }
    }
