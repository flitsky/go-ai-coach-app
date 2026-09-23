package com.worksoc.goaicoach.application.movereview

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMoveSource
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.policy.pointLossLabel

data class MoveReviewMarker(
    val coordinate: BoardCoordinate,
    val moveNumber: Int,
    val tone: MoveReviewTone,
    /**
     * 최선수 대비 손실집수. **[tone]은 이 값을 구간으로 뭉갠 것**이라 되돌릴 수 없어서 따로 든다
     * (백로그 #151, U-35). 다시보기가 *"10집 이상 잃은 수"* 처럼 **수치로** 고르려면 필요하다.
     *
     * ⚠️ 마커가 만들어졌다면 값이 있다 — [buildMoveReview]가 `pointLoss == null`인 후보는 애초에
     * 마커를 만들지 않기 때문이다. `null`로 남는 경우는 **옛 저장분을 읽을 때**뿐이다.
     */
    val pointLoss: Double? = null,
)

enum class MoveReviewTone {
    Excellent,
    Good,
    Inaccuracy,
    Mistake,
    Blunder,
    Unknown,
}

data class MoveReviewResult(
    val marker: MoveReviewMarker?,
    val text: String,
)

internal fun buildMoveReview(
    move: Move,
    analysis: MoveAnalysisSnapshot,
    boardSize: BoardSize,
    moveNumber: Int,
): MoveReviewResult {
    val play = move as? Move.Play
        ?: return MoveReviewResult(
            marker = null,
            text = "Move review: pass/resign has no board spot evaluation.",
        )

    if (!analysis.hasEngineCandidates) {
        return MoveReviewResult(
            marker = null,
            text = "Move review: no pre-move analysis cache was ready.",
        )
    }

    val matchedCandidate = analysis.candidateAt(play.coordinate)
    if (matchedCandidate == null) {
        return MoveReviewResult(
            marker = null,
            text = "Move review: ${play.coordinate.label(boardSize)} was not legal in the pre-move analysis snapshot.",
        )
    }

    // 정책망 추정치(PolicyOnly/PolicyRefine)나 미평가(LegalFallback) 후보는 점수 손실 값이
    // 불안정하거나 아예 없어, 착수 품질을 오도할 수 있다. 실제 엔진 탐색(EngineSearch) 결과가
    // 있을 때만 색상 마커를 표시하고, 그렇지 않으면 아무 표시도 하지 않는다.
    if (matchedCandidate.source != CandidateMoveSource.EngineSearch || matchedCandidate.pointLoss == null) {
        return MoveReviewResult(
            marker = null,
            text = "Move review: ${play.coordinate.label(boardSize)} has no reliable engine evaluation yet.",
        )
    }

    val pointLoss = matchedCandidate.pointLoss
    val tone = moveReviewToneFor(pointLoss)
    val lossText = matchedCandidate.pointLossLabel()
        ?.let { "loss $it point(s)" }
        ?: "score loss pending"
    val priorText = matchedCandidate.policyPrior
        ?.let { ", policy ${(it * 100).toInt()}%" }
        .orEmpty()

    return MoveReviewResult(
        marker = MoveReviewMarker(
            coordinate = play.coordinate,
            moveNumber = moveNumber,
            tone = tone,
            pointLoss = pointLoss,
        ),
        text = "Move review: ${play.coordinate.label(boardSize)} ${moveReviewTextFor(pointLoss)} ($lossText$priorText).",
    )
}

/**
 * [MoveReviewMarker] 목록을, **엔진 후보수 탐색 없이** 이미 기록된 형세(승률·집수) 스냅샷의
 * 앞뒤 차이만으로 만든다(백로그 #151 개정, 2026-09-20 사용자).
 *
 * ## 왜 이게 필요한가 — [buildMoveReview]는 "지금 이 순간"이 있어야만 작동한다
 * [buildMoveReview]는 **그 수를 두기 직전 국면의 후보수 탐색 결과**가 있어야 손실집수를
 * 계산한다. 그 결과를 얻으려면 사람 차례가 시작되자마자 엔진에 탐색을 걸어야 하는데,
 * 그게 정확히 대국 진행을 방해하던 그 탐색이다 — "추천 수 보기"·"착수 평가"를 켜지 않은
 * 사용자에게도 매 턴 돌았다. 이 함수는 그 탐색을 걸지 않는다.
 *
 * ## 정확도는 다르다 — "최선수 대비 손실"이 아니라 "이 수가 만든 형세 변화"
 * [buildMoveReview]는 최선수와 비교하지만, 이 함수는 **그 수를 두기 전 스냅샷과 직후 스냅샷의
 * 차이**만 본다(둘 다 매 수 엔진 동기화의 부산물로 이미 공짜로 기록된다 — 새 엔진 호출이
 * 없다). 대국 중 실시간 코칭(구독자 전용 "착수 평가")처럼 **더 정밀한 값이 필요하면** 그건
 * 여전히 사용자가 "추천 수 보기"·"착수 평가"를 켰을 때만 [buildMoveReview] 경로로 돈다 —
 * 이 함수는 **아무도 켜지 않았을 때도 리플레이의 큰 실수 표시가 비지 않도록** 하는 몫이다.
 *
 * ⚠️ **AI끼리 둔 수는 절대 포함하지 않는다** — [humanColors]에 없는 진영의 수는 건너뛴다.
 * 사람이 두 진영 다(로컬 2인 대국) 두면 둘 다 포함된다.
 */
fun deriveMoveReviewMarkersFromScoreSwing(
    moves: List<Move>,
    scoreSnapshots: List<ScoreSnapshot>,
    humanColors: Set<StoneColor>,
): List<MoveReviewMarker> {
    if (humanColors.isEmpty() || scoreSnapshots.isEmpty()) return emptyList()
    val whiteLeadByMoveNumber = scoreSnapshots
        .mapNotNull { snapshot -> snapshot.whiteScoreLead?.let { snapshot.moveNumber to it } }
        .toMap()

    val markers = mutableListOf<MoveReviewMarker>()
    moves.forEachIndexed { index, move ->
        val play = move as? Move.Play ?: return@forEachIndexed
        if (move.player !in humanColors) return@forEachIndexed
        val moveNumber = index + 1
        val beforeLead = whiteLeadByMoveNumber[moveNumber - 1] ?: return@forEachIndexed
        val afterLead = whiteLeadByMoveNumber[moveNumber] ?: return@forEachIndexed
        val swingForWhite = afterLead - beforeLead
        val swingForMover = if (move.player == StoneColor.White) swingForWhite else -swingForWhite
        // ⚠️ `(-swingForMover).coerceAtLeast(0.0)`이면 안 된다 — swingForMover가 정확히
        // 0.0일 때 음수 0.0(`-0.0`)이 나오는데, IEEE754에서 `-0.0 < 0.0`은 거짓이라
        // coerceAtLeast가 걸러내지 못한다.
        val pointLoss = if (swingForMover >= 0.0) 0.0 else -swingForMover
        markers += MoveReviewMarker(
            coordinate = play.coordinate,
            moveNumber = moveNumber,
            tone = moveReviewToneFor(pointLoss),
            pointLoss = pointLoss,
        )
    }
    return markers
}

internal fun List<MoveReviewMarker>.withReviewMarker(
    marker: MoveReviewMarker?,
): List<MoveReviewMarker> =
    if (marker == null) {
        this
    } else {
        filterNot { existing -> existing.moveNumber == marker.moveNumber } + marker
    }

fun moveReviewToneFor(pointLoss: Double?): MoveReviewTone =
    when {
        pointLoss == null -> MoveReviewTone.Unknown
        pointLoss <= 0.5 -> MoveReviewTone.Excellent
        pointLoss <= 1.5 -> MoveReviewTone.Good
        pointLoss <= 3.0 -> MoveReviewTone.Inaccuracy
        pointLoss <= 6.0 -> MoveReviewTone.Mistake
        else -> MoveReviewTone.Blunder
    }

fun topMoveDisplayToneFor(
    pointLoss: Double?,
    bestShownPointLoss: Double?,
    worstShownPointLoss: Double?,
): MoveReviewTone {
    val absoluteTone = moveReviewToneFor(pointLoss)
    val loss = pointLoss ?: return absoluteTone
    val bestLoss = bestShownPointLoss ?: return absoluteTone
    if (bestLoss <= 3.0) {
        return absoluteTone
    }

    val worstLoss = worstShownPointLoss ?: bestLoss
    if (worstLoss <= bestLoss) {
        return MoveReviewTone.Inaccuracy
    }

    val relativeLoss = ((loss - bestLoss) / (worstLoss - bestLoss)).coerceIn(0.0, 1.0)
    return when {
        relativeLoss <= 0.15 -> MoveReviewTone.Inaccuracy
        relativeLoss >= 0.85 -> MoveReviewTone.Blunder
        else -> MoveReviewTone.Mistake
    }
}

internal fun moveReviewTextFor(pointLoss: Double?): String =
    when (moveReviewToneFor(pointLoss)) {
        MoveReviewTone.Excellent -> "excellent"
        MoveReviewTone.Good -> "good"
        MoveReviewTone.Inaccuracy -> "inaccuracy"
        MoveReviewTone.Mistake -> "mistake"
        MoveReviewTone.Blunder -> "blunder"
        MoveReviewTone.Unknown -> "unknown"
    }
