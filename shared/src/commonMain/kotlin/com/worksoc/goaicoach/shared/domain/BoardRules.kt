package com.worksoc.goaicoach.shared.domain

/**
 * [BoardRules.validate]가 돌려주는, 착수가 거부되는 **사유**.
 *
 * ## 왜 이 타입이 필요한가 (리팩토링 백로그 #37)
 * [BoardRules.play]는 이 다섯 가지 사유 중 하나로 예외를 던진다. 그런데 대국 화면은
 * 판 전체(최대 19x19=361점)를 매 착수마다 훑어 합법수를 구하고([LegalMoveGenerator]),
 * 끌기 중에는 좌표가 바뀔 때마다 한 점씩 다시 물어야 한다 — 그 두 경로가 지금까지
 * `runCatching { state.play(...) }.isSuccess`를 썼다. 매번 새 돌 배치 맵을 만들고
 * 예외를 던지고 잡는 비용을 냈다는 뜻이다.
 *
 * [BoardRules.validate]는 상태를 바꾸지 않고, 새 맵을 만들지 않고, 예외 없이 **같은
 * 판정**을 값으로 돌려준다. **[BoardRules.play]가 거부하는 모든 경우와 정확히 같아야
 * 한다** — 어긋나면 화면은 허용하는데 실제 착수는 거부되거나, 그 반대가 된다
 * ([BoardRulesValidateTest]의 무작위 대조 테스트가 이것을 지킨다).
 */
sealed class MoveRejection {
    /** 차례가 아닌 쪽의 착수·통과·기권. */
    data object WrongTurn : MoveRejection()

    /** 좌표가 판 밖(착수에만 해당). */
    data object OutOfBounds : MoveRejection()

    /** 이미 돌이 있는 자리(착수에만 해당). */
    data object Occupied : MoveRejection()

    /** 패의 즉시 되따기(착수에만 해당). */
    data object KoRecapture : MoveRejection()

    /** 자살수 — 단점이든 여러 점을 이은 그룹이든, 따내는 상대가 없어 활로가 0이 되는 경우(착수에만 해당). */
    data object Suicide : MoveRejection()
}

object BoardRules {
    fun play(
        state: GameState,
        move: Move,
    ): GameState =
        when (move) {
            is Move.Play -> playStone(state, move)
            is Move.Pass -> pass(state, move)
            is Move.Resign -> resign(state, move)
        }

    /**
     * [play]와 정확히 같은 판정을, 상태를 바꾸지 않고 예외 없이 값으로 돌려준다.
     *
     * ⚠️ 조건을 보는 순서를 [playStone]의 `require` 순서와 맞춘다 — 여러 조건이 동시에
     * 걸리는 국면에서 "어느 사유가 먼저냐"까지 어긋나지 않게 하려는 것이다.
     *
     * 자살수 판정은 [playStone]처럼 새 돌 배치 맵을 만들어 그 위에서 그룹을 다시 모으지
     * 않는다. **지금 있는 판** 위에서 이 좌표의 이웃 그룹들만 본다 — 그것으로 충분한 이유:
     * - 빈 이웃이 하나라도 있으면 따냄과 무관하게 그 자리가 새 돌의 활로다.
     * - 상대 이웃 그룹의 활로가 이 좌표 하나뿐이면, 두면 그 그룹은 반드시 잡힌다 — 잡힌
     *   자리는 비고 이 좌표와 이웃이므로, 새 돌은 반드시 활로를 하나 얻는다(자살일 수 없다).
     * - 잡는 그룹이 없다면, 자기 색 이웃 그룹 중 이 좌표 말고 다른 활로를 가진 것이
     *   하나라도 있으면 합쳐진 그룹에 그 활로가 남는다.
     * - 이 셋 다 아니면(빈 이웃도, 잡는 상대 그룹도, 남는 활로를 가진 자기 그룹도 없음)
     *   자살수다.
     */
    fun validate(
        state: GameState,
        move: Move,
    ): MoveRejection? =
        when (move) {
            is Move.Play -> validatePlay(state, move)
            is Move.Pass -> validateTurn(state, move)
            is Move.Resign -> validateTurn(state, move)
        }

    private fun validateTurn(
        state: GameState,
        move: Move,
    ): MoveRejection? = if (move.player != state.nextPlayer) MoveRejection.WrongTurn else null

    private fun validatePlay(
        state: GameState,
        move: Move.Play,
    ): MoveRejection? {
        if (move.player != state.nextPlayer) return MoveRejection.WrongTurn
        val coordinate = move.coordinate
        if (!coordinate.isInside(state.boardSize)) return MoveRejection.OutOfBounds
        if (state.stoneAt(coordinate) != null) return MoveRejection.Occupied
        if (coordinate == state.koPoint && move.player == state.koForbiddenFor) return MoveRejection.KoRecapture

        val neighbors = coordinate.neighbors(state.boardSize)
        if (neighbors.any { state.stones[it] == null }) return null

        val opponent = move.player.opponent
        val checkedOpponentGroups = mutableSetOf<BoardCoordinate>()
        for (neighbor in neighbors) {
            if (state.stones[neighbor] != opponent || !checkedOpponentGroups.add(neighbor)) continue
            val group = state.stones.groupAt(neighbor, state.boardSize)
            checkedOpponentGroups += group.stones
            // `coordinate`는 이 그룹의 활로 목록에 이미 들어 있다(비어 있고 그룹에 이웃하므로).
            // 활로가 그 하나뿐이면 이 착수가 그룹을 정확히 잡는다.
            if (group.liberties.size == 1) return null
        }

        val checkedOwnGroups = mutableSetOf<BoardCoordinate>()
        for (neighbor in neighbors) {
            if (state.stones[neighbor] != move.player || !checkedOwnGroups.add(neighbor)) continue
            val group = state.stones.groupAt(neighbor, state.boardSize)
            checkedOwnGroups += group.stones
            if (group.liberties.any { it != coordinate }) return null
        }

        return MoveRejection.Suicide
    }

    private fun playStone(
        state: GameState,
        move: Move.Play,
    ): GameState {
        require(move.player == state.nextPlayer) {
            "Expected ${state.nextPlayer.label}, got ${move.player.label}"
        }
        require(move.coordinate.isInside(state.boardSize)) {
            "${move.coordinate} is outside ${state.boardSize.value}x${state.boardSize.value}"
        }
        require(state.stoneAt(move.coordinate) == null) {
            "${move.coordinate.label(state.boardSize)} is already occupied"
        }
        require(move.coordinate != state.koPoint || move.player != state.koForbiddenFor) {
            "Illegal ko recapture at ${move.coordinate.label(state.boardSize)}"
        }

        var nextStones = state.stones + (move.coordinate to move.player)
        val capturedStones = mutableSetOf<BoardCoordinate>()
        for (neighbor in move.coordinate.neighbors(state.boardSize)) {
            if (nextStones[neighbor] == move.player.opponent) {
                val group = nextStones.groupAt(neighbor, state.boardSize)
                if (group.liberties.isEmpty()) {
                    capturedStones += group.stones
                }
            }
        }
        nextStones = nextStones - capturedStones

        val ownGroup = nextStones.groupAt(move.coordinate, state.boardSize)
        require(ownGroup.liberties.isNotEmpty()) {
            "Suicide move is not allowed at ${move.coordinate.label(state.boardSize)}"
        }

        val nextKoPoint = nextKoPoint(capturedStones, ownGroup)

        return state.copy(
            nextPlayer = state.nextPlayer.opponent,
            stones = nextStones,
            moves = state.moves + move,
            capturedByBlack = state.capturedByBlack + if (move.player == StoneColor.Black) capturedStones.size else 0,
            capturedByWhite = state.capturedByWhite + if (move.player == StoneColor.White) capturedStones.size else 0,
            koPoint = nextKoPoint,
            koForbiddenFor = nextKoPoint?.let { move.player.opponent },
        )
    }

    private fun pass(
        state: GameState,
        move: Move.Pass,
    ): GameState {
        require(move.player == state.nextPlayer) {
            "Expected ${state.nextPlayer.label}, got ${move.player.label}"
        }
        return state.copy(
            nextPlayer = state.nextPlayer.opponent,
            moves = state.moves + move,
            koPoint = null,
            koForbiddenFor = null,
        )
    }

    private fun resign(
        state: GameState,
        move: Move.Resign,
    ): GameState {
        require(move.player == state.nextPlayer) {
            "Expected ${state.nextPlayer.label}, got ${move.player.label}"
        }
        return state.copy(moves = state.moves + move, koPoint = null, koForbiddenFor = null)
    }

    private fun nextKoPoint(
        capturedStones: Set<BoardCoordinate>,
        ownGroup: BoardGroup,
    ): BoardCoordinate? =
        capturedStones.singleOrNull()
            ?.takeIf { ownGroup.stones.size == 1 && ownGroup.liberties.size == 1 }
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

internal fun BoardCoordinate.neighbors(boardSize: BoardSize): List<BoardCoordinate> =
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
