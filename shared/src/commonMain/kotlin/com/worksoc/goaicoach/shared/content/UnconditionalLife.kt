package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * [point]의 [color] 무리가 **무조건 사는가** — 벤슨의 판정(Benson 1976). 상대가 몇 수를 연달아 두어도, 그 무리가 한 수도 받지 않아도
 * 따낼 수 없는 무리다(눈이 둘 — 정확히는 「그 무리의 공배로만 된 둘레 안 구역」이 둘 이상).
 *
 * 풀이기(백로그 #277)가 「이 무리는 이제 못 잡는다」를 **끝까지 읽지 않고** 알려고 쓴다. 판정은 보수적이다 — 산다고 하면 반드시 산다
 * (상대가 판 어디에 두어도, 패가 나도 못 잡는다). 그래서 풀이기의 답은 바뀌지 않고 읽는 국면만 준다. 산다고 하지 않았다고
 * 죽은 것은 아니다(빅 · 아직 눈 모양이 덜 된 삶은 여기서 못 본다 — 풀이기가 마저 읽는다).
 *
 * ⚠️ 자살수가 없는 규칙을 전제한다 — `BoardRules`가 자살수를 막는다.
 *
 * @param size 판 한 변의 줄 수.
 */
internal fun isUnconditionallyAlive(
    stones: Map<BoardCoordinate, StoneColor>,
    size: Int,
    color: StoneColor,
    point: BoardCoordinate,
): Boolean {
    val n = size * size
    val mine = BooleanArray(n)
    for ((p, c) in stones) if (c == color) mine[p.row * size + p.column] = true
    val start = point.row * size + point.column
    if (!mine[start]) return false
    val empty = BooleanArray(n) { index -> !mine[index] && BoardCoordinate(index / size, index % size) !in stones }

    // 무리(내 돌)와 구역(내 돌이 아닌 점 — 빈 점 · 상대 돌)에 번호를 단다.
    val chain = IntArray(n) { -1 }
    val region = IntArray(n) { -1 }
    var chains = 0
    var regions = 0
    val stack = IntArray(n)
    for (index in 0 until n) {
        val labels = if (mine[index]) chain else region
        if (labels[index] != -1) continue
        val id = if (mine[index]) chains++ else regions++
        labels[index] = id
        var top = 0
        stack[top++] = index
        while (top > 0) {
            val at = stack[--top]
            forEachNeighbor(at, size) { next ->
                if (mine[next] == mine[at] && labels[next] == -1) {
                    labels[next] = id
                    stack[top++] = next
                }
            }
        }
    }

    // 구역마다: 둘레의 무리들, 그리고 무리마다 「이 구역의 빈 점이 전부 그 무리의 공배인가」(건강한가).
    val bordering = Array(regions) { HashSet<Int>() }
    val emptyCount = IntArray(regions)
    val libertyCount = Array(regions) { HashMap<Int, Int>() }
    for (index in 0 until n) {
        if (mine[index]) continue
        val r = region[index]
        val touching = HashSet<Int>()
        forEachNeighbor(index, size) { next -> if (mine[next]) touching += chain[next] }
        bordering[r] += touching
        if (empty[index]) {
            emptyCount[r] += 1
            for (c in touching) libertyCount[r][c] = (libertyCount[r][c] ?: 0) + 1
        }
    }
    fun healthy(r: Int, c: Int): Boolean = emptyCount[r] > 0 && libertyCount[r][c] == emptyCount[r]

    val aliveChains = BooleanArray(chains) { true }
    val aliveRegions = BooleanArray(regions) { true }
    var changed = true
    while (changed) {
        changed = false
        for (c in 0 until chains) {
            if (!aliveChains[c]) continue
            var vital = 0
            for (r in 0 until regions) if (aliveRegions[r] && c in bordering[r] && healthy(r, c)) vital += 1
            if (vital < 2) {
                aliveChains[c] = false
                changed = true
            }
        }
        for (r in 0 until regions) {
            if (aliveRegions[r] && bordering[r].any { !aliveChains[it] }) {
                aliveRegions[r] = false
                changed = true
            }
        }
    }
    return aliveChains[chain[start]]
}

private inline fun forEachNeighbor(index: Int, size: Int, action: (Int) -> Unit) {
    val row = index / size
    val column = index % size
    if (row > 0) action(index - size)
    if (row < size - 1) action(index + size)
    if (column > 0) action(index - 1)
    if (column < size - 1) action(index + 1)
}
