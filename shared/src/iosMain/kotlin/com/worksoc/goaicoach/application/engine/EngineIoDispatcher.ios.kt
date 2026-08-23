package com.worksoc.goaicoach.application.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * kotlinx-coroutines 1.8.0은 네이티브 타깃에서도 `Dispatchers.IO`를 공개하지 않는다(1.9.0부터
 * 공개). iOS 앱은 아직 없고 이 타깃은 `-PenableIosTargets=true`로만 빌드되는 이식성 검증용이라,
 * 지금은 `Dispatchers.Default`로 둔다. iOS를 실제로 출시할 때는 coroutines를 1.9+로 올려
 * `Dispatchers.IO`로 바꾸거나 전용 스레드 풀을 붙여야 한다 — 그대로 두면 블로킹 엔진 호출이
 * Default 풀을 굶긴다.
 */
internal actual val engineIoDispatcher: CoroutineDispatcher = Dispatchers.Default
