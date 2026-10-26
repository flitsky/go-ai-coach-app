package com.worksoc.goaicoach.engine.android

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 엔진 파이프의 입출력 오류인가(백로그 #293) — 어댑터의 「파이프가 깨졌다 → 그 핸들은 Died」 분기가 본다.
 * 안드로이드에서는 **`java.io.IOException`인가**다 — 엔진 코드가 공용으로 옮겨 오기 전에 잡던 그 타입이고, 예외 객체도
 * 그대로 다시 던지므로 위의 호출자가 보던 예외가 같다. iOS에서는 앱 안 엔진의 파이프 읽기 · 쓰기 실패.
 * `expect class`(typealias)가 아니라 함수인 이유는 `SharedLock`과 같다 — expect/actual **클래스**는 아직 Beta라 경고가 붙는다(KT-61573).
 */
internal expect fun Throwable.isEnginePipeBroken(): Boolean

/**
 * 파이프 왕복(블로킹 쓰기 · 읽기)이 도는 풀 — 어댑터의 `pipeIo`. 안드로이드는 `Dispatchers.IO`(예전 그대로).
 * iOS에는 `Dispatchers.IO`가 없다 — 블로킹을 감당하는 고정 풀을 따로 둔다(`:shared`의 `engineIoDispatcher`와 같은 이유).
 */
internal expect val enginePipeIoDispatcher: CoroutineDispatcher

/**
 * 엔진 프로세스의 기동 · 폐기 한 줄([EngineProcessEvents.StdErr]). 안드로이드는 `System.err`(로그캣 `System.err` 태그 —
 * `adb logcat -s System.err | grep KataGoProcess`), iOS는 표준 오류.
 */
internal expect fun engineDiagnosticLine(line: String)
