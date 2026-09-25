package com.worksoc.goaicoach.application.premium.app

import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.state.PremiumSource
import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

data class PremiumAdGrantRunRequest(
    val outcome: AdRewardOutcome,
    val nowMillis: Long,
    /**
     * 지금 로컬 상태 — **살아 있는 구독을 덮지 않기 위해 필요하다**(백로그 #158).
     *
     * ⚠️ [PremiumState.adGranted]는 **새 상태를 만든다.** 그대로 저장하면 `source`가
     * `Purchase` → `AdGrant`로 바뀌어, **구독자가 광고를 본 순간 구독이 1시간짜리로 강등된다.**
     * `saveMergingClaimedFeatures`는 `claimedFeatures`만 되살리므로 이 덮어쓰기를 막지 못한다.
     */
    val currentState: PremiumState = PremiumState(),
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
 * 6계층(Session & Continuity) — 광고 시청 결과([AdRewardOutcome])를 프리미엄
 * 상태 전이 + 진단 로그로 변환하는 순수 함수. [AdRewardOutcome.RewardEarned]일 때만
 * [PremiumState.adGranted]로 전이하고, 그 외에는 상태를 바꾸지 않는다 — "광고 로드 실패/시청
 * 중단 시 일반 모드 유지"는 호출부가 별도로 신경 쓸 필요 없이 이 함수의 반환값(`nextState == null`)
 * 만으로 보장된다. 실패 사유를 담은 진단 이벤트는 항상(성공/실패 모두) 함께 반환해, 폴백을
 * 조용히 삼키지 않는다.
 */
fun runPremiumAdGrantApplication(request: PremiumAdGrantRunRequest): PremiumAdGrantRunResult =
    when (val outcome = request.outcome) {
        is AdRewardOutcome.RewardEarned -> if (request.currentState.source == PremiumSource.Purchase) {
            // ⚠️ **구독이 살아 있으면 광고는 아무것도 바꾸지 않는다**(#158). 광고가 주는 것은
            // 구독이 이미 주는 것의 부분집합이라 얹을 것이 없고, 덮으면 **강등**이 된다.
            // (지금은 구독자에게 광고 버튼이 뜨지 않아 도달하기 어렵지만, 유예 기간처럼
            //  "구독인데 업셀이 보이는" 상태가 생기면 곧바로 도달한다.)
            PremiumAdGrantRunResult(
                nextState = null,
                diagnosticEvent = DiagnosticEvent(
                    severity = DiagnosticSeverity.Info,
                    code = "premium_ad_grant_ignored_active_subscription",
                    message = "Rewarded ad ignored; an active subscription already grants more.",
                    context = mapOf("consoleRewardType" to (outcome.type ?: "")),
                ),
            )
        } else {
            // ⚠️ **`claimedFeatures`를 이어 붙인다** — `adGranted`는 새 상태를 만들기 때문에
            // 그냥 두면 출석으로 받은 영구 클레임이 이 저장에서 사라진다(저장소 병합이 다시
            // 살려 주지만, 여기서 맞는 값을 만드는 편이 한 겹 덜 위험하다).
            val nextState = PremiumState.adGranted(nowMillis = request.nowMillis)
                .copy(claimedFeatures = request.currentState.claimedFeatures)
            PremiumAdGrantRunResult(
                nextState = nextState,
                diagnosticEvent = DiagnosticEvent(
                    severity = DiagnosticSeverity.Info,
                    code = "premium_ad_grant_activated",
                    message = "Premium ad grant activated.",
                    // SDK가 준 콘솔 보상값도 함께 남긴다 — 앱은 이 값으로 보상을 계산하지 않으므로
                    // (항상 1시간) 콘솔과 앱 해석이 어긋나도 동작으로는 드러나지 않는다. 로그에
                    // 남겨야 나중에 "콘솔에서 수량을 바꿨는데 왜 그대로냐"를 확인할 수 있다.
                    context = mapOf(
                        "adGrantStartedAtMillis" to nextState.adGrantStartedAtMillis.toString(),
                        "consoleRewardType" to (outcome.type ?: ""),
                        "consoleRewardAmount" to (outcome.amount?.toString() ?: ""),
                    ),
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
