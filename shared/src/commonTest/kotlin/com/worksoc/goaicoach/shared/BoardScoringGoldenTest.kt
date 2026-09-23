package com.worksoc.goaicoach.shared

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DeadStoneCleaner
import com.worksoc.goaicoach.shared.domain.DeadStoneDetector
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.scoring.BoardAreaScorer
import com.worksoc.goaicoach.shared.scoring.BoardScorer
import com.worksoc.goaicoach.shared.scoring.BoardTerritoryScorer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **계가 세 짝(`BoardScorer`/`BoardAreaScorer`/`BoardTerritoryScorer`)의 골든 테스트.**
 *
 * ## 왜 지금 이것이 필요한가
 * 리팩토링 트랙의 다음 단계가 **도메인 파일을 옮긴다**(#24 `shared` 루트 22파일 분할,
 * #38 플러드필 통합). 옮긴 뒤 "같은 답이 나오는가"를 물으려면 **옮기기 전의 답이 표로 박혀
 * 있어야** 한다. 이 파일이 그 표다.
 *
 * ## `BoardTerritoryScorer`는 여기 오기 전까지 전용 테스트가 없었다
 * 저장소 전체에서 이름이 등장하는 곳은 `BoardScorer`의 분기 한 줄뿐이었다. 일본식 룰로 두는
 * 사용자가 보는 점수를 **아무 테스트도 지키지 않고 있었다.**
 *
 * ## 판은 그림으로 적는다
 * `goldenBoard(...)`가 다이어그램을 판으로 옮긴다 — 기대값이 틀렸는지 판이 틀렸는지
 * **읽어서 가릴 수 있어야** 골든 테스트다. 파서 자체는 `GoldenBoardDiagramTest`가 고정한다.
 *
 * ## 두 계가기가 **답하지 않는** 것
 * 둘 다 *"사석은 이미 제거돼 있다"* 를 전제한다(각자의 `summary` 문구에 그렇게 적혀 있다).
 * 사석 판정은 `DeadStoneDetector`/`DeadStoneCleaner`의 일이고 `DeadStoneGoldenTest`가 맡는다.
 */
class BoardScoringGoldenTest {

    // ---------------------------------------------------------------- 점수표

    /**
     * 판 · 룰셋 · 덤의 조합마다 최종 점수 문자열과 양쪽 합을 고정한다.
     *
     * `BoardScorer.score(state)`로 부른다 — 덤 인자를 생략하면 **`state.komi`를 읽는다**는 것까지
     * 함께 고정하기 위해서다(#1 「이어하기 덤 유실」과 같은 종류의 누락을 막는 자리다).
     */
    @Test
    fun scoreTableIsGolden() {
        val failures = scoreCases().mapNotNull { case ->
            val actual = BoardScorer.score(case.state)
            val mismatches = buildList {
                if (actual.rawScore != case.expectedRawScore) {
                    add("rawScore=${actual.rawScore} (기대 ${case.expectedRawScore})")
                }
                if (actual.winner != case.expectedWinner) {
                    add("winner=${actual.winner} (기대 ${case.expectedWinner})")
                }
                if (actual.blackArea != case.expectedBlack) {
                    add("blackArea=${actual.blackArea} (기대 ${case.expectedBlack})")
                }
                if (actual.whiteAreaWithKomi != case.expectedWhiteWithKomi) {
                    add("whiteAreaWithKomi=${actual.whiteAreaWithKomi} (기대 ${case.expectedWhiteWithKomi})")
                }
                if (actual.komi != case.state.komi) {
                    add("komi=${actual.komi} (기대 ${case.state.komi} — state.komi를 읽지 않았다)")
                }
            }
            if (mismatches.isEmpty()) null else "${case.name}: ${mismatches.joinToString(", ")}"
        }

        assertEquals(emptyList<String>(), failures, "계가 골든 표가 어긋났다:\n" + failures.joinToString("\n"))
    }

    /**
     * `BoardScorer`가 룰셋에 따라 **어느 계가기에게 넘기는가**를 고정한다.
     *
     * 같은 판에서 두 룰셋이 **다른 답**을 내는 국면을 쓴다 — 분기를 지워도 통과하는 판으로는
     * 이 계약을 지킬 수 없다.
     */
    @Test
    fun boardScorerDispatchesByRuleset() {
        val chinese = dividedState(Ruleset.Chinese, komi = 6.5)
        val japanese = dividedState(Ruleset.Japanese, komi = 6.5)

        assertEquals(BoardAreaScorer.score(chinese), BoardScorer.score(chinese))
        assertEquals(BoardTerritoryScorer.score(japanese), BoardScorer.score(japanese))
        assertTrue(
            BoardScorer.score(chinese).rawScore != BoardScorer.score(japanese).rawScore,
            "두 룰셋이 같은 답을 내는 판으로는 분기를 검증할 수 없다.",
        )
    }

    /** 덤 인자를 명시하면 `state.komi`가 아니라 그 값을 쓴다. */
    @Test
    fun explicitKomiArgumentOverridesTheStateKomi() {
        val state = dividedState(Ruleset.Chinese, komi = 0.5)

        val overridden = BoardScorer.score(state, komi = 7.5)

        assertEquals(7.5, overridden.komi)
        assertEquals(43.5, overridden.whiteAreaWithKomi)
        assertEquals("W+7.5", overridden.rawScore)
        assertEquals("W+0.5", BoardScorer.score(state).rawScore)
    }

    /**
     * 중국식은 **사석을 세지 않고**, 일본식은 **판 위의 돌을 세지 않는다.**
     *
     * 같은 판·같은 덤에서 사석만 바꿔 두 계가기가 각각 어디에 반응하는지를 가른다.
     */
    @Test
    fun areaScoringIgnoresPrisonersAndTerritoryScoringIgnoresStonesOnTheBoard() {
        val withoutPrisoners = dividedBoard().toState(komi = 6.5)
        val withPrisoners = dividedBoard().toState(komi = 6.5, capturedByBlack = 4, capturedByWhite = 1)

        assertEquals(
            BoardAreaScorer.score(withoutPrisoners).rawScore,
            BoardAreaScorer.score(withPrisoners).rawScore,
        )
        assertEquals("W+6.5", BoardAreaScorer.score(withPrisoners).rawScore)

        assertEquals("W+6.5", BoardTerritoryScorer.score(withoutPrisoners).rawScore)
        assertEquals("W+3.5", BoardTerritoryScorer.score(withPrisoners).rawScore)
    }

    /**
     * 접바둑에서 **계가기는 접바둑 돌을 보정하지 않는다.**
     *
     * 흑이 미리 놓은 돌은 중국식에서는 그대로 흑 영역으로 세어지고, 일본식에서는 집으로 세어지지
     * 않는다. `handicapCount`는 계가에 **아무 영향도 주지 않는다** — 보정은 덤 설정이 맡는다.
     */
    @Test
    fun handicapStonesAreScoredAsPlainStonesWithNoCompensation() {
        val handicap = GameState.withHandicap(BoardSize.Nine, Ruleset.Chinese, handicapCount = 5, komi = 0.5)

        assertEquals("B+80.5", BoardScorer.score(handicap).rawScore)
        assertEquals("B+75.5", BoardScorer.score(handicap.copy(ruleset = Ruleset.Japanese)).rawScore)
        assertEquals(
            BoardScorer.score(handicap).rawScore,
            BoardScorer.score(handicap.copy(handicapCount = 0)).rawScore,
        )
    }

    /** 전부 채운 판에는 빈 점이 없으므로 집도 영역도 돌 수 그대로다. */
    @Test
    fun aCompletelyFilledBoardHasNoEmptyRegionsLeftToOwn() {
        val state = fullBoard().toState(komi = 6.5)

        assertTrue(state.isBoardFull())
        assertEquals(EmptyPointOwnership(black = 0, white = 0), areaScorerOwnership(state))
        assertEquals(EmptyPointOwnership(black = 0, white = 0), territoryScorerOwnership(state))
    }

    // ------------------------------------------------- 두 플러드필의 소유 판정 일치

    /**
     * ⭐ **#38(`BoardRegionAnalyzer` 통합)이 기대는 안전망.**
     *
     * `BoardAreaScorer`와 `BoardTerritoryScorer`의 플러드필은 지금 **타입 이름만 다른 복제**다.
     * 두 계가기가 내는 *점수*는 다를 수 있지만(전자는 돌을, 후자는 사석을 더한다),
     * **어느 빈 점이 누구 것인가**는 같아야 한다. 통합한 뒤에도 그래야 하고, 통합하지 않더라도
     * 복제가 다시 갈라지면 여기서 빨개진다.
     *
     * 소유 수는 공개 API에서 되짚는다 — 자세한 산식은 `areaScorerOwnership` KDoc에 있다.
     */
    @Test
    fun bothScorersAgreeOnWhoOwnsEachEmptyPoint() {
        val failures = ownershipCases().mapNotNull { case ->
            val fromArea = areaScorerOwnership(case.state)
            val fromTerritory = territoryScorerOwnership(case.state)
            when {
                fromArea != fromTerritory ->
                    "${case.name}: 영역 계가기는 $fromArea, 집 계가기는 $fromTerritory — 복제가 갈라졌다"
                fromArea != case.expected ->
                    "${case.name}: $fromArea (기대 ${case.expected})"
                else -> null
            }
        }

        assertEquals(emptyList<String>(), failures, "빈 점 소유 판정이 어긋났다:\n" + failures.joinToString("\n"))
    }

    /**
     * 손으로 기대값을 셀 수 없을 만큼 복잡한 판에서도 두 플러드필이 갈라지지 않는지 본다.
     *
     * 절대값 대신 **관계**만 단언한다 — 소유 판정이 서로 같고, 소유한 빈 점의 합이 실제 빈 점 수를
     * 넘지 않는다(나머지는 공배다).
     */
    @Test
    fun bothScorersAgreeOnACrowdedBoardThatNobodyCanCountByHand() {
        val state = crowdedBoard().toState(capturedByBlack = 7, capturedByWhite = 3, komi = 6.5)
        val emptyPoints = state.boardSize.value * state.boardSize.value - state.stones.size

        val fromArea = areaScorerOwnership(state)
        val fromTerritory = territoryScorerOwnership(state)

        assertEquals(fromArea, fromTerritory)
        assertTrue(
            fromArea.black + fromArea.white <= emptyPoints,
            "소유한 빈 점 ${fromArea.black + fromArea.white}이 실제 빈 점 $emptyPoints 보다 많다.",
        )
        assertTrue(emptyPoints > 0, "빈 점이 없는 판으로는 플러드필을 비교할 수 없다.")
    }

    // ------------------------------------------------------------------- 표와 판

    private data class ScoreCase(
        val name: String,
        val state: GameState,
        val expectedRawScore: String,
        val expectedWinner: StoneColor?,
        val expectedBlack: Double,
        val expectedWhiteWithKomi: Double,
    )

    private data class OwnershipCase(
        val name: String,
        val state: GameState,
        val expected: EmptyPointOwnership,
    )

    private fun scoreCases(): List<ScoreCase> = listOf(
        // 빈 판 — 판 전체가 경계색 없는 한 덩어리라 아무의 것도 아니다. 덤만 남는다.
        ScoreCase(
            name = "빈 판 · 중국식 · 덤 6.5",
            state = emptyBoard().toState(Ruleset.Chinese, komi = 6.5),
            expectedRawScore = "W+6.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 0.0,
            expectedWhiteWithKomi = 6.5,
        ),
        ScoreCase(
            name = "빈 판 · 일본식 · 덤 6.5",
            state = emptyBoard().toState(Ruleset.Japanese, komi = 6.5),
            expectedRawScore = "W+6.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 0.0,
            expectedWhiteWithKomi = 6.5,
        ),

        // 한 수만 둔 판 — 빈 점 80개가 전부 흑 경계 하나만 접한다.
        // 중국식은 거기에 돌 1개를 더해 81, 일본식은 집 80 그대로다.
        ScoreCase(
            name = "한 수만 둔 판 · 중국식 · 덤 6.5",
            state = singleStoneBoard().toState(Ruleset.Chinese, komi = 6.5),
            expectedRawScore = "B+74.5",
            expectedWinner = StoneColor.Black,
            expectedBlack = 81.0,
            expectedWhiteWithKomi = 6.5,
        ),
        ScoreCase(
            name = "한 수만 둔 판 · 일본식 · 덤 6.5",
            state = singleStoneBoard().toState(Ruleset.Japanese, komi = 6.5),
            expectedRawScore = "B+73.5",
            expectedWinner = StoneColor.Black,
            expectedBlack = 80.0,
            expectedWhiteWithKomi = 6.5,
        ),

        // 전부 채운 판 — 빈 점이 없다. 중국식은 돌 41:40, 일본식은 사석 0:0이라 덤만 남는다.
        ScoreCase(
            name = "전부 채운 판 · 중국식 · 덤 6.5",
            state = fullBoard().toState(Ruleset.Chinese, komi = 6.5),
            expectedRawScore = "W+5.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 41.0,
            expectedWhiteWithKomi = 46.5,
        ),
        ScoreCase(
            name = "전부 채운 판 · 일본식 · 덤 6.5",
            state = fullBoard().toState(Ruleset.Japanese, komi = 6.5),
            expectedRawScore = "W+6.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 0.0,
            expectedWhiteWithKomi = 6.5,
        ),

        // 분단된 판 — 흑 27집 · 백 27집 · 공배 9. 돌은 9:9, 사석은 흑 4 · 백 1.
        // 중국식은 36:36이라 승부가 **덤만으로** 갈리고, 일본식은 사석이 붙어 31:28+덤이 된다.
        ScoreCase(
            name = "분단된 판 · 중국식 · 덤 0.5",
            state = dividedState(Ruleset.Chinese, komi = 0.5),
            expectedRawScore = "W+0.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 36.0,
            expectedWhiteWithKomi = 36.5,
        ),
        ScoreCase(
            name = "분단된 판 · 중국식 · 덤 6.5",
            state = dividedState(Ruleset.Chinese, komi = 6.5),
            expectedRawScore = "W+6.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 36.0,
            expectedWhiteWithKomi = 42.5,
        ),
        ScoreCase(
            name = "분단된 판 · 중국식 · 덤 7.5",
            state = dividedState(Ruleset.Chinese, komi = 7.5),
            expectedRawScore = "W+7.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 36.0,
            expectedWhiteWithKomi = 43.5,
        ),
        // 같은 판인데 덤 0.5에서는 **흑이 이긴다** — 룰셋이 승자를 뒤집는 국면이다.
        ScoreCase(
            name = "분단된 판 · 일본식 · 덤 0.5",
            state = dividedState(Ruleset.Japanese, komi = 0.5),
            expectedRawScore = "B+2.5",
            expectedWinner = StoneColor.Black,
            expectedBlack = 31.0,
            expectedWhiteWithKomi = 28.5,
        ),
        ScoreCase(
            name = "분단된 판 · 일본식 · 덤 6.5",
            state = dividedState(Ruleset.Japanese, komi = 6.5),
            expectedRawScore = "W+3.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 31.0,
            expectedWhiteWithKomi = 34.5,
        ),
        ScoreCase(
            name = "분단된 판 · 일본식 · 덤 7.5",
            state = dividedState(Ruleset.Japanese, komi = 7.5),
            expectedRawScore = "W+4.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 31.0,
            expectedWhiteWithKomi = 35.5,
        ),
        // 덤이 정수면 무승부가 실재한다 — `winner`가 null이고 문자열은 "Draw"다.
        ScoreCase(
            name = "분단된 판 · 중국식 · 덤 0.0(무승부)",
            state = dividedState(Ruleset.Chinese, komi = 0.0),
            expectedRawScore = "Draw",
            expectedWinner = null,
            expectedBlack = 36.0,
            expectedWhiteWithKomi = 36.0,
        ),

        // 네 귀 — 같은 색 집이 **떨어진 두 곳**으로 나뉜다(누적이 맞는지 본다).
        // 흑 8집 · 백 8집 · 공배 45, 돌은 10:10, 사석은 흑 3.
        ScoreCase(
            name = "네 귀로 나뉜 판 · 중국식 · 덤 6.5",
            state = fourCornersState(Ruleset.Chinese),
            expectedRawScore = "W+6.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 18.0,
            expectedWhiteWithKomi = 24.5,
        ),
        ScoreCase(
            name = "네 귀로 나뉜 판 · 일본식 · 덤 6.5",
            state = fourCornersState(Ruleset.Japanese),
            expectedRawScore = "W+3.5",
            expectedWinner = StoneColor.White,
            expectedBlack = 11.0,
            expectedWhiteWithKomi = 14.5,
        ),
    )

    private fun ownershipCases(): List<OwnershipCase> = listOf(
        OwnershipCase(
            name = "빈 판 — 경계색이 없어 아무의 것도 아니다",
            state = emptyBoard().toState(),
            expected = EmptyPointOwnership(black = 0, white = 0),
        ),
        OwnershipCase(
            name = "한 수만 둔 판 — 나머지 80점이 전부 흑의 것이다",
            state = singleStoneBoard().toState(),
            expected = EmptyPointOwnership(black = 80, white = 0),
        ),
        OwnershipCase(
            name = "전부 채운 판 — 빈 점이 없다",
            state = fullBoard().toState(),
            expected = EmptyPointOwnership(black = 0, white = 0),
        ),
        OwnershipCase(
            name = "분단된 판 — 27:27, 가운데 9점은 공배",
            state = dividedState(Ruleset.Chinese, komi = 6.5),
            expected = EmptyPointOwnership(black = 27, white = 27),
        ),
        OwnershipCase(
            name = "네 귀 — 같은 색 집이 두 곳으로 나뉘어도 합산된다",
            state = fourCornersState(Ruleset.Chinese),
            expected = EmptyPointOwnership(black = 8, white = 8),
        ),
        OwnershipCase(
            name = "접바둑 5점 — 흑 돌만 있으므로 빈 점 76이 전부 흑의 것이다",
            state = GameState.withHandicap(BoardSize.Nine, Ruleset.Chinese, handicapCount = 5, komi = 0.5),
            expected = EmptyPointOwnership(black = 76, white = 0),
        ),
    )

    private fun emptyBoard(): GoldenBoard = goldenBoard(
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
    )

    private fun singleStoneBoard(): GoldenBoard = goldenBoard(
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . X . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
    )

    private fun fullBoard(): GoldenBoard = goldenBoard(
        "X X X X X X X X X",
        "X X X X X X X X X",
        "X X X X X X X X X",
        "X X X X X X X X X",
        "X X X X X O O O O",
        "O O O O O O O O O",
        "O O O O O O O O O",
        "O O O O O O O O O",
        "O O O O O O O O O",
    )

    /** D열의 흑 벽과 F열의 백 벽이 판을 셋으로 가른다 — 왼쪽 27집 · 가운데 공배 9 · 오른쪽 27집. */
    private fun dividedBoard(): GoldenBoard = goldenBoard(
        ". . . X . O . . .",
        ". . . X . O . . .",
        ". . . X . O . . .",
        ". . . X . O . . .",
        ". . . X . O . . .",
        ". . . X . O . . .",
        ". . . X . O . . .",
        ". . . X . O . . .",
        ". . . X . O . . .",
    )

    /** 네 귀에 4집씩, 가운데는 양쪽 색을 다 접하는 공배 45점. */
    private fun fourCornersBoard(): GoldenBoard = goldenBoard(
        ". . X . . . O . .",
        ". . X . . . O . .",
        "X X X . . . O O O",
        ". . . . . . . . .",
        ". . . . . . . . .",
        ". . . . . . . . .",
        "O O O . . . X X X",
        ". . O . . . X . .",
        ". . O . . . X . .",
    )

    /** 손으로 셀 수 없는 실전 형태 — 절대값이 아니라 두 플러드필의 **일치**만 본다. */
    private fun crowdedBoard(): GoldenBoard = goldenBoard(
        "X X O O . O O X X",
        "X . X O O O X X .",
        "X X X O . O X . X",
        "O O X X O O X X X",
        ". O O X X O O O O",
        "O . O X . X X O .",
        "O O O X X X . X O",
        "X X O O X . X X O",
        ". X O . O X X . O",
    )

    private fun dividedState(
        ruleset: Ruleset,
        komi: Double,
    ): GameState = dividedBoard().toState(
        ruleset = ruleset,
        komi = komi,
        capturedByBlack = 4,
        capturedByWhite = 1,
    )

    private fun fourCornersState(ruleset: Ruleset): GameState = fourCornersBoard().toState(
        ruleset = ruleset,
        komi = 6.5,
        capturedByBlack = 3,
        capturedByWhite = 0,
    )
}
