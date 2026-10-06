package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.engine.EngineOperationBusy
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * 급수 캐릭터와 둔 판의 형세 기록은 사람 모델의 임시 값이다 — 대국이 끝난 뒤 한 국면씩 주 모델로 다시 잰다(백로그 #215 보강 ①).
 * 대국을 방해하지 않는 것이 첫째 조건이라, 엔진이 바쁘면 물러나고, 멈춰도 잰 데까지는 남긴다.
 */
class ScoreRecordRemeasureTest {
    private val engine = RemeasuringEngine()
    private val saved = mutableListOf<GameReplayData>()
    private val progress = mutableListOf<GameReplayData>()

    private suspend fun remeasure(
        replay: GameReplayData,
        batchSize: Int = 2,
        waits: MutableList<Long> = mutableListOf(),
    ): GameReplayData =
        remeasureScoreRecord(
            entry = Entry,
            replay = replay,
            engineClient = engine,
            profile = Profile,
            save = { saved += it },
            onProgress = { progress += it },
            batchSize = batchSize,
            busyRetryDelayMillis = 1_500L,
            waitMillis = { millis -> waits += millis },
        )

    /** 임시 값이 있던 국면만, 수순대로, **그 수까지 둔 국면**을 엔진에 묻는다 — 주 모델 값·국소 계가·종국 계가는 건드리지 않는다. */
    @Test
    fun onlyTheProvisionalPositionsAreMeasuredAndReplaced() = runBlocking {
        val replay = GameReplayData(
            moves = FourMoves,
            scoreSnapshots = listOf(main(0, 0.5), provisional(1, 3.0), local(2, 9.0), provisional(3, -2.0), final(4, 4.5)),
        )

        val result = remeasure(replay)

        assertEquals(listOf(1, 3), engine.askedMoveNumbers)
        assertEquals(
            listOf(
                ScoreSnapshotSource.EngineEstimate,
                ScoreSnapshotSource.EngineEstimate,
                ScoreSnapshotSource.LocalAreaEstimate,
                ScoreSnapshotSource.EngineEstimate,
                ScoreSnapshotSource.FinalScore,
            ),
            result.scoreSnapshots.map { it.source },
        )
        assertEquals(listOf(0.5, 101.0, 9.0, 103.0, 4.5), result.scoreSnapshots.map { it.whiteScoreLead })
        assertFalse(result.scoreSnapshots.hasProvisionalScores())
    }

    /** 국면마다 파일을 다시 쓰지 않는다 — 묶음마다, 그리고 끝에 남은 것을 한 번. 저장한 것을 그대로 화면에 알린다. */
    @Test
    fun itSavesInBatchesAndOnceMoreAtTheEnd() = runBlocking {
        val replay = GameReplayData(moves = FourMoves, scoreSnapshots = (0..4).map { provisional(it, 1.0) })

        val result = remeasure(replay, batchSize = 2)

        assertEquals(listOf(2, 4, 5), saved.map { record -> record.scoreSnapshots.count { it.source == ScoreSnapshotSource.EngineEstimate } })
        assertEquals(saved, progress)
        assertEquals(result, saved.last())
    }

    /** 엔진이 다른 일을 하고 있으면 줄을 서지 않는다 — 물러났다가 **같은 국면**을 다시 건다. */
    @Test
    fun aBusyEngineIsRetriedAfterBackingOff() = runBlocking {
        engine.busyTimesBeforeAnswering = 2
        val waits = mutableListOf<Long>()

        val result = remeasure(GameReplayData(moves = FourMoves, scoreSnapshots = listOf(provisional(1, 3.0))), waits = waits)

        assertEquals(listOf(1, 1, 1), engine.askedMoveNumbers)
        assertEquals(listOf(1_500L, 1_500L), waits)
        assertFalse(result.scoreSnapshots.hasProvisionalScores())
    }

    /**
     * 새 대국을 시작하거나 화면을 떠나면 부르는 쪽이 이 작업을 취소한다 — **잰 데까지는 저장한다.**
     * 반쯤 잰 기록은 두 망의 값이 섞여 있지만, 변곡점은 같은 망이 본 값끼리만 재므로 온전한 기록이다.
     */
    @Test
    fun cancellingKeepsWhatWasAlreadyMeasured() = runBlocking {
        val replay = GameReplayData(moves = FourMoves, scoreSnapshots = (0..4).map { provisional(it, 1.0) })
        engine.parkAtMoveNumber = 2

        val job = async { remeasure(replay, batchSize = 16) }
        engine.parked.await()
        job.cancel()
        job.join()

        assertEquals(1, saved.size, "the pieces measured before the cancel must be saved once")
        assertEquals(listOf(0, 1), saved.single().scoreSnapshots.filter { it.source == ScoreSnapshotSource.EngineEstimate }.map { it.moveNumber })
        assertTrue(saved.single().scoreSnapshots.hasProvisionalScores())
    }

    /** 엔진이 못 재면(프로세스가 죽었다) 거기서 멈춘다 — 국면마다 실패하며 돌지 않는다. 잰 데까지는 남고, 다음에 다시 부르면 잇는다. */
    @Test
    fun anEngineFailureStopsTheRunAndKeepsTheRest() = runBlocking {
        val replay = GameReplayData(moves = FourMoves, scoreSnapshots = (0..4).map { provisional(it, 1.0) })
        engine.failAtMoveNumber = 2

        val result = remeasure(replay, batchSize = 16)

        assertEquals(listOf(0, 1, 2), engine.askedMoveNumbers)
        assertEquals(listOf(2, 3, 4), result.scoreSnapshots.filter { it.source == ScoreSnapshotSource.HumanNetworkEstimate }.map { it.moveNumber })
        assertEquals(result, saved.single())
    }

    /** 주 모델을 올릴 수 없는 상태에서 다시 잰 값은 여전히 임시 값이다 — 받지 않는다. */
    @Test
    fun anAnswerFromTheHumanNetworkIsNotAccepted() = runBlocking {
        engine.answeringNetwork = EngineNetwork.Human

        val result = remeasure(GameReplayData(moves = FourMoves, scoreSnapshots = listOf(provisional(1, 3.0))))

        assertTrue(result.scoreSnapshots.hasProvisionalScores())
        assertTrue(saved.isEmpty())
    }

    /** 다시 잴 것이 없는 판(주 모델로 둔 판·옛 기록)은 엔진도 저장소도 건드리지 않는다. */
    @Test
    fun aRecordWithNothingProvisionalIsLeftAlone() = runBlocking {
        val replay = GameReplayData(moves = FourMoves, scoreSnapshots = listOf(main(0, 0.5), main(1, 1.0), final(4, 4.5)))

        val result = remeasure(replay)

        assertEquals(replay, result)
        assertTrue(engine.askedMoveNumbers.isEmpty())
        assertTrue(saved.isEmpty())
    }

    /** 저장해 두는 실수 표시도 새 기록에서 다시 뽑는다 — 임시 값으로 뽑아 둔 옛 표시가 남지 않게. */
    @Test
    fun theStoredMoveMarkersAreDerivedAgainFromTheNewRecord() = runBlocking {
        engine.whiteLeadFor = { moveNumber -> if (moveNumber == 1) 8.0 else 0.0 }
        val replay = GameReplayData(moves = FourMoves, scoreSnapshots = listOf(provisional(0, 0.0), provisional(1, 0.0)))

        val result = remeasure(replay)

        // 흑(사람)의 1수 뒤 백 리드가 8집 늘었다 — 주 모델 값으로는 큰 실수다.
        assertEquals(listOf(1), result.moveEvaluations.map { it.moveNumber })
        assertEquals(8.0, result.moveEvaluations.single().pointLoss)
    }

    /** 살아 있는 기록에는 **임시 값이 있던 자리에 온 주 모델 값만** 받아들인다 — 그 밖의 값과 다른 수순은 그대로다. */
    @Test
    fun theLiveRecordAdoptsOnlyMainValuesForItsProvisionalSlots() {
        val live = listOf(main(0, 0.5), provisional(1, 3.0), provisional(2, 4.0), final(3, 7.5))
        val remeasured = listOf(main(0, 99.0), main(1, 2.0), provisional(2, 4.0), main(3, 99.0))

        assertEquals(listOf(main(0, 0.5), main(1, 2.0), provisional(2, 4.0), final(3, 7.5)), adoptRemeasuredScores(live, remeasured))
    }

    private companion object {
        val Profile = EngineProfile()
        val HumanBlackVsAiWhite = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )
        val Entry = GameHistoryEntry(
            id = "game-1",
            playedAtMillis = 1L,
            boardSize = 9,
            ruleset = Ruleset.Japanese,
            komi = 6.5,
            handicapCount = 0,
            playerSetup = HumanBlackVsAiWhite,
            moveCount = 4,
            humanColor = StoneColor.Black,
            winner = StoneColor.White,
        )
        val FourMoves = listOf(
            Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C3", BoardSize.Nine)),
            Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("G7", BoardSize.Nine)),
            Move.Play(StoneColor.White, BoardCoordinate.fromLabel("G3", BoardSize.Nine)),
        )

        fun provisional(moveNumber: Int, whiteLead: Double) =
            ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteLead, source = ScoreSnapshotSource.HumanNetworkEstimate)

        fun main(moveNumber: Int, whiteLead: Double) =
            ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteLead, source = ScoreSnapshotSource.EngineEstimate)

        fun local(moveNumber: Int, whiteLead: Double) =
            ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteLead, source = ScoreSnapshotSource.LocalAreaEstimate)

        fun final(moveNumber: Int, whiteLead: Double) =
            ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteLead, source = ScoreSnapshotSource.FinalScore)
    }
}

/** 다시 재라는 요청만 받는 엔진 — 물은 국면의 수순 번호를 적고, 기본으로는 `100 + 수순 번호`집 백 우세라고 답한다. */
private class RemeasuringEngine : EngineScoringClient {
    val askedMoveNumbers = mutableListOf<Int>()
    var busyTimesBeforeAnswering = 0
    var failAtMoveNumber: Int? = null
    var parkAtMoveNumber: Int? = null
    val parked = CompletableDeferred<Unit>()
    var answeringNetwork = EngineNetwork.Main
    var whiteLeadFor: (Int) -> Double = { moveNumber -> 100.0 + moveNumber }

    override suspend fun remeasureGraphScore(state: GameState, profile: EngineProfile): ScoreEstimate {
        val moveNumber = state.moves.size
        askedMoveNumbers += moveNumber
        if (busyTimesBeforeAnswering > 0) {
            busyTimesBeforeAnswering -= 1
            throw EngineOperationBusy("remeasureGraphScore")
        }
        if (moveNumber == failAtMoveNumber) error("engine process died")
        if (moveNumber == parkAtMoveNumber) {
            parked.complete(Unit)
            CompletableDeferred<Unit>().await()
        }
        return ScoreEstimate(status = EngineStatus.ready("ok"), whiteScoreLead = whiteLeadFor(moveNumber), summary = "ok", network = answeringNetwork)
    }

    override suspend fun syncAndEstimateGraphScore(state: GameState, profile: EngineProfile): ScoreEstimate = error("not used")

    override suspend fun configureSyncAndEstimateGraphScore(state: GameState, profile: EngineProfile): ScoreEstimate = error("not used")

    override suspend fun estimateScoreForState(state: GameState, profile: EngineProfile, syncFirst: Boolean): ScoreEstimate = error("not used")
}
