package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource

/**
 * 대국 뒤 「복기 하기」 추천의 실착 임계 — 둔 쪽에 **불리한 5집 이상** 변화(2026-09-30 사용자, 백로그 #200).
 *
 * 매 수 평가가 탐색 없는 신경망 1회(`kata-raw-nn 0`)라 몇 집쯤은 노이즈다 — 그래서 5집이다.
 *
 * ⚠️ 다시보기 「변곡점」의 [ScoreSwingThreshold]가 **이 값을 그대로 따른다**(2026-10-01 사용자 통일, 백로그 #207).
 * 처음(2026-09-30)에는 변곡점만 3집으로 낮게 두어 실수가 적은 판에서도 누를 버튼이 있게 했었다.
 * 이 값을 바꾸면 변곡점 임계도 함께 바뀐다.
 */
const val ReviewRecommendationMistakeThreshold: Double = 5.0

/**
 * 끝난 판에서 **「5집 이상 실착」이 몇 수였는가**(백로그 #200) — 판정 결과 팝업의 배지와 대국 뒤 말풍선이 함께 쓴다.
 *
 * ## 규칙
 * `moveNumber == k`인 스냅샷은 k번째 수를 둔 **뒤**의 형세다([GameReplayTimeline]의 KDoc). 그래서 k번째 수는
 * 스냅샷 `k-1`과 `k`가 **둘 다 [ScoreSnapshotSource.EngineEstimate]이고 백 리드가 있을 때만** 잰다.
 * 흑이 두었으면 백 리드가 [thresholdPoints] 이상 **늘었을 때**, 백이 두었으면 그만큼 **줄었을 때** 실착이다.
 *
 * - ⚠️ **둔 쪽은 수순에서 읽는다**(`moves[k-1].player`) — 흑백이 번갈아 둔다고 가정하지 않는다. 접바둑은 백이
 *   먼저 두고, 통과가 끼면 차례가 어긋나 보이지 않는다. 통과도 수다(둘 곳이 있는데 통과했다면 그것도 실착이다).
 *   기권([Move.Resign])은 잴 수가 아니라 건너뛴다.
 * - ⚠️ **누가 두었든 센다**(U-57, 2026-09-30 사용자) — 사람·AI 모두, AI끼리 판도. *"AI의 실수였는데 내가 활용
 *   못했구나"* 하는 것도 복기의 학습 경로다. 다만 **방향은 둔 쪽에 불리한 쪽만** — 유리한 급변은 대개 빠른
 *   평가가 늦게 알아챈 것이지 실착이 아니다.
 * - ⚠️ **출처가 섞이면 건너뛴다.** 엔진 평가가 실패·시간 초과·미준비인 수에는 로컬 영역 계산
 *   ([ScoreSnapshotSource.LocalAreaEstimate])이, 종국에는 [ScoreSnapshotSource.FinalScore]가 기록되는데, 둘은
 *   신경망 우세와 **척도가 달라** 섞어 빼면 가짜 대형 실착이 생긴다. 놓칠 뿐 지어내지 않는다.
 * - 개수는 **자르지 않는다**(`9+` 같은 표기는 화면의 몫이다).
 *
 * @return 실착 수. ⚠️ **잴 수 있는 수가 하나도 없으면 `null`** 이다 — `0`이 아니다. 0은 *"재 봤더니 없었다"* 이고
 *   `null`은 *"잴 자료가 없었다"* 라, 호출부가 배지·말풍선을 숨기는 이유가 서로 다르다([scoreSnapshotUpTo]와 같은 원칙).
 */
fun countReviewRecommendationMistakes(
    moves: List<Move>,
    scoreSnapshots: List<ScoreSnapshot>,
    thresholdPoints: Double = ReviewRecommendationMistakeThreshold,
): Int? {
    val leadByMoveNumber = engineEstimateWhiteLeadByMoveNumber(scoreSnapshots)
    var measuredMoves = 0
    var mistakes = 0
    moves.forEachIndexed { index, move ->
        if (move is Move.Resign) return@forEachIndexed
        val moveNumber = index + 1
        val before = leadByMoveNumber[moveNumber - 1] ?: return@forEachIndexed
        val after = leadByMoveNumber[moveNumber] ?: return@forEachIndexed
        measuredMoves += 1
        val lossForMover = when (move.player) {
            StoneColor.Black -> after - before
            StoneColor.White -> before - after
        }
        if (lossForMover >= thresholdPoints) mistakes += 1
    }
    return if (measuredMoves == 0) null else mistakes
}

/**
 * 신경망 평가([ScoreSnapshotSource.EngineEstimate]) 스냅샷만 골라 `수순 번호 → 백 리드`로 — 「복기 하기」 추천과
 * 다시보기 「변곡점」([deriveScoreSwingHighlights])이 **같은 거름망**을 쓴다(백로그 #200).
 */
internal fun engineEstimateWhiteLeadByMoveNumber(scoreSnapshots: List<ScoreSnapshot>): Map<Int, Double> =
    scoreSnapshots
        .filter { snapshot -> snapshot.source == ScoreSnapshotSource.EngineEstimate }
        .mapNotNull { snapshot -> snapshot.whiteScoreLead?.let { snapshot.moveNumber to it } }
        .toMap()
