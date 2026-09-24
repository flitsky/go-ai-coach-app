package com.worksoc.goaicoach.shared.policy

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.domain.DeadStoneRemoval
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.scoring.BoardScorer
import com.worksoc.goaicoach.shared.scoring.chineseHandicapProbeState
import kotlin.test.Test
import kotlin.test.assertEquals

class EndgameScoreSelectorTest {
    @Test
    fun selectsUnsettledEngineEstimateWhenLocalAreaConflictsWithoutDeadStoneCleanup() {
        val localScore = FinalScoreResult(
            status = EngineStatus.ready("Local score"),
            rawScore = "W+3.5",
            winner = StoneColor.White,
            margin = 3.5,
            blackArea = 35.0,
            whiteAreaWithKomi = 38.5,
            komi = 6.5,
            summary = "Local area",
        )
        val engineEstimate = ScoreEstimate(
            status = EngineStatus.ready("Estimate"),
            whiteScoreLead = -28.527,
            whiteWinRate = 0.01,
            summary = "Estimate",
        )

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = DeadStoneCleanupResult(GameState.empty(), emptyList()),
            localScore = localScore,
            engineEstimate = engineEstimate,
        )

        assertEquals(EndgameScoreSource.UnsettledEngineEstimate, selection.source)
        assertEquals(StoneColor.Black, selection.displayScore.winner)
        assertEquals("B+28.5?", selection.displayScore.rawScore)
    }

    @Test
    fun keepsCleanedLocalAreaWhenDeadStonesWereRemoved() {
        val localScore = FinalScoreResult(
            status = EngineStatus.ready("Local score"),
            rawScore = "B+12.5",
            winner = StoneColor.Black,
            margin = 12.5,
            blackArea = 50.0,
            whiteAreaWithKomi = 37.5,
            komi = 6.5,
            summary = "Local area",
        )
        val cleanup = DeadStoneCleanupResult(
            state = GameState.empty(),
            removedStones = listOf(
                DeadStoneRemoval(
                    coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                    color = StoneColor.White,
                ),
            ),
        )

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = cleanup,
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = -40.0,
                summary = "Estimate",
            ),
        )

        assertEquals(EndgameScoreSource.CleanedLocalArea, selection.source)
        assertEquals(localScore, selection.displayScore)
    }

    @Test
    fun selectsUnsettledPrePassTopMoveEstimateWhenPassFinalConflictsWithBestContinuation() {
        val localScore = FinalScoreResult(
            status = EngineStatus.ready("Local score"),
            rawScore = "W+4.5",
            winner = StoneColor.White,
            margin = 4.5,
            blackArea = 38.0,
            whiteAreaWithKomi = 42.5,
            komi = 6.5,
            summary = "Local area",
        )
        val cleanup = DeadStoneCleanupResult(
            state = GameState.empty(),
            removedStones = listOf(
                DeadStoneRemoval(
                    coordinate = BoardCoordinate.fromLabel("B1", BoardSize.Nine),
                    color = StoneColor.White,
                ),
            ),
        )
        val prePassCandidates = listOf(
            CandidateMove(
                move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("J5", BoardSize.Nine)),
                scoreLead = -6.9,
            ),
            CandidateMove(
                move = Move.Pass(StoneColor.Black),
                scoreLead = 4.1,
            ),
        )

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = cleanup,
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = 3.0,
                summary = "Post-pass estimate",
            ),
            prePassCandidates = prePassCandidates,
        )

        assertEquals(EndgameScoreSource.UnsettledPrePassTopMoveEstimate, selection.source)
        assertEquals(StoneColor.Black, selection.displayScore.winner)
        assertEquals("B+6.9?", selection.displayScore.rawScore)
    }

    @Test
    fun keepsCleanedLocalAreaWhenPrePassLeadAgreesWithCleanupFinal() {
        val localScore = FinalScoreResult(
            status = EngineStatus.ready("Local score"),
            rawScore = "B+31.5",
            winner = StoneColor.Black,
            margin = 31.5,
            blackArea = 51.0,
            whiteAreaWithKomi = 19.5,
            komi = 6.5,
            summary = "Local area",
        )
        val cleanup = DeadStoneCleanupResult(
            state = GameState.empty(),
            removedStones = listOf(
                DeadStoneRemoval(
                    coordinate = BoardCoordinate.fromLabel("A9", BoardSize.Nine),
                    color = StoneColor.White,
                ),
            ),
        )
        val prePassCandidates = listOf(
            CandidateMove(
                move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("H8", BoardSize.Nine)),
                scoreLead = -30.546545,
            ),
            CandidateMove(
                move = Move.Pass(StoneColor.Black),
                scoreLead = -31.0,
            ),
        )

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = cleanup,
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = -31.655,
                summary = "Post-pass estimate",
            ),
            prePassCandidates = prePassCandidates,
        )

        assertEquals(EndgameScoreSource.CleanedLocalArea, selection.source)
        assertEquals("B+31.5", selection.displayScore.rawScore)
    }

    @Test
    fun keepsCleanedLocalAreaWhenBlackGtpPrePassLeadAgreesWithBlackFinal() {
        val localScore = FinalScoreResult(
            status = EngineStatus.ready("Local score"),
            rawScore = "B+39.5",
            winner = StoneColor.Black,
            margin = 39.5,
            blackArea = 49.0,
            whiteAreaWithKomi = 9.5,
            komi = 6.5,
            summary = "Local territory",
        )
        val cleanup = DeadStoneCleanupResult(
            state = GameState.empty(ruleset = Ruleset.Japanese),
            removedStones = listOf(
                DeadStoneRemoval(
                    coordinate = BoardCoordinate.fromLabel("C9", BoardSize.Nine),
                    color = StoneColor.White,
                ),
            ),
        )
        val prePassCandidates = listOf(
            CandidateMove(
                move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("B9", BoardSize.Nine)),
                scoreLead = 39.5561,
            ),
            CandidateMove(
                move = Move.Pass(StoneColor.Black),
                scoreLead = 39.5,
            ),
        )

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = cleanup,
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = -40.953,
                summary = "Post-pass estimate",
            ),
            prePassCandidates = prePassCandidates,
        )

        assertEquals(EndgameScoreSource.CleanedLocalArea, selection.source)
        assertEquals("B+39.5", selection.displayScore.rawScore)
    }

    // ------------------------------------------ 면적계가 접바둑 보정과 10점 전환(#89)

    /**
     * ⭐ **로컬 계가가 접바둑 보정 N을 품으면 엔진과의 차가 N만큼 줄어든다**(refactor backlog #89).
     *
     * 엔진 추정(KataGo `chinese`)은 원래 N을 품고 있었으므로, 보정을 빠뜨린 로컬 계가와의 차에는
     * **N이 통째로 끼어** 있었다. 19x19 9점이면 실제 불일치가 1점뿐이어도 1 + 9 = 10으로 문턱(10점)에
     * 닿아 결과가 `?` 붙은 엔진 추정으로 넘어갔다 — 같은 판인데 계가 기준이 조용히 바뀌는 자리였다.
     * 이제 로컬이 N을 품으니 차는 1이고 로컬 계가가 그대로 결과다.
     */
    @Test
    fun theHandicapBonusNoLongerPushesA19x19NineStoneGameOverTheSwitchThreshold() {
        val state = GameState.withHandicap(BoardSize.Nineteen, Ruleset.Chinese, handicapCount = 9, komi = 6.5)
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))
        val localScore = BoardScorer.score(state)
        // 흑 = 돌 9 + 빈 점 352 = 361, 백 = 0 + 덤 6.5 + 보정 9 = 15.5.
        assertEquals(9.0, localScore.whiteHandicapBonus)
        val localWhiteLead = localScore.whiteAreaWithKomi!! - localScore.blackArea!!
        assertEquals(-345.5, localWhiteLead)
        val localWhiteLeadWithoutBonus = localWhiteLead - localScore.whiteHandicapBonus

        // 엔진이 로컬(보정 포함)과 1점만 다르다고 본다.
        val engineWhiteLead = localWhiteLead + 1.0
        assertEquals(10.0, engineWhiteLead - localWhiteLeadWithoutBonus, "보정이 없던 때의 차는 1 + N = 10이었다.")

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = DeadStoneCleanupResult(state, emptyList()),
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = engineWhiteLead,
                summary = "KataGo chinese estimate (includes handicap bonus)",
            ),
        )

        assertEquals(EndgameScoreSource.CleanedLocalArea, selection.source)
        assertEquals("B+345.5", selection.displayScore.rawScore)
    }

    /** 보정을 빼고 잰 차가 **여전히** 10점 이상이면 전환은 그대로 일어난다 — 문턱을 끈 것이 아니다. */
    @Test
    fun aRealTenPointDisagreementStillSwitchesAfterTheHandicapBonusIsCounted() {
        val state = GameState.withHandicap(BoardSize.Nineteen, Ruleset.Chinese, handicapCount = 9, komi = 6.5)
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))
        val localScore = BoardScorer.score(state)
        val localWhiteLead = localScore.whiteAreaWithKomi!! - localScore.blackArea!!

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = DeadStoneCleanupResult(state, emptyList()),
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = localWhiteLead + 10.0,
                summary = "KataGo chinese estimate",
            ),
        )

        assertEquals(EndgameScoreSource.UnsettledEngineEstimate, selection.source)
    }

    /**
     * #89 재현 국면(9x9 2점, 덤 6.5)을 조사가 잰 KataGo raw NN `whiteLead` +2.206과 함께 넣는다.
     * 차가 2.706 → 0.706으로 줄고, 결과는 로컬 계가 **W+1.5**다(고치기 전 B+0.5 — 승자 반전).
     */
    @Test
    fun theIssue89ProbeKeepsTheLocalScoreAndNowAgreesWithKataGoOnTheWinner() {
        val state = chineseHandicapProbeState(Ruleset.Chinese, komi = 6.5)
        val localScore = BoardScorer.score(state)

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = DeadStoneCleanupResult(state, emptyList()),
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = 2.206,
                summary = "KataGo raw NN (measured, #89)",
            ),
        )

        assertEquals(EndgameScoreSource.CleanedLocalArea, selection.source)
        assertEquals("W+1.5", selection.displayScore.rawScore)
        assertEquals(StoneColor.White, selection.displayScore.winner)
    }
}
