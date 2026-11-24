package com.worksoc.goaicoach.platform

import android.app.Activity
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google Play In-App Review 흐름을 실행한다.
 *
 * ⚠️ `review-ktx`를 쓰지 않는 것은 의도다 — 이 저장소의 Billing · AdMob · AppUpdate · AssetDelivery와
 * 동일하게 콜백 기반 `Task`를 `suspendCancellableCoroutine`으로 직접 감싼다.
 * Play 스토어 미설치 · 오프라인 · 쿼터 초과 등의 모든 실패는 조용히 무시해 사용자 경험을 해치지 않는다.
 */
internal suspend fun requestPlayInAppReview(activity: Activity) {
    if (activity.isFinishing || activity.isDestroyed) return
    runCatching {
        val manager = ReviewManagerFactory.create(activity.applicationContext)
        val reviewInfo = manager.requestReviewInfoOrNull() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        manager.launchReviewFlowAndAwait(activity, reviewInfo)
    }
}

private suspend fun ReviewManager.requestReviewInfoOrNull(): ReviewInfo? =
    suspendCancellableCoroutine { continuation ->
        requestReviewFlow()
            .addOnCompleteListener { task ->
                if (continuation.isActive) {
                    continuation.resume(if (task.isSuccessful) task.result else null)
                }
            }
    }

private suspend fun ReviewManager.launchReviewFlowAndAwait(activity: Activity, reviewInfo: ReviewInfo) =
    suspendCancellableCoroutine { continuation ->
        launchReviewFlow(activity, reviewInfo)
            .addOnCompleteListener {
                if (continuation.isActive) continuation.resume(Unit)
            }
    }
