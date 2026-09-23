package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeGameHistoryStore : GameHistoryStorePort {
    private val entries = mutableListOf<GameHistoryEntry>()
    val replays = mutableMapOf<String, GameReplayData>()

    override fun appendCompletedGame(entry: GameHistoryEntry, replay: GameReplayData?) {
        entries += entry
        replay?.let { replays[entry.id] = it }
    }

    override fun loadAll(): List<GameHistoryEntry> = entries.toList()

    override fun loadReplay(id: String): GameReplayData? = replays[id]
}

private val HumanBlackVsAiWhite = PlayerSetup(
    black = SidePlayerSetup(controller = SeatController.Human),
    white = SidePlayerSetup(controller = SeatController.Ai),
)

private val HumanWhiteVsAiBlack = PlayerSetup(
    black = SidePlayerSetup(controller = SeatController.Ai),
    white = SidePlayerSetup(controller = SeatController.Human),
)

private fun gameStateWithMoves(moves: List<Move>) =
    GameState.empty(boardSize = BoardSize.Nine, ruleset = Ruleset.Chinese).copy(moves = moves)

private fun passMoves(count: Int) = List(count) { Move.Pass(StoneColor.Black) }

private fun judgement(winner: StoneColor?, margin: Double? = 3.5) =
    FinalScoreJudgement(
        winner = winner,
        margin = margin,
        ruleset = Ruleset.Chinese,
        isEstimatedDisplay = false,
        removedBlack = 0,
        removedWhite = 0,
        blackArea = null,
        whiteAreaWithKomi = null,
        capturedByBlack = 0,
        capturedByWhite = 0,
        komi = 6.5,
    )

class GameHistoryAppendApplicationTest {
    @Test
    fun humanWinIsRecordedAsWin() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.Black),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(StoneColor.Black, entry.winner)
        assertEquals(StoneColor.Black, entry.humanColor)
        assertEquals(3.5, entry.margin)
    }

    @Test
    fun humanLossIsRecordedAsLoss() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.White),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(StoneColor.White, entry.winner)
    }

    @Test
    fun nullWinnerIsRecordedAsDraw() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = null, margin = null),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertNull(entry.winner)
        assertFalse(entry.isResign)
    }

    @Test
    fun resignationIsRecordedRegardlessOfFinalScoreJudgement() {
        // resignCurrentGameIfAllowed never sets finalScoreJudgement — this is the bug this
        // test guards against (game history staying empty after a resign).
        val store = FakeGameHistoryStore()
        val gameState = gameStateWithMoves(passMoves(10) + Move.Resign(StoneColor.White))

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = null,
            gameState = gameState,
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertTrue(entry.isResign)
        // ⭐ 백이 기권했으므로 **흑이 이겼다** — 옛 구현은 이것을 버렸다(백로그 #151).
        assertEquals(StoneColor.Black, entry.winner)
        assertNull(entry.margin)
    }

    @Test
    fun resignationByTheHumanRecordsTheOpponentAsTheWinner() {
        // 기권한 쪽이 사람이든 AI든 **승자는 언제나 그 반대편**이다(백로그 #151).
        val store = FakeGameHistoryStore()
        val gameState = gameStateWithMoves(passMoves(10) + Move.Resign(StoneColor.Black))

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = null,
            gameState = gameState,
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertTrue(entry.isResign)
        assertEquals(StoneColor.White, entry.winner)
    }

    @Test
    fun humanPlayingWhiteIsRecordedFromWhitesPerspective() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.White),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanWhiteVsAiBlack,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertEquals(StoneColor.White, entry.humanColor)
        assertEquals(StoneColor.White, entry.winner)
    }

    @Test
    fun humanVsHumanIsRecordedWithNoHumanColor() {
        val store = FakeGameHistoryStore()
        val bothHuman = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.Black),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = bothHuman,
            nowMillis = 1_000L,
            store = store,
        )

        // ⚠️ 2026-09-18 사용자 결정으로 **모든 대국 방식**을 기록한다(백로그 #151).
        assertTrue(entry != null)
        assertNull(entry.humanColor, "사람이 둘이면 '사람의 진영'이 성립하지 않는다")
        assertEquals(StoneColor.Black, entry.winner)
        assertEquals(1, store.loadAll().size)
    }

    @Test
    fun aiVsAiIsRecordedWithNoHumanColor() {
        val store = FakeGameHistoryStore()
        val bothAi = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.Black),
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = bothAi,
            nowMillis = 1_000L,
            store = store,
        )

        assertTrue(entry != null)
        assertNull(entry.humanColor)
        assertEquals(StoneColor.Black, entry.winner)
    }

    @Test
    fun replayCarriesBlunderMarkersDerivedFromScoreSnapshotsWithoutAnyPassedInMarkers() {
        // ⚠️ 2026-09-20 개정 — moveEvaluations는 더 이상 파라미터로 넘어오지 않는다.
        // scoreSnapshots만 주면 여기서 직접 계산해 실어야 한다(백로그 #151).
        val store = FakeGameHistoryStore()
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val moves = listOf(Move.Play(StoneColor.Black, coordinate))
        // 흑이 두자 백 리드가 +11 뛴다 — 흑에게 11집 손해(#151 U-37의 10집 임계를 넘는다).
        val scoreSnapshots = listOf(
            ScoreSnapshot(moveNumber = 0, whiteScoreLead = 0.0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 1, whiteScoreLead = 11.0, source = ScoreSnapshotSource.EngineEstimate),
        )

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = judgement(winner = StoneColor.White),
            gameState = gameStateWithMoves(moves),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
            scoreSnapshots = scoreSnapshots,
        )

        assertTrue(entry != null)
        val replay = store.replays[entry.id]
        assertTrue(replay != null)
        val marker = replay.moveEvaluations.single()
        assertEquals(1, marker.moveNumber)
        assertEquals(coordinate, marker.coordinate)
        assertEquals(11.0, marker.pointLoss)
    }

    @Test
    fun unendedGameIsNotAppended() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = false,
            finalScoreJudgement = null,
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertNull(entry)
    }

    @Test
    fun endedGameWithoutAJudgementOrResignationIsNotAppended() {
        val store = FakeGameHistoryStore()

        val entry = runGameHistoryAppendIfCompleted(
            isGameEnded = true,
            finalScoreJudgement = null,
            gameState = gameStateWithMoves(passMoves(42)),
            playerSetup = HumanBlackVsAiWhite,
            nowMillis = 1_000L,
            store = store,
        )

        assertNull(entry)
    }

    @Test
    fun sameGameIsNotRecordedTwiceAcrossRepeatedEffectFirings() {
        val store = FakeGameHistoryStore()
        val gameState = gameStateWithMoves(passMoves(42))
        runGameHistoryAppendIfCompleted(true, judgement(StoneColor.Black), gameState, HumanBlackVsAiWhite, 1_000L, store)

        val second = runGameHistoryAppendIfCompleted(
            true, judgement(StoneColor.Black), gameState, HumanBlackVsAiWhite, 1_050L, store,
        )

        assertNull(second)
        assertEquals(1, store.loadAll().size)
    }

    @Test
    fun aDifferentSubsequentGameIsRecordedAsANewEntry() {
        val store = FakeGameHistoryStore()
        runGameHistoryAppendIfCompleted(
            true, judgement(StoneColor.Black), gameStateWithMoves(passMoves(42)), HumanBlackVsAiWhite, 1_000L, store,
        )

        val second = runGameHistoryAppendIfCompleted(
            true, judgement(StoneColor.White), gameStateWithMoves(passMoves(80)), HumanBlackVsAiWhite, 2_000L, store,
        )

        assertTrue(second != null)
        assertEquals(2, store.loadAll().size)
    }
}
