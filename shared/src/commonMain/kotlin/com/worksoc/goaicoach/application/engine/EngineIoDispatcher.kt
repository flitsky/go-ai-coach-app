package com.worksoc.goaicoach.application.engine

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 엔진의 블로킹 작업을 태울 디스패처. [runEngineIo]가 쓰는 유일한 지점이다.
 *
 * `Dispatchers.IO`는 commonMain에서 `internal`이라 공용 코드가 직접 부를 수 없다(플랫폼별
 * 소스셋에서만 공개된다). 그렇다고 `Dispatchers.Default`로 바꾸면 CPU 코어 수만큼만 도는
 * 풀에서 블로킹 엔진 호출이 스레드를 잡아먹는다 — 그래서 "블로킹을 감당하는 풀"이라는
 * 정책만 플랫폼에 위임한다(expect/actual 하나). 지금은 android/ios 양쪽 다
 * `Dispatchers.IO`다.
 */
internal expect val engineIoDispatcher: CoroutineDispatcher
