package com.worksoc.goaicoach.application.safety

import com.worksoc.goaicoach.shared.SearchTimeLimit

/**
 * 안전 관리(레프리) 도메인 — AI 차례가 비정상적으로 오래 걸리는지 감지하는 순수 판정 로직.
 * 이전에 고친 KataGo 프로세스 stdin/stdout 레이스 컨디션과는 별개로, 나중에 다른 원인으로
 * 다시 AI 응답이 멎더라도 이 판정으로 감지할 수 있게 한다. 감지에만 집중하며, 자동 복구나
 * 사용자 팝업 여부는 이번 범위에 포함하지 않는다(호출부에서 나중에 선택).
 */

/**
 * 대국 설정의 AI 최대 응답 시간에 맞춰 와치독 한도를 계산한다.
 * - 응답 시간 제한이 설정돼 있으면 그 값의 1.2배 + 3초.
 * - 응답 시간 제한이 꺼져 있으면(무제한 탐색) 고정 60초.
 */
internal fun engineTurnWatchdogTimeoutMillisFor(searchTimeLimit: SearchTimeLimit): Long {
    val configuredMillis = searchTimeLimit.maximumMillis
    return if (configuredMillis != null) {
        (configuredMillis * 1.2).toLong() + 3_000L
    } else {
        60_000L
    }
}

/** AI 차례에서 [elapsedSinceTurnStartMillis]가 와치독 한도를 넘겼는지 판정한다. */
internal fun isEngineTurnWatchdogTriggered(
    isAiTurn: Boolean,
    elapsedSinceTurnStartMillis: Long,
    searchTimeLimit: SearchTimeLimit,
): Boolean =
    isAiTurn && elapsedSinceTurnStartMillis >= engineTurnWatchdogTimeoutMillisFor(searchTimeLimit)
