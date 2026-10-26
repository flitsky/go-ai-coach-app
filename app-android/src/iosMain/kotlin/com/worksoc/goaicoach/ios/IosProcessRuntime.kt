package com.worksoc.goaicoach.ios

import com.worksoc.goaicoach.GoCoachProcessRuntime
import com.worksoc.goaicoach.application.analysis.NoopPositionAnalysisCacheStore
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.engine.EngineBootstrap
import com.worksoc.goaicoach.engine.android.IosEngineCoreApiFactory
import com.worksoc.goaicoach.engine.android.KataGoProcessConfig
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS 앱의 엔진 묶음(백로그 #294 — 이식 계획 S30) — 안드로이드와 **같은** [GoCoachProcessRuntime]이다(세션 클라이언트 · 지연 엔진 · 세대 중계 ·
 * 프로세스에 한 번 도는 부트스트랩). 다른 것은 재료뿐이다: 엔진은 앱 안 KataGo(#293), 진단 로그 · 분석 캐시는 아직 없다(no-op — #295).
 *
 * 모델 · 설정을 찾는 차례: ① 환경 변수(`GOCOACH_KATAGO_MODEL` · `…_HUMAN_MODEL` · `…_CONFIG` · `…_ANALYSIS_CONFIG` — 시뮬레이터에서 맥의 파일을
 * 그대로 읽는다, `make ios-run`) ② 앱 묶음의 `appassets/katago/`(모델을 싣는 방식은 #293 남은 일) ③ 없으면 스텁 — 「엔진 아직」 흐름을 탄다.
 * 사람 모델이 없으면 캐릭터 대국이 사람 모델 없이 도는 것을 막으려고 안드로이드처럼 스텁이다(#249 U-78: 두 모델 필수).
 *
 * 하나만 만든다 — `MainViewController()`가 앱을 띄울 때 한 번(안드로이드는 `GoAiCoachApplication`이 쥔다, #110). 전역 object로 두지 않는다:
 * 컴포즈 상태를 든 전역은 셸 원장이 막는다(`ShellStateLedgerContractTest`).
 */
internal fun iosProcessRuntime(): GoCoachProcessRuntime = GoCoachProcessRuntime(
    diagnosticEventLog = NoopDiagnosticEventLog,
    positionAnalysisCacheStore = NoopPositionAnalysisCacheStore,
    createBootstrap = { createIosEngineBootstrap() },
    remoteEngineUrl = null,
)

@OptIn(ExperimentalForeignApi::class)
private fun createIosEngineBootstrap(): EngineBootstrap {
    val env = NSProcessInfo.processInfo.environment
    fun envPath(name: String): String? = (env[name] as? String)?.takeIf { it.isNotBlank() && NSFileManager.defaultManager.fileExistsAtPath(it) }
    val bundled = NSBundle.mainBundle.resourcePath?.let { "$it/appassets/katago" }
    fun bundledPath(file: String): String? = bundled?.let { "$it/$file" }?.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
    val model = envPath("GOCOACH_KATAGO_MODEL") ?: bundledPath("model.bin.gz")
    val humanModel = envPath("GOCOACH_KATAGO_HUMAN_MODEL") ?: bundledPath("human.bin.gz")
    val config = envPath("GOCOACH_KATAGO_CONFIG") ?: bundledPath("gtp_learning.cfg")
    val analysisConfig = envPath("GOCOACH_KATAGO_ANALYSIS_CONFIG") ?: bundledPath("analysis_learning.cfg")
    val missing = listOfNotNull("model".takeIf { model == null }, "human model".takeIf { humanModel == null }, "config".takeIf { config == null })
    if (missing.isNotEmpty()) {
        return EngineBootstrap(
            coreApi = IosEngineCoreApiFactory.stub(),
            mode = EngineMode.Stub,
            displayName = "Stub (iOS)",
            diagnostic = "iOS engine not configured — missing ${missing.joinToString()} (set GOCOACH_KATAGO_* or bundle appassets/katago).",
        )
    }
    val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
    val workDirectory = "${support ?: "/tmp"}/katago"
    for (dir in listOf(workDirectory, "$workDirectory/logs", "$workDirectory/home")) {
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    }
    val processConfig = KataGoProcessConfig(
        executablePath = "katago",
        modelPath = model!!,
        configPath = config!!,
        analysisConfigPath = analysisConfig,
        humanModelPath = humanModel,
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
        diagnostic = "in-process KataGo; model=${model.substringAfterLast('/')} human=${humanModel?.substringAfterLast('/')}",
    )
}
