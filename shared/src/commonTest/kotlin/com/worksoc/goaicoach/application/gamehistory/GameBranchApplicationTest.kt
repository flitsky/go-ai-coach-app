package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 「현 지점부터 커스텀 새 대국하기」(백로그 #172)가 짓는 시작점. */
class GameBranchApplicationTest {

    private fun play(row: Int, column: Int, player: StoneColor) =
        Move.Play(player, BoardCoordinate(row, column))

    private fun snapshot(moveNumber: Int) =
        ScoreSnapshot(
            moveNumber = moveNumber,
            whiteScoreLead = 1.0,
            source = ScoreSnapshotSource.EngineEstimate,
        )

    private fun timelineOf(moves: List<Move>, handicapCount: Int = 0) =
        buildGameReplayTimeline(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Chinese,
            handicapCount = handicapCount,
            komi = 6.5,
            moves = moves,
        )

    private val threeMoves = listOf(
        play(2, 2, StoneColor.Black),
        play(6, 6, StoneColor.White),
        play(2, 6, StoneColor.Black),
    )

    private val aiWhiteSetup = PlayerSetup(
        black = SidePlayerSetup(controller = SeatController.Human),
        white = SidePlayerSetup(
            controller = SeatController.Ai,
            playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 4),
        ),
    )

    private fun branchAt(
        moveNumber: Int,
        moves: List<Move> = threeMoves,
        handicapCount: Int = 0,
        scoreSnapshots: List<ScoreSnapshot> = emptyList(),
        topMovesEnabled: Boolean = false,
    ) = buildBranchedGameSnapshot(
        branchState = timelineOf(moves, handicapCount).stateAt(moveNumber),
        playerSetup = aiWhiteSetup,
        scoreSnapshots = scoreSnapshots,
        topMovesEnabled = topMovesEnabled,
        nowMillis = 1_000L,
    )

    /**
     * ⭐ **전반부를 새로 계산하지 않는다** — `stateAt(N)`이 주는 `moves`가 이미 원본의 앞 N수라
     * 그대로 새 대국의 시작 국면이 된다. 이 대응이 깨지면 분기 대국이 **엉뚱한 판**에서 시작한다.
     */
    @Test
    fun theBranchStartsFromExactlyTheMovesUpToThatPoint() {
        val branched = branchAt(2)

        assertEquals(2, branched.gameState.moves.size)
        assertEquals(threeMoves.take(2), branched.gameState.moves)
        assertEquals(StoneColor.Black, branched.gameState.nextPlayer)
    }

    /** 판 크기·룰·덤·접바둑이 그대로 따라온다 — 대국 설정 화면을 다시 거치지 않는다(구 U-39). */
    @Test
    fun theBoardSettingsAreInherited() {
        val branched = branchAt(1, moves = listOf(play(4, 4, StoneColor.White)), handicapCount = 3)

        assertEquals(BoardSize.Nine, branched.gameState.boardSize)
        assertEquals(Ruleset.Chinese, branched.gameState.ruleset)
        assertEquals(6.5, branched.gameState.komi)
        assertEquals(3, branched.gameState.handicapCount)
    }

    /** 좌석 설정도 그대로 — **AI 기력까지** 기록에 실려 있어 함께 물려받는다. */
    @Test
    fun theSeatSetupAndAiLevelAreInherited() {
        val branched = branchAt(2)

        assertEquals(aiWhiteSetup, branched.playerSetup)
        assertEquals(PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 4), branched.playLevel)
    }

    /**
     * ⚠️ **분기점 뒤의 형세는 버린다.** 물려주지 않으면 새 대국의 그래프가 **아직 두지도 않은
     * 수의 형세**를 미리 그린다 — 지나간 판을 베낀 것이 아니라 앞을 내다본 것처럼 보인다.
     */
    @Test
    fun onlyTheScoreHistoryUpToTheBranchPointIsCarriedOver() {
        val branched = branchAt(
            moveNumber = 2,
            scoreSnapshots = listOf(snapshot(0), snapshot(1), snapshot(2), snapshot(3)),
        )

        assertEquals(listOf(0, 1, 2), branched.scoreSnapshots.map { it.moveNumber })
    }

    /**
     * ⚠️ **끝난 판의 결과 팝업을 되살리는 값이 붙으면 안 된다** —
     * `buildEndedGameRestoreDisplayPlan`이 그것을 보고 "끝난 대국"으로 복원한다.
     */
    @Test
    fun theBranchIsNeverRestoredAsAFinishedGame() {
        assertNull(branchAt(2).finalScoreJudgement)
    }

    /** 지금 설정을 그대로 통과시킨다 — 복원 경로가 이 값을 설정에 되쓰기 때문이다. */
    @Test
    fun theCurrentTopMovesSettingPassesThrough() {
        assertTrue(branchAt(2, topMovesEnabled = true).topMovesEnabled)
        assertFalse(branchAt(2, topMovesEnabled = false).topMovesEnabled)
    }

    /**
     * ⚠️ **끝난 국면에서는 분기할 수 없다** — 다시보기는 **마지막 수에서 열리므로**(#156) 기권으로
     * 끝난 판에서는 그 자리가 곧바로 눌린다. 막지 않으면 시작하자마자 끝나 있는 대국이 생긴다.
     */
    @Test
    fun aFinishedPositionCannotBeBranched() {
        val resigned = timelineOf(threeMoves + Move.Resign(StoneColor.White))
        val passed = timelineOf(
            listOf(play(2, 2, StoneColor.Black), Move.Pass(StoneColor.White), Move.Pass(StoneColor.Black)),
        )

        assertFalse(canStartBranchedGameAt(resigned.stateAt(resigned.lastMoveNumber)))
        assertFalse(canStartBranchedGameAt(passed.stateAt(passed.lastMoveNumber)))
        assertTrue(canStartBranchedGameAt(resigned.stateAt(3)))
    }

    /** 시작 국면(0수)은 막지 않는다 — 그 판의 설정 그대로 처음부터 다시 두는 것도 뜻이 있다. */
    @Test
    fun theStartPositionCanBeBranched() {
        val branched = branchAt(0)

        assertTrue(canStartBranchedGameAt(branched.gameState))
        assertTrue(branched.gameState.moves.isEmpty())
    }

    /** 원본은 읽기만 한다 — 같은 입력으로 두 번 지어도 서로 영향을 주지 않는다. */
    @Test
    fun buildingABranchDoesNotTouchTheSourceTimeline() {
        val timeline = timelineOf(threeMoves)
        val before: List<GameState> = timeline.states.toList()

        buildBranchedGameSnapshot(
            branchState = timeline.stateAt(1),
            playerSetup = aiWhiteSetup,
            scoreSnapshots = listOf(snapshot(0), snapshot(1)),
            topMovesEnabled = false,
            nowMillis = 1_000L,
        )

        assertEquals(before, timeline.states)
    }
}
