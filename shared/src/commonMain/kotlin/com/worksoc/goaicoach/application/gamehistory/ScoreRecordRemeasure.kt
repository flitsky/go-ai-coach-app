package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.engine.EngineOperationBusy
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.movereview.deriveMoveReviewMarkersFromScoreSwing
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.scoring.ScoreTimeline
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 이 판의 형세 기록에 **사람 모델의 임시 값이 남아 있는가**(백로그 #215 보강 ①) — 있으면 주 모델로 다시 잴 대상이다.
 * 급수 캐릭터와 둔 판만 그렇다. 주 모델로 둔 판·옛 기록·번들 참고 기보에는 없다.
 */
fun List<ScoreSnapshot>.hasProvisionalScores(): Boolean =
    any { snapshot -> snapshot.source == ScoreSnapshotSource.HumanNetworkEstimate }

/**
 * 끝난 판 하나의 형세 기록을 **주 모델로 다시 잰다**(백로그 #215 보강 ①).
 *
 * 급수 캐릭터와 두는 동안 엔진에는 사람 모델만 올라가 있어서, 수마다 남는 형세는 그 모델이 가장 센 프로필로 본 임시 값이다
 * ([ScoreSnapshotSource.HumanNetworkEstimate] — 주 모델보다 평균 2집 덜 정확하고 5집 출렁임의 38%를 놓친다, 실험실 E6).
 * 변곡점·「복기 하기」 추천·다시보기의 실수 표시가 전부 이 기록에서 나오므로, 대국이 끝난 뒤 한 국면씩 주 모델 값으로 바꿔 쓴다.
 *
 * ## 대국을 방해하지 않는다 — 이 작업의 첫째 조건
 * - 한 국면이 한 조각이다([EngineScoringClient.remeasureGraphScore] — 평가 1회). 조각과 조각 사이에 기다리던 다른 오퍼레이션이 돈다.
 * - 엔진이 쓰이고 있으면 줄을 서지 않고 **물러났다가**([busyRetryDelayMillis]) 다시 건다. 사용자가 누른 분석은 이 작업 때문에
 *   거절당하지 않는다(조각 하나만 기다린다 — `LocalEngineSessionClient.backgroundPiece`).
 * - **언제 멈출지는 부르는 쪽이 정한다** — 새 대국을 시작하거나 화면을 떠나면 이 코루틴을 취소한다. 취소돼도 잰 데까지는 저장한다.
 *
 * ## 반쯤 잰 기록도 온전한 기록이다
 * [batchSize]국면마다(그리고 멈출 때) [save]하고 [onProgress]로 알린다. 반쯤 잰 판은 두 망의 값이 섞여 있는데, 변곡점·복기 추천·
 * 실수 표시는 **같은 망이 본 값끼리만** 빼므로(`networkScoreSwingByMoveNumber`) 가짜 변곡점이 생기지 않는다. 다음에 다시 부르면
 * 남은 국면부터 잇는다.
 *
 * @return 다시 잰 뒤의 기록. 더 잴 것이 없었으면 [replay] 그대로다.
 */
suspend fun remeasureScoreRecord(
    entry: GameHistoryEntry,
    replay: GameReplayData,
    engineClient: EngineScoringClient,
    profile: EngineProfile,
    save: suspend (GameReplayData) -> Unit,
    onProgress: suspend (GameReplayData) -> Unit = {},
    batchSize: Int = ScoreRecordRemeasureBatchSize,
    busyRetryDelayMillis: Long = ScoreRecordRemeasureBusyRetryMillis,
    waitMillis: suspend (Long) -> Unit = { millis -> delay(millis) },
): GameReplayData {
    if (!replay.scoreSnapshots.hasProvisionalScores()) return replay
    val timeline = buildGameReplayTimeline(
        boardSize = BoardSize(entry.boardSize),
        ruleset = entry.ruleset,
        handicapCount = entry.handicapCount,
        komi = entry.komi,
        moves = replay.moves,
    )
    val provisionalMoveNumbers = replay.scoreSnapshots
        .filter { snapshot -> snapshot.source == ScoreSnapshotSource.HumanNetworkEstimate }
        .map { snapshot -> snapshot.moveNumber }
        // 되짚다 끊긴 판(위법수가 섞인 옛 기록)은 끊긴 데까지만 국면이 있다.
        .filter { moveNumber -> moveNumber <= timeline.lastMoveNumber }
        .sorted()

    var snapshots = replay.scoreSnapshots
    var unsaved = 0

    fun current(): GameReplayData = replay.withScoreSnapshots(snapshots, entry)

    suspend fun flush() {
        if (unsaved == 0) return
        unsaved = 0
        val measured = current()
        save(measured)
        onProgress(measured)
    }

    try {
        for (moveNumber in provisionalMoveNumbers) {
            val estimate = try {
                remeasureWhenEngineIsFree(engineClient, timeline.stateAt(moveNumber), profile, busyRetryDelayMillis, waitMillis)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                // 엔진이 못 쟀다(프로세스가 죽었다 등) — 여기서 멈춘다. 국면마다 다시 실패하며 도는 것보다 다음 기회에 잇는 편이 낫다.
                break
            }
            // 주 모델이 본 값만 받는다 — 사람 모델만 쓸 수 있는 상태라면 다시 재도 임시 값이다.
            if (estimate.network != EngineNetwork.Main || estimate.whiteScoreLead == null) continue
            snapshots = ScoreTimeline.record(snapshots, ScoreTimeline.fromEstimate(moveNumber, estimate))
            unsaved += 1
            if (unsaved >= batchSize) flush()
        }
    } finally {
        // 취소돼도 잰 데까지는 남긴다 — 평가 한 번이 폰에서 0.3~0.5초다.
        withContext(NonCancellable) { flush() }
    }
    return current()
}

/** 엔진이 다른 일을 하고 있으면 물러났다가 다시 건다 — 배경 작업은 줄을 서지 않는다. 취소되면 기다리다가도 곧바로 멈춘다. */
private suspend fun remeasureWhenEngineIsFree(
    engineClient: EngineScoringClient,
    state: GameState,
    profile: EngineProfile,
    busyRetryDelayMillis: Long,
    waitMillis: suspend (Long) -> Unit,
): ScoreEstimate {
    while (true) {
        try {
            return runEngineIo { engineClient.remeasureGraphScore(state, profile) }
        } catch (busy: EngineOperationBusy) {
            waitMillis(busyRetryDelayMillis)
        }
    }
}

/**
 * 형세 기록을 바꿔 끼운 기록. 저장해 두는 실수 표시([GameReplayData.moveEvaluations])도 새 기록에서 다시 뽑는다 —
 * 대국이 끝나던 순간에 임시 값으로 뽑아 둔 것이라 그대로 두면 옛 값이 남는다.
 */
internal fun GameReplayData.withScoreSnapshots(
    scoreSnapshots: List<ScoreSnapshot>,
    entry: GameHistoryEntry,
): GameReplayData =
    if (scoreSnapshots == this.scoreSnapshots) {
        this
    } else {
        copy(
            scoreSnapshots = scoreSnapshots,
            moveEvaluations = deriveMoveReviewMarkersFromScoreSwing(
                moves = moves,
                scoreSnapshots = scoreSnapshots,
                humanColors = humanControlledColors(entry.playerSetup),
            ),
        )
    }

/**
 * 살아 있는 형세 기록([live])에 다시 잰 값([remeasured])을 받아들인다 — **임시 값이 있던 자리에 주 모델 값이 온 것만.**
 * 그 사이에 살아 있는 쪽이 달라졌더라도(무르기·새 대국) 엉뚱한 수순의 값을 덮어쓰지 않는다: 수순 번호가 같고, 살아 있는 쪽이
 * 아직 임시 값일 때만 바꾼다.
 */
fun adoptRemeasuredScores(
    live: List<ScoreSnapshot>,
    remeasured: List<ScoreSnapshot>,
): List<ScoreSnapshot> {
    val mainByMoveNumber = remeasured
        .filter { snapshot -> snapshot.source == ScoreSnapshotSource.EngineEstimate }
        .associateBy { snapshot -> snapshot.moveNumber }
    return live.map { snapshot ->
        if (snapshot.source == ScoreSnapshotSource.HumanNetworkEstimate) mainByMoveNumber[snapshot.moveNumber] ?: snapshot else snapshot
    }
}

/**
 * 방금 끝난 판([moves])의 기록을 저장소에서 찾는다 — 대국 화면은 자기 기록의 id를 모르므로 **가장 최근 기록**을 집고,
 * 그것이 정말 이 판인지 **수순 전체**로 확인한다. 기록 붙이기가 아직 안 돌았거나 실패했으면 가장 최근 기록은 직전 대국이다 —
 * 확인 없이 쓰면 남의 판의 형세 기록을 다시 재고, 그 값을 이 판에 받아들인다.
 */
fun GameHistoryStorePort.findRecordedGame(moves: List<Move>): Pair<GameHistoryEntry, GameReplayData>? {
    val newest = loadAll().lastOrNull() ?: return null
    val replay = loadReplay(newest.id) ?: return null
    return if (replay.moves == moves) newest to replay else null
}

/** 이만큼 잴 때마다 저장하고 화면에 알린다 — 국면마다 파일을 다시 쓰지 않으려는 묶음이다. */
const val ScoreRecordRemeasureBatchSize: Int = 16

/** 엔진이 바쁠 때 물러나 있는 시간. */
const val ScoreRecordRemeasureBusyRetryMillis: Long = 1_500L
