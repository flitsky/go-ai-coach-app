package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
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
import com.worksoc.goaicoach.shared.SearchTimeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportBuilderTest {
    @Test
    fun debugReportIncludesRuntimeBoardMovesAndDisplayedTexts() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.White))

        val report = buildDebugReport(
            DebugReportSnapshot(
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
                diagnosticEventLogText = "diagnostic event log ok",
            ),
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
        assertTrue(report.contains("[DiagnosticEventLog]"))
        assertTrue(report.contains("diagnostic event log ok"))
    }

    @Test
    fun controllerStateBuildsDebugReportSnapshot() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val searchTimeSettings = SearchTimeSettings(b16Millis = 1_500L)
        val controller = GameSessionControllerState(
            core = GameSessionCoreState(
                gameState = state,
                isGameEnded = true,
                analysisState = GameSessionAnalysisState.empty(state, candidateText = "candidate text"),
                scoreState = GameSessionScoreState.reset(
                    scoreText = "score text",
                    scoreSnapshots = listOf(localScoreSnapshot(state)),
                    endgameLog = "endgame log",
                ),
                runtimeState = GameSessionRuntimeState(
                    playLevel = PlayLevelSetting(level = 2),
                    engineProfile = EngineProfile(name = "Diagnostic"),
                    analysisPreset = AnalysisPreset.Balanced,
                ),
                moveReviewState = GameSessionMoveReviewState.reset(
                    moveReviewText = "move review",
                    lastMoveText = "Black E5",
                ),
                engineMessage = "engine message",
            ),
            settings = GameSessionSettingsState(
                playerSetup = PlayerSetup(),
                autoPlayDelaySetting = AutoPlayDelaySetting.Default,
                searchTimeSettings = searchTimeSettings,
                topMovesEnabled = true,
            ),
            benchmark = EngineBenchmarkUiState(
                benchmarkText = "benchmark text",
                searchTimeBenchmarkAverages = mapOf(16 to 1_250.0),
            ),
            savedSession = SavedSessionUiState(),
            autoAiTurn = AutoAiTurnUiState(),
            positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
        )

        val snapshot = controller.toDebugReportSnapshot(
            engineName = "KataGo",
            engineDiagnostic = "diagnostic ok",
            analysisCacheStats = "entries=1",
            positionAnalysisCacheStats = "position entries=2",
            isEngineReady = true,
            isEngineBusy = false,
            turnTimeText = "Time B 1.2s / W 2.3s",
            turnTimeDebugText = "blackMillis=1200, whiteMillis=2300",
            runtimeEventLogText = "runtime log",
            diagnosticEventLogText = "diagnostic log",
        )

        assertEquals(MatchMode.HumanVsAi, snapshot.mode)
        assertEquals(state, snapshot.gameState)
        assertEquals(true, snapshot.isGameEnded)
        assertEquals(true, snapshot.topMovesEnabled)
        assertEquals("candidate text", snapshot.candidateText)
        assertEquals("score text", snapshot.scoreText)
        assertEquals("move review", snapshot.moveReviewText)
        assertEquals("benchmark text", snapshot.engineBenchmarkText)
        assertEquals(searchTimeSettings, snapshot.searchTimeSettings)
        assertEquals("position entries=2", snapshot.positionAnalysisCacheStats)
        assertEquals("diagnostic log", snapshot.diagnosticEventLogText)
    }

    @Test
    fun debugReportCopyPlanCarriesReportAndPlatformEffectText() {
        val state = GameState.empty()
        val plan = buildDebugReportCopyPlan(
            DebugReportSnapshot(
                mode = MatchMode.HumanVsAi,
                playerSetup = PlayerSetup(),
                engineName = "KataGo",
                engineDiagnostic = "diagnostic ok",
                engineProfile = EngineProfile(),
                playLevel = PlayLevelSetting(),
                analysisPreset = AnalysisPreset.Lite,
                analysisCacheStats = "entries=0",
                isEngineReady = true,
                isEngineBusy = false,
                isGameEnded = false,
                topMovesEnabled = true,
                topMoveCandidateCount = 0,
                moveAnalysisCoverage = "none",
                gameState = state,
                engineMessage = "engine",
                candidateText = "candidate",
                scoreText = "score",
                scoreSnapshots = emptyList(),
                moveReviewText = "review",
                lastMoveText = "None",
                endgameLog = "No endgame result recorded.",
                engineBenchmarkText = "benchmark",
                runtimeEventLogText = "runtime",
                diagnosticEventLogText = "diagnostic",
            ),
        )

        assertEquals("Go AI Coach debug report", plan.clipboardLabel)
        assertEquals("Debug report copied", plan.toastMessage)
        assertTrue(plan.engineMessage.contains("clipboard"))
        assertTrue(plan.report.contains("[DiagnosticEventLog]"))
    }

    @Test
    fun debugReportCopyEffectUsesPlatformPortsAndReturnsDisplayMessage() {
        val plan = DebugReportCopyPlan(
            clipboardLabel = "label",
            report = "report",
            engineMessage = "copied",
            toastMessage = "toast",
        )
        val clipboard = FakeClipboardPort()
        val mirror = FakeDebugReportMirrorPort()
        val userNotice = FakeUserNoticePort()

        val result = runDebugReportCopyEffect(
            effect = GameSessionEffect.CopyDebugReport(plan),
            clipboard = clipboard,
            mirror = mirror,
            userNotice = userNotice,
        )

        assertEquals("label", clipboard.label)
        assertEquals("report", clipboard.text)
        assertEquals("report", mirror.report)
        assertEquals("toast", userNotice.message)
        assertEquals("copied", result.engineMessage)
    }
}

private class FakeClipboardPort : ClipboardPort {
    var label: String? = null
        private set
    var text: String? = null
        private set

    override fun setText(label: String, text: String) {
        this.label = label
        this.text = text
    }
}

private class FakeDebugReportMirrorPort : DebugReportMirrorPort {
    var report: String? = null
        private set

    override fun save(report: String) {
        this.report = report
    }
}

private class FakeUserNoticePort : UserNoticePort {
    var message: String? = null
        private set

    override fun showShort(message: String) {
        this.message = message
    }
}
