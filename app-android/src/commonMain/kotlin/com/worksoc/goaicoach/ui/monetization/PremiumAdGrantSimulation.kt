package com.worksoc.goaicoach.ui.monetization

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.premium.app.PremiumAdGrantRunRequest
import com.worksoc.goaicoach.application.premium.app.runPremiumAdGrantApplication
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.application.time.currentEpochMillis

// `PremiumPurchaseGlue.kt`에서 옮겼다(백로그 #294) — 결제 SDK를 모르는 순수 함수라 공용이다. 시계만 `currentEpochMillis()`다.

/**
 * **광고를 본 것으로 상정해** 프리미엄 보상 루틴을 그대로 태운다 — 개발자 테스트 2차 전용
 * (백로그 #78).
 *
 * ## 왜 상태를 직접 쓰지 않는가
 * `PremiumState.adGranted(...)`를 여기서 만들어 저장하면 **테스트하려던 그 루틴을 우회한다.**
 * 이 버튼의 목적이 둘이기 때문이다 — ⓐ *광고 보상이 실제로 잘 들어오는지* 확인하고,
 * ⓑ *프리미엄 1시간 활성화·만료* 자체를 확인하는 것. 그래서 [performPremiumAdGrant]와
 * **광고를 띄우는 한 걸음만 다르고** 나머지(상태 전이·진단 로그)는 완전히 같은 경로를 쓴다.
 *
 * ## ✅ 부수 이득 — 실기에서 실제 광고를 밟지 않아도 된다
 * `release`만 `local.properties`의 **실제 AdMob 단위**를 쓰므로, 개발자가 자기 빌드에서 광고를
 * 보거나 누르면 자기 노출·자기 클릭이라 **AdMob 정책 위반**이다(`GOOGLE_PLAY_LAUNCH_PLAN.md` §0 B-3이
 * "광고만 일부러 안 밟았다"고 적은 이유). 이 함수는 광고를 **띄우지 않고** 보상 경로만 밟는다.
 *
 * ⚠️ 그래도 이 버튼은 **debug 전용 2차**에만 둔다(#77) — 프리미엄을 무료로 찍어내는 경로다.
 */
internal fun simulatePremiumAdGrant(
    diagnosticEventLog: DiagnosticEventLogPort,
    // ⚠️ **실제 광고 경로와 같은 이유로 필요하다**(#158). 이 개발자 버튼은 *"광고를 띄우는 한
    // 걸음만 건너뛰고 보상 루틴은 그대로 탄다"* 는 것이 존재 이유라, 여기만 상태를 안 넘기면
    // **개발자 버튼으로만 구독이 강등되는** 더 나쁜 어긋남이 생긴다.
    currentState: PremiumState = PremiumState(),
): PremiumState? {
    val result = runPremiumAdGrantApplication(
        PremiumAdGrantRunRequest(
            // 콘솔 보상값은 앱이 쓰지 않는다(항상 1시간) — 실제 콜백에서도 비어 올 수 있다.
            outcome = AdRewardOutcome.RewardEarned(),
            nowMillis = currentEpochMillis(),
            currentState = currentState,
        ),
    )
    diagnosticEventLog.append(result.diagnosticEvent)
    return result.nextState
}
