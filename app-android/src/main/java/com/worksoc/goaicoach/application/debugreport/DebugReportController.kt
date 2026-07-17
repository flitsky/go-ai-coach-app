package com.worksoc.goaicoach.application.debugreport

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.session.GameSessionControllerState

internal class DebugReportController(
    private val engineName: String,
    private val engineDiagnostic: String,
    private val runtimeEventLog: RuntimeEventLogPort,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val clipboard: ClipboardPort,
    private val mirror: DebugReportMirrorPort,
    private val userNotice: UserNoticePort,
    private val currentControllerState: () -> GameSessionControllerState,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val analysisCacheStatsText: () -> String,
    private val positionAnalysisCacheStatsText: (Long) -> String,
    private val turnTimeText: () -> String,
    private val turnTimeDebugText: (Long) -> String,
    private val onEngineMessage: (String) -> Unit,
    private val currentSavedSessionJson: () -> String?,
) {
    fun copy() {
        runDebugReportCopyApplication(
            DebugReportCopyRunRequest(
                controllerState = currentControllerState(),
                engineName = engineName,
                engineDiagnostic = engineDiagnostic,
                analysisCacheStatsText = analysisCacheStatsText,
                positionAnalysisCacheStatsText = positionAnalysisCacheStatsText,
                isEngineReady = isEngineReady(),
                isEngineBusy = isEngineBusy(),
                turnTimeText = turnTimeText,
                turnTimeDebugText = turnTimeDebugText,
                runtimeEventLog = runtimeEventLog,
                diagnosticEventLog = diagnosticEventLog,
                clipboard = clipboard,
                mirror = mirror,
                userNotice = userNotice,
                applyEngineMessage = onEngineMessage,
                savedSessionJson = currentSavedSessionJson(),
            ),
        )
    }
}
