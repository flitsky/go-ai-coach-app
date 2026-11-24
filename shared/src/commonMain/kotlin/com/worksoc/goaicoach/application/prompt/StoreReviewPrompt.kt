package com.worksoc.goaicoach.application.prompt

/**
 * 인앱 스토어 리뷰(Google Play In-App Review / iOS SKStoreReviewController) 요청 정책.
 *
 * - 첫 판 직후에 바로 묻지 않고, 최소 [StoreReviewMinCompletedGames]판 이상 대국을 완주하고
 *   판정 결과를 확인한 사용자에게만 조용히 요청한다.
 * - 한 번 요청하면 [StoreReviewCooldownDays]일 동안 다시 묻지 않으며, 기기당 누적
 *   [StoreReviewMaxPrompts]회를 넘지 않는다(Google Play 쿼터 및 UX 보호).
 * - 시계를 되감아 `today < lastPromptDay`가 되어도 쿨다운은 통과하지 않는다.
 */
const val StoreReviewMinCompletedGames: Int = 2
const val StoreReviewCooldownDays: Long = 90L
const val StoreReviewMaxPrompts: Int = 3

/**
 * 스토어 리뷰 요청 진행도 — 기기에만 저장한다.
 *
 * @property completedGames 판정 결과를 확인하며 완주한 대국 누적 횟수.
 * @property promptCount 인앱 리뷰 API를 실제로 호출한 누적 횟수.
 * @property lastPromptDay 마지막으로 리뷰를 요청한 UTC 하루 번호(`utcDayIndex`). 한 번도 없으면 `null`.
 */
data class StoreReviewProgress(
    val completedGames: Int = 0,
    val promptCount: Int = 0,
    val lastPromptDay: Long? = null,
)

/** 스토어 리뷰 진행도 저장 포트(4계층 어댑터: Android SharedPreferences / iOS NSUserDefaults). */
interface StoreReviewStorePort {
    fun load(): StoreReviewProgress

    fun save(progress: StoreReviewProgress)
}

/** 대국 완주 1건을 반영한 평가 결과. */
data class StoreReviewEvaluation(
    val shouldRequestReview: Boolean,
    val nextProgress: StoreReviewProgress,
)

/**
 * 대국 완주 1건을 누적하고, 지금 인앱 리뷰를 요청할 조건인지 순수하게 판정한다.
 *
 * Google Play In-App Review API는 사용자가 실제로 별점을 남겼는지 돌려주지 않으므로,
 * 요청 조건을 충족하면 즉시 [StoreReviewProgress.promptCount]와 [StoreReviewProgress.lastPromptDay]를
 * 갱신해 다음 [StoreReviewCooldownDays]일간 중복 호출을 막는다.
 */
fun recordCompletedGameForStoreReview(
    progress: StoreReviewProgress,
    today: Long,
): StoreReviewEvaluation {
    val nextCompleted = progress.completedGames.coerceAtLeast(0) + 1
    val safePromptCount = progress.promptCount.coerceAtLeast(0)
    val withinPromptCap = safePromptCount < StoreReviewMaxPrompts
    val cooldownElapsed = progress.lastPromptDay == null || (today - progress.lastPromptDay) >= StoreReviewCooldownDays
    val shouldRequest = nextCompleted >= StoreReviewMinCompletedGames && withinPromptCap && cooldownElapsed

    val nextProgress = if (shouldRequest) {
        StoreReviewProgress(
            completedGames = nextCompleted,
            promptCount = safePromptCount + 1,
            lastPromptDay = today,
        )
    } else {
        progress.copy(completedGames = nextCompleted, promptCount = safePromptCount)
    }
    return StoreReviewEvaluation(shouldRequestReview = shouldRequest, nextProgress = nextProgress)
}
