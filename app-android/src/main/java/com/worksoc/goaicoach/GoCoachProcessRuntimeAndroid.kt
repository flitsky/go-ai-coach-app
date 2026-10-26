package com.worksoc.goaicoach

import android.app.Application
import com.worksoc.goaicoach.application.engine.RemoteEngineCandidate
import com.worksoc.goaicoach.engine.createEngineBootstrap
import com.worksoc.goaicoach.engine.createRemoteEngineSessionClient
import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import com.worksoc.goaicoach.persistence.JsonPositionAnalysisCacheStore
import java.io.File

/**
 * 안드로이드 앱의 [GoCoachProcessRuntime] — `GoAiCoachApplication.processRuntime`이 `by lazy`로 한 번 만든다(#110). 본체는 공용이고(#294 — iOS도
 * 같은 클래스), 여기는 안드로이드의 재료(파일 진단 로그 · 분석 캐시 · PAD 부트스트랩 · 디버그 원격 엔진)를 꽂기만 한다.
 */
internal fun GoCoachProcessRuntime.Companion.forApplication(app: Application): GoCoachProcessRuntime {
    // 개발용 원격 엔진 스파이크(`REMOTE_ENGINE_AND_LAYERING.md` Stage E-3).
    // BuildConfig.REMOTE_ENGINE_URL은 debug 빌드에서만, local.properties의
    // debug.remoteEngineUrl 키가 있을 때만 비어있지 않다(app-android/build.gradle.kts 참고)
    // — playInternal/release는 항상 빈 문자열로 고정돼 있어 이 분기를 절대 타지 않는다.
    val remoteEngineUrl = BuildConfig.REMOTE_ENGINE_URL
    val remoteEngineRequested = BuildConfig.DEBUG && remoteEngineUrl.isNotBlank()
    // 로컬 · 원격 클라이언트가 같은 로그 · 같은 캐시를 쓴다(옮기기 전과 같다).
    val diagnosticEventLog = DiagnosticEventLog(File(app.filesDir, DiagnosticEventLog.FileName))
    val positionAnalysisCacheStore = JsonPositionAnalysisCacheStore(app)
    return GoCoachProcessRuntime(
        diagnosticEventLog = diagnosticEventLog,
        positionAnalysisCacheStore = positionAnalysisCacheStore,
        createBootstrap = {
            createEngineBootstrap(
                context = app,
                nativeLibraryDir = app.applicationInfo.nativeLibraryDir,
            )
        },
        remoteEngineUrl = remoteEngineUrl.takeIf { remoteEngineRequested },
        createRemoteClient = { url, currentSessionGeneration ->
            createRemoteEngineSessionClient(
                candidates = listOf(RemoteEngineCandidate(endpointUrl = url, enabled = true)),
                currentSessionGeneration = currentSessionGeneration,
                positionAnalysisCacheStore = positionAnalysisCacheStore,
                diagnosticEventLog = diagnosticEventLog,
            )
        },
    )
}
