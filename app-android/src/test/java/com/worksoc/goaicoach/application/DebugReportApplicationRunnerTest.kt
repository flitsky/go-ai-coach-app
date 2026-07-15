package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.debugreport.DebugReportCopyRunRequest
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.debugreport.UserNoticePort
import com.worksoc.goaicoach.application.debugreport.runDebugReportCopyApplication
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportApplicationRunnerTest {
    @Test
    fun copyRunnerCollectsRuntimeInputsAndAppliesResultMessage() {
        val clipboard = RunnerFakeClipboardPort()
        val mirror = RunnerFakeDebugReportMirrorPort()
        val notice = RunnerFakeUserNoticePort()
        var appliedMessage: String? = null

        runDebugReportCopyApplication(
            DebugReportCopyRunRequest(
                controllerState = controllerState(),
                engineName = "KataGo",
                engineDiagnostic = "diagnostic ok",
                analysisCacheStatsText = { "analysis entries=1" },
                positionAnalysisCacheStatsText = { nowMillis -> "position now=$nowMillis" },
                isEngineReady = true,
                isEngineBusy = false,
                turnTimeText = { "Time B 1.0s / W 2.0s" },
                turnTimeDebugText = { nowMillis -> "debug now=$nowMillis" },
                runtimeEventLog = FakeRuntimeEventLogPort("runtime log"),
                diagnosticEventLog = FakeDiagnosticEventLogPort("diagnostic log"),
                clipboard = clipboard,
                mirror = mirror,
                userNotice = notice,
                nowMillis = { 321L },
                applyEngineMessage = { message -> appliedMessage = message },
            ),
        )

        assertEquals("Go AI Coach debug report", clipboard.label)
        assertEquals("Debug report copied", notice.message)
        assertEquals(clipboard.text, mirror.report)
        assertEquals("Debug report copied to clipboard. Paste it into chat for review.", appliedMessage)
        assertTrue(clipboard.text.orEmpty().contains("analysisCache=analysis entries=1"))
        assertTrue(clipboard.text.orEmpty().contains("positionAnalysisCache=position now=321"))
        assertTrue(clipboard.text.orEmpty().contains("turnTime=Time B 1.0s / W 2.0s"))
        assertTrue(clipboard.text.orEmpty().contains("turnTimeDebug=debug now=321"))
        assertTrue(clipboard.text.orEmpty().contains("runtime log"))
        assertTrue(clipboard.text.orEmpty().contains("diagnostic log"))
    }

    @Test
    fun copyRunnerHandlesClipboardFailureGracefully() {
        val clipboard = RunnerFakeClipboardPort().apply { nextResult = false }
        val mirror = RunnerFakeDebugReportMirrorPort()
        val notice = RunnerFakeUserNoticePort()
        var appliedMessage: String? = null

        runDebugReportCopyApplication(
            DebugReportCopyRunRequest(
                controllerState = controllerState(),
                engineName = "KataGo",
                engineDiagnostic = "diagnostic ok",
                analysisCacheStatsText = { "analysis entries=1" },
                positionAnalysisCacheStatsText = { nowMillis -> "position now=$nowMillis" },
                isEngineReady = true,
                isEngineBusy = false,
                turnTimeText = { "Time B 1.0s / W 2.0s" },
                turnTimeDebugText = { nowMillis -> "debug now=$nowMillis" },
                runtimeEventLog = FakeRuntimeEventLogPort("runtime log"),
                diagnosticEventLog = FakeDiagnosticEventLogPort("diagnostic log"),
                clipboard = clipboard,
                mirror = mirror,
                userNotice = notice,
                nowMillis = { 321L },
                applyEngineMessage = { message -> appliedMessage = message },
            ),
        )

        assertEquals("Go AI Coach debug report", clipboard.label)
        assertEquals("Debug report saved to file, but failed to copy to clipboard", notice.message)
        assertEquals(clipboard.text, mirror.report) // 로그가 짧으므로 둘의 텍스트가 일치함
    }

    private fun controllerState(): GameSessionControllerState {
        val state = GameState.empty()
        return GameSessionControllerState(
            core = GameSessionCoreState(
                gameState = state,
                isGameEnded = false,
                analysisState = GameSessionAnalysisState.empty(state, candidateText = "candidate"),
                scoreState = GameSessionScoreState.reset(
                    scoreText = "score",
                    scoreSnapshots = emptyList(),
                    endgameLog = "endgame",
                ),
                runtimeState = GameSessionRuntimeState(
                    playLevel = PlayLevelSetting(),
                    engineProfile = EngineProfile(),
                    analysisPreset = AnalysisPreset.Lite,
                ),
                moveReviewState = GameSessionMoveReviewState.reset(
                    moveReviewText = "review",
                    lastMoveText = "None",
                ),
                engineMessage = "engine",
            ),
            settings = GameSessionSettingsState(
                playerSetup = PlayerSetup(),
                autoPlayDelaySetting = AutoPlayDelaySetting.Default,
                searchTimeSettings = SearchTimeSettings(),
                topMovesEnabled = true,
                boardSize = BoardSize.Nine,
            ),
            benchmark = EngineBenchmarkUiState(benchmarkText = "benchmark"),
            savedSession = SavedSessionUiState(),
            autoAiTurn = AutoAiTurnUiState(),
            positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
        )
    }
}

private class FakeRuntimeEventLogPort(
    private val text: String,
) : RuntimeEventLogPort {
    override fun append(
        event: String,
        nowMillis: Long,
    ) = Unit

    override fun readText(): String = text

    override fun clear() = Unit
}

private class FakeDiagnosticEventLogPort(
    private val text: String,
) : DiagnosticEventLogPort {
    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) = Unit

    override fun readText(): String = text

    override fun clear() = Unit
}

private class RunnerFakeClipboardPort : ClipboardPort {
    var label: String? = null
        private set
    var text: String? = null
        private set
    var nextResult: Boolean = true

    override fun setText(label: String, text: String): Boolean {
        this.label = label
        this.text = text
        return nextResult
    }
}

private class RunnerFakeDebugReportMirrorPort : DebugReportMirrorPort {
    var report: String? = null
        private set

    override fun save(report: String) {
        this.report = report
    }
}

private class RunnerFakeUserNoticePort : UserNoticePort {
    var message: String? = null
        private set

    override fun showShort(message: String) {
        this.message = message
    }
}
