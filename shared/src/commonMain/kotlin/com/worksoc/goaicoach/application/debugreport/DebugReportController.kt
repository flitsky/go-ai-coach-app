package com.worksoc.goaicoach.application.debugreport

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.session.GameSessionControllerState

class DebugReportController(
    private val engineName: String,
    private val engineDiagnostic: String,
    /**
     * 착수 진동 진단(#36). 플랫폼 API를 타므로 `shared`가 만들 수 없어 **app-android가 람다로
     * 넘긴다.** 매번 호출해 **리포트를 뽑는 그 시점**의 기기 상태를 읽는다.
     */
    private val hapticDiagnostic: () -> String = { "not recorded" },
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
                hapticDiagnostic = hapticDiagnostic(),
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
