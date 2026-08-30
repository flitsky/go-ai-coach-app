package com.worksoc.goaicoach.application.debugreport

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.time.currentEpochMillis

internal data class DebugReportCopyRunRequest(
    val controllerState: GameSessionControllerState,
    val engineName: String,
    val engineDiagnostic: String,
    /** 착수 진동 진단(#36). app-android가 채운다. */
    val hapticDiagnostic: String = "not recorded",
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
    val nowMillis: () -> Long = { currentEpochMillis() },
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
            hapticDiagnostic = request.hapticDiagnostic,
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
