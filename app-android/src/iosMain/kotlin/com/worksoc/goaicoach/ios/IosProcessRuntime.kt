package com.worksoc.goaicoach.ios

import com.worksoc.goaicoach.GoCoachProcessRuntime
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.engine.EngineBootstrap
import com.worksoc.goaicoach.engine.android.IosEngineCoreApiFactory
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.kv.IosAppFileStore
import com.worksoc.goaicoach.kv.KvPositionAnalysisCacheStore
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * iOS 앱의 엔진 묶음(백로그 #294 — 이식 계획 S30) — 안드로이드와 **같은** [GoCoachProcessRuntime]이다(세션 클라이언트 · 지연 엔진 · 세대 중계 ·
 * 프로세스에 한 번 도는 부트스트랩). 다른 것은 재료뿐이다: 엔진은 앱 안 KataGo(#293), 진단 로그는 [diagnosticEventLog] · 분석 캐시는
 * `KvPositionAnalysisCacheStore` — 둘 다 안드로이드와 같은 파일 · 같은 JSON.
 *
 * 모델 · 설정을 찾는 차례: ① 환경 변수(`GOCOACH_KATAGO_MODEL` · `…_HUMAN_MODEL` · `…_CONFIG` · `…_ANALYSIS_CONFIG` — 시뮬레이터에서 맥의 파일을
 * 그대로 읽는다, `make ios-run`) ② 앱 묶음의 `appassets/katago/`(모델을 싣는 방식은 #293 남은 일) ③ 없으면 스텁 — 「엔진 아직」 흐름을 탄다.
 * 사람 모델이 없으면 캐릭터 대국이 사람 모델 없이 도는 것을 막으려고 안드로이드처럼 스텁이다(#249 U-78: 두 모델 필수).
 *
 * 하나만 만든다 — [IosAppRoot]가(Swift `AppDelegate`가 프로세스에 하나 든다) 한 번(안드로이드는 `GoAiCoachApplication`이 쥔다, #110). 전역 object로 두지 않는다:
 * 컴포즈 상태를 든 전역은 셸 원장이 막는다(`ShellStateLedgerContractTest`).
 */
internal fun iosProcessRuntime(diagnosticEventLog: DiagnosticEventLogPort, modelSource: IosEngineModelSource?): GoCoachProcessRuntime = GoCoachProcessRuntime(
    diagnosticEventLog = diagnosticEventLog,
    positionAnalysisCacheStore = KvPositionAnalysisCacheStore(IosAppFileStore()),
    createBootstrap = { createIosEngineBootstrap(modelSource) },
    remoteEngineUrl = null,
)

@OptIn(ExperimentalForeignApi::class)
private suspend fun createIosEngineBootstrap(modelSource: IosEngineModelSource?): EngineBootstrap {
    val files = locateIosModelFiles(modelSource) ?: modelSource?.let { awaitIosModelPack(it) }
    if (files == null) {
        return EngineBootstrap(
            coreApi = IosEngineCoreApiFactory.stub(),
            mode = EngineMode.Stub,
            displayName = "Stub (iOS)",
            diagnostic = "iOS engine not configured — no model files (set GOCOACH_KATAGO_*, bundle appassets/katago, or download the model pack).",
        )
    }
    val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
    val workDirectory = "${support ?: "/tmp"}/katago"
    for (dir in listOf(workDirectory, "$workDirectory/logs", "$workDirectory/home")) {
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    }
    // 엔진의 작업 폴더(로그 · 임시 기보)는 백업에서 뺀다 — 안드로이드 `backup_rules.xml`의 `exclude path="katago"`와 같은 짝. 폴더에 걸면 안이 모두 빠진다.
    NSURL.fileURLWithPath(workDirectory, isDirectory = true).setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    val processConfig = KataGoProcessConfig(
        executablePath = "katago",
        modelPath = files.model,
        configPath = files.config,
        analysisConfigPath = files.analysisConfig,
        humanModelPath = files.humanModel,
        // 안드로이드 `EngineBootstrap.kt`의 기동 인자와 같게.
        startupOverrides = mapOf(
            "numSearchThreads" to "1",
            "logDir" to "$workDirectory/logs",
            "homeDataDir" to "$workDirectory/home",
            "logToStderr" to "false",
            "logAllGTPCommunication" to "false",
            "logSearchInfo" to "false",
            "allowResignation" to "false",
            "startupPrintMessageToStderr" to "false",
        ),
    )
    return EngineBootstrap(
        coreApi = IosEngineCoreApiFactory.inProcess(processConfig, workDirectory),
        mode = EngineMode.LocalProcess,
        displayName = "KataGo (in-process)",
        diagnostic = "in-process KataGo; model=${files.model.substringAfterLast('/')} human=${files.humanModel.substringAfterLast('/')}",
    )
}
