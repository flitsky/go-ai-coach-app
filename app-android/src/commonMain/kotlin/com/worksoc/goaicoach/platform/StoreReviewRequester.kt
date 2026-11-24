package com.worksoc.goaicoach.platform

/**
 * 스토어 인앱 리뷰 요청 어댑터(Android Play In-App Review / iOS SKStoreReviewController).
 *
 * 실패나 Play 서비스 미설치 기기에서도 예외를 던지지 않고 조용히 끝난다.
 */
fun interface StoreReviewRequester {
    suspend fun requestReview()
}
