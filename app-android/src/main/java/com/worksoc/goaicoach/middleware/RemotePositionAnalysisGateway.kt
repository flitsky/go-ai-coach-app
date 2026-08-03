package com.worksoc.goaicoach.middleware

import com.worksoc.goaicoach.shared.RemotePositionAnalysisRequest
import com.worksoc.goaicoach.shared.RemotePositionAnalysisTransport
import com.worksoc.goaicoach.shared.analysisFingerprint

/**
 * Read-only remote analysis spike.
 *
 * This gateway deliberately exposes only explicit position analysis. It does
 * not own a match, does not mutate local engine state, and does not implement
 * genmove/play/undo. That keeps remote rollout safe: the app can first compare
 * remote analysis quality/latency while local offline play remains unchanged.
 *
 * [transport]는 `:shared`의 [RemotePositionAnalysisTransport] 계약을 만족하는 아무 구현체나
 * 받는다 — 실제 JVM/HTTP 구현체는 `engine-android` 모듈에 있다(엔진 로컬/원격 구현체를
 * 물리적으로 한 곳에 모은 260804 정리). 이 파일은 그 구현체 이름조차 몰라야 하는 계약 위치라
 * 일부러 이름을 적지 않는다 — `LayeringContractTest`가 이 파일에 transport 세부사항이 새어
 * 들어오지 않는지 검증한다.
 */
internal class RemotePositionAnalysisGateway(
    private val transport: RemotePositionAnalysisTransport,
) : PositionAnalysisGateway {
    override suspend fun analyze(request: PositionAnalysisRequest): PositionAnalysisResponse {
        val response = transport.analyze(
            RemotePositionAnalysisRequest(
                state = request.state,
                limit = request.limit,
                searchMode = request.searchMode,
                positionFingerprint = request.state.analysisFingerprint(),
            ),
        )
        return PositionAnalysisResponse(
            result = response.result,
            backend = PositionAnalysisBackend.Remote,
            diagnosticText = response.diagnosticText,
        )
    }
}
