package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportBuilderTest {
    @Test
    fun debugReportIncludesRuntimeBoardMovesAndDisplayedTexts() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.White))

        val report = buildDebugReport(
            mode = MatchMode.HumanVsAi,
            playerSetup = PlayerSetup(),
            engineName = "KataGo",
            engineDiagnostic = "diagnostic ok",
            engineProfile = EngineProfile(),
            playLevel = PlayLevelSetting(),
            analysisPreset = AnalysisPreset.Lite,
            analysisCacheStats = "entries=1, hits=0, misses=1",
            positionAnalysisCacheStats = "entries=2, reusable=1, complete=0",
            isEngineReady = true,
            isEngineBusy = false,
            isGameEnded = false,
            topMovesEnabled = true,
            topMoveCandidateCount = 3,
            moveAnalysisCoverage = "Analysis coverage: legal 79",
            gameState = state,
            engineMessage = "engine message",
            candidateText = "candidate text",
            scoreText = "score text",
            scoreSnapshots = listOf(
                ScoreSnapshot(
                    moveNumber = 2,
                    whiteScoreLead = 1.5,
                    whiteWinRate = 0.55,
                    source = ScoreSnapshotSource.EngineEstimate,
                ),
            ),
            moveReviewText = "move review",
            lastMoveText = "White pass",
            endgameLog = "No endgame result recorded.",
            engineBenchmarkText = "benchmark ok",
            turnTimeText = "Time B 3.2s / W 4.1s",
            turnTimeDebugText = "blackMillis=3200, whiteMillis=4100, currentTurn=Black, currentElapsedMillis=0",
            runtimeEventLogText = "runtime log ok",
        )

        assertTrue(report.contains("[Runtime]"))
        assertTrue(report.contains("mode=HumanVsAi"))
        assertTrue(report.contains("positionAnalysisCache=entries=2, reusable=1, complete=0"))
        assertTrue(report.contains("turnTime=Time B 3.2s / W 4.1s"))
        assertTrue(report.contains("turnTimeDebug=blackMillis=3200"))
        assertTrue(report.contains("[Board]"))
        assertTrue(report.contains("E5=Black"))
        assertTrue(report.contains("1. Black E5"))
        assertTrue(report.contains("2. White pass"))
        assertTrue(report.contains("[DisplayedTexts]"))
        assertTrue(report.contains("engine message"))
        assertTrue(report.contains("[EngineDiagnostic]"))
        assertTrue(report.contains("diagnostic ok"))
        assertTrue(report.contains("[EngineBenchmark]"))
        assertTrue(report.contains("benchmark ok"))
        assertTrue(report.contains("[RuntimeEventLog]"))
        assertTrue(report.contains("runtime log ok"))
    }
}
