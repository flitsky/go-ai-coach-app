package com.worksoc.goaicoach.application.device

/**
 * 6계층(Session & Continuity) — 이 설치(install)를 가리키는 안정적인 로컬 식별자.
 *
 * 계정(로그인 UID)과는 다른 축이다: 계정은 "누구인가"를, 이 값은 "그 계정이 지금 어느
 * 기기에서 쓰이고 있는가"를 나타낸다.
 * [com.worksoc.goaicoach.application.premium.PremiumState]/[com.worksoc.goaicoach.application.auth.AuthState]처럼
 * 플랫폼 SDK에 의존하지 않는 순수 모델로 설계해, 값을 어떻게 생성/저장할지는
 * [DeviceIdentityStorePort] 뒤로 분리한다.
 *
 * 이 시점(2026-08-03)에는 이 값을 실제로 소비하는 다중 기기 정책(예: 구매 아이템 기기 제한)이
 * 없다 — 계정 기반 교차 기기 상태(Firestore 동기화, 실제 로그인) 자체가 아직 없기 때문이다.
 * 이 타입은 그 정책이 필요해질 때 바로 쓸 수 있도록 식별자 인프라만 먼저 마련해 둔 것이다.
 */
data class DeviceIdentity(val id: String) {
    init {
        require(id.isNotBlank()) { "Device identity id must not be blank" }
    }
}
