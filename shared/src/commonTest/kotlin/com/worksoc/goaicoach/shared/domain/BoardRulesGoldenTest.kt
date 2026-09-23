package com.worksoc.goaicoach.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **`BoardRules` 골든 테스트 — 착수가 거부되는 조건과, 패(ko)가 *서지 않는* 조건.**
 *
 * ## 기존 테스트가 이미 덮은 것
 * `GameStateTest`가 단수 따냄·그룹 따냄·단점 자살수·패 즉시 되따기·되무르기를 덮고 있다.
 * 여기서는 **그 밖의 것**만 다룬다. 같은 것을 두 번 단언하는 테스트는 가치가 없다.
 *
 * ## 여기서 새로 고정하는 것
 * - 거부 사유 **여섯 가지**를 표로 — 지금까지는 `IllegalArgumentException`이 났다는 것만 보고
 *   **어느 규칙이 걸렸는지는 아무도 확인하지 않았다.** 다른 이유로 터져도 초록이었다.
 * - **여러 점을 이어 놓는 자살수**(단점 자살수가 아니라 3점짜리 그룹이 통째로 숨이 막히는 경우).
 * - **패가 서지 않는 두 경우** — 두 점 이상을 따냈을 때, 그리고 따낸 돌의 활로가 둘 이상일 때.
 *   `nextKoPoint`의 두 조건(`capturedStones.singleOrNull()`, `ownGroup.stones.size == 1 &&
 *   ownGroup.liberties.size == 1`)을 각각 무너뜨리는 국면이다.
 * - **백이 따낸 사석은 백 쪽에 쌓인다** — 기존 테스트는 흑이 따내는 경우만 본다.
 * - **기권은 차례를 넘기지 않는다** — 통과(pass)와의 비대칭. `Move.Resign`은 이 저장소의 어떤
 *   테스트에서도 `BoardRules.play`를 통과한 적이 없었다.
 */
class BoardRulesGoldenTest {

    /**
     * 거부되는 착수와 **그 사유**를 표로 고정한다.
     *
     * 메시지 조각까지 보는 이유: `assertFailsWith`만으로는 *"차례가 아니라서"* 거부된 것을
     * *"자살수라서"* 거부됐다고 착각해도 초록이다. 규칙이 뒤섞여도 알 수 없다.
     */
    @Test
    fun rejectedMoveTableIsGolden() {
        val failures = rejectionCases().mapNotNull { case ->
            val failure = runCatching { case.state.play(case.move) }.exceptionOrNull()
            when {
                failure == null -> "${case.name}: 거부되지 않고 통과했다"
                failure !is IllegalArgumentException -> "${case.name}: ${failure::class.simpleName}로 터졌다"
                !failure.message.orEmpty().contains(case.expectedMessagePart) ->
                    "${case.name}: 사유가 \"${failure.message}\" — \"${case.expectedMessagePart}\"를 기대했다"
                else -> null
            }
        }

        assertEquals(emptyList<String>(), failures, "착수 거부 골든 표가 어긋났다:\n" + failures.joinToString("\n"))
    }

    /**
     * **두 점 이상을 한 번에 따내면 패가 서지 않는다.**
     *
     * `nextKoPoint`의 첫 조건(`capturedStones.singleOrNull()`)이다. 두 점을 따낸 자리는 상대가
     * 곧바로 되따도 같은 모양이 반복되지 않으므로 금지할 이유가 없다.
     */
    @Test
    fun capturingTwoStonesAtOnceLeavesNoKoPoint() {
        val before = goldenBoard(
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . X X . . . .",
            ". . X O O . . . .",
            ". . . X X . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
        ).toState(nextPlayer = StoneColor.Black)

        val after = before.play(Move.Play(StoneColor.Black, point("F5")))

        assertEquals(null, after.stoneAt(point("D5")))
        assertEquals(null, after.stoneAt(point("E5")))
        assertEquals(2, after.capturedBy(StoneColor.Black))
        assertEquals(null, after.koPoint)
        assertEquals(null, after.koForbiddenFor)
    }

    /**
     * **한 점을 따냈어도 따낸 돌의 활로가 둘 이상이면 패가 서지 않는다.**
     *
     * `nextKoPoint`의 둘째 조건(`ownGroup.liberties.size == 1`)이다. 되따기가 성립하지 않는
     * 모양이므로 금지가 필요 없다. 같은 착수에서 **백이 따내면 사석이 백 쪽에 쌓이는 것**도
     * 함께 고정한다 — 기존 테스트는 흑이 따내는 경우만 본다.
     */
    @Test
    fun whiteCaptureCreditsWhitePrisonersAndSetsNoKoWhenTheCapturerKeepsSpareLiberties() {
        val before = goldenBoard(
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . O . . . .",
            ". . . O X . . . .",
            ". . . . O . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
        ).toState(nextPlayer = StoneColor.White, capturedByBlack = 2, capturedByWhite = 3)

        val after = before.play(Move.Play(StoneColor.White, point("F5")))

        assertEquals(null, after.stoneAt(point("E5")))
        assertEquals(2, after.capturedBy(StoneColor.Black))
        assertEquals(4, after.capturedBy(StoneColor.White))
        assertEquals(null, after.koPoint)
        assertEquals(null, after.koForbiddenFor)
        assertEquals(StoneColor.Black, after.nextPlayer)
    }

    /**
     * **기권은 차례를 넘기지 않는다** — 통과(pass)는 넘긴다.
     *
     * 대국이 끝났으므로 다음 차례에 의미가 없다는 판단이 코드에 들어 있고, 그것을 여기서 고정한다.
     * 이 비대칭을 모르고 `pass`와 같은 모양으로 "정리"하면 기권 직후의 화면 표시가 조용히 바뀐다.
     */
    @Test
    fun resignAppendsTheMoveWithoutHandingOverTheTurn() {
        val before = goldenBoard(
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . X . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
        ).toState(
            nextPlayer = StoneColor.White,
            koPoint = point("D5"),
            koForbiddenFor = StoneColor.White,
        )

        val resigned = before.play(Move.Resign(StoneColor.White))
        val passed = before.play(Move.Pass(StoneColor.White))

        assertEquals(StoneColor.White, resigned.nextPlayer)
        assertEquals(StoneColor.Black, passed.nextPlayer)
        assertEquals(listOf(Move.Resign(StoneColor.White)), resigned.moves)
        assertEquals(null, resigned.koPoint)
        assertEquals(null, resigned.koForbiddenFor)
        assertEquals(before.stones, resigned.stones)
    }

    /**
     * **여러 점을 이어 놓는 자살수도 거부된다.**
     *
     * 기존 `suicideMoveIsRejected`는 단점 자살수만 본다. 여기서는 착수로 세 점짜리 그룹이 완성되며
     * 그 그룹 전체의 활로가 0이 되는 경우다 — `ownGroup`을 한 점이 아니라 **그룹으로** 모아야만
     * 잡히는 국면이고, 그 자리에서 따낼 상대 돌도 없다.
     */
    @Test
    fun connectingIntoAGroupWithNoLibertiesIsAlsoSuicide() {
        val before = groupSuicideState()

        val failure = assertFailsWith<IllegalArgumentException> {
            before.play(Move.Play(StoneColor.Black, point("A8")))
        }

        assertTrue(failure.message.orEmpty().contains("Suicide"), failure.message.orEmpty())
        // 판이 아니라 **활로가 0이라는 사실**이 거부 사유임을 보인다 — 백 A7 하나를 치우면
        // 같은 착수가 활로 하나를 얻어 그대로 합법이 된다.
        val withOneLiberty = before.copy(stones = before.stones - point("A7"))
        assertEquals(
            StoneColor.Black,
            withOneLiberty.play(Move.Play(StoneColor.Black, point("A8"))).stoneAt(point("A8")),
        )
    }

    // ------------------------------------------------------------------- 표와 판

    private data class RejectionCase(
        val name: String,
        val state: GameState,
        val move: Move,
        val expectedMessagePart: String,
    )

    private fun rejectionCases(): List<RejectionCase> = listOf(
        RejectionCase(
            name = "차례가 아닌 쪽의 착수",
            state = plainState(nextPlayer = StoneColor.Black),
            move = Move.Play(StoneColor.White, point("E5")),
            expectedMessagePart = "Expected Black",
        ),
        RejectionCase(
            name = "차례가 아닌 쪽의 통과",
            state = plainState(nextPlayer = StoneColor.Black),
            move = Move.Pass(StoneColor.White),
            expectedMessagePart = "Expected Black",
        ),
        RejectionCase(
            name = "차례가 아닌 쪽의 기권",
            state = plainState(nextPlayer = StoneColor.Black),
            move = Move.Resign(StoneColor.White),
            expectedMessagePart = "Expected Black",
        ),
        RejectionCase(
            name = "판 밖 좌표",
            state = plainState(nextPlayer = StoneColor.Black),
            move = Move.Play(StoneColor.Black, BoardCoordinate(row = 9, column = 0)),
            expectedMessagePart = "outside 9x9",
        ),
        RejectionCase(
            name = "이미 돌이 있는 자리",
            state = occupiedState(),
            move = Move.Play(StoneColor.Black, point("E5")),
            expectedMessagePart = "already occupied",
        ),
        RejectionCase(
            name = "패 즉시 되따기",
            state = koState(),
            move = Move.Play(StoneColor.White, point("D5")),
            expectedMessagePart = "Illegal ko recapture",
        ),
        RejectionCase(
            name = "단점 자살수",
            state = singlePointSuicideState(),
            move = Move.Play(StoneColor.White, point("E5")),
            expectedMessagePart = "Suicide",
        ),
        RejectionCase(
            name = "그룹 자살수",
            state = groupSuicideState(),
            move = Move.Play(StoneColor.Black, point("A8")),
            expectedMessagePart = "Suicide",
        ),
    )

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
