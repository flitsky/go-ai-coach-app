package com.worksoc.goaicoach.application.session

/**
 * AI 차례의 탐색이 **시간 초과로 끝난** 자리(refactor backlog #74). 사용자가 「한 번 더 기다리기」/「엔진 다시
 * 시작하기」를 고를 때까지 같은 국면의 조용한 재시도를 막는다.
 *
 * 같은 국면에서 **진짜 실패**가 [AutoAiTurnFailureChoiceThreshold]번 잇달아 나도 이 표시를 단다(refactor backlog #109) —
 * 같은 선택 팝업(상태 B)이 뜨고, 고른 뒤의 두 길도 같다.
 *
 * 세대·수순 길이에 묶여 있으므로 무르기·새 대국·이어하기·나가기(모두 세대를 올린다)가 **저절로** 푼다 —
 * 그 경로마다 지우는 코드를 따로 두지 않는다.
 */
data class AutoAiTurnTimeout(
    val sessionGeneration: Long,
    val moveCount: Int,
)

/**
 * 같은 국면에서 진짜 실패(시간 초과도 취소도 아니다)가 몇 번이면 조용한 재시도를 멈추고 선택 팝업으로 넘기는가
 * (refactor backlog #109).
 *
 * **2인 이유** — 첫 실패 뒤의 조용한 재시도 한 번은 스스로 낫는 길이다. 프로세스가 차례 사이에 죽었으면(저메모리 킬 등)
 * 다음 차례의 기동이 죽은 핸들을 거두고 새로 띄운다(#14). 그 새 프로세스에서도 **같은 국면이 또** 실패하면 한 번 더
 * 돌려서 나을 일이 아니다 — 사용자에게 묻는다. 1이면 스스로 나을 실패에도 팝업이 뜨고, 3 이상이면 멈춘 판을 그만큼
 * 더 조용히 보여 준다. 한 번의 실패에는 이미 분석과 `genMove` 폴백 두 번의 시도가 들어 있다.
 */
const val AutoAiTurnFailureChoiceThreshold: Int = 2

/** 한 국면에서 잇달아 난 진짜 실패의 수(refactor backlog #109). 국면(세대·수순 길이)이 바뀌면 새로 센다. */
data class AutoAiTurnFailureStreak(
    val position: AutoAiTurnTimeout,
    val count: Int,
)

data class AutoAiTurnUiState(
    val isPending: Boolean = false,
    val timedOut: AutoAiTurnTimeout? = null,
    val failureStreak: AutoAiTurnFailureStreak? = null,
    /**
     * 기다리는 사이 앱이 멈춰(백그라운드·동결) 끊긴 시간 초과를 **팝업 없이 한 번 다시 요청한** 국면(backlog #204).
     * 같은 국면에서 또 시간 초과가 나면 이유와 상관없이 지금처럼 선택 팝업이다 — 조용한 재시도는 국면마다 한 번뿐이다.
     * 국면(세대·수순 길이)이 바뀌면 저절로 효력을 잃고, 사용자가 팝업에서 고르면([clearTimedOut]) 지운다.
     */
    val interruptedRetry: AutoAiTurnTimeout? = null,
    /**
     * AI가 **기권을 제안해** 사용자의 답(받아들이기 / 계속 두기)을 기다리는 국면(백로그 #213, 사용자 2026-10-07).
     * 그 국면은 **AI가 둘 차례**다 — 받아들이면 AI가 제 차례에 기권하고(규칙상 기권은 제 차례에만 둔다), 거절하면 AI가 그대로 둔다.
     * 표시가 맞는 동안 AI 차례를 요청하지 않는다(`buildAutoAiTurnRequestPlan`) — 사용자가 고르기 전에 AI가 두어 버리지 않게.
     * 세대·수순 길이가 바뀌면(무르기·새 대국) 저절로 효력을 잃는다.
     */
    val resignationOffer: AutoAiTurnTimeout? = null,
    /** 기권 제안에 사용자가 이미 답한 대국의 세대 — **한 판에 한 번만** 묻는다(거절한 판에서 수마다 다시 묻지 않는다). */
    val resignationAnsweredGeneration: Long? = null,
) {
    fun markScheduled(): AutoAiTurnUiState =
        copy(isPending = true)

    fun clearPending(): AutoAiTurnUiState =
        copy(isPending = false)

    fun markTimedOut(timeout: AutoAiTurnTimeout): AutoAiTurnUiState =
        copy(timedOut = timeout)

    /** 사용자가 팝업에서 골랐다 — 표시를 지우고, 그 국면의 조용한 재시도(backlog #204)도 다시 쓸 수 있게 한다. */
    fun clearTimedOut(): AutoAiTurnUiState =
        copy(timedOut = null, interruptedRetry = null)

    /** [position]의 시간 초과가 기다리는 사이의 멈춤 때문이라 조용히 한 번 다시 요청한다(backlog #204) — 그 한 번을 쓴다. */
    fun markInterruptedRetry(position: AutoAiTurnTimeout): AutoAiTurnUiState =
        copy(interruptedRetry = position)

    /** [position]에서 조용한 재시도(backlog #204)를 이미 썼는가. */
    fun hasSpentInterruptedRetry(position: AutoAiTurnTimeout): Boolean =
        interruptedRetry == position

    /**
     * [position]에서 AI 차례가 진짜로 실패했다(refactor backlog #109). 같은 국면에서 [AutoAiTurnFailureChoiceThreshold]번째면
     * 시간 초과와 같은 표시를 달아 조용한 재시도를 막는다 — 선택 팝업이 뜬다.
     *
     * 사용자가 고른 뒤([clearTimedOut])에도 세던 수는 남는다 — 그 국면에서 또 실패하면 곧바로 다시 묻는다(사용자가 이미
     * 본 문제를 다시 조용히 돌리지 않는다). 국면이 바뀌면(AI가 뒀다, 무르기·새 대국 — 세대가 오른다) 처음부터 센다.
     */
    fun recordFailure(position: AutoAiTurnTimeout): AutoAiTurnUiState {
        val count = if (failureStreak?.position == position) failureStreak.count + 1 else 1
        val streak = AutoAiTurnFailureStreak(position = position, count = count)
        return if (count >= AutoAiTurnFailureChoiceThreshold) {
            copy(failureStreak = streak, timedOut = position)
        } else {
            copy(failureStreak = streak)
        }
    }

    /**
     * AI가 기권을 제안한다 — [position]은 사용자가 답할 국면, 곧 **AI의 다음 차례**다. 이 대국에서 이미 제안했거나 답을 받았으면
     * 그대로 둔다(한 판에 한 번).
     */
    fun offerResignation(position: AutoAiTurnTimeout): AutoAiTurnUiState =
        if (resignationAnsweredGeneration == position.sessionGeneration || resignationOffer?.sessionGeneration == position.sessionGeneration) {
            this
        } else {
            copy(resignationOffer = position)
        }

    /** 사용자가 기권 제안에 답했다 — 표시를 지우고, 이 대국([sessionGeneration])에서는 다시 묻지 않는다. */
    fun answerResignationOffer(sessionGeneration: Long): AutoAiTurnUiState =
        copy(resignationOffer = null, resignationAnsweredGeneration = sessionGeneration)

    /** 지금 이 국면(세대·수순 길이)에서 기권 제안에 대한 사용자의 답을 기다리는 중인가. */
    fun isAwaitingResignationChoice(
        sessionGeneration: Long,
        moveCount: Int,
    ): Boolean =
        resignationOffer == AutoAiTurnTimeout(sessionGeneration = sessionGeneration, moveCount = moveCount)

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
