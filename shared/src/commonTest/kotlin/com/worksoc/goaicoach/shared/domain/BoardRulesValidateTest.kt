package com.worksoc.goaicoach.shared.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **`BoardRules.validate` — `play`가 던지는 모든 거부 사유와 정확히 같아야 한다(리팩토링 백로그 #37).**
 *
 * ## 왜 이 테스트가 필요한가
 * `validate`는 `play`처럼 상태를 바꾸거나 예외를 던지지 않고 값으로만 판정한다
 * ([LegalMoveGenerator]가 판 전체를 훑을 때, [GoBoard][com.worksoc.goaicoach.ui.GoBoard]가
 * 끌기 매 프레임 좌표 하나를 물을 때 그 비용을 없애려는 것). 두 판정 경로가 하나라도
 * 어긋나면 화면은 허용하는데 실제 착수는 거부되거나, 그 반대가 된다 — 조용히 드러나는
 * 종류의 버그다.
 *
 * ## 여기서 고정하는 것
 * - **거부 사유 다섯 가지**를 표로 — `validate`가 `play`가 거부하는 것과 같은
 *   [MoveRejection] 값을 돌려주는지, `BoardRulesGoldenTest`의 여섯 국면을 그대로 재사용해 본다.
 * - **무작위 대국 수천 국면**에서 판의 모든 좌표(현재 차례)와 통과·기권(양쪽 차례)에 대해
 *   `play`의 성공/실패와 `validate`의 null/거부가 정확히 일치하는지 대조한다.
 */
class BoardRulesValidateTest {
    @Test
    fun validateReturnsTheMatchingRejectionForEachDistinctReason() {
        val cases = listOf(
            "차례가 아닌 쪽의 착수" to (
                plainState(nextPlayer = StoneColor.Black) to Move.Play(StoneColor.White, point("E5"))
            ),
            "차례가 아닌 쪽의 통과" to (
                plainState(nextPlayer = StoneColor.Black) to Move.Pass(StoneColor.White)
            ),
            "차례가 아닌 쪽의 기권" to (
                plainState(nextPlayer = StoneColor.Black) to Move.Resign(StoneColor.White)
            ),
            "판 밖 좌표" to (
                plainState(nextPlayer = StoneColor.Black) to
                    Move.Play(StoneColor.Black, BoardCoordinate(row = 9, column = 0))
            ),
            "이미 돌이 있는 자리" to (occupiedState() to Move.Play(StoneColor.Black, point("E5"))),
            "패 즉시 되따기" to (koState() to Move.Play(StoneColor.White, point("D5"))),
            "단점 자살수" to (singlePointSuicideState() to Move.Play(StoneColor.White, point("E5"))),
            "그룹 자살수" to (groupSuicideState() to Move.Play(StoneColor.Black, point("A8"))),
        )

        for ((name, stateAndMove) in cases) {
            val (state, move) = stateAndMove
            // 두 판정 경로가 같은 국면·같은 수에서 정확히 같은 결론(성공/실패)에 이르는지 먼저 본다.
            val playFailure = runCatching { state.play(move) }.exceptionOrNull()
            val rejection = BoardRules.validate(state, move)
            assertEquals(playFailure != null, rejection != null, "$name: play와 validate의 성공/실패가 어긋났다")
        }

        assertIs<MoveRejection.WrongTurn>(
            BoardRules.validate(plainState(StoneColor.Black), Move.Play(StoneColor.White, point("E5"))),
        )
        assertIs<MoveRejection.WrongTurn>(
            BoardRules.validate(plainState(StoneColor.Black), Move.Pass(StoneColor.White)),
        )
        assertIs<MoveRejection.WrongTurn>(
            BoardRules.validate(plainState(StoneColor.Black), Move.Resign(StoneColor.White)),
        )
        assertIs<MoveRejection.OutOfBounds>(
            BoardRules.validate(
                plainState(StoneColor.Black),
                Move.Play(StoneColor.Black, BoardCoordinate(row = 9, column = 0)),
            ),
        )
        assertIs<MoveRejection.Occupied>(
            BoardRules.validate(occupiedState(), Move.Play(StoneColor.Black, point("E5"))),
        )
        assertIs<MoveRejection.KoRecapture>(
            BoardRules.validate(koState(), Move.Play(StoneColor.White, point("D5"))),
        )
        assertIs<MoveRejection.Suicide>(
            BoardRules.validate(singlePointSuicideState(), Move.Play(StoneColor.White, point("E5"))),
        )
        assertIs<MoveRejection.Suicide>(
            BoardRules.validate(groupSuicideState(), Move.Play(StoneColor.Black, point("A8"))),
        )

        // 합법수는 여전히 합법수다 — 거부 사유만 고정하고 통과 경로를 깨뜨리지 않았는지도 본다.
        assertEquals(null, BoardRules.validate(plainState(StoneColor.Black), Move.Play(StoneColor.Black, point("E5"))))
    }

    @Test
    fun validateAgreesWithPlayAcrossThousandsOfRandomPositions() {
        var statesChecked = 0
        var moveChecksRun = 0
        val mismatches = mutableListOf<String>()

        fun crossCheck(
            state: GameState,
            move: Move,
        ) {
            val expectedLegal = runCatching { state.play(move) }.isSuccess
            val actualLegal = BoardRules.validate(state, move) == null
            moveChecksRun++
            if (expectedLegal != actualLegal) {
                mismatches += "${move.describe(state.boardSize)} @ ${state.stones.size}수: " +
                    "play=${if (expectedLegal) "성공" else "거부"}, validate=${if (actualLegal) "null" else "거부"}"
            }
        }

        fun checkState(state: GameState) {
            statesChecked++
            val player = state.nextPlayer
            for (coordinate in state.boardSize.allCoordinates()) {
                crossCheck(state, Move.Play(player, coordinate))
            }
            crossCheck(state, Move.Pass(player))
            crossCheck(state, Move.Pass(player.opponent))
            crossCheck(state, Move.Resign(player))
            crossCheck(state, Move.Resign(player.opponent))
        }

        val seedRandom = Random(20260937)
        repeat(60) {
            randomGame(BoardSize.Nine, halfMoves = 50, random = Random(seedRandom.nextLong())).forEach(::checkState)
        }
        repeat(20) {
            randomGame(BoardSize.Thirteen, halfMoves = 50, random = Random(seedRandom.nextLong())).forEach(::checkState)
        }

        assertTrue(statesChecked >= 2000, "무작위 국면이 충분히 돌지 않았다: $statesChecked 국면")
        assertEquals(
            emptyList<String>(),
            mismatches,
            "validate와 play가 어긋난 경우 ${mismatches.size}건 " +
                "(국면 $statesChecked 개, 판정 $moveChecksRun 회 대조):\n" +
                mismatches.take(20).joinToString("\n"),
        )
    }

    // ------------------------------------------------------------------- 무작위 대국 생성

    /**
     * 빈 판에서 시작해 `halfMoves`수까지, 매 수 **실제로 합법한 자리 중 하나**를 골라 두는
     * 무작위 대국을 재생한다. 연속 두 번 통과하면 그 자리에서 멈춘다.
     *
     * ⚠️ 다음 수를 고르는 데 쓰는 합법수 판정은 일부러 `runCatching { play(...) }`로 직접
     * 훑는다 — 이 테스트가 대조하려는 대상([BoardRules.validate])에 기대어 자기 자신을
     * 검증하는 순환을 피하려는 것이다.
     */
    private fun randomGame(
        boardSize: BoardSize,
        halfMoves: Int,
        random: Random,
    ): List<GameState> {
        val states = mutableListOf<GameState>()
        var state = GameState.empty(boardSize, Ruleset.Chinese)
        states += state
        var consecutivePasses = 0

        for (move in 0 until halfMoves) {
            if (consecutivePasses >= 2) break
            val player = state.nextPlayer
            val legalCoordinates = state.boardSize
                .allCoordinates()
                .filter { coordinate -> runCatching { state.play(Move.Play(player, coordinate)) }.isSuccess }
                .toList()

            state = if (legalCoordinates.isEmpty() || random.nextInt(15) == 0) {
                consecutivePasses++
                state.play(Move.Pass(player))
            } else {
                consecutivePasses = 0
                state.play(Move.Play(player, legalCoordinates[random.nextInt(legalCoordinates.size)]))
            }
            states += state
        }

        return states
    }

    // ------------------------------------------------------------------- 표와 판(BoardRulesGoldenTest와 같은 국면)

    private fun plainState(nextPlayer: StoneColor): GameState = goldenBoard(
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
    ).toState(nextPlayer = nextPlayer)

    private fun occupiedState(): GameState = goldenBoard(
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . O . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
    ).toState(nextPlayer = StoneColor.Black)

    /** 흑이 E5로 D5를 따낸 직후 — 백은 D5로 곧바로 되딸 수 없다. */
    private fun koState(): GameState = goldenBoard(
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . X O . . . .",
        ". . X . X O . . .",
        ". . . X O . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
    ).toState(
        nextPlayer = StoneColor.White,
        koPoint = point("D5"),
        koForbiddenFor = StoneColor.White,
    )

    private fun singlePointSuicideState(): GameState = goldenBoard(
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . X . . . .",
        ". . . X . X . . .",
        ". . . . X . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
    ).toState(nextPlayer = StoneColor.White)

    /**
     * 좌상귀 — 흑 A9·B9가 이미 놓여 있고 백이 C9·B8·A7로 감쌌다.
     * 흑이 A8로 이으면 세 점 그룹의 활로가 0이 된다.
     */
    private fun groupSuicideState(): GameState = goldenBoard(
        "X X O . . . . . .",
        ". O . . . . . . .",
        "O . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
    ).toState(nextPlayer = StoneColor.Black)

    private fun point(label: String): BoardCoordinate = goldenPoint(label, BoardSize.Nine)
}
