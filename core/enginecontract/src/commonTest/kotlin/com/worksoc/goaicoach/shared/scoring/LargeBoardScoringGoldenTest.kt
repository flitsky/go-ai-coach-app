package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.EmptyPointOwnership
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.GoldenBoard
import com.worksoc.goaicoach.shared.domain.HandicapKomi
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.areaScorerOwnership
import com.worksoc.goaicoach.shared.domain.goldenBoard
import com.worksoc.goaicoach.shared.domain.regionAnalyzerOwnership
import com.worksoc.goaicoach.shared.domain.territoryScorerOwnership
import com.worksoc.goaicoach.shared.domain.toState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **13x13·19x19 판의 절대값 골든** (refactor backlog #102).
 *
 * ## 왜 `BoardScoringGoldenTest`(9x9)만으로는 모자란가
 * `#38`이 두 계가기의 플러드필을 `BoardRegionAnalyzer` 한 벌로 합친 뒤,
 * [BoardScoringGoldenTest.bothScorersAgreeOnWhoOwnsEachEmptyPoint] 같은 **일치 단언**은
 * *"두 계가기가 서로 갈라졌는가"* 만 잡는다. 분석기 자체가 틀려도 두 계가기가 **같은 틀린 값**에
 * 동시에 동의하면 그 단언은 초록이다 — `#38` 검수가 실제로 이렇게 증명했다: 로컬에서
 * `BoardRegionAnalyzer.ownedEmptyPoints`의 `null -> Unit`(공배는 아무의 것도 아니다) 분기를
 * **19x19에서만** `null -> if (state.boardSize.value == 19 && region.points.size == 1) black += 1 else Unit`
 * (**19줄 판의 홑점 공배를 흑으로**)로 바꿔도 `:shared` 테스트가 전부 초록이었다 — 절대값 골든은
 * 9x9뿐이고, 무작위 종국 판 테스트는 절대값이 아니라 "세 값이 서로 같은가"만 보기 때문에
 * 분석기·영역계가기·집계가기가 **셋 다 똑같이 틀려도** 여전히 서로 같다.
 * (크기 조건 없이 모든 판의 홑점 공배를 흑으로 바꾸면 9x9 실전 국면 회귀 테스트
 * `EndgameRegressionTest` 둘이 잡는다 — 사각지대는 **큰 판**이었다.)
 *
 * 그래서 이 파일은 **절대값**을 박는다 — 판을 작게 설계해 **손으로 셀 수 있게** 하고,
 * 기대값은 지금 코드가 내는 값을 그대로 베끼지 않았다(순환 검증 금지). 19x19 조건 사보타주를
 * 넣고 `:shared` 전체를 돌리면 **이 파일의 19x19 벽판 테스트 둘만** 빨갛고(13x13에만 걸면 13x13 둘만),
 * 되돌리면 초록이다 — 검수가 확인했다(2026-09-25). 이웃 계산을 9줄로 고정하거나, 공배를 먼저 본
 * 색에 주거나, 영역 전파를 끊는 변이에도 이 파일이 빨갛다.
 *
 * ## 판을 어떻게 손으로 셀 수 있게 설계했는가
 * 판의 대부분을 **돌로 채운다** — 빈 사고 넓은 "바다"를 만들면 그 바다가 어디까지 이어지는지
 * 눈으로 좇아야 해서 19줄에서는 손으로 셀 수 없다. 대신 왼쪽 6줄을 통째로 흑, 오른쪽 6~7줄을
 * 통째로 백으로 채우고, 그 사이 **경계 줄** 몇 칸만 비워 빈 점의 모양을 고른다 — 이러면 경계
 * 칸 하나하나의 이웃만 보면 되므로 판 크기가 커져도 손 계산이 늘지 않는다.
 *
 * @see BoardScoringGoldenTest 9x9 골든 표 — 판·룰셋·덤의 조합마다 점수 문자열을 고정한다.
 */
class LargeBoardScoringGoldenTest {

    // ------------------------------------------------------------ 13x13: 벽 + 홑점 공배·패 흔적

    /**
     * 13x13, 흑 81돌 · 백 81돌 · 공배 7점. 왼쪽 6줄(A~F열)은 흑으로, 오른쪽 6줄(H~N열, I는
     * 좌표 표기에 없다)은 백으로 빽빽하게 채우고, 그 사이 G열(13칸)만 손으로 고른다:
     * - 다이어그램 행 0·2·7·9·11, G열 — 위아래가 전부 돌이라 **홑점**으로 고립되고, 좌우가
     *   흑·백이라 어느 쪽도 아니다(공배 5개).
     * - 다이어그램 행 4·5(두 칸) — 위아래가 돌이라 이 둘만 이어진 **2점** 영역이고, 역시 좌우가
     *   흑·백이라 어느 쪽도 아니다 — 세키에서 두 편이 나눠 갖는 공배를 흉내 낸 자리다.
     * - 다이어그램 행 7 — 나머지와 같은 홑점 공배이지만, 이 좌표를 [nineteenByNineteenBoardIgnoresKoMetadataWhenScoring]과
     *   같은 방식으로 `koPoint`에 걸어 **패 흔적**(진행 중 패의 잔재)이 계가에 새지 않는지 함께 본다.
     * - G열의 나머지 6칸(행 1·3·6·8·10·12)은 채움돌(임의 색)이라 위 홑점들이 G열 전체로 이어지지
     *   않게 끊는다 — 채움돌의 색은 좌우 판정에 아무 영향이 없다(좌우가 이미 흑·백을 하나씩 준다).
     *
     * G열 7칸이 전부 아무의 것도 아니므로 **흑·백 소유 빈 점은 0**이다(흑 81돌 · 백 81돌은
     * 영역계가에서 그대로 점수가 된다).
     */
    @Test
    fun thirteenByThirteenWallBoardHasSevenNeutralPointsAndNoOwnedTerritory() {
        val state = thirteenWallBoard().toState(komi = 6.5)

        val expected = EmptyPointOwnership(black = 0, white = 0)
        assertEquals(expected, areaScorerOwnership(state))
        assertEquals(expected, territoryScorerOwnership(state))
        assertEquals(expected, regionAnalyzerOwnership(state))
    }

    /**
     * 위 판의 점수 — 영역계가는 흑 81(돌만, 소유 빈 점 0) · 백 81 + 덤, 집계가는 사석만 더한다.
     *
     * 영역계가(덤 6.5): 흑 81, 백 81+6.5=87.5 → 차 -6.5, **W+6.5**.
     * 영역계가(덤 0.5): 흑 81, 백 81+0.5=81.5 → 차 -0.5, **W+0.5**(공배 7점이 그대로 한쪽에
     * 붙지 않았다는 것 자체가 홑점 하나만 흑으로 새도 0.5 차가 뒤집힐 만큼 민감한 판이다).
     * 집계가(덤 6.5, 사석 흑3·백1): 흑 0+3=3, 백 0+1+6.5=7.5 → 차 -4.5, **W+4.5**.
     */
    @Test
    fun thirteenByThirteenWallBoardScoreMatchesTheHandCountedStoneTotals() {
        val areaHighKomi = BoardAreaScorer.score(thirteenWallBoard().toState(komi = 6.5))
        assertEquals("W+6.5", areaHighKomi.rawScore)
        assertEquals(StoneColor.White, areaHighKomi.winner)
        assertEquals(81.0, areaHighKomi.blackArea)
        assertEquals(87.5, areaHighKomi.whiteAreaWithKomi)
        assertEquals(0.0, areaHighKomi.whiteHandicapBonus)

        val areaHandicapKomi = BoardAreaScorer.score(thirteenWallBoard().toState(komi = HandicapKomi))
        assertEquals("W+0.5", areaHandicapKomi.rawScore)
        assertEquals(81.0, areaHandicapKomi.blackArea)
        assertEquals(81.5, areaHandicapKomi.whiteAreaWithKomi)

        val territory = BoardTerritoryScorer.score(
            thirteenWallBoard().toState(komi = 6.5, capturedByBlack = 3, capturedByWhite = 1),
        )
        assertEquals("W+4.5", territory.rawScore)
        assertEquals(StoneColor.White, territory.winner)
        assertEquals(3.0, territory.blackArea)
        assertEquals(7.5, territory.whiteAreaWithKomi)
    }

    /**
     * 다이어그램 행 7·G열(공배 5개 중 하나, 위 함수 KDoc 참고)에 `koPoint`/`koForbiddenFor`를
     * 걸어도 — 진행 중이던 패의 흔적이 판에 남은 상태를 흉내 낸다 — 소유·점수가 조금도 바뀌지
     * 않는지 본다. 두 계가기·분석기 전부 `state.stones`만 보고 `koPoint`는 보지 않아야 한다.
     */
    @Test
    fun thirteenByThirteenBoardIgnoresKoMetadataWhenScoring() {
        val withoutKo = thirteenWallBoard().toState(komi = 6.5)
        val withKoTrace = thirteenWallBoard().toState(
            komi = 6.5,
            koPoint = BoardCoordinate(row = 7, column = 6),
            koForbiddenFor = StoneColor.Black,
        )

        assertEquals(areaScorerOwnership(withoutKo), areaScorerOwnership(withKoTrace))
        assertEquals(BoardAreaScorer.score(withoutKo).rawScore, BoardAreaScorer.score(withKoTrace).rawScore)
        assertEquals(BoardAreaScorer.score(withoutKo).blackArea, BoardAreaScorer.score(withKoTrace).blackArea)
    }

    /**
     * 13x13 접바둑 5점(`BoardSize.Thirteen.handicapStonePositions(5)`, 실제 접바둑 배치 그대로) —
     * 나머지가 전부 빈 판이므로 169−5=**164점이 전부 흑의 것**이다(9x9 「접바둑 5점」 골든과 같은
     * 논리를 13x13에서 다시 박는다).
     *
     * 영역계가: 흑 5+164=169, 백 0+덤 0.5+보정(접바둑 5점→5.0)=5.5 → **B+163.5**.
     * 집계가: 흑 164+사석0=164, 백 0+덤0.5=0.5 → **B+163.5**(#89 불변식 — 보정 없는 접바둑 빈
     * 판은 두 룰셋이 같은 답을 낸다 — 도 13x13에서 다시 확인된다).
     */
    @Test
    fun thirteenByThirteenHandicapBoardOwnsEveryEmptyPointAsBlack() {
        val state = GameState.withHandicap(BoardSize.Thirteen, Ruleset.Chinese, handicapCount = 5, komi = HandicapKomi)
        assertEquals(5, state.stones.size, "화점 5개가 겹치지 않고 5돌이어야 한다.")

        val expected = EmptyPointOwnership(black = 164, white = 0)
        assertEquals(expected, areaScorerOwnership(state))
        assertEquals(expected, territoryScorerOwnership(state))
        assertEquals(expected, regionAnalyzerOwnership(state))

        val chinese = BoardAreaScorer.score(state)
        assertEquals("B+163.5", chinese.rawScore)
        assertEquals(169.0, chinese.blackArea)
        assertEquals(5.5, chinese.whiteAreaWithKomi)
        assertEquals(5.0, chinese.whiteHandicapBonus)

        val japanese = BoardTerritoryScorer.score(state.copy(ruleset = Ruleset.Japanese))
        assertEquals("B+163.5", japanese.rawScore)
        assertEquals(164.0, japanese.blackArea)
        assertEquals(0.5, japanese.whiteAreaWithKomi)
    }

    // -------------------------------------------------- 19x19: 벽 + 세키 + 홑점 공배·패 흔적

    /**
     * 19x19, 흑 120돌 · 백 229돌 · 공배 10점 · 백 소유 2점(흑 소유 0점). 왼쪽 6줄(A~F열)은
     * 흑, 오른쪽 6줄(O~T열)은 백으로 채우고, 그 사이 G~N열(7칸 × 19행, I는 좌표 표기에 없다)에
     * 판을 박는다:
     * - 다이어그램 행 0·2·6·12·14 — G열만 비우고 나머지(H~N열)는 채움돌 — 위아래도 채움돌이라
     *   **홑점 공배** 5개(좌우가 흑·백이라 어느 쪽도 아니다).
     * - 다이어그램 행 4 — 같은 모양의 홑점 공배이지만 이 좌표에 `koPoint`를 걸어 패 흔적을
     *   흉내 낸다([nineteenByNineteenBoardIgnoresKoMetadataWhenScoring]).
     * - 다이어그램 행 8~10, G~M열(교과서 **두 칸 세키**, 이른바 "월형" 그대로 — N열은 채움돌 1칸
     *   더 끼워 오른쪽 벽과 갈라놓는다):
     *   ```
     *   G H  J K L M   N
     *   .  X X O O .   O
     *   X  X . . O O   O
     *   .  X X O O .   O
     *   ```
     *   가운데 행(다이어그램 행 9)의 두 빈 칸(J·K열)이 흑·백을 **둘 다** 접하는 진짜 공유 활로다
     *   (세키에서 두 편이 나눠 갖는 공배). 네 귀퉁이 점은 세키 모양의 일부일 뿐 판정은 갈린다 —
     *   G열 쪽(행 8·10)은 왼쪽이 A~F열 흑 벽이면서 위·아래·오른쪽 중 하나는 세키 자체의 흑돌이라
     *   흑·백을 **둘 다** 접해 공배이고, M열 쪽(행 8·10)은 위·아래·오른쪽(N열 채움돌)이 전부
     *   백이라 **백 소유**다.
     *
     * 그래서 이 판의 소유는: 홑점 공배 5 + 패 흔적 자리 1 + 세키 진짜 공배 2 + 세키 G열 쪽 귀퉁이
     * 2(행 8·10) = 공배 10점, M열 쪽 귀퉁이 2(행 8·10)만 **백 소유**다. 흑이 소유하는 빈 점은
     * 이 판에 하나도 없다 — 그래서 [BoardRegionAnalyzer]가 홑점 공배 하나라도 흑으로 잘못
     * 세면 이 테스트의 흑 소유가 0에서 벗어나 바로 드러난다(클래스 KDoc의 사보타주 재현).
     */
    @Test
    fun nineteenByNineteenWallBoardWithSekiHasTwoWhiteOwnedPointsAndTenNeutralPoints() {
        val state = nineteenWallBoard().toState(komi = 6.5)

        val expected = EmptyPointOwnership(black = 0, white = 2)
        assertEquals(expected, areaScorerOwnership(state))
        assertEquals(expected, territoryScorerOwnership(state))
        assertEquals(expected, regionAnalyzerOwnership(state))
    }

    /**
     * 위 판의 점수 — 영역계가: 흑 120(돌만), 백 229(돌)+2(소유)+6.5(덤)=237.5 → 차 -117.5,
     * **W+117.5**. 집계가(사석 흑4·백2): 흑 0+4=4, 백 2+2+6.5=10.5 → 차 -6.5, **W+6.5**.
     */
    @Test
    fun nineteenByNineteenWallBoardScoreMatchesTheHandCountedStoneTotals() {
        val area = BoardAreaScorer.score(nineteenWallBoard().toState(komi = 6.5))
        assertEquals("W+117.5", area.rawScore)
        assertEquals(StoneColor.White, area.winner)
        assertEquals(120.0, area.blackArea)
        assertEquals(237.5, area.whiteAreaWithKomi)
        assertEquals(0.0, area.whiteHandicapBonus)

        val territory = BoardTerritoryScorer.score(
            nineteenWallBoard().toState(komi = 6.5, capturedByBlack = 4, capturedByWhite = 2),
        )
        assertEquals("W+6.5", territory.rawScore)
        assertEquals(4.0, territory.blackArea)
        assertEquals(10.5, territory.whiteAreaWithKomi)
    }

    /**
     * 다이어그램 행 4·G열(위 함수 KDoc의 홑점 공배 중 하나)에 `koPoint`/`koForbiddenFor`를 걸어도
     * 소유·점수가 그대로인지 본다 — 13x13 쪽과 같은 계약을 19x19에서도 확인한다.
     */
    @Test
    fun nineteenByNineteenBoardIgnoresKoMetadataWhenScoring() {
        val withoutKo = nineteenWallBoard().toState(komi = 6.5)
        val withKoTrace = nineteenWallBoard().toState(
            komi = 6.5,
            koPoint = BoardCoordinate(row = 4, column = 6),
            koForbiddenFor = StoneColor.White,
        )

        assertEquals(regionAnalyzerOwnership(withoutKo), regionAnalyzerOwnership(withKoTrace))
        assertEquals(BoardAreaScorer.score(withoutKo).rawScore, BoardAreaScorer.score(withKoTrace).rawScore)
        assertEquals(BoardAreaScorer.score(withoutKo).blackArea, BoardAreaScorer.score(withKoTrace).blackArea)
    }

    /**
     * 19x19 접바둑 9점(이 판에서 지원하는 최대치 — `BoardSize.handicapStonePositions`의 "변
     * 가운데" 네 자리까지 실제로 쓰인다) — 나머지가 전부 빈 판이므로 361−9=**352점이 전부
     * 흑의 것**이다.
     *
     * 영역계가: 흑 9+352=361, 백 0+덤0.5+보정9.0=9.5 → **B+351.5**.
     * 집계가: 흑 352+0=352, 백 0+덤0.5=0.5 → **B+351.5**(#89 불변식, 19x19에서도 유지).
     */
    @Test
    fun nineteenByNineteenHandicapBoardOwnsEveryEmptyPointAsBlack() {
        val state = GameState.withHandicap(BoardSize.Nineteen, Ruleset.Chinese, handicapCount = 9, komi = HandicapKomi)
        assertEquals(9, state.stones.size, "화점+변 자리 9개가 겹치지 않고 9돌이어야 한다.")

        val expected = EmptyPointOwnership(black = 352, white = 0)
        assertEquals(expected, areaScorerOwnership(state))
        assertEquals(expected, territoryScorerOwnership(state))
        assertEquals(expected, regionAnalyzerOwnership(state))

        val chinese = BoardAreaScorer.score(state)
        assertEquals("B+351.5", chinese.rawScore)
        assertEquals(361.0, chinese.blackArea)
        assertEquals(9.5, chinese.whiteAreaWithKomi)
        assertEquals(9.0, chinese.whiteHandicapBonus)

        val japanese = BoardTerritoryScorer.score(state.copy(ruleset = Ruleset.Japanese))
        assertEquals("B+351.5", japanese.rawScore)
        assertEquals(352.0, japanese.blackArea)
        assertEquals(0.5, japanese.whiteAreaWithKomi)
    }

    // ------------------------------------------------------------------------------- 판

    /**
     * A~F열(6칸) 흑 벽 · H~N열(6칸) 백 벽 · 그 사이 G열(1칸)에 홑점 공배 5개 + 2점짜리 이음
     * 공배 1개(다이어그램 행 4·5) + 패 흔적 자리 1개(다이어그램 행 7)를 채움돌로 끊어 박는다.
     * 채움돌은 임의로 흑·백을 번갈아 쓴다(색은 좌우 판정에 영향이 없다 — 클래스 KDoc 참고).
     */
    private fun thirteenWallBoard(): GoldenBoard = goldenBoard(
        "X X X X X X . O O O O O O",
        "X X X X X X X O O O O O O",
        "X X X X X X . O O O O O O",
        "X X X X X X O O O O O O O",
        "X X X X X X . O O O O O O",
        "X X X X X X . O O O O O O",
        "X X X X X X X O O O O O O",
        "X X X X X X . O O O O O O",
        "X X X X X X O O O O O O O",
        "X X X X X X . O O O O O O",
        "X X X X X X X O O O O O O",
        "X X X X X X . O O O O O O",
        "X X X X X X O O O O O O O",
    )

    /**
     * A~F열(6칸) 흑 벽 · O~T열(6칸) 백 벽 · 그 사이 G~N열(7칸)에 홑점 공배 5개(다이어그램 행
     * 0·2·6·12·14) + 패 흔적 자리 1개(행 4) + 교과서 두 칸 세키(행 8~10, 클래스 KDoc의 그림)를
     * 박는다. 나머지 행은 G~N열 전부 채움돌(백)이라 위 자리들이 서로 이어지지 않는다.
     */
    private fun nineteenWallBoard(): GoldenBoard = goldenBoard(
        "X X X X X X . O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X . O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X . O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X . O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X . X X O O . O O O O O O O",
        "X X X X X X X X . . O O O O O O O O O",
        "X X X X X X . X X O O . O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X . O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X . O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
        "X X X X X X O O O O O O O O O O O O O",
    )
}
