package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.neighbors

/**
 * 실제 대국에서는 나올 수 없는 배치 — **숨(활로)이 하나도 없는 돌 무리**의 돌들(백로그 #210).
 *
 * 사진 인식이 돌 하나를 잘못 읽으면(빈 점을 돌로, 흑을 백으로) 이런 무리가 흔히 생긴다. 분석 전에 보정 화면이
 * "인식이 틀렸을 수 있다"고 알리는 근거다 — 지우지는 않는다(사용자가 사진과 비교해 고친다).
 */
internal fun stonesWithoutLiberties(stones: Map<BoardCoordinate, StoneColor>, boardSize: BoardSize): Set<BoardCoordinate> {
    val result = mutableSetOf<BoardCoordinate>()
    val seen = mutableSetOf<BoardCoordinate>()
    for ((start, color) in stones) {
        if (start in seen) continue
        val group = mutableListOf<BoardCoordinate>()
        val queue = ArrayDeque(listOf(start))
        seen += start
        var hasLiberty = false
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            group += p
            for (q in p.neighbors(boardSize)) {
                when (stones[q]) {
                    null -> hasLiberty = true
                    color -> if (seen.add(q)) queue.addLast(q)
                    else -> Unit
                }
            }
        }
        if (!hasLiberty) result += group
    }
    return result
}
