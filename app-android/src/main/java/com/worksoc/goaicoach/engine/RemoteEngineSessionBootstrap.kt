package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.application.analysis.NoopPositionAnalysisCacheStore
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheStore
import com.worksoc.goaicoach.application.analysis.TrustedPositionAnalysisCacheProvider
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.engine.EngineClock
import com.worksoc.goaicoach.application.engine.EngineSessionBackend
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.application.engine.RemoteEngineCandidate
import com.worksoc.goaicoach.application.engine.SystemEngineClock
import com.worksoc.goaicoach.application.engine.selectRemoteEngineCandidate
import com.worksoc.goaicoach.engine.android.EngineCoreApiFactory
import com.worksoc.goaicoach.engine.android.RemoteEngineHttpConfig

/**
 * `EngineBootstrap.kt`(로컬 엔진)와 같은 자리 — composition root에 가까운 이 패키지만
 * `com.worksoc.goaicoach.engine.android`(2계층 구현 모듈)와 `application.engine`(3계층 계약)을
 * 동시에 알 수 있다. `application/` 자체는 engine-android를 import할 수 없다
 * (`LayeringContractTest`), 그래서 3계층의 "후보 선택" 판단([selectRemoteEngineCandidate])과
 * "실제로 그 후보를 EngineCoreApi로 만드는" 배선을 이 파일이 이어붙인다.
 *
 * Stage E-1 범위: 아직 실제 원격 서버가 없어 GoCoachApp/MainActivity의 실제 컴포지션에는
 * 배선하지 않았다 — 이 함수는 독립적으로 빌드/테스트되는 컴포넌트다.
 */
internal fun createRemoteEngineSessionClient(
    candidates: List<RemoteEngineCandidate>,
    positionAnalysisCacheStore: PositionAnalysisCacheStore = NoopPositionAnalysisCacheStore,
    trustedPositionAnalysisCacheProviders: List<TrustedPositionAnalysisCacheProvider> = emptyList(),
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    clock: EngineClock = SystemEngineClock,
): EngineSessionClient? {
    val candidate = selectRemoteEngineCandidate(candidates) ?: return null
    return LocalEngineSessionClient(
        coreApi = EngineCoreApiFactory.remote(
            RemoteEngineHttpConfig(
                endpointUrl = candidate.endpointUrl,
                enabled = candidate.enabled,
                connectTimeoutMillis = candidate.connectTimeoutMillis,
                readTimeoutMillis = candidate.readTimeoutMillis,
            ),
        ),
        capabilitiesProvider = {
            EngineSessionCapabilities(
                supportsDeviceBenchmark = false,
                backend = EngineSessionBackend.RemoteServer,
            )
        },
        positionAnalysisCacheStore = positionAnalysisCacheStore,
        trustedPositionAnalysisCacheProviders = trustedPositionAnalysisCacheProviders,
        diagnosticEventLog = diagnosticEventLog,
        clock = clock,
    )
}
