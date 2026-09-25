package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DeadStoneCleaner
import com.worksoc.goaicoach.shared.domain.DeadStoneDetector
import com.worksoc.goaicoach.shared.domain.EmptyPointOwnership
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.GoldenBoard
import com.worksoc.goaicoach.shared.domain.KomiOptions
import com.worksoc.goaicoach.shared.domain.LegalMoveGenerator
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.allCoordinates
import com.worksoc.goaicoach.shared.domain.areaScorerOwnership
import com.worksoc.goaicoach.shared.domain.goldenBoard
import com.worksoc.goaicoach.shared.domain.neighbors
import com.worksoc.goaicoach.shared.domain.territoryScorerOwnership
import com.worksoc.goaicoach.shared.domain.toState
import kotlin.random.Random
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
     * 접바둑에서 **면적계가는 백에게 접바둑 돌 수(N)만큼 보정하고, 집계가는 보정하지 않는다.**
     *
     * 흑이 미리 놓은 돌은 중국식에서는 그대로 흑 영역으로 세어지고, 일본식에서는 집으로 세어지지
     * 않는다. 그래서 보정이 없으면 같은 판이 면적계가에서 N점 더 흑에게 유리하다.
     *
     * ## 2026-09-24에 기대값을 뒤집었다(refactor backlog #89)
     * 이 테스트는 원래 #9가 **그때의 동작을 특성화한** 것이었다 — *"`handicapCount`는 계가에 아무
     * 영향도 주지 않는다, 보정은 덤 설정이 맡는다"*. 그 전제가 틀렸다:
     * - 앱은 엔진에 `kata-set-rules chinese`를 보내고 KataGo는 거기서 `whiteHandicapBonus:"N"`을
     *   쓴다. AI·형세 그래프·추천 수 점수는 N을 품는데 종국 계가만 빼서, 9x9 2점 덤 6.5 국면에서
     *   KataGo `final_score` W+1.5를 앱은 B+0.5로 보여 **승자가 뒤집혔다**
     *   ([chineseHandicapGameGivesWhiteTheHandicapBonusLikeKataGoFinalScore]).
     * - 덤 선택지는 0.5·6.5·7.5뿐이라 사용자가 덤으로 +N을 표현할 길도 없었다.
     *
     * 그래서 5점 빈 판은 면적계가도 **B+75.5**로, 집계가와 같아졌다(예전 B+80.5 − 보정 5).
     * 보정은 판의 돌이 아니라 `handicapCount`를 따른다 — 같은 돌에 `handicapCount = 0`이면 보정이 없다.
     */
    @Test
    fun chineseScoringCompensatesWhiteForHandicapStonesAndJapaneseDoesNot() {
        val handicap = GameState.withHandicap(BoardSize.Nine, Ruleset.Chinese, handicapCount = 5, komi = 0.5)

        val chinese = BoardScorer.score(handicap)
        val japanese = BoardScorer.score(handicap.copy(ruleset = Ruleset.Japanese))
        assertEquals("B+75.5", chinese.rawScore)
        assertEquals(81.0, chinese.blackArea)
        assertEquals(5.5, chinese.whiteAreaWithKomi)
        assertEquals(5.0, chinese.whiteHandicapBonus)
        assertEquals("B+75.5", japanese.rawScore)
        assertEquals(0.0, japanese.whiteHandicapBonus)
        assertEquals(chinese.rawScore, japanese.rawScore, "5점 빈 판에서 두 계가가 같은 답을 내야 한다(#89).")

        val sameStonesWithoutHandicap = BoardScorer.score(handicap.copy(handicapCount = 0))
        assertEquals("B+80.5", sameStonesWithoutHandicap.rawScore)
        assertEquals(0.0, sameStonesWithoutHandicap.whiteHandicapBonus)
    }

    /** KataGo처럼 접바둑 돌 1개 이하는 보정 0이고, 2개부터 돌 수 그대로다. */
    @Test
    fun theHandicapBonusIsZeroUpToOneStoneAndTheStoneCountFromTwo() {
        assertEquals(0.0, BoardAreaScorer.whiteHandicapBonus(0))
        assertEquals(0.0, BoardAreaScorer.whiteHandicapBonus(1))
        assertEquals(2.0, BoardAreaScorer.whiteHandicapBonus(2))
        assertEquals(9.0, BoardAreaScorer.whiteHandicapBonus(9))

        val oneStoneMarkedAsHandicap = BoardScorer.score(
            singleStoneBoard().toState(Ruleset.Chinese, komi = 6.5, handicapCount = 1),
        )
        assertEquals("B+74.5", oneStoneMarkedAsHandicap.rawScore)
        assertTrue(
            !oneStoneMarkedAsHandicap.summary.contains("handicap bonus"),
            "보정이 없으면 요약에도 보정을 적지 않는다: ${oneStoneMarkedAsHandicap.summary}",
        )
    }

    /**
     * ⭐ **#89 재현 국면 — 면적계가 접바둑에서 백은 접바둑 보정 N점을 받는다.**
     *
     * 9x9 2점, 백 선, 백·흑 12수씩 둔 뒤 두 번 통과. 벽으로 나뉘어 공배·사석·따낸 돌이 없고
     * 흑 영역 44(돌 14) · 백 영역 37(돌 12)이다 — 판은 `chineseHandicapProbeBoard`의 그림이다
     * (`ChineseHandicapProbe.kt`, 종국 판정 테스트도 같은 판을 쓴다).
     *
     * 앱은 엔진에 `kata-set-rules chinese`를 보내고, KataGo는 그 룰에서
     * `whiteHandicapBonus:"N"`을 쓴다(`showboard`가 `Handicap bonus score: 2`를 찍는다).
     * 같은 명령열(`boardsize 9`/`komi`/`kata-set-rules`/`clear_board`/`set_free_handicap G7 C3`/
     * 이 수순/`pass`×2)을 앱 모델로 KataGo 1.16.4에 넣어 잰 `final_score`가 기대값이다
     * (2026-09-24, refactor backlog #89 조사):
     *
     * | 룰 · 덤 | KataGo `final_score` | 고치기 전 앱 |
     * |---|---|---|
     * | 면적계가 · 6.5 | **W+1.5** | B+0.5 (**승자 반전**) |
     * | 면적계가 · 0.5 | **B+4.5** | B+6.5 |
     * | 집계가 · 6.5   | **W+1.5** | W+1.5 |
     * | 집계가 · 0.5   | **B+4.5** | B+4.5 |
     *
     * 집계가는 KataGo japanese의 `whiteHandicapBonus:"0"`과 같아 원래 맞았다. 면적계가만
     * N(=2)이 빠져 흑의 차가 정확히 N만큼 부풀었다.
     */
    @Test
    fun chineseHandicapGameGivesWhiteTheHandicapBonusLikeKataGoFinalScore() {
        val expected = mapOf(
            (Ruleset.Chinese to 6.5) to "W+1.5",
            (Ruleset.Chinese to 0.5) to "B+4.5",
            (Ruleset.Japanese to 6.5) to "W+1.5",
            (Ruleset.Japanese to 0.5) to "B+4.5",
        )

        val failures = expected.mapNotNull { (key, expectedRawScore) ->
            val (ruleset, komi) = key
            val state = chineseHandicapProbeState(ruleset, komi)
            val actual = BoardScorer.score(state).rawScore
            if (actual == expectedRawScore) null else "$ruleset · 덤 $komi: $actual (KataGo final_score $expectedRawScore)"
        }

        assertEquals(emptyList<String>(), failures, "접바둑 계가가 KataGo final_score와 어긋났다(#89):\n" + failures.joinToString("\n"))

        // 면적계가의 백 합계는 영역 37 + 덤 6.5 + 보정 2 이고, 요약 문자열이 보정을 밝힌다.
        val chinese = BoardScorer.score(chineseHandicapProbeState(Ruleset.Chinese, komi = 6.5))
        assertEquals(44.0, chinese.blackArea)
        assertEquals(45.5, chinese.whiteAreaWithKomi)
        assertEquals(2.0, chinese.whiteHandicapBonus)
        assertEquals(StoneColor.White, chinese.winner)
        assertTrue(chinese.summary.contains("handicap bonus 2"), chinese.summary)
        assertEquals(0.0, BoardScorer.score(chineseHandicapProbeState(Ruleset.Japanese, komi = 6.5)).whiteHandicapBonus)
    }

    /** 재현 국면의 판·수순이 조사에서 KataGo에 넣은 것과 같은지 — 기대값보다 판이 먼저 맞아야 한다. */
    @Test
    fun theChineseHandicapProbePositionIsTheOneMeasuredOnKataGo() {
        val state = chineseHandicapProbeState(Ruleset.Chinese, komi = 6.5)

        assertEquals(
            setOf("G7", "C3"),
            GameState.withHandicap(BoardSize.Nine, Ruleset.Chinese, handicapCount = 2)
                .stones.keys.map { it.label(BoardSize.Nine) }.toSet(),
        )
        assertEquals(chineseHandicapProbeBoard().stones, state.stones)
        assertEquals(2, state.handicapCount)
        assertTrue(state.hasConsecutivePasses())
        assertEquals(0, state.capturedByBlack)
        assertEquals(0, state.capturedByWhite)
        assertEquals(emptyList(), DeadStoneDetector.capturableDeadStones(state))
        assertEquals(44.0, BoardScorer.score(state).blackArea)
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

    /**
     * ⭐ **무작위 종국 판 수백 개에서도 두 룰셋이 같은 빈 점 소유를 낸다**(refactor backlog #38).
     *
     * 위 두 테스트의 판은 전부 손으로 고른 9x9다. 복제가 갈라지는 길은 손으로 고른 판이 닿지 않는
     * 곳에 더 많다 — 판 크기를 9로 박은 이웃 계산, 수십 점짜리 큰 영역, 두 색이 다 닿는 공배 덩어리.
     * 그래서 9·13·19줄에서 무작위 대국을 두고 그 **종국 판**(양쪽이 연달아 통과한 판)마다 두 계가기의
     * 소유를 대조한다. 대국 하나에서 판 둘을 낸다 — 끝까지 둔 판과, 임의 수순에서 양쪽이 통과해 끝낸 판.
     * 절반은 접바둑으로 시작해, 면적계가의 접바둑 보정(#89)을 빼고 되짚는 경로도 함께 지난다.
     *
     * 판 생성이 망가져 대조가 헛돌지 않도록 **판이 고루 섞였는지**도 단언한다. 하한은 이 시드에서
     * 실제로 센 값(공배가 있는 판 127 · 양쪽 다 빈 점을 가진 판 222)보다 낮게 잡았다.
     */
    @Test
    fun bothRulesetsAgreeOnOwnershipAcrossHundredsOfRandomFinishedGames() {
        val boards = randomFinishedBoards(seed = 38_2026_0925L)
        val owned = boards.map { board ->
            Triple(board, areaScorerOwnership(board.state), territoryScorerOwnership(board.state))
        }

        val failures = owned.mapNotNull { (board, fromArea, fromTerritory) ->
            if (fromArea == fromTerritory) null else "${board.name}: 영역 계가기는 $fromArea, 집 계가기는 $fromTerritory"
        }
        assertEquals(
            emptyList<String>(),
            failures,
            "무작위 종국 판 ${boards.size}개 중 ${failures.size}개에서 두 룰셋의 빈 점 소유가 갈라졌다:\n" +
                failures.take(10).joinToString("\n"),
        )

        val withDame = owned.count { (board, fromArea, _) ->
            val emptyPoints = board.state.boardSize.value * board.state.boardSize.value - board.state.stones.size
            fromArea.black + fromArea.white < emptyPoints
        }
        val bothSidesOwn = owned.count { (_, fromArea, _) -> fromArea.black > 0 && fromArea.white > 0 }
        assertEquals(300, boards.size)
        assertTrue(boards.all { it.state.hasConsecutivePasses() }, "양쪽이 연달아 통과하지 않은 판이 섞였다.")
        assertEquals(BoardSize.supported().toSet(), boards.map { it.state.boardSize }.toSet())
        assertTrue(boards.count { it.state.handicapCount >= 2 } >= 100, "접바둑 판이 너무 적다.")
        assertTrue(withDame >= 100, "공배가 있는 판이 ${withDame}개뿐이다 — 두 색이 다 닿는 영역을 거의 대조하지 않는다.")
        assertTrue(bothSidesOwn >= 150, "양쪽 다 빈 점을 가진 판이 ${bothSidesOwn}개뿐이다.")
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

    // ------------------------------------------------------------------- 무작위 종국 판

    private data class FinishedBoard(
        val name: String,
        val state: GameState,
    )

    /**
     * 무작위 대국 150판(9줄 120 · 13줄 24 · 19줄 6)에서 종국 판 300개를 만든다. 짝수 번째 대국은
     * 접바둑(2점~그 판의 최대)으로 시작한다. 같은 `seed`면 늘 같은 판이 나온다.
     */
    private fun randomFinishedBoards(seed: Long): List<FinishedBoard> {
        val seeds = Random(seed)
        val sizes = List(120) { BoardSize.Nine } + List(24) { BoardSize.Thirteen } + List(6) { BoardSize.Nineteen }
        return sizes.flatMapIndexed { index, boardSize ->
            val random = Random(seeds.nextLong())
            val komi = KomiOptions[random.nextInt(KomiOptions.size)]
            val start = if (index % 2 == 0) {
                val handicap = random.nextInt(2, boardSize.maxHandicapCount + 1)
                GameState.withHandicap(boardSize, Ruleset.Chinese, handicapCount = handicap, komi = komi)
            } else {
                GameState.empty(boardSize, Ruleset.Chinese, komi = komi)
            }
            val positions = playRandomGameToTheEnd(start, random)
            val cut = positions[random.nextInt(positions.size)]
            val label = "대국 $index(${boardSize.value}줄, 접바둑 ${start.handicapCount})"
            listOf(
                FinishedBoard("$label 끝까지 ${positions.last().moves.size}수", positions.last()),
                FinishedBoard("$label ${cut.moves.size}수에서 양쪽 통과", cut.passTwice()),
            )
        }
    }

    /**
     * 매 수 합법하면서 **제 눈(네 방향이 전부 제 돌인 빈 점)을 메우지 않는** 자리 중 하나를 고르고,
     * 그런 자리가 없으면 통과한다. 양쪽이 연달아 통과하면 끝난다. 거친 국면을 전부 돌려준다.
     * 무한 반복(삼패 등)을 막으려고 판 넓이의 세 배 수에서 끊고, 끊은 판은 양쪽 통과로 닫는다.
     *
     * 빈 점을 무작위 순서로 하나씩 꺼내 처음 통과하는 자리를 둔다 — 그런 자리 전체에서 고르게 뽑는
     * 것과 분포가 같고, 19줄에서 매 수 361점을 전부 판정하지 않아도 된다.
     */
    private fun playRandomGameToTheEnd(
        start: GameState,
        random: Random,
    ): List<GameState> {
        val positions = mutableListOf(start)
        var state = start
        val moveLimit = start.boardSize.value * start.boardSize.value * 3
        while (!state.hasConsecutivePasses() && state.moves.size < moveLimit) {
            val player = state.nextPlayer
            val empties = state.boardSize.allCoordinates().filter { state.stoneAt(it) == null }.toMutableList()
            var chosen: BoardCoordinate? = null
            while (chosen == null && empties.isNotEmpty()) {
                val candidate = empties.removeAt(random.nextInt(empties.size))
                val fillsOwnEye = candidate.neighbors(state.boardSize).all { state.stoneAt(it) == player }
                if (!fillsOwnEye && LegalMoveGenerator.isLegalPlay(state, candidate)) {
                    chosen = candidate
                }
            }
            state = state.play(if (chosen == null) Move.Pass(player) else Move.Play(player, chosen))
            positions += state
        }
        if (!state.hasConsecutivePasses()) {
            positions += state.passTwice()
        }
        return positions
    }

    private fun GameState.passTwice(): GameState =
        play(Move.Pass(nextPlayer)).let { it.play(Move.Pass(it.nextPlayer)) }
}
