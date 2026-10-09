package com.worksoc.goaicoach.application.rankmeasure

/**
 * 4계층 α(Abstract Driven Port) — 승급 대국의 상태([RankMeasureState])를 기기에 남기는 자리(백로그 #219).
 * 다른 6계층 클러스터의 포트 파일(`BotCharacterPorts.kt`·`ConsumablePorts.kt`)과 같은 파일 단위 예외다.
 *
 * ⚠️ 권한 저장소가 아니다 — 기력과 연승·연패 수일 뿐이라 `ReleaseResetCoordinator`의 초기화 목록(함정 6)에 넣지 않는다.
 */
interface RankMeasureStorePort {
    fun load(): RankMeasureState

    fun save(state: RankMeasureState)
}
