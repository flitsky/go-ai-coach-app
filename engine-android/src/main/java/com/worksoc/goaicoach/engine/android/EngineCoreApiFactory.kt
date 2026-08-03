package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.EngineCoreApi

/**
 * `engine-android`가 공개하는 유일한 생성 지점 — 이 모듈이 실제로 노출하는 것은
 * [EngineCoreApi] 계약과 이 팩토리뿐이다. [KataGoProcessEngineAdapter]/[StubEngineAdapter]/
 * `RemoteEngineCoreApiAdapter`는 전부 `internal`이라 app-android는 이 클래스들의 이름조차
 * 모른다 — 어떤 구현을 쓸지는 파일 존재 여부 등 app-android가 가진 정보로 결정하되(그래서
 * [local]/[stub]로 함수를 나눴다), 실제 생성/배선은 여기서만 일어난다.
 */
object EngineCoreApiFactory {
    fun local(config: KataGoProcessConfig): EngineCoreApi = KataGoProcessEngineAdapter(config)

    fun stub(): EngineCoreApi = StubEngineAdapter()
}
