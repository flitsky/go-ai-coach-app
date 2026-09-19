package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreSnapshot

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

/** 「큰 실수」의 기본 임계 — 최선수 대비 **10집** 이상 잃은 수(2026-09-18 사용자, U-37). */
const val BlunderPointLossThreshold: Double = 10.0

/**
 * 큰 실수로 볼 수의 번호들, 수순 오름차순.
 *
 * ⚠️ [MoveReviewMarker.pointLoss]가 `null`인 것은 **옛 저장분**이다([MoveReviewMarker]의 KDoc) —
 * 값이 없는 것을 0으로 보아 "실수 아님"으로 넘기지 않고, 애초에 세지 않는다.
 */
fun blunderMoveNumbers(
    markers: List<MoveReviewMarker>,
    thresholdPoints: Double = BlunderPointLossThreshold,
): List<Int> =
    markers
        .filter { marker -> (marker.pointLoss ?: 0.0) >= thresholdPoints }
        .map { marker -> marker.moveNumber }
        .distinct()
        .sorted()

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
