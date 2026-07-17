package com.worksoc.goaicoach.application.debugreport

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.session.GameSessionControllerState

internal data class DebugReportCopyRunRequest(
    val controllerState: GameSessionControllerState,
    val engineName: String,
    val engineDiagnostic: String,
    val analysisCacheStatsText: () -> String,
    val positionAnalysisCacheStatsText: (Long) -> String,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val turnTimeText: () -> String,
    val turnTimeDebugText: (Long) -> String,
    val runtimeEventLog: RuntimeEventLogPort,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val clipboard: ClipboardPort,
    val mirror: DebugReportMirrorPort,
    val userNotice: UserNoticePort,
    val savedSessionJson: String?,
    val nowMillis: () -> Long = { System.currentTimeMillis() },
    val applyEngineMessage: (String) -> Unit,
)

internal fun runDebugReportCopyApplication(
    request: DebugReportCopyRunRequest,
): DebugReportCopyResult {
    val nowMillis = request.nowMillis()
    val result = runDebugReportCopyAction(
        request = DebugReportCopyActionRequest(
            controllerState = request.controllerState,
            engineName = request.engineName,
            engineDiagnostic = request.engineDiagnostic,
            analysisCacheStats = request.analysisCacheStatsText(),
            positionAnalysisCacheStats = request.positionAnalysisCacheStatsText(nowMillis),
            isEngineReady = request.isEngineReady,
            isEngineBusy = request.isEngineBusy,
            turnTimeText = request.turnTimeText(),
            turnTimeDebugText = request.turnTimeDebugText(nowMillis),
            runtimeEventLogText = request.runtimeEventLog.readText(),
            diagnosticEventLogText = request.diagnosticEventLog.readText(),
            savedSessionJson = request.savedSessionJson,
        ),
        clipboard = request.clipboard,
        mirror = request.mirror,
        userNotice = request.userNotice,
    )
    request.applyEngineMessage(result.engineMessage)
    return result
}
