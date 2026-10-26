package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode

/** 엔진 부트스트랩의 결과 — 띄운 엔진과 그 정체. 만드는 일은 플랫폼마다 다르다(안드로이드 `EngineBootstrap.kt`의 `createEngineBootstrap`). */
data class EngineBootstrap(
    val coreApi: EngineCoreApi,
    val mode: EngineMode,
    val displayName: String,
    val diagnostic: String,
)
