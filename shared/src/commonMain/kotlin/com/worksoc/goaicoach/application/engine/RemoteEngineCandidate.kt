package com.worksoc.goaicoach.application.engine

/**
 * 3계층(Extended API) — DePIN/원격 엔진 준비(Stage E-1)의 "후보" 표현. `engine-android`의
 * `RemoteEngineHttpConfig`(2계층, 실제 전송 설정)와 필드 모양은 같지만 의도적으로 별개
 * 타입이다 — `application/`은 `com.worksoc.goaicoach.engine.android`를 import하면 안 되는
 * 기존 경계(`LayeringContractTest`)를 그대로 지키기 위함. 실제 [engine-android
 * RemoteEngineHttpConfig]로의 변환은 composition root에 가까운 `com.worksoc.goaicoach.engine`
 * 패키지(예: `EngineBootstrap.kt`와 같은 자리)에서 일어난다.
 */
data class RemoteEngineCandidate(
    val endpointUrl: String,
    val enabled: Boolean,
    val connectTimeoutMillis: Int = 3_000,
    val readTimeoutMillis: Int = 30_000,
)

/**
 * 후보 중 지금 쓸 만한 것 하나를 고른다 — "여러 원격 후보 중 선택·신뢰도 판단"을 3계층이
 * 흡수해, 상위(5계층 이상)는 지금 엔진이 로컬인지 원격인지, 원격이라면 후보가 몇 개인지 전혀
 * 몰라도 된다(`docs/ARCHITECTURE.md`의 3계층 "DePIN 관점에서의 역할" 절 그대로).
 *
 * 지금은 실제 피어 네트워킹 없이 "고정된 원격 서버 1대"로 시작하는 단계라(계획서
 * `LAYERED_ARCHITECTURE_REFACTORING_PLAN`의 Stage E-1 범위), 판단은 아직 "활성화돼 있고
 * 엔드포인트가 비어있지 않은 첫 후보"만큼만 있다 — 여러 후보의 응답 시간/성공률을 비교하는
 * 판단은 실제로 후보가 2개 이상 존재할 때(DePIN 방향 확장) 추가한다. 선언된 순서를 우선순위로
 * 취급한다.
 */
fun selectRemoteEngineCandidate(
    candidates: List<RemoteEngineCandidate>,
): RemoteEngineCandidate? =
    candidates.firstOrNull { candidate -> candidate.enabled && candidate.endpointUrl.isNotBlank() }
