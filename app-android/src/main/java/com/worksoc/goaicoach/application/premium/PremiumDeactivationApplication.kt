package com.worksoc.goaicoach.application.premium

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

/**
 * 프리미엄이 비활성 상태가 될 때 남길 진단 로그를 판정하는 순수 함수. [previousState]의
 * source가 [PremiumSource.None]이면 애초에 활성화된 적이 없으므로(예: 앱 최초 실행) null을
 * 반환해 로그하지 않는다 — 실제로 활성 상태였다가 꺼진 경우(만료/명시적 비활성화)만
 * 이벤트를 만든다.
 */
internal fun buildPremiumDeactivatedDiagnosticEvent(
    previousState: PremiumState,
    nowMillis: Long,
): DiagnosticEvent? {
    if (previousState.source == PremiumSource.None) return null
    return DiagnosticEvent(
        severity = DiagnosticSeverity.Info,
        code = "premium_deactivated",
        message = "Premium is no longer active.",
        context = mapOf(
            "source" to previousState.source.name,
            "adGrantStartedAtMillis" to (previousState.adGrantStartedAtMillis?.toString() ?: "null"),
            "nowMillis" to nowMillis.toString(),
        ),
    )
}
