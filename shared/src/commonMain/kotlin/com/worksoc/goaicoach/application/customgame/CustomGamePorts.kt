package com.worksoc.goaicoach.application.customgame

/**
 * 4계층 α(Abstract Driven Port) — 커스텀 대국의 상태([CustomGameState])를 기기에 남기는 자리(백로그 #217).
 * 다른 6계층 클러스터의 포트 파일(`BotCharacterPorts.kt`·`ConsumablePorts.kt`)과 같은 파일 단위 예외다.
 *
 * ⚠️ 권한 저장소가 아니다 — 급수 설정과 연승 수일 뿐이라 `ReleaseResetCoordinator`의 초기화 목록(함정 6)에 넣지 않는다.
 * 무엇을 고를 수 있는지(해금)는 컬렉션·구독이 정하고, 여기 적힌 급수가 범위를 넘으면 읽는 쪽이 당긴다.
 */
interface CustomGameStorePort {
    fun load(): CustomGameState

    fun save(state: CustomGameState)
}
