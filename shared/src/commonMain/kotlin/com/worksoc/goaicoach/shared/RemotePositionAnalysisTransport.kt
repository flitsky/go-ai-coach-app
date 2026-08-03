package com.worksoc.goaicoach.shared

/**
 * 2계층(Middleware / Bridge) — position-analysis 단위 원격 호출 계약.
 *
 * `app-android`의 `RemotePositionAnalysisGateway`(3계층에 가까운 어댑터)와
 * `engine-android`의 `HttpRemotePositionAnalysisTransport`(JVM/HTTP 구현체)가 순환 의존 없이
 * 같은 계약을 공유하도록 `:shared`에 둔다 — 둘 다 [GameState]/[AnalysisLimit]/[AnalysisResult]/
 * [EngineSearchMode]처럼 이미 KMP-safe한 타입만 사용하므로 이 계약 자체도 플랫폼 SDK에
 * 의존하지 않는다.
 */
interface RemotePositionAnalysisTransport {
    suspend fun analyze(request: RemotePositionAnalysisRequest): RemotePositionAnalysisResponse
}

data class RemotePositionAnalysisRequest(
    val state: GameState,
    val limit: AnalysisLimit,
    val searchMode: EngineSearchMode,
    val positionFingerprint: String,
)

data class RemotePositionAnalysisResponse(
    val result: AnalysisResult,
    val diagnosticText: String? = null,
)
