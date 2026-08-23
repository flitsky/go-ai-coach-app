package com.worksoc.goaicoach.application.botcharacter

/**
 * 4계층 — 봇 컬렉션 상태를 저장/복원하는 외부 저장소 포트. 구현체는 플랫폼 쪽
 * (`app-android/.../persistence/BotCollectionStore.kt`)에 두고, 나중에 로그인이 켜지면
 * 같은 포트를 구현하는 Firestore 어댑터만 추가하면 된다(킥오프 플랜 8장).
 */
interface BotCollectionStorePort {
    fun save(state: BotCollectionState)
    fun load(): BotCollectionState
}
