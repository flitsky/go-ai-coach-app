package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource

/**
 * 저장 골든의 입력 — **판 정체성 네 값(판 크기·룰·접바둑·덤)이 전부 기본값이 아닌 판**을 일부러 섞는다
 * (refactor backlog #22). 기본값(9줄·일본·호선·6.5)만 쓰면 한 값이 빠져도 기본값으로 복원돼 골든이 초록으로 남는다.
 *
 * ⚠️ 이 값을 바꾸면 골든 문자열이 함께 바뀐다 — 골든은 **#22 이전 코드로 뽑은 바이트**이므로, 바꾸려면
 * 옛 코드(`adf89db3`)에서 다시 뽑아야 한다. 같은 내용이 계기 테스트 쪽(`androidTest`)에도 있다 — 두 벌을 함께 둔다.
 */
internal object GameSetupGoldenFixtures {
    private val HumanBlackAiWhite = PlayerSetup(
        black = SidePlayerSetup(controller = SeatController.Human),
        white = SidePlayerSetup(controller = SeatController.Ai),
    )

    /** 13줄·중국(집계)·2점·덤 0.5 — 두다 멈춘 이어하기. 백이 먼저 두고, 패스가 한 번 섞인다. */
    fun handicapSession(): SavedGameSnapshot =
        SavedGameSnapshot(
            gameState = GameState.withHandicap(BoardSize.Thirteen, Ruleset.Chinese, handicapCount = 2, komi = 0.5)
                .play(Move.Play(StoneColor.White, BoardCoordinate(row = 2, column = 3)))
                .play(Move.Play(StoneColor.Black, BoardCoordinate(row = 9, column = 9)))
                .play(Move.Pass(StoneColor.White)),
            playerSetup = HumanBlackAiWhite,
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 1_727_000_000_000L,
            scoreSnapshots = listOf(
                ScoreSnapshot(moveNumber = 1, whiteScoreLead = -4.5, whiteWinRate = 0.25, source = ScoreSnapshotSource.EngineEstimate),
                ScoreSnapshot(moveNumber = 2, whiteScoreLead = null, whiteWinRate = null, source = ScoreSnapshotSource.LocalAreaEstimate),
            ),
        )

    /** 19줄·일본·호선·덤 7.5 — 끝나 계가 판정만 남은 저장분(결과 팝업 복원용). */
    fun endedSession(): SavedGameSnapshot =
        SavedGameSnapshot(
            gameState = GameState.empty(BoardSize.Nineteen, Ruleset.Japanese, komi = 7.5)
                .play(Move.Play(StoneColor.Black, BoardCoordinate(row = 3, column = 15)))
                .play(Move.Play(StoneColor.White, BoardCoordinate(row = 15, column = 3)))
                .play(Move.Pass(StoneColor.Black))
                .play(Move.Pass(StoneColor.White)),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = 42L,
            finalScoreJudgement = FinalScoreJudgement(
                winner = StoneColor.White,
                margin = 7.5,
                ruleset = Ruleset.Japanese,
                isEstimatedDisplay = false,
                removedBlack = 0,
                removedWhite = 0,
                blackArea = 0.0,
                whiteAreaWithKomi = 7.5,
                capturedByBlack = 0,
                capturedByWhite = 0,
                komi = 7.5,
            ),
        )

    /** 9줄·일본·호선·6.5 — 전부 기본값인 판. 기본값 흡수 경로가 같은 바이트를 내는지 본다. */
    fun defaultSession(): SavedGameSnapshot =
        SavedGameSnapshot(
            gameState = GameState.empty(BoardSize.Nine, Ruleset.Japanese)
                .play(Move.Play(StoneColor.Black, BoardCoordinate(row = 4, column = 4))),
            playerSetup = PlayerSetup(),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = 0L,
        )

    fun historyEntries(): List<GameHistoryEntry> =
        listOf(
            GameHistoryEntry(
                id = "1727000000000-123456",
                playedAtMillis = 1_727_000_000_000L,
                boardSize = 13,
                ruleset = Ruleset.Chinese,
                komi = 0.5,
                handicapCount = 2,
                playerSetup = HumanBlackAiWhite,
                moveCount = 3,
                humanColor = StoneColor.Black,
                winner = StoneColor.Black,
                isResign = false,
                margin = 12.5,
                hasReplay = true,
                note = "좋은 판",
            ),
            GameHistoryEntry(
                id = "42-7",
                playedAtMillis = 42L,
                boardSize = 19,
                ruleset = Ruleset.Japanese,
                komi = 7.5,
                handicapCount = 0,
                playerSetup = PlayerSetup(),
                moveCount = 4,
                humanColor = null,
                winner = null,
                isResign = true,
                margin = null,
                hasReplay = false,
                note = null,
            ),
            GameHistoryEntry(
                id = "0-0",
                playedAtMillis = 0L,
                boardSize = 9,
                ruleset = Ruleset.Japanese,
                komi = 6.5,
                handicapCount = 0,
                playerSetup = PlayerSetup(),
                moveCount = 0,
                humanColor = StoneColor.White,
                winner = StoneColor.White,
            ),
        )
}
