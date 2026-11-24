package com.worksoc.goaicoach.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.worksoc.goaicoach.application.attendance.utcDayIndex
import com.worksoc.goaicoach.application.prompt.recordCompletedGameForStoreReview
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.foundation.rememberStoreReviewRequester
import kotlinx.coroutines.delay

/**
 * 대국 완주 뒤 판정 결과 팝업이 닫힌 자연스러운 순간에 스토어 인앱 리뷰를 요청한다.
 *
 * - 상태 훅을 `GoCoachApp`에 추가하지 않기 위해 독립 호스트로 분리한다.
 * - [lastRecordedJudgementKey]를 프로세스 안에서 기억해, 복기 화면에 갔다가 돌아오거나
 *   재구성되어도 같은 판을 두 번 세지 않는다.
 * - 다른 다이얼로그(벤치마크 · 기력 변동/승급 팝업)가 열려 있는 동안에는 리뷰 창이 겹치지 않게 미룬다.
 */
@Composable
internal fun StoreReviewPromptHost(
    completedJudgementKey: String?,
    isBlockedByDialog: Boolean,
) {
    if (completedJudgementKey == null || isBlockedByDialog) return
    val store = LocalAppPlatform.current.stores.storeReview
    val requester = rememberStoreReviewRequester()
    LaunchedEffect(completedJudgementKey) {
        if (StoreReviewSessionGuard.markHandledIfNew(completedJudgementKey).not()) return@LaunchedEffect
        val today = utcDayIndex(currentEpochMillis())
        val evaluation = recordCompletedGameForStoreReview(progress = store.load(), today = today)
        store.save(evaluation.nextProgress)
        if (evaluation.shouldRequestReview) {
            delay(StoreReviewSoftDelayMillis)
            requester.requestReview()
        }
    }
}

private object StoreReviewSessionGuard {
    private var lastHandledKey: String? = null

    fun markHandledIfNew(key: String): Boolean {
        if (lastHandledKey == key) return false
        lastHandledKey = key
        return true
    }
}

/** 판정 팝업 닫힘 애니메이션 직후 부드럽게 리뷰 시트를 띄우기 위한 짧은 여백. */
private const val StoreReviewSoftDelayMillis = 650L
