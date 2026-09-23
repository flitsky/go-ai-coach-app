package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.deriveMoveReviewMarkersFromScoreSwing
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import kotlin.math.abs

/**
 * 다시보기가 되짚는 한 판 — **화면과 무관한 계산만** 여기 있다(백로그 #156).
 *
 * ⚠️ **수순 번호는 곧 [states]의 인덱스다.** 저장된 [ScoreSnapshot.moveNumber]와
 * [MoveReviewMarker.moveNumber]가 둘 다 *"그 수를 둔 **뒤**의 `moves.size`"* 로 기록되므로
 * (`HumanMoveApplication.kt:159`·`EngineSession.kt:49`), `moveNumber == n`은 `states[n]`이다.
 * 0은 **아직 아무도 두지 않은 시작 국면**이고, 접바둑이면 거기 이미 흑돌이 놓여 있다.
 */
data class GameReplayTimeline(
    /** `states[n]` = n수까지 둔 국면. 비지 않는다 — 최소한 시작 국면 하나는 있다. */
    val states: List<GameState>,
    /**
     * 복원이 중간에 끊겼으면 **끊긴 수의 번호**, 끝까지 되짚었으면 `null`.
     *
     * ⚠️ 끊겨도 **예외를 밖으로 내보내지 않는다** — [GameState.play]는 위법수에 맨몸으로 던지는데
     * (`BoardRules`의 require 다섯) 이 계산이 컴포지션 중에 불리므로 그대로 두면 **화면이 죽는다.**
     * 되짚은 만큼은 보여 주고 어디서 끊겼는지 말하는 편이, 판 하나를 통째로 못 여는 것보다 낫다.
     */
    val truncatedAtMoveNumber: Int?,
) {
    /** 되짚을 수 있는 마지막 수순 번호. 시작 국면뿐이면 0이다. */
    val lastMoveNumber: Int = states.size - 1

    val isTruncated: Boolean = truncatedAtMoveNumber != null

    fun stateAt(moveNumber: Int): GameState = states[moveNumber.coerceIn(0, lastMoveNumber)]
}

/**
 * 저장된 수순을 처음부터 다시 두며 **매 수의 국면을 미리 만들어 둔다.**
 *
 * ⚠️ **한 수 옮길 때마다 처음부터 다시 두지 않는다.** 되짚기는 슬라이더로 훑는 동작이고,
 * 매 프레임 O(n)을 다시 도는 것은 400수짜리 판에서 눈에 보이게 끊긴다. 대신 이 목록을
 * 대국당 한 번만 만든다.
 */
fun buildGameReplayTimeline(
    boardSize: BoardSize,
    ruleset: Ruleset,
    handicapCount: Int,
    komi: Double,
    moves: List<Move>,
): GameReplayTimeline {
    val initial = if (handicapCount > 0) {
        GameState.withHandicap(boardSize, ruleset, handicapCount, komi = komi)
    } else {
        GameState.empty(boardSize = boardSize, ruleset = ruleset, komi = komi)
    }

    val states = mutableListOf(initial)
    var truncatedAt: Int? = null
    for ((index, move) in moves.withIndex()) {
        val next = runCatching { states.last().play(move) }.getOrNull()
        if (next == null) {
            truncatedAt = index + 1
            break
        }
        states += next
    }
    return GameReplayTimeline(states = states, truncatedAtMoveNumber = truncatedAt)
}

/**
 * 다시보기 「변곡점」 버튼 하나 — 그 수를 전후로 형세가 크게 움직인 지점(2026-09-20 사용자 결정,
 * 백로그 #156 리메이크 — "실착" 개념을 대체한다).
 *
 * [swing]은 **백 리드 기준** 이전 수 대비 증감분(부호 있음)이다 — 양수면 그 수 이후 백이,
 * 음수면 흑이 유리해졌다. 옛 "실착" 목록([MoveReviewMarker] 기반)은 "최선수 대비 얼마나
 * 잃었는가"만(항상 0 이상, 손해만) 사람이 둔 수에만 매겼는데, 이건 **판이 얼마나 흔들렸는가**만
 * 보므로 이득/손실도, 사람/AI 구분도 하지 않는다.
 */
data class ScoreSwingHighlight(
    val moveNumber: Int,
    val swing: Double,
)

/** 「변곡점」의 기본 임계 — 이전 수 대비 **3집** 이상 증감(2026-09-20 사용자). */
const val ScoreSwingThreshold: Double = 3.0

/** 변곡점 버튼으로 띄울 최대 개수 — 이보다 많으면 **변동폭이 큰 쪽부터** 자른다(2026-09-20 사용자). */
const val ScoreSwingMaxCount: Int = 5

/**
 * [scoreSnapshots] 하나만으로 변곡점을 고른다 — [Move]도 [MoveReviewMarker]도 필요 없다.
 *
 * ⚠️ **누가 두었는지 절대 보지 않는다.** 그래서 사람:사람·사람:AI·AI:AI 어느 조합의 대국에도
 * 똑같이 뜬다 — 예전 실착 목록이 사람이 둔 수에만 붙던 것과 다른 점이다.
 *
 * 손실집수와 달리 **부호 있는 값**을 그대로 남긴다 — 임계는 절댓값 기준으로 걸지만,
 * 반환하는 [ScoreSwingHighlight.swing]은 방향(어느 쪽이 유리해졌는지)까지 화면에 보여 줄 수 있게
 * 부호를 지운 적이 없다. 앞뒤 스냅샷이 둘 다 있는 수순에만 매기고(`moveNumber-1`·`moveNumber`),
 * 없으면 그 수는 후보에서 빠진다.
 */
fun deriveScoreSwingHighlights(
    scoreSnapshots: List<ScoreSnapshot>,
    thresholdPoints: Double = ScoreSwingThreshold,
    maxCount: Int = ScoreSwingMaxCount,
): List<ScoreSwingHighlight> {
    val leadByMoveNumber = scoreSnapshots
        .mapNotNull { snapshot -> snapshot.whiteScoreLead?.let { snapshot.moveNumber to it } }
        .toMap()

    return leadByMoveNumber.keys
        .filter { moveNumber -> moveNumber > 0 }
        .mapNotNull { moveNumber ->
            val before = leadByMoveNumber[moveNumber - 1] ?: return@mapNotNull null
            val after = leadByMoveNumber.getValue(moveNumber)
            val swing = after - before
            ScoreSwingHighlight(moveNumber, swing).takeIf { abs(swing) >= thresholdPoints }
        }
        .sortedByDescending { highlight -> abs(highlight.swing) }
        .take(maxCount)
        .sortedBy { highlight -> highlight.moveNumber }
}

/**
 * 착수 평가를 **저장된 값을 믿지 않고 그 자리에서 다시 계산한다**(2026-09-20 사용자 결정).
 *
 * ## 왜 — [GameReplayData.moveEvaluations]는 **저장 시점 코드의 스냅샷**이다
 * 대국이 끝나는 순간 [deriveMoveReviewMarkersFromScoreSwing]로 한 번 계산해 파일에 캐시해
 * 두는데, 그 뒤 임계값·집계 방식이 바뀌어도 **이미 저장된 대국의 캐시는 갱신되지 않는다** —
 * 사용자가 옛 기록을 다시보기로 열었을 때 "착수 평가 기록이 없다"는 문구를 보게 된 게 이
 * 어긋남 때문이었다.
 *
 * ## 그래서 원본만 신뢰한다
 * [GameReplayData.moves]와 [GameReplayData.scoreSnapshots]는 그 판의 **원 데이터**(백 기준
 * 점수·좌표)라 바뀌지 않는다. 이 함수는 그 둘과 [GameHistoryEntry.playerSetup]에서 다시 뽑아낸
 * 사람 진영만으로 [deriveMoveReviewMarkersFromScoreSwing]를 매번 새로 돌린다 — 그러면 대국이
 * 언제 기록됐든, 지금 이 순간의 임계값·집계 로직을 **그대로** 적용받는다.
 *
 * ⚠️ **캐시([GameReplayData.moveEvaluations])는 여전히 저장은 된다** — 다른 소비자를 깨지 않기
 * 위해서다(`ReplayRecordingContractTest`). 다만 다시보기 화면은 더 이상 그것을 읽지 않는다.
 */
fun deriveReplayMoveEvaluations(
    entry: GameHistoryEntry,
    replay: GameReplayData,
): List<MoveReviewMarker> =
    deriveMoveReviewMarkersFromScoreSwing(
        moves = replay.moves,
        scoreSnapshots = replay.scoreSnapshots,
        humanColors = humanControlledColors(entry.playerSetup),
    )

/**
 * [moveNumber] 시점의 형세. 그 수에 스냅샷이 없으면 **그 이전의 가장 가까운 것**을 준다.
 *
 * ⚠️ 없으면 `null`이다 — **0으로 메우지 않는다.** 형세를 재지 않은 구간을 `0.0`(비김)으로 그리면
 * 그래프가 *"이때는 호각이었다"* 고 **거짓말을 한다**(무료 대국은 형세가 성기게 찍힌다).
 */
fun scoreSnapshotUpTo(snapshots: List<ScoreSnapshot>, moveNumber: Int): ScoreSnapshot? =
    snapshots
        .filter { it.hasScoreData && it.moveNumber <= moveNumber }
        .maxByOrNull { it.moveNumber }
