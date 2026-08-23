package com.worksoc.goaicoach.application.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext

/**
 * 네이티브 타깃에는 `Dispatchers.IO`가 없다 — commonMain의 `internal` 선언뿐이라
 * iosMain에서도 접근할 수 없다(kotlinx-coroutines 1.8.0/1.11.0 양쪽에서 확인). 그래서
 * "블로킹을 감당하는 별도 풀"이라는 안드로이드 쪽 정책을 직접 만들어 맞춘다.
 *
 * `Dispatchers.Default`로 대신하면 안 된다 — CPU 코어 수만큼만 도는 풀이라 블로킹 엔진 호출이
 * 스레드를 잡아먹는다. `Dispatchers.Default.limitedParallelism(n)`도 마찬가지다(Default의
 * 스레드를 나눠 쓰는 뷰일 뿐 스레드가 늘지 않는다).
 *
 * `@DelicateCoroutinesApi`인 이유는 이 풀을 아무도 `close()`하지 않기 때문인데, 앱 수명 내내
 * 사는 단일 디스패처라는 게 여기서는 정확히 의도한 바다(`Dispatchers.IO`도 같은 성격).
 * 스레드 수 8은 엔진 호출이 대체로 직렬이라 넉넉히 잡은 값 — iOS를 실제로 출시할 때
 * 실측해서 조정하면 된다.
 */
@OptIn(DelicateCoroutinesApi::class)
internal actual val engineIoDispatcher: CoroutineDispatcher =
    newFixedThreadPoolContext(nThreads = 8, name = "engine-io")
