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
) {
    fun markScheduled(): AutoAiTurnUiState =
        copy(isPending = true)

    fun clearPending(): AutoAiTurnUiState =
        copy(isPending = false)

    fun markTimedOut(timeout: AutoAiTurnTimeout): AutoAiTurnUiState =
        copy(timedOut = timeout)

    fun clearTimedOut(): AutoAiTurnUiState =
        copy(timedOut = null)

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
