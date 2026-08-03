package com.worksoc.goaicoach.application.premium

/**
 * 4계층(External Integration) α — [PremiumState]를 로컬에 저장/복원하는 순수 포트.
 * 실제 어댑터는 `persistence/PremiumStateStore.kt`(플랫폼 계층)에 둔다. 앱 프로세스가
 * 백그라운드에서 시스템에 의해 종료된 뒤 재시작되는 경우(사용자의 명시적 강제 종료와
 * 구분되지 않는다) 메모리에만 있던 활성화 상태가 사라져 광고 시청 팝업이 다시 뜨는 문제를
 * 막기 위해 도입했다.
 */
internal interface PremiumStateStorePort {
    fun save(state: PremiumState)
    fun load(): PremiumState
}
