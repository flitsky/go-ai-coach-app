package com.worksoc.goaicoach.shared.gamerecord

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameSetup
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * 기보 한 판(백로그 #269 「기보 보기」) — SGF에서 읽은 **본 수순**과 그 판의 정체성.
 *
 * 판을 다시 세우는 길은 대국 기록과 같다 — [setup]을 `buildGameReplayTimeline`에 넘기면 시작 배치(#266)·접바둑·빈 판이
 * 한 곳(`GameState.withHandicap`)에서 서고, 수가 규칙에 어긋나면 거기서 잘린다(「잘림」 표시).
 */
data class GameRecord(
    val setup: GameSetup,
    val moves: List<Move>,
    val info: GameRecordInfo,
)

/** 기보의 머리 정보 — SGF에 적힌 그대로다(이름 표기를 바꾸는 것은 화면의 몫). 없으면 null. */
data class GameRecordInfo(
    val blackName: String? = null,
    val whiteName: String? = null,
    val blackRank: String? = null,
    val whiteRank: String? = null,
    val date: String? = null,
    val event: String? = null,
    val round: String? = null,
    val result: String? = null,
    val place: String? = null,
    val gameName: String? = null,
    /** SGF의 `HA` — 접바둑 돌 수. 돌 자체는 [GameRecord.setup]의 시작 배치에 있다(표시용). */
    val handicap: Int = 0,
)

sealed interface GameRecordRead {
    data class Read(val record: GameRecord) : GameRecordRead

    data class Unreadable(val problem: GameRecordProblem) : GameRecordRead
}

enum class GameRecordProblem {
    /** `(;`로 시작하는 기보가 아니다. */
    NotSgf,

    /** 바둑이 아닌 게임의 SGF다(`GM`이 1이 아니다). */
    NotGo,

    /** 앱이 두는 판(9 · 13 · 19줄)이 아니다. */
    UnsupportedBoardSize,

    /** 수순 중간에 돌을 놓거나 들어내는 배치(`AB`·`AW`·`AE`)가 있다 — 1차는 첫 국면의 배치만 읽는다. */
    SetupAfterStart,

    /** 판 밖이나 읽을 수 없는 좌표가 있다. */
    BadPoint,
}

/**
 * SGF(FF[4]) 글자를 기보 한 판으로 읽는다. 파일에 여러 판이 있으면 첫 판, 갈래가 있으면 **첫 갈래(본 수순)** 만 읽는다.
 *
 * SGF는 한 갈래 안에서 노드가 하위 갈래보다 **앞에** 온다. 그래서 앞에서부터 노드를 읽다가 `(`를 만나면 그 첫 하위 갈래로
 * 들어가 계속 읽고, 처음 만나는 `)`에서 본 수순이 끝난다 — 나머지(다른 갈래)는 볼 필요가 없다. 재귀하지 않는다.
 *
 * 스레드가 정한 해석(2026-10-19): 덤이 없으면 0(옛 기보) · 규칙(`RU`)에 중국·잉·AGA·NZ가 들어 있으면 중국식(계가), 그 밖과 없음은 일본식 ·
 * 시작 배치(`AB`·`AW`)가 있으면 접바둑 돌도 그 배치로 싣고(`HA`는 표시용), 배치 없이 `HA`만 있으면 화점 배치로 놓는다.
 */
fun readSgfGameRecord(text: String): GameRecordRead {
    val nodes = SgfMainLine(text).read() ?: return GameRecordRead.Unreadable(GameRecordProblem.NotSgf)
    if (nodes.isEmpty()) return GameRecordRead.Unreadable(GameRecordProblem.NotSgf)
    val root = nodes.first()

    root.first("GM")?.let { if (it.trim() != "1") return GameRecordRead.Unreadable(GameRecordProblem.NotGo) }
    val sizeValue = root.first("SZ")?.trim()?.substringBefore(':')?.toIntOrNull() ?: 19
    if (sizeValue !in SupportedSizes) return GameRecordRead.Unreadable(GameRecordProblem.UnsupportedBoardSize)
    val boardSize = BoardSize(sizeValue)

    val setupStones = LinkedHashMap<BoardCoordinate, StoneColor>()
    for ((key, color) in listOf("AB" to StoneColor.Black, "AW" to StoneColor.White)) {
        for (value in root.all(key)) {
            val points = pointsOf(value, boardSize) ?: return GameRecordRead.Unreadable(GameRecordProblem.BadPoint)
            points.forEach { setupStones[it] = color }
        }
    }

    val moves = mutableListOf<Move>()
    for ((index, node) in nodes.withIndex()) {
        if (index > 0 && (node.has("AB") || node.has("AW") || node.has("AE"))) {
            return GameRecordRead.Unreadable(GameRecordProblem.SetupAfterStart)
        }
        for ((key, color) in listOf("B" to StoneColor.Black, "W" to StoneColor.White)) {
            val value = node.first(key) ?: continue
            val move = moveOf(color, value, boardSize) ?: return GameRecordRead.Unreadable(GameRecordProblem.BadPoint)
            moves += move
        }
    }

    val handicap = root.first("HA")?.trim()?.toIntOrNull() ?: 0
    val handicapFromStarPoints = if (setupStones.isEmpty() && handicap in 2..boardSize.maxHandicapCount) handicap else 0
    val firstPlayer = colorOf(root.first("PL"))
        ?: moves.firstOrNull()?.player
        ?: GameSetup.firstPlayerFor(if (setupStones.isEmpty()) handicapFromStarPoints else handicap)
    val setup = GameSetup(
        boardSize = boardSize,
        ruleset = rulesetOf(root.first("RU")),
        handicapCount = handicapFromStarPoints,
        komi = root.first("KM")?.trim()?.toDoubleOrNull() ?: 0.0,
        setupStones = setupStones,
        firstPlayer = firstPlayer,
    )
    val info = GameRecordInfo(
        blackName = root.text("PB"),
        whiteName = root.text("PW"),
        blackRank = root.text("BR"),
        whiteRank = root.text("WR"),
        date = root.text("DT"),
        event = root.text("EV"),
        round = root.text("RO"),
        result = root.text("RE"),
        place = root.text("PC"),
        gameName = root.text("GN"),
        handicap = handicap,
    )
    return GameRecordRead.Read(GameRecord(setup = setup, moves = moves, info = info))
}

private val SupportedSizes = setOf(9, 13, 19)

private fun rulesetOf(value: String?): Ruleset {
    val rules = value?.lowercase() ?: return Ruleset.Japanese
    val isArea = listOf("chinese", "ing", "aga", "nz").any { it in rules }
    return if (isArea) Ruleset.Chinese else Ruleset.Japanese
}

private fun colorOf(value: String?): StoneColor? = when (value?.trim()?.uppercase()) {
    "B" -> StoneColor.Black
    "W" -> StoneColor.White
    else -> null
}

/** 수 하나 — 빈 값과 19줄 이하의 `tt`는 통과다. */
private fun moveOf(color: StoneColor, value: String, boardSize: BoardSize): Move? {
    val point = value.trim()
    if (point.isEmpty() || (point == "tt" && boardSize.value <= 19)) return Move.Pass(color)
    return pointOf(point, boardSize)?.let { Move.Play(color, it) }
}

/** 점 하나 또는 `aa:cc` 같은 직사각형(FF[4]의 압축 표기). */
private fun pointsOf(value: String, boardSize: BoardSize): List<BoardCoordinate>? {
    val text = value.trim()
    if (':' !in text) return pointOf(text, boardSize)?.let(::listOf)
    val from = pointOf(text.substringBefore(':'), boardSize) ?: return null
    val to = pointOf(text.substringAfter(':'), boardSize) ?: return null
    return (minOf(from.row, to.row)..maxOf(from.row, to.row)).flatMap { row ->
        (minOf(from.column, to.column)..maxOf(from.column, to.column)).map { column -> BoardCoordinate(row, column) }
    }
}

/** SGF 좌표는 왼쪽 위가 `aa`이고 첫 글자가 **열**, 둘째가 **행**이다 — [BoardCoordinate]도 0행이 맨 위라 그대로 옮긴다. */
private fun pointOf(text: String, boardSize: BoardSize): BoardCoordinate? {
    if (text.length != 2) return null
    val column = text[0] - 'a'
    val row = text[1] - 'a'
    if (column !in 0 until boardSize.value || row !in 0 until boardSize.value) return null
    return BoardCoordinate(row = row, column = column)
}

/** 노드 하나의 속성들 — 같은 이름이 여러 값을 가질 수 있다(`AB[aa][bb]`). */
private class SgfNode(private val properties: Map<String, List<String>>) {
    fun has(key: String): Boolean = key in properties

    fun first(key: String): String? = properties[key]?.firstOrNull()

    fun all(key: String): List<String> = properties[key].orEmpty()

    /** 글자 값 — 앞뒤 공백을 걷고, 비었으면 null. */
    fun text(key: String): String? = first(key)?.trim()?.takeIf { it.isNotEmpty() }
}

/** 본 수순의 노드만 앞에서부터 읽는다. `(;`로 시작하지 않으면 null. */
private class SgfMainLine(private val text: String) {
    private var at = 0

    fun read(): List<SgfNode>? {
        at = text.indexOf('(')
        if (at < 0) return null
        at++
        skipSpace()
        if (peek() != ';') return null
        val nodes = mutableListOf<SgfNode>()
        while (at < text.length) {
            skipSpace()
            when (peek()) {
                ';' -> {
                    at++
                    nodes += readNode()
                }
                // 첫 하위 갈래로 들어가 계속 읽는다 — 본 수순이 거기서 이어진다.
                '(' -> at++
                // 처음 만나는 `)`가 본 수순의 끝이다.
                ')', null -> return nodes
                else -> at++
            }
        }
        return nodes
    }

    private fun readNode(): SgfNode {
        val properties = LinkedHashMap<String, MutableList<String>>()
        while (true) {
            skipSpace()
            val start = at
            while (at < text.length && text[at].isLetter()) at++
            if (start == at) break
            // 옛 표기(FF[1~3])의 소문자는 이름이 아니다 — 대문자만 이름이다(`AddBlack` → `AB`).
            val key = text.substring(start, at).filter { it.isUpperCase() }
            skipSpace()
            while (peek() == '[') {
                at++
                properties.getOrPut(key) { mutableListOf() } += readValue()
                skipSpace()
            }
        }
        return SgfNode(properties)
    }

    /** `]`까지의 값 — `\` 다음 글자는 그대로, `\` 다음 줄바꿈은 지운다(이어 쓴 줄). */
    private fun readValue(): String {
        val value = StringBuilder()
        while (at < text.length) {
            val c = text[at++]
            when {
                c == ']' -> return value.toString()
                c == '\\' && at < text.length -> {
                    val escaped = text[at++]
                    if (escaped == '\r' && peek() == '\n') at++
                    if (escaped != '\n' && escaped != '\r') value.append(escaped)
                }
                else -> value.append(c)
            }
        }
        return value.toString()
    }

    private fun skipSpace() {
        while (at < text.length && text[at].isWhitespace()) at++
    }

    private fun peek(): Char? = text.getOrNull(at)
}
