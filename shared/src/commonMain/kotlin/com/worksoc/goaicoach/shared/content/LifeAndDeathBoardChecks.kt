package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.neighbors

/**
 * **판에 보이는 그대로도 맞는 문제인가** — 풀이기가 묻지 않는 두 잣대(백로그 #184 · #277). 실은 문제를 되묻는 테스트
 * (`LifeAndDeathProblemsTest`)와 맥에서 문제를 만드는 도구(`LifeAndDeathCandidateExportTest`)가 같은 이 함수를 쓴다 — 잣대가 두 벌이면
 * 만든 문제가 테스트에서 처음 빨개진다.
 */

/**
 * 목표 무리의 공배 가운데 **문제 밖(`,`)에 있는 것**. 비어 있어야 한다 — 문제 밖은 아무도 메우지 못하므로, 그런 공배가 있으면
 * 잡을 수 없는 무리가 된다(살리기 문제는 거저 산다).
 */
fun LifeAndDeathProblem.targetLibertiesOutside(): Set<BoardCoordinate> {
    val playable = playable.toSet()
    return groupAt(targetPoint)
        .flatMap { it.neighbors(boardSize) }
        .filter { stones[it] == null && it !in playable }
        .toSet()
}

/**
 * **판에서 약해 보이는 벽**을 사람이 읽을 문장으로(비어 있어야 한다 — 사용자 2026-10-21, 급소 1·2번 → 급소 여섯 전부).
 *
 * 풀이기는 문제 밖(`,`)에 아무도 두지 않는다고 보므로, 판에서는 약한 벽도 풀이기에게는 영원히 살아 있다 — 문제 밖 활로가 하나뿐이면
 * 단수이고(상대가 그 점을 두면 따내고 산다), 벽이 대각선으로만 닿아 있으면 끊기는 자리가 남는다. 그래서 목표 무리가 아닌 무리는
 * ① 활로가 전부 문제 안이라 풀이기가 다 읽거나 ② **그 색의 바깥 벽 하나**에 속해야 한다 — 한 색에 문제 밖으로 숨 쉬는 무리는
 * 하나뿐이고, 그 무리의 문제 밖 활로는 [SafeOutsideLiberties]개 이상이다.
 */
fun LifeAndDeathProblem.weakWalls(): List<String> {
    val playable = playable.toSet()
    val seen = groupAt(targetPoint).toMutableSet()
    val walls = mutableListOf<Pair<Set<BoardCoordinate>, Set<BoardCoordinate>>>()
    stones.keys.sortedWith(compareBy({ it.row }, { it.column })).forEach { stone ->
        if (stone in seen) return@forEach
        val group = groupAt(stone)
        seen += group
        val outside = group.flatMap { it.neighbors(boardSize) }
            .filter { stones[it] == null && it !in playable }.toSet()
        if (outside.isNotEmpty()) walls += group to outside
    }
    val labels = { points: Collection<BoardCoordinate> -> points.map { it.label(boardSize) }.sorted() }
    return walls.groupBy { (group, _) -> stones.getValue(group.first()) }.flatMap { (color, pieces) ->
        buildList {
            if (pieces.size > 1) add("$id: the $color wall is cut into ${pieces.size} pieces ${pieces.map { labels(it.first) }}")
            pieces.filter { it.second.size < SafeOutsideLiberties }
                .forEach { add("$id: $color stones ${labels(it.first)} breathe outside only through ${labels(it.second)}") }
        }
    }
}

/** 문제 밖 활로가 이만큼 있으면 상대가 문제 밖에 한두 수를 두어도 잡히지 않는다 — 판에서 「안정」으로 보이는 하한. */
const val SafeOutsideLiberties: Int = 3

/** 처음 국면에서 [start]의 돌과 이어진 같은 색 돌 전부. */
internal fun LifeAndDeathProblem.groupAt(start: BoardCoordinate): Set<BoardCoordinate> {
    val color = stones.getValue(start)
    val seen = mutableSetOf(start)
    val pending = ArrayDeque(listOf(start))
    while (pending.isNotEmpty()) {
        pending.removeFirst().neighbors(boardSize)
            .filter { stones[it] == color && seen.add(it) }
            .forEach { pending += it }
    }
    return seen
}
