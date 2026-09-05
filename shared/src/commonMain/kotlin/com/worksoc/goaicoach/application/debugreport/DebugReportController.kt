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
    /**
     * 빌드·기기 스탬프(#92) — 앱 버전/versionCode/빌드타입/기기/OS/ABI.
     * ⚠️ **기본값을 일부러 주지 않았다.** 아래 계층들은 호출부를 안 깨려고 `"not recorded"`를
     * 기본값으로 갖는데, 그 편의가 여기까지 오면 **배선을 빠뜨려도 조용히 통과한다.**
     * 여기서 끊어 두면 app-android가 값을 넘기지 않는 순간 컴파일이 깨진다 — 리포트가
     * 거짓말하는 것보다 빌드가 깨지는 편이 싸다.
     */
    private val buildStamp: () -> String,
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
                buildStamp = buildStamp(),
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
