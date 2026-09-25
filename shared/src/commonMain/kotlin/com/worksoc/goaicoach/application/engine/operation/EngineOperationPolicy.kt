package com.worksoc.goaicoach.application.engine.operation

// refactor backlog #18 (절반): 이 파일이 재선언하던 EngineOperationGate/EngineOperationResultGuard/
// EngineOperationApplyPlan 세 sealed class와, 그 사이를 위임하던 EngineOperationPolicyAdapter.kt가
// com.worksoc.goaicoach.shared.policy의 원본과 100% 동일한 필드/변형/기본값을 가진 중복이라
// 삭제했다(260923) — 소비자는 이제 shared.policy 타입을 직접 쓴다(#70: shared.engine에서 이름
// 정리로 이사). 아래 typealias들은 순수 재선언이 아니라 이름만 재수출하므로 남긴다.
internal typealias EngineFallbackPolicy = com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
internal typealias EngineOperationKind = com.worksoc.goaicoach.shared.policy.EngineOperationKind
typealias EngineOperationRequest = com.worksoc.goaicoach.shared.policy.EngineOperationRequest
typealias EngineTimeoutPolicy = com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
internal typealias PositionScopedOperationToken = com.worksoc.goaicoach.shared.policy.PositionScopedOperationToken

typealias EngineOperationBlockReason = com.worksoc.goaicoach.shared.policy.EngineOperationBlockReason
