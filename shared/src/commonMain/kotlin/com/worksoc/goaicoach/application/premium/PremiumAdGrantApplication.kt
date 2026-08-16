package com.worksoc.goaicoach.application.premium

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

data class PremiumAdGrantRunRequest(
    val outcome: AdRewardOutcome,
    val nowMillis: Long,
)

/**
 * [nextState]가 `null`이면 상태를 바꾸지 말라는 뜻이다(일반 모드 유지) — 호출부가 이 경우
 * `premiumState`/저장소 쓰기를 건너뛰도록 구분하려고 별도 래퍼 대신 nullable로 표현했다.
 */
data class PremiumAdGrantRunResult(
    val nextState: PremiumState?,
    val diagnosticEvent: DiagnosticEvent,
)

/**
 * 6계층(Session & Continuity)/App Service 경계 — 광고 시청 결과([AdRewardOutcome])를 프리미엄
 * 상태 전이 + 진단 로그로 변환하는 순수 함수. [AdRewardOutcome.RewardEarned]일 때만
 * [PremiumState.adGranted]로 전이하고, 그 외에는 상태를 바꾸지 않는다 — "광고 로드 실패/시청
 * 중단 시 일반 모드 유지"는 호출부가 별도로 신경 쓸 필요 없이 이 함수의 반환값(`nextState == null`)
 * 만으로 보장된다. 실패 사유를 담은 진단 이벤트는 항상(성공/실패 모두) 함께 반환해, 폴백을
 * 조용히 삼키지 않는다.
 */
fun runPremiumAdGrantApplication(request: PremiumAdGrantRunRequest): PremiumAdGrantRunResult =
    when (val outcome = request.outcome) {
        AdRewardOutcome.RewardEarned -> {
            val nextState = PremiumState.adGranted(nowMillis = request.nowMillis)
            PremiumAdGrantRunResult(
                nextState = nextState,
                diagnosticEvent = DiagnosticEvent(
                    severity = DiagnosticSeverity.Info,
                    code = "premium_ad_grant_activated",
                    message = "Premium ad grant activated.",
                    context = mapOf("adGrantStartedAtMillis" to nextState.adGrantStartedAtMillis.toString()),
                ),
            )
        }

        is AdRewardOutcome.NotRewarded -> PremiumAdGrantRunResult(
            nextState = null,
            diagnosticEvent = DiagnosticEvent(
                severity = DiagnosticSeverity.Warning,
                code = "premium_ad_grant_not_rewarded",
                message = "Rewarded ad did not grant premium; general mode continues.",
                context = mapOf(
                    "reason" to outcome.reason.name,
                    "detail" to (outcome.detail ?: ""),
                ),
            ),
        )
    }
