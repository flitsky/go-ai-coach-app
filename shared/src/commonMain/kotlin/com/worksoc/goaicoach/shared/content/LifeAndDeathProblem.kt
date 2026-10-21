package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.neighbors

/**
 * 「기본 사활」의 단원(백로그 #184). **순서가 곧 화면의 순서다** — 앞 단원을 풀었다는 전제로 뒤 단원의
 * 문제가 놓여 있다(두 눈을 알아야 옥집이 읽히고, 궁도를 알아야 급소가 읽힌다).
 *
 * ⚠️ 이름은 저장값이 아니다 — 푼 기록은 문제의 [LifeAndDeathProblem.id] 문자열로 남는다. 단원을 갈라도
 * 기록은 이어진다. 중급 · 고급(#277)은 맥에서 만든 문제다.
 */
enum class LifeAndDeathUnit {
    /** 두 눈이 있으면 산다 — 눈을 만들어 살고, 눈 자리를 빼앗아 잡는다. */
    TwoEyes,

    /** 옥집 — 눈처럼 보여도 끊는 자리가 남은 눈은 눈이 아니다. */
    FalseEye,

    /** 급소 — 궁도의 한가운데(직삼·곡삼·매화육궁 …)를 먼저 차지하는 쪽이 이긴다. */
    VitalPoint,

    /** 넓히기·좁히기 — 안에서 궁도를 넓혀 살고, 밖에서 좁혀 잡는다. */
    EyeSpace,

    /** 귀의 사활 — 귀는 같은 모양이라도 변보다 좁다(2·1의 급소, 판육). */
    Corner,

    /**
     * 중급(백로그 #277) — 몇 수 앞을 읽어야 풀린다. 맥에서 만들고 풀이기가 증명한 9줄 판 문제(`LifeAndDeathCandidateExportTest`) 가운데,
     * 사람 모델이 첫눈에 정답을 둘 확률이 기본 사활(약 0.9)보다 확실히 낮은 것(실험실 E11).
     */
    Intermediate,

    /** 고급(#277) — 첫 수가 첫눈에 안 보이고(사람 모델의 확률이 가장 낮은 쪽) 수순이 길다. */
    Advanced,
}

/** 단원의 묶음 — 단원 목록이 이 차례로 칸을 나눠 보인다(#277). */
enum class LifeAndDeathSection {
    /** 「기본 사활」(#184)의 다섯 단원. */
    Basics,

    /** 중급 · 고급 — 맥에서 만든 문제. */
    Practice,
}

/** 이 단원이 속한 묶음. */
val LifeAndDeathUnit.section: LifeAndDeathSection
    get() = when (this) {
        LifeAndDeathUnit.TwoEyes, LifeAndDeathUnit.FalseEye, LifeAndDeathUnit.VitalPoint,
        LifeAndDeathUnit.EyeSpace, LifeAndDeathUnit.Corner,
        -> LifeAndDeathSection.Basics
        LifeAndDeathUnit.Intermediate, LifeAndDeathUnit.Advanced -> LifeAndDeathSection.Practice
    }

/** 문제의 목표 — **문제를 푸는 쪽([LifeAndDeathProblem.toPlay])이 무엇을 해내야 하는가.** */
enum class LifeAndDeathGoal {
    /** [LifeAndDeathProblem.target]이 속한 상대 무리를 따낸다(또는 따낼 수밖에 없게 만든다). */
    Kill,

    /** [LifeAndDeathProblem.target]이 속한 내 무리가 끝까지 따내지지 않는다(빅도 산 것으로 친다). */
    Live,
}

/**
 * 사활 문제 하나 — **판 위의 사실만** 든다. 정답과 오답의 응수는 적지 않는다: [LifeAndDeathSolver]가
 * 문제 구역 안을 끝까지 풀어 본 결과가 정답이다(백로그 #184 — *"수순 하나를 정답으로 박는다"* 로 시작하면
 * 조금만 다르게 두어도 틀렸다고 말하는 화면이 된다). 문구는 `app-android`의 곁표에 있다.
 *
 * @param id 푼 기록의 저장 키. ⚠️ **바꾸지 말 것** — 바꾸면 그 문제를 푼 기록이 끊긴다(함정 1과 같은 결).
 *   쉼표·공백을 넣지 않는다(저장소가 쉼표로 잇는다).
 * @param diagram 판 그림, 위(9줄)에서 아래로 한 줄에 한 행. 칸은 공백으로 가른다 —
 *   `X` 흑 · `O` 백 · `.` 둘 수 있는 빈 점 · `,` 문제 밖의 빈 점(아무도 두지 않는다).
 *   돌이 놓인 자리는 그 돌이 따내지면 둘 수 있는 빈 점이 된다.
 * @param target 목표가 걸린 무리의 돌 하나(바둑 표기, 예: `B8`).
 * @param answer 지은이가 의도한 첫 수. **판정에는 쓰지 않는다** — 풀이기가 이 수를 정답으로 보는지
 *   테스트가 되묻는다(문제를 잘못 놓았을 때 알아채려는 것). 「정답 보기」가 여럿 중 이 수를 먼저 보인다.
 */
data class LifeAndDeathProblem(
    val id: String,
    val unit: LifeAndDeathUnit,
    val goal: LifeAndDeathGoal,
    val toPlay: StoneColor,
    val target: String,
    val answer: String,
    val diagram: List<String>,
) {
    val boardSize: BoardSize = when (diagram.size) {
        9 -> BoardSize.Nine
        13 -> BoardSize.Thirteen
        19 -> BoardSize.Nineteen
        else -> throw IllegalArgumentException("Problem $id: a diagram has 9, 13 or 19 rows, not ${diagram.size}")
    }

    private val cells: List<List<Char>> = diagram.mapIndexed { row, line ->
        val cells = line.trim().split(' ').filter { it.isNotEmpty() }
        require(cells.size == boardSize.value) {
            "Problem $id: row ${row + 1} has ${cells.size} cells, expected ${boardSize.value}"
        }
        cells.map { cell ->
            require(cell.length == 1 && cell[0] in DiagramCells) { "Problem $id: unknown cell '$cell'" }
            cell[0]
        }
    }

    /** 처음 국면의 돌. */
    val stones: Map<BoardCoordinate, StoneColor> = buildMap {
        cells.forEachIndexed { row, line ->
            line.forEachIndexed { column, cell ->
                when (cell) {
                    'X' -> put(BoardCoordinate(row, column), StoneColor.Black)
                    'O' -> put(BoardCoordinate(row, column), StoneColor.White)
                }
            }
        }
    }

    /**
     * 수를 둘 수 있는 자리 — `.`과, 돌이 놓인 자리 전부(따내지면 빈다). `,`만 빠진다.
     * ⚠️ 순서가 결정적이어야 한다(위에서 아래, 왼쪽에서 오른쪽) — 풀이기가 이 순서로 수를 보고, 같은 값이면
     * 앞의 것을 고른다. 셋(`Set`)의 해시 순서에 맡기면 응수가 기기마다 달라질 수 있다.
     */
    val playable: List<BoardCoordinate> = buildList {
        cells.forEachIndexed { row, line ->
            line.forEachIndexed { column, cell -> if (cell != ',') add(BoardCoordinate(row, column)) }
        }
    }

    val targetPoint: BoardCoordinate = BoardCoordinate.fromLabel(target, boardSize)
    val answerPoint: BoardCoordinate = BoardCoordinate.fromLabel(answer, boardSize)

    /**
     * 판에 보일 줄 수 — 왼쪽 위 귀에서부터(백로그 #277 · #279). 19줄 판 문제(고전 사활 — #279)는 문제가 왼쪽 위 귀에 있어, 돌과 문제 구역을 다 담고
     * 한 줄을 더 띄운 정사각형만 크게 보인다(폰 폭에서 19줄 판 전체는 칸이 작아 잘못 누르기 쉽다). 9줄 · 13줄 판은 `null` — 판 전체.
     * ⚠️ 지금 실은 문제는 모두 9줄 판이라 늘 `null`이다 — 19줄 판 문제가 처음 실리는 날 화면에서 확인할 것.
     */
    val visibleLines: Int? = if (boardSize != BoardSize.Nineteen) {
        null
    } else {
        val used = stones.keys + playable.filter { it !in stones }
        (used.maxOf { maxOf(it.row, it.column) } + 2).coerceAtMost(boardSize.value)
    }

    /** 목표가 걸린 무리의 색 — [LifeAndDeathGoal.Kill]이면 상대, [LifeAndDeathGoal.Live]이면 나. */
    val targetColor: StoneColor = requireNotNull(stones[targetPoint]) { "Problem $id: no stone at target $target" }

    init {
        require(!id.contains(',') && !id.contains(' ')) { "Problem $id: an id holds no comma or space" }
        when (goal) {
            LifeAndDeathGoal.Kill -> require(targetColor != toPlay) { "Problem $id: Kill targets the opponent's group" }
            LifeAndDeathGoal.Live -> require(targetColor == toPlay) { "Problem $id: Live targets the solver's own group" }
        }
        require(stones[answerPoint] == null && answerPoint in playable) { "Problem $id: answer $answer is not an empty playable point" }
        // 숨이 없는 돌은 판에 있을 수 없다 — 맥에서 만든 후보가 닫힌 구멍에 돌을 놓아 처음 드러났다(#277, 엔진이 그 판을 거부했다).
        val breathless = stones.keys.filter { stone -> groupAt(stone).none { member -> member.neighbors(boardSize).any { it !in stones } } }
        require(breathless.isEmpty()) { "Problem $id: stones without a liberty ${breathless.map { it.label(boardSize) }}" }
    }

    /** 아무 수도 두기 전의 국면. 학습 단원(`StudyLesson.startState`)과 같은 규칙(일본식)으로 앉힌다. */
    fun startState(): GameState =
        GameState(
            boardSize = boardSize,
            ruleset = Ruleset.Japanese,
            nextPlayer = toPlay,
            stones = stones,
            moves = emptyList(),
        )

    private companion object {
        val DiagramCells = setOf('X', 'O', '.', ',')
    }
}
