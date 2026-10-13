package com.worksoc.goaicoach.shared.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * 인식한 국면 하나를 SGF(FF[4]) 기보 글자로 쓴다(백로그 #263) — 국면을 다른 바둑 앱·사이트로 넘기는 가장 흔한 형식이다.
 *
 * 수순이 없는 **배치 기보**다: 돌은 `AB`(흑)·`AW`(백) 배치로, 다음 차례는 `PL`로 적는다. 사진에서는 수순을 알 수 없다
 * (수순 번호를 읽는 것은 #211).
 * SGF 좌표는 왼쪽 위가 `aa`이고 첫 글자가 **열**, 둘째가 **행**이다 — [BoardCoordinate]도 0행이 맨 위라 그대로 옮긴다.
 * ⚠️ 돌은 좌표 순으로 정렬해 쓴다 — 같은 국면은 늘 같은 글자가 되게.
 *
 * @param application `AP` 값(앱 이름). SGF의 합성 값이라 `:`도 이스케이프한다.
 */
fun scannedPositionSgf(state: GameState, application: String): String = buildString {
    append("(;GM[1]FF[4]CA[UTF-8]")
    append("AP[").append(escapeSgfText(application, composed = true)).append(']')
    append("SZ[").append(state.boardSize.value).append(']')
    append("KM[").append(state.komi).append(']')
    append("RU[").append(state.ruleset.sgfName).append(']')
    append("PL[").append(if (state.nextPlayer == StoneColor.Black) 'B' else 'W').append(']')
    appendSetup("AB", state.stones, StoneColor.Black)
    appendSetup("AW", state.stones, StoneColor.White)
    append(')')
}

/**
 * 사진으로 읽은 국면의 다음 차례 **기본값** — 흑돌이 더 많으면 백 차례, 아니면 흑 차례(백로그 #263, 스레드가 정한 규칙).
 *
 * 흑이 먼저 두므로 따낸 돌이 없으면 흑 차례일 때 개수가 같고 백 차례일 때 흑이 하나 많다. 접바둑 배치도 흑이 많으면 백 차례다.
 * 따낸 돌 때문에 틀릴 수 있다 — 그래서 **기본값일 뿐**이고, 보정 화면에서 사용자가 고친다.
 */
fun guessNextPlayer(stones: Map<BoardCoordinate, StoneColor>): StoneColor {
    val black = stones.values.count { it == StoneColor.Black }
    val white = stones.size - black
    return if (black > white) StoneColor.White else StoneColor.Black
}

private val Ruleset.sgfName: String
    get() = when (this) {
        Ruleset.Chinese -> "Chinese"
        Ruleset.Japanese -> "Japanese"
    }

private fun StringBuilder.appendSetup(property: String, stones: Map<BoardCoordinate, StoneColor>, color: StoneColor) {
    val points = stones.filterValues { it == color }.keys.sortedWith(compareBy({ it.row }, { it.column }))
    if (points.isEmpty()) return
    append(property)
    for (point in points) {
        append('[').append('a' + point.column).append('a' + point.row).append(']')
    }
}

private fun escapeSgfText(text: String, composed: Boolean): String = buildString {
    for (c in text) {
        if (c == ']' || c == '\\' || (composed && c == ':')) append('\\')
        append(c)
    }
}
