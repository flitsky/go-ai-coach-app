package com.worksoc.goaicoach.engine

/**
 * 엔진 받기 · 준비의 플랫폼 이음매(백로그 #294 — 이식 계획 S15).
 *
 * - [isStoreInstallationErrorCode]: 스토어 밖 설치처럼 「스토어에서 설치」가 답인 영구 실패인가 — 안드로이드는 Play 에셋 팩
 *   오류 코드로 가른다(`EngineBootstrap.kt`). iOS는 모델을 앱에 싣거나 따로 받으므로 그런 실패가 없다.
 * - [engineSetupLog]: 받기 경로의 로그 한 줄 — 안드로이드는 `EnginePad` 태그(`adb logcat -s EnginePad`).
 */
internal expect fun isStoreInstallationErrorCode(errorCode: Int?): Boolean

internal expect fun engineSetupLog(message: String, error: Throwable? = null)
