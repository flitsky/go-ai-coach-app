package com.worksoc.goaicoach.application.session

/**
 * AI 차례의 탐색이 **시간 초과로 끝난** 자리(refactor backlog #74). 사용자가 「한 번 더 기다리기」/「엔진 다시
 * 시작하기」를 고를 때까지 같은 국면의 조용한 재시도를 막는다.
 *
 * 세대·수순 길이에 묶여 있으므로 무르기·새 대국·이어하기·나가기(모두 세대를 올린다)가 **저절로** 푼다 —
 * 그 경로마다 지우는 코드를 따로 두지 않는다.
 */
data class AutoAiTurnTimeout(
    val sessionGeneration: Long,
    val moveCount: Int,
)

data class AutoAiTurnUiState(
    val isPending: Boolean = false,
    val timedOut: AutoAiTurnTimeout? = null,
) {
    fun markScheduled(): AutoAiTurnUiState =
        copy(isPending = true)

    fun clearPending(): AutoAiTurnUiState =
        copy(isPending = false)

    fun markTimedOut(timeout: AutoAiTurnTimeout): AutoAiTurnUiState =
        copy(timedOut = timeout)

    fun clearTimedOut(): AutoAiTurnUiState =
        copy(timedOut = null)

    /** 지금 이 국면(세대·수순 길이)에서 시간 초과 뒤 사용자의 선택을 기다리는 중인가. */
    fun isAwaitingTimeoutChoice(
        sessionGeneration: Long,
        moveCount: Int,
    ): Boolean =
        timedOut == AutoAiTurnTimeout(sessionGeneration = sessionGeneration, moveCount = moveCount)
}

data class AutoAiTurnFailureDisplayPlan(
    val engineMessage: String,
    val candidateText: String,
)
