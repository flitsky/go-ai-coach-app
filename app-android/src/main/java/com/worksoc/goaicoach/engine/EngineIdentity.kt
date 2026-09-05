package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.shared.EngineMode

/**
 * 부트스트랩이 끝나야 알 수 있는 **엔진의 정체**(백로그 #101 ③단계).
 *
 * ## 왜 셋을 묶었나
 * `mode`·`name`·`diagnostic`은 서로 다른 세 파라미터였는데, **셋 다 같은 순간에 같은 곳에서**
 * (= `createEngineBootstrap`이 끝날 때) 온다. 준비 화면이 사라지면 셋 다 *"아직 모름"* 구간을
 * 함께 지나므로, 하나로 묶어 **한 번에 묻는 편**이 배선도 규칙도 단순하다.
 *
 * ## ⚠️ 값이 아니라 공급자로 넘긴다
 * `GoCoachApp`은 이것을 `() -> EngineIdentity`로 받는다. 이유는 [Unresolved]에 적었다.
 */
data class EngineIdentity(
    val mode: EngineMode,
    val name: String,
    val diagnostic: String,
) {
    companion object {
        /**
         * 아직 부트스트랩이 끝나지 않은 구간의 답.
         *
         * ⚠️ **예측하지 않는다** — 2026-09-05 사용자 결정. 여기서 *"어차피 로컬 프로세스겠지"* 로
         * 찍으면 스텁 폴백 기기에서 **진단 리포트가 거짓말을 한다**(`engineProfile=…/LocalProcess`).
         * [EngineMode.Unknown]이 존재하는 이유가 정확히 이것이다(`EngineModels.kt`의 머리말).
         *
         * ⚠️ [name]이 **빈 문자열이면 안 된다** — AI 좌석 라벨이 이 값을 **그대로** 쓰므로
         * 빈 값이면 좌석이 **이름 없이** 그려진다. `"AI"`는 4개 언어에서 모두 읽히고,
         * 앱 이름(바둑 AI)과도 맞는다.
         *
         * ⚠️ **이 사유는 2026-09-05까지 거짓이었다**(백로그 #109). 그때까지 좌석은
         * `aiEngine.label.ifBlank { engineName }`을 썼는데 그 라벨이 **절대 비지 않아**
         * 여기 값이 **한 번도 쓰이지 않았다.** #109가 그 열거형을 걷어내면서 비로소 참이 됐다.
         */
        val Unresolved: EngineIdentity = EngineIdentity(
            mode = EngineMode.Unknown,
            name = "AI",
            diagnostic = "Engine bootstrap in progress.",
        )
    }
}

fun EngineBootstrap.identity(): EngineIdentity =
    EngineIdentity(
        mode = mode,
        name = displayName,
        diagnostic = diagnostic,
    )
