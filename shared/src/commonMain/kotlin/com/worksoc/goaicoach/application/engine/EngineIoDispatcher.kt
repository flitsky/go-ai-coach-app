package com.worksoc.goaicoach.application.engine

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 엔진의 블로킹 작업을 태울 디스패처. [runEngineIo]가 쓰는 유일한 지점이다.
 *
 * `Dispatchers.IO`는 kotlinx-coroutines 1.8.0의 commonMain에서 `internal`이라 공용 코드에서
 * 직접 부를 수 없다. 그렇다고 `Dispatchers.Default`로 바꾸면 CPU 코어 수만큼만 도는 풀에서
 * 블로킹 엔진 호출이 스레드를 잡아먹어 안드로이드 동작이 달라진다 — 그래서 "블로킹을 감당하는
 * 풀"이라는 정책만 플랫폼에 위임한다(expect/actual 하나).
 */
internal expect val engineIoDispatcher: CoroutineDispatcher
