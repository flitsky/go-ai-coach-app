package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreTimelineTest {
    @Test
    fun recordReplacesSameMoveNumberAndKeepsOrder() {
        val snapshots = listOf(
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = 1.0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 0, whiteScoreLead = -3.0, source = ScoreSnapshotSource.EngineEstimate),
        )

        val updated = ScoreTimeline.record(
            snapshots,
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = 4.5, source = ScoreSnapshotSource.FinalScore),
        )

        assertEquals(listOf(0, 2), updated.map { it.moveNumber })
        assertEquals(4.5, updated.last().whiteScoreLead)
        assertEquals(ScoreSnapshotSource.FinalScore, updated.last().source)
    }

    @Test
    fun trimAfterDropsFutureSnapshots() {
        val snapshots = listOf(
            ScoreSnapshot(moveNumber = 0, whiteScoreLead = 0.0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = 1.0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 4, whiteScoreLead = 2.0, source = ScoreSnapshotSource.EngineEstimate),
        )

        assertEquals(
            listOf(0, 2),
            ScoreTimeline.trimAfter(snapshots, moveNumber = 2).map { it.moveNumber },
        )
    }

    @Test
    fun finalScoreSnapshotUsesWhiteLeadSign() {
        val blackWin = FinalScoreResult(
            status = EngineStatus.ready("done"),
            rawScore = "B+9.5",
            winner = StoneColor.Black,
            margin = 9.5,
            summary = "done",
        )
        val whiteWin = blackWin.copy(rawScore = "W+2.5", winner = StoneColor.White, margin = 2.5)

        assertEquals(-9.5, ScoreTimeline.fromFinalScore(10, blackWin).whiteScoreLead)
        assertEquals(2.5, ScoreTimeline.fromFinalScore(10, whiteWin).whiteScoreLead)
    }

    /**
     * 형세 기록은 **어느 망이 본 값인지**를 남긴다(백로그 #215) — 급수 캐릭터와 두는 동안의 수마다 기록은 사람 모델의 임시 값이라
     * 주 모델 값과 섞어 쓰면 안 되고(변곡점), 대국이 끝나면 주 모델로 다시 잴 대상이다.
     */
    @Test
    fun aSnapshotRemembersWhichNetworkSawIt() {
        val fromMain = ScoreEstimate(status = EngineStatus.ready("ok"), whiteScoreLead = 1.5, summary = "ok")
        val fromHuman = fromMain.copy(network = EngineNetwork.Human)

        assertEquals(ScoreSnapshotSource.EngineEstimate, ScoreTimeline.fromEstimate(3, fromMain).source)
        assertEquals(ScoreSnapshotSource.HumanNetworkEstimate, ScoreTimeline.fromEstimate(3, fromHuman).source)
        assertEquals(
            listOf(ScoreSnapshotSource.EngineEstimate, ScoreSnapshotSource.HumanNetworkEstimate),
            ScoreSnapshotSource.entries.filter { it.isNetworkEstimate },
        )
    }
}
