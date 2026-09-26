package com.worksoc.goaicoach.shared.domain

import com.worksoc.goaicoach.shared.scoring.BoardAreaScorer
import com.worksoc.goaicoach.shared.scoring.BoardRegionAnalyzer
import com.worksoc.goaicoach.shared.scoring.BoardTerritoryScorer

/**
 * 골든 테스트가 판을 **눈으로 검증할 수 있게** 기술하기 위한 다이어그램 파서.
 *
 * ## 왜 코드 조립이 아니라 그림인가
 * 기존 도메인 테스트는 `point("A9") to StoneColor.White`를 60줄 늘어놓는 방식으로 판을 만든다
 * (`EndgameRegressionTest`). 그 형태는 **디버그 리포트에서 받아 적은 실제 국면**을 옮길 때는 맞지만,
 * 골든 테스트가 고정하려는 것 — *"이 모양에서 이 점수가 나와야 한다"* — 에는 맞지 않는다.
 * 기대값이 틀렸는지 판이 틀렸는지 **읽어서 가릴 수 없기** 때문이다.
 *
 * 그래서 여기서는 판을 그림 그대로 적는다.
 *
 * ```
 * goldenBoard(
 *     ". . . X . O . . .",
 *     ...
 * )
 * ```
 *
 * `X`=흑, `O`=백, `.`=빈 점. 공백은 무시하므로 칸을 띄워 정렬해도 된다.
 * **맨 윗줄이 9선**(9x9 기준)이고 맨 왼쪽 칸이 A열이다 — 화면에 보이는 바둑판과 같은 방향이다.
 *
 * ## 이 파서 자체가 틀리면 골든 테스트 전체가 무의미하다
 * 그래서 줄 수·칸 수·기호를 전부 `require`로 막는다. 조용히 다른 판을 만드는 경로가 없어야 한다.
 * 파서의 방향(윗줄=높은 수, 왼쪽=A열)은 `GoldenBoardDiagramTest`가 `BoardCoordinate.label`과
 * 대조해 고정한다.
 */
internal data class GoldenBoard(
    val boardSize: BoardSize,
    val stones: Map<BoardCoordinate, StoneColor>,
)

internal const val GoldenBlack = 'X'
internal const val GoldenWhite = 'O'
internal const val GoldenEmpty = '.'

/**
 * 다이어그램 줄들을 판으로 옮긴다. 줄 수가 곧 판 크기이므로 9줄이면 9x9, 19줄이면 19x19다.
 *
 * @throws IllegalArgumentException 줄 수가 지원하지 않는 판 크기이거나, 어떤 줄의 칸 수가
 *   판 크기와 다르거나, `X`/`O`/`.` 이외의 기호가 섞였을 때. **조용히 통과하지 않는다.**
 */
internal fun goldenBoard(vararg rows: String): GoldenBoard {
    val cleaned = rows.map { row -> row.filterNot { it.isWhitespace() } }
    require(cleaned.isNotEmpty()) { "빈 다이어그램은 판이 아니다." }
    val boardSize = BoardSize(cleaned.size)

    val stones = buildMap {
        cleaned.forEachIndexed { row, line ->
            require(line.length == boardSize.value) {
                "${boardSize.value - row}선의 칸이 ${line.length}개다 — ${boardSize.value}개여야 한다: $line"
            }
            line.forEachIndexed { column, symbol ->
                val color = when (symbol) {
                    GoldenEmpty -> null
                    GoldenBlack -> StoneColor.Black
                    GoldenWhite -> StoneColor.White
                    else -> throw IllegalArgumentException(
                        "알 수 없는 기호 '$symbol' — '$GoldenBlack'(흑)/'$GoldenWhite'(백)/'$GoldenEmpty'(빈 점)만 쓴다.",
                    )
                }
                if (color != null) {
                    put(BoardCoordinate(row, column), color)
                }
            }
        }
    }

    return GoldenBoard(boardSize = boardSize, stones = stones)
}

/**
 * 다이어그램을 `GameState`로 만든다.
 *
 * ⚠️ 여기서 만든 상태는 **착수 규칙을 통과한 적이 없다.** 골든 테스트는 "이 모양에서 이 답"을
 * 고정하는 것이지 그 모양에 이르는 수순을 재현하는 것이 아니므로, 도달 불가능한 모양도 일부러
 * 만들 수 있어야 한다(예: 전부 채운 판).
 */
internal fun GoldenBoard.toState(
    ruleset: Ruleset = Ruleset.Chinese,
    nextPlayer: StoneColor = StoneColor.Black,
    komi: Double = DefaultKomi,
    capturedByBlack: Int = 0,
    capturedByWhite: Int = 0,
    moves: List<Move> = emptyList(),
    handicapCount: Int = 0,
    koPoint: BoardCoordinate? = null,
    koForbiddenFor: StoneColor? = null,
): GameState =
    GameState(
        boardSize = boardSize,
        ruleset = ruleset,
        nextPlayer = nextPlayer,
        stones = stones,
        moves = moves,
        capturedByBlack = capturedByBlack,
        capturedByWhite = capturedByWhite,
        koPoint = koPoint,
        koForbiddenFor = koForbiddenFor,
        handicapCount = handicapCount,
        komi = komi,
    )

internal fun GoldenBoard.stoneCount(color: StoneColor): Int = stones.count { it.value == color }

/** `"E5"` 같은 GTP 라벨을 좌표로. 테스트가 좌표 산술을 하지 않게 하려는 것이다. */
internal fun goldenPoint(
    label: String,
    boardSize: BoardSize = BoardSize.Nine,
): BoardCoordinate = BoardCoordinate.fromLabel(label, boardSize)

/**
 * 두 계가기가 **빈 점의 주인을 누구로 보는가**를 공개 결과만으로 되짚은 값.
 *
 * 두 계가기는 이 판정을 `BoardRegionAnalyzer`에게 맡긴다(refactor backlog #38). 그래도 여기서는
 * 분석기를 부르지 않고 각 계가기가 공개하는 합에서 자기 항을 뺀다 — 그러면 **빈 점 소유 수만**
 * 남고, 어느 계가기가 분석기 대신 제 판정을 다시 들이면 그 차이가 이 값에 드러난다.
 *
 * - 영역(중국식): `blackArea = 흑 돌 수 + 흑 소유 빈 점`
 * - 집(일본식):   `blackArea = 흑 소유 빈 점 + 흑 사석`
 *
 * 백 쪽 합계에는 접바둑 보정이 들어 있을 수 있어 둘 다 그것도 뺀다(#89). 보정은 계가기 종류가 아니라
 * `state.ruleset`이 정하므로(#106) 면적계가 룰셋 판을 집 계가기에 넣어도 보정이 붙는다.
 *
 * 분석기를 직접 부른 값은 [regionAnalyzerOwnership]이다.
 */
internal data class EmptyPointOwnership(
    val black: Int,
    val white: Int,
)

internal fun areaScorerOwnership(state: GameState): EmptyPointOwnership {
    val score = BoardAreaScorer.score(state, komi = 0.0)
    val blackArea = requireNotNull(score.blackArea) { "로컬 영역 계가기가 blackArea를 비워 두면 안 된다." }
    // 덤은 0으로 눌렀지만 접바둑 보정(#89)은 합계에 남는다 — 빼야 빈 점 소유 수만 남는다.
    val whiteArea = requireNotNull(score.whiteAreaWithKomi) { "로컬 영역 계가기가 whiteArea를 비워 두면 안 된다." } -
        score.whiteHandicapBonus
    return EmptyPointOwnership(
        black = (blackArea - state.stones.count { it.value == StoneColor.Black }).toInt(),
        white = (whiteArea - state.stones.count { it.value == StoneColor.White }).toInt(),
    )
}

internal fun territoryScorerOwnership(state: GameState): EmptyPointOwnership {
    val score = BoardTerritoryScorer.score(state, komi = 0.0)
    val blackScore = requireNotNull(score.blackArea) { "로컬 집 계가기가 blackArea를 비워 두면 안 된다." }
    val whiteScore = requireNotNull(score.whiteAreaWithKomi) { "로컬 집 계가기가 whiteArea를 비워 두면 안 된다." } -
        score.whiteHandicapBonus
    return EmptyPointOwnership(
        black = (blackScore - state.capturedBy(StoneColor.Black)).toInt(),
        white = (whiteScore - state.capturedBy(StoneColor.White)).toInt(),
    )
}

/** `BoardRegionAnalyzer`를 직접 부른 빈 점 소유 수 — 두 계가기에서 되짚은 값이 이것과 같아야 한다. */
internal fun regionAnalyzerOwnership(state: GameState): EmptyPointOwnership =
    BoardRegionAnalyzer.ownedEmptyPoints(state).let { owned ->
        EmptyPointOwnership(black = owned.black, white = owned.white)
    }
