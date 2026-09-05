package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.shared.EngineMode

/**
 * 엔진의 세 상태(백로그 「핵심 동작 기조」 1ⓒ, 2026-09-05 사용자 정리).
 *
 * ⚠️ **`Boolean` 하나로는 이 셋을 표현할 수 없다.** 예전에는 `isEngineReady` 하나뿐이라
 * *"아직"* 과 *"안 된다"* 가 같은 `false`로 뭉개졌고, 그 결과 **양쪽 실수가 다 났다** —
 * 아직일 뿐인데 오류처럼 다루거나, 정말 실패했는데 조용히 넘어가거나.
 */
enum class EngineAvailability {
    /** 아직 준비 중. **정상이다** — 시간이 지나면 저절로 [Ready]가 된다. 문제시하지 말 것. */
    Preparing,

    /** 쓸 수 있다. */
    Ready,

    /**
     * 끝내 뜨지 못했다. **시간이 해결해 주지 않는다.** 사용자에게 적극적으로 알려야 한다.
     */
    Unavailable,
}

/**
 * ⚠️ **`when`을 exhaustive로 둔 것이 이 함수의 요점이다.** [EngineMode]에 새 값이 생기면
 * 컴파일러가 여기서 멈춰 *"이 백엔드는 셋 중 무엇인가"* 를 묻는다. `else ->`를 넣는 순간
 * 새 백엔드가 조용히 [Ready]로 흘러가므로 **넣지 말 것.**
 */
fun engineAvailabilityFor(mode: EngineMode): EngineAvailability =
    when (mode) {
        // "아직 모른다" 는 곧 "아직" 이다 — 부트스트랩이 끝나면 다른 값으로 바뀐다.
        EngineMode.Unknown -> EngineAvailability.Preparing

        // ⚠️ 스텁은 **고장이다.** 수를 두긴 하지만 그 대국도 분석도 믿을 수 없다.
        // `createEngineBootstrap`이 네이티브 라이브러리나 모델을 못 찾으면 여기로 강등된다.
        EngineMode.Stub -> EngineAvailability.Unavailable

        EngineMode.LocalProcess,
        EngineMode.JniNative,
        EngineMode.RemoteServer,
        -> EngineAvailability.Ready
    }
