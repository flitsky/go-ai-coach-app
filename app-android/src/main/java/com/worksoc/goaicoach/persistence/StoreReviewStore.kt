package com.worksoc.goaicoach.persistence

import android.content.Context
import androidx.core.content.edit
import com.worksoc.goaicoach.application.prompt.StoreReviewProgress
import com.worksoc.goaicoach.application.prompt.StoreReviewStorePort

/**
 * 인앱 스토어 리뷰 요청 진행도 저장소 — 판정은 `:shared`의 `StoreReviewPrompt.kt` 한 곳이다.
 *
 * 기기에만 남는 로컬 설정값이며 앱 접두사(`go_ai_coach_`)를 따라 개발자 초기화가 함께 지운다.
 */
internal class StoreReviewStore(context: Context) : StoreReviewStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun load(): StoreReviewProgress = StoreReviewProgress(
        completedGames = prefs.getInt(CompletedGamesKey, 0),
        promptCount = prefs.getInt(PromptCountKey, 0),
        lastPromptDay = prefs.getLong(LastPromptDayKey, NoDay).takeIf { it != NoDay },
    )

    override fun save(progress: StoreReviewProgress) {
        prefs.edit {
            putInt(CompletedGamesKey, progress.completedGames)
            putInt(PromptCountKey, progress.promptCount)
            putLong(LastPromptDayKey, progress.lastPromptDay ?: NoDay)
        }
    }

    private companion object {
        const val PrefsName = "go_ai_coach_store_review"
        const val CompletedGamesKey = "completed_games"
        const val PromptCountKey = "prompt_count"
        const val LastPromptDayKey = "last_prompt_day"
        const val NoDay = -1L
    }
}
