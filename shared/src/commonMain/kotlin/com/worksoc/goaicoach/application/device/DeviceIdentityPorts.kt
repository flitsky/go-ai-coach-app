package com.worksoc.goaicoach.application.device

/**
 * 4계층(External Integration) α — [DeviceIdentity]를 로컬에 생성/저장/복원하는 순수 포트.
 * 실제 어댑터(값 생성 + SharedPreferences 저장)는 `persistence/DeviceIdentityStore.kt`
 * (플랫폼 계층)에 둔다 — [com.worksoc.goaicoach.application.premium.PremiumStateStorePort]와
 * 같은 자리.
 */
interface DeviceIdentityStorePort {
    /**
     * 저장된 식별자가 있으면 그대로 반환하고, 없으면(최초 실행) 새로 생성해 저장한 뒤
     * 반환한다. 매 호출마다 같은 값을 반환해야 한다 — 호출자는 이 값이 이 설치 동안
     * 안정적이라고 가정한다.
     */
    fun loadOrCreate(): DeviceIdentity
}
