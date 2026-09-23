package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **`DeadStoneDetector`/`DeadStoneCleaner` 골든 테스트.**
 *
 * ## 이 둘이 무엇인가
 * 양패스로 끝난 판에서 계가기는 *"사석은 이미 제거돼 있다"* 를 전제한다. 그 전제를 만들어 주는 것이
 * 이 둘이다 — 탐지기가 **한 수로 즉시 따이는 그룹**을 찾고, 정리기가 그 돌을 걷어 사석으로 옮긴다.
 * 엔진의 `final_status_list dead`가 1순위이고 탐지기는 그 폴백이다(`docs/engine/error-cases/`).
 *
 * ## 기존 테스트가 이미 덮은 것
 * `EndgameRegressionTest`가 실제 사고 국면 세 건(우하단 G2·H2 사석, 정리 전 pass 승자 뒤집힘,
 * 백 pass 이후 흑 pass)을 고정하고 있고, `docs/engine/error-cases/`의 세 문서가 전부 그 테스트를
 * 가리키며 **해소됨**으로 닫혀 있다. `GameStateTest`는 정리기의 기본 동작 두 가지를 덮는다.
 * 여기서는 그 밖의 것만 다룬다.
 *
 * ## 여기서 새로 고정하는 것
 * - 탐지기가 **무엇을 사석으로 보지 않는지** — 활로가 둘 이상인 그룹.
 * - 탐지기가 **색을 가리지 않는다** — 흑 그룹이 단수면 흑 돌도 사석 후보로 나온다.
 * - 탐지기가 **살아 있는 판의 패 금지를 그대로 물려받는다** — 따낼 자리가 패금지면 그 그룹은
 *   후보에서 빠진다. 양패스 종료 시점에는 통과가 패를 지우므로 실전에서 닿지 않는 경로지만,
 *   코드가 그렇게 동작한다는 사실 자체를 박아 둔다.
 * - 정리기가 **두 색을 한 번에** 걷을 때 사석이 양쪽에 각각 쌓인다.
 * - 정리기가 **덤과 접바둑 수를 보존한다** — #1 「이어하기 덤 유실」과 같은 종류의 누락이
 *   이 경로에서 다시 나지 않게 하는 자리다.
 */
class DeadStoneGoldenTest {

    /**
     * 판마다 **사석으로 나와야 하는 좌표 집합**을 표로 고정한다.
     *
     * 반환 순서는 계약이 아니므로 집합으로 비교한다.
     */
    @Test
    fun deadStoneTableIsGolden() {
        val failures = detectorCases().mapNotNull { case ->
            val actual = DeadStoneDetector.capturableDeadStones(case.state)
                .map { it.label(case.state.boardSize) }
                .toSet()
            if (actual == case.expectedDeadStones) {
                null
            } else {
                "${case.name}: $actual (기대 ${case.expectedDeadStones})"
            }
        }

        assertEquals(emptyList<String>(), failures, "사석 탐지 골든 표가 어긋났다:\n" + failures.joinToString("\n"))
    }

    /**
     * 탐지한 사석을 정리한 점수가 **실제로 따내고 끝낸 판의 점수와 같아야 한다.**
     *
     * `EndgameRegressionTest`가 실제 디버그 로그 국면으로 같은 불변식을 고정하고 있다.
     * 여기서는 **눈으로 셀 수 있는 최소 국면**에서 같은 것을 본다 — 회귀가 났을 때 어디가
     * 틀렸는지 읽어서 알 수 있는 크기의 판이 하나는 있어야 한다.
     *
     * ⚠️ **두 룰셋에서 "같다"의 뜻이 다르다.**
     * 중국식은 자기 집을 메워도 영역이 변하지 않으므로 **영역이 그대로 81로 같다.**
     * 일본식은 메운 한 점이 집에서 빠지므로 **실제로 따낸 쪽이 1집 손해**다(79 대 78).
     * 이 차이는 결함이 아니라 두 룰셋의 정의 그 자체이고, 그래서 사석은 **두지 말고 걷어야** 한다.
     */
    @Test
    fun cleaningDetectedDeadStonesMatchesActuallyCapturingThem() {
        val state = singleDeadWhiteStoneBoard().toState(nextPlayer = StoneColor.Black, komi = 6.5)

        val cleaned = DeadStoneCleaner.apply(state, DeadStoneDetector.capturableDeadStones(state)).state
        val actuallyCaptured = state.play(Move.Play(StoneColor.Black, point("E4")))

        assertEquals(null, cleaned.stoneAt(point("E5")))
        assertEquals(null, actuallyCaptured.stoneAt(point("E5")))
        assertEquals(1, cleaned.capturedBy(StoneColor.Black))
        assertEquals(1, actuallyCaptured.capturedBy(StoneColor.Black))

        assertEquals(81.0, BoardAreaScorer.score(cleaned).blackArea)
        assertEquals(81.0, BoardAreaScorer.score(actuallyCaptured).blackArea)

        assertEquals(79.0, BoardTerritoryScorer.score(cleaned).blackArea)
        assertEquals(78.0, BoardTerritoryScorer.score(actuallyCaptured).blackArea)
    }

    /**
     * 두 색의 사석을 한 번에 걷으면 사석이 **양쪽에 각각** 쌓인다.
     *
     * ⚠️ 흑 **둘**과 백 **하나**를 걷는다. 하나씩 걷으면 두 계산을 맞바꿔도 같은 수가 나와
     * **틀린 코드가 통과한다** — 실제로 음성 대조에서 확인한 함정이다.
     */
    @Test
    fun cleaningBothColorsAtOnceCreditsEachSideSeparately() {
        val state = goldenBoard(
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . X X X O O . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
        ).toState(capturedByBlack = 1, capturedByWhite = 2)

        val cleanup = DeadStoneCleaner.apply(state, listOf(point("C5"), point("D5"), point("F5")))

        assertEquals(3, cleanup.removedCount)
        assertEquals(
            setOf(
                DeadStoneRemoval(point("C5"), StoneColor.Black),
                DeadStoneRemoval(point("D5"), StoneColor.Black),
                DeadStoneRemoval(point("F5"), StoneColor.White),
            ),
            cleanup.removedStones.toSet(),
        )
        // 흑이 딴 것은 걷어 낸 **백** 돌 하나, 백이 딴 것은 걷어 낸 **흑** 돌 둘이다.
        assertEquals(2, cleanup.state.capturedBy(StoneColor.Black))
        assertEquals(4, cleanup.state.capturedBy(StoneColor.White))
        assertEquals(StoneColor.Black, cleanup.state.stoneAt(point("E5")))
        assertEquals(StoneColor.White, cleanup.state.stoneAt(point("G5")))
    }

    /**
     * 걷을 돌이 하나도 없으면 **판을 그대로 돌려준다.**
     *
     * 빈 자리만 넘겨도 사석 수가 움직이지 않아야 하고, 패 정보도 건드리지 않은 채여야 한다
     * (한 점이라도 걷으면 패를 지우는 것과 대비되는 경로다).
     */
    @Test
    fun cleaningNothingLeavesTheStateUntouched() {
        val state = goldenBoard(
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
            capturedByBlack = 4,
            capturedByWhite = 5,
            koPoint = point("A1"),
            koForbiddenFor = StoneColor.Black,
        )

        val cleanup = DeadStoneCleaner.apply(state, listOf(point("A9"), point("J1")))

        assertEquals(0, cleanup.removedCount)
        assertEquals(state, cleanup.state)
        assertEquals(point("A1"), cleanup.state.koPoint)
    }

    /**
     * 정리기가 **덤·접바둑 수·수순을 보존한다.**
     *
     * #1 「이어하기 덤(komi) 유실」은 덤을 한 군데서 흘려 버린 사고였다. 같은 종류의 누락이
     * 계가 직전 경로에서 나면 화면에 뜨는 최종 점수가 통째로 달라진다.
     */
    @Test
    fun cleaningPreservesKomiHandicapAndMoveHistory() {
        val moves = listOf(Move.Pass(StoneColor.Black), Move.Pass(StoneColor.White))
        val state = singleDeadWhiteStoneBoard().toState(
            ruleset = Ruleset.Japanese,
            komi = 0.5,
            handicapCount = 5,
            moves = moves,
        )

        val cleanup = DeadStoneCleaner.apply(state, listOf(point("E5")))

        assertEquals(0.5, cleanup.state.komi)
        assertEquals(5, cleanup.state.handicapCount)
        assertEquals(Ruleset.Japanese, cleanup.state.ruleset)
        assertEquals(BoardSize.Nine, cleanup.state.boardSize)
        assertEquals(moves, cleanup.state.moves)
        assertTrue(cleanup.state.hasConsecutivePasses())
        assertEquals(0.5, BoardScorer.score(cleanup.state).komi)
    }

    // ------------------------------------------------------------------- 표와 판

    private data class DetectorCase(
        val name: String,
        val state: GameState,
        val expectedDeadStones: Set<String>,
    )

    private fun detectorCases(): List<DetectorCase> = listOf(
        DetectorCase(
            name = "활로 하나짜리 백 한 점 — 흑이 E4에 두면 즉시 따인다",
            state = singleDeadWhiteStoneBoard().toState(),
            expectedDeadStones = setOf("E5"),
        ),
        DetectorCase(
            name = "활로 둘짜리 백 한 점 — 한 수로 따이지 않으므로 사석이 아니다",
            state = goldenBoard(
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . X O X . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
            ).toState(),
            expectedDeadStones = emptySet(),
        ),
        DetectorCase(
            name = "단수에 몰린 백 두 점 — 그룹 전체가 사석이다",
            state = goldenBoard(
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . X . . . .",
                ". . . X O X . . .",
                ". . . X O X . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
            ).toState(),
            expectedDeadStones = setOf("E5", "E4"),
        ),
        DetectorCase(
            name = "색을 가리지 않는다 — 단수에 몰린 흑 한 점도 사석 후보다",
            state = goldenBoard(
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . O . . . .",
                ". . . O X O . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
            ).toState(),
            expectedDeadStones = setOf("E5"),
        ),
        DetectorCase(
            name = "양쪽에 하나씩 — 흑 사석과 백 사석이 한 판에서 함께 나온다",
            state = goldenBoard(
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . X . . . .",
                ". . . X O X . . .",
                ". . . . . . . . .",
                "O . . . . . . . .",
                "X O . . . . . . .",
                ". . . . . . . . .",
            ).toState(),
            expectedDeadStones = setOf("E5", "A2"),
        ),
        DetectorCase(
            name = "따낼 자리가 패금지면 후보에서 빠진다 — 탐지기가 살아 있는 패를 물려받는다",
            state = singleDeadWhiteStoneBoard().toState(
                koPoint = goldenPoint("E4"),
                koForbiddenFor = StoneColor.Black,
            ),
            expectedDeadStones = emptySet(),
        ),
    )

    /** 백 E5의 활로가 E4 하나뿐이다 — 흑이 E4에 두면 즉시 따인다. */
    private fun singleDeadWhiteStoneBoard(): GoldenBoard = goldenBoard(
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . X . . . .",
        ". . . X O X . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
    )

    private fun point(label: String): BoardCoordinate = goldenPoint(label, BoardSize.Nine)
}
