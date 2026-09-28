package com.worksoc.goaicoach.shared.domain

import com.worksoc.goaicoach.shared.scoring.BoardAreaScorer
import com.worksoc.goaicoach.shared.scoring.BoardRegionAnalyzer
import com.worksoc.goaicoach.shared.scoring.BoardTerritoryScorer

// 계가기에 기대는 골든 픽스처 — `GoldenBoardDiagram.kt`에서 떼어 냈다(refactor backlog #49). 그림 파서는
// 도메인만 알면 되지만 이 셋은 계가기를 부르고, 특히 [regionAnalyzerOwnership]은 `internal`인
// `BoardRegionAnalyzer`를 직접 부르므로 **계가기와 같은 모듈의 테스트에** 있어야 한다.

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
