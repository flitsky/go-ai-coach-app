package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.GoldenBoard
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.goldenBoard
import com.worksoc.goaicoach.shared.domain.goldenPoint

/**
 * **refactor backlog #89의 재현 국면** — 면적계가 접바둑에서 앱 계가가 백의 보정 N점을 빠뜨리던 판.
 *
 * 9x9 2점(G7·C3), 백 선, 백·흑 12수씩 둔 뒤 백·흑 순으로 두 번 통과. 조사가 KataGo 1.16.4에
 * 앱 모델로 넣은 GTP 명령열(`boardsize 9`/`komi`/`kata-set-rules`/`clear_board`/
 * `set_free_handicap G7 C3`/이 수순/`pass`×2)과 **같은 수순**이다. 잰 `final_score`는
 * `BoardScoringGoldenTest.chineseHandicapGameGivesWhiteTheHandicapBonusLikeKataGoFinalScore`의 KDoc 표에 있다.
 *
 * 계가 골든(`BoardScoringGoldenTest`)과 종국 판정 경로(`EndgameResolverTest`·`EndgameScoreSelectorTest`)가
 * **같은 판**을 써야 "계가기가 고쳐졌다"와 "종국 결과가 고쳐졌다"가 같은 국면에 대한 말이 된다 — 그래서
 * 여기 한 곳에 둔다.
 */

/**
 * 종국 판(흑 = G7·C3 접바둑 돌 + 12수, 백 = 12수).
 * 흑 영역 = 돌 14 + 집 30 = 44, 백 영역 = 돌 12 + 집 25 = 37, 공배·사석·따낸 돌 0.
 */
internal fun chineseHandicapProbeBoard(): GoldenBoard = goldenBoard(
    ". . . . . . . . .",
    ". . . . . . X X X",
    ". . . X X X X O O",
    ". . X X O O O O .",
    ". . X O O . . . .",
    ". . X O . . . . .",
    ". . X O . . . . .",
    ". . X O . . . . .",
    ". . X O . . . . .",
)

/**
 * 같은 수순을 앱의 착수 규칙으로 둔다 — 그림을 바로 상태로 만들지 않는 이유는 `withHandicap`
 * (백 선·`handicapCount`)과 두 번 통과까지 실제 경로로 재현하려는 것이다.
 */
internal fun chineseHandicapProbeState(
    ruleset: Ruleset,
    komi: Double,
): GameState {
    val whiteThenBlack = listOf(
        "D1" to "C1", "D2" to "C2", "D3" to "C4", "D4" to "C5",
        "D5" to "C6", "E5" to "D6", "E6" to "D7", "F6" to "E7",
        "G6" to "F7", "H6" to "G8", "H7" to "H8", "J7" to "J8",
    )
    var state = GameState.withHandicap(BoardSize.Nine, ruleset, handicapCount = 2, komi = komi)
    for ((white, black) in whiteThenBlack) {
        state = state
            .play(Move.Play(StoneColor.White, goldenPoint(white)))
            .play(Move.Play(StoneColor.Black, goldenPoint(black)))
    }
    return state
        .play(Move.Pass(StoneColor.White))
        .play(Move.Pass(StoneColor.Black))
}
