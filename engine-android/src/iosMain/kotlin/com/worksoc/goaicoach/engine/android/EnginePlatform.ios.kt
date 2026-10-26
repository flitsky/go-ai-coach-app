package com.worksoc.goaicoach.engine.android

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.stderr

/** 앱 안 엔진의 파이프 읽기 · 쓰기가 실패했다(`read` · `write`가 오류를 돌려줬다). */
internal class EnginePipeBrokenException(message: String) : Exception(message)

internal actual fun Throwable.isEnginePipeBroken(): Boolean = this is EnginePipeBrokenException

/**
 * 파이프 왕복이 막혀 기다리는 풀 — 막힌 읽기 하나가 스레드 하나를 쥔다(GTP · analysis의 왕복과 시간 초과 뒤의 배수 · 비우기).
 * 8은 `:shared`의 `engineIoDispatcher`와 같은 어림이다(백로그 #293 — iOS 실측 전).
 */
@OptIn(DelicateCoroutinesApi::class)
internal actual val enginePipeIoDispatcher: CoroutineDispatcher =
    newFixedThreadPoolContext(nThreads = 8, name = "katago-pipe-io")

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun engineDiagnosticLine(line: String) {
    fprintf(stderr, "%s\n", line)
    fflush(stderr)
}
