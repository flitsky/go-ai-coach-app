package com.worksoc.goaicoach.shared.domain

/**
 * 양패스로 끝난 판에서 **걷어야 할 사석**을 찾는 로컬 폴백.
 *
 * 엔진의 `final_status_list dead`가 1순위이고 이쪽은 그것이 없거나 실패했을 때의 보조다
 * (`EndgameResolver`가 둘을 합쳐 [DeadStoneCleaner]에 넘긴다).
 */
object DeadStoneDetector {
    /**
     * 이 함수가 답하는 질문은 **"이 돌이 죽었는가"가 아니라 "지금 이 판에서 한 수로 따낼 수 있는가"** 다.
     *
     * 판정을 **실제로 따내 보는 방식**으로 하기 때문에, 살아 있는 판의 제약이 그대로 따라온다.
     * 가장 눈에 띄는 것이 **패**다 — 어떤 그룹의 유일한 활로가 `koPoint`이고 `koForbiddenFor`가
     * 그 그룹을 따낼 쪽이면, 따내기가 패 금지로 거부되어 **그 그룹은 사석 후보에서 조용히 빠진다.**
     * `DeadStoneGoldenTest`의 「따낼 자리가 패금지면 후보에서 빠진다」 행이 이 사실을 박아 둔다.
     *
     * ## 그런데도 그것을 고치지 않는 이유 (2026-09-23 판단, refactor backlog #59)
     * 프로덕션에서 이 함수를 부르는 곳은 `EndgameResolver.resolveAiEndgame` 하나이고, 거기에 이르는
     * 경로는 **전부** `MatchReferee.shouldResolveEndgame`으로 막혀 있다 — 즉 **양패스**이거나 **판이 꽉 찼을 때**다.
     * - 양패스면 `BoardRules`의 통과 처리가 `koPoint`/`koForbiddenFor`를 **null로 지운다.**
     * - 판이 꽉 차면 빈 점이 없으므로 활로도, 패 자리도 없다.
     *
     * 그래서 패를 물려받는 경로는 **실전에서 닿지 않는다.** 이 전제가 깨지는지는
     * `DeadStoneGoldenTest.koIsAlreadyClearedBeforeTheDetectorEverRuns`가 지킨다 —
     * 그 테스트가 빨개지면 여기 적힌 판단부터 다시 읽어야 한다.
     *
     * ⚠️ 대국 **도중**에도 이 함수를 부르게 만들지 마라. 그 순간 위 전제가 사라지고,
     * "따낼 수 있는가"와 "살 수 있는가"의 차이가 곧바로 계가에 드러난다.
     */
    fun capturableDeadStones(state: GameState): List<BoardCoordinate> {
        val visited = mutableSetOf<BoardCoordinate>()
        val deadStones = mutableListOf<BoardCoordinate>()

        for (row in 0 until state.boardSize.value) {
            for (column in 0 until state.boardSize.value) {
                val coordinate = BoardCoordinate(row, column)
                if (coordinate in visited || state.stoneAt(coordinate) == null) {
                    continue
                }

                val group = collectGroup(state, coordinate, visited)
                // ⚠️ `singleOrNull`은 **정확성 게이트가 아니라 성능 필터**다. 읽는 사람은 여기에
                // "활로가 정확히 하나여야 한다"는 규칙이 있다고 믿기 쉽지만, 판정을 전담하는 것은
                // 아래 `group.stones.all { … == null }`이다. 활로가 둘 이상인 그룹은 한 점을 메워도
                // 남은 활로 때문에 그 단언에서 어차피 떨어진다 — `firstOrNull`로 바꿔도 결과가 같다
                // (2026-09-23 음성 대조: shared 단위 테스트 730건 전부 초록). 다만 그때는 그룹마다
                // 헛되이 `play()`를 한 번씩 돌리게 되므로, 미리 쳐 내려고 `singleOrNull`을 쓴다.
                val capturePoint = group.liberties.singleOrNull() ?: continue
                val capturer = group.color.opponent
                val captureState = runCatching {
                    state.copy(nextPlayer = capturer)
                        .play(Move.Play(capturer, capturePoint))
                }.getOrNull() ?: continue

                if (group.stones.all { captureState.stoneAt(it) == null }) {
                    deadStones += group.stones
                }
            }
        }

        return deadStones.distinct()
    }

    private fun collectGroup(
        state: GameState,
        start: BoardCoordinate,
        visited: MutableSet<BoardCoordinate>,
    ): CapturableGroup {
        val color = requireNotNull(state.stoneAt(start))
        val stones = mutableSetOf<BoardCoordinate>()
        val liberties = mutableSetOf<BoardCoordinate>()
        val pending = mutableListOf(start)
        var index = 0

        while (index < pending.size) {
            val current = pending[index++]
            if (!visited.add(current)) {
                continue
            }
            stones += current

            for (neighbor in current.neighbors(state.boardSize)) {
                when (state.stoneAt(neighbor)) {
                    null -> liberties += neighbor
                    color -> if (neighbor !in visited) pending += neighbor
                    else -> Unit
                }
            }
        }

        return CapturableGroup(
            color = color,
            stones = stones.toList(),
            liberties = liberties,
        )
    }
}

private data class CapturableGroup(
    val color: StoneColor,
    val stones: List<BoardCoordinate>,
    val liberties: Set<BoardCoordinate>,
)
