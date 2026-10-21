package com.worksoc.goaicoach.persistence

import android.content.Context
import androidx.core.content.edit

/**
 * 「기본 사활」에서 **스스로 푼 문제**의 기록(백로그 #184) — 문제 id의 집합 하나다. 「정답 보기」로 본 것은 넣지 않는다.
 *
 * 지웠다 다시 깔아도 이어지는 길은 안드로이드 자동 백업이다 — `shared_prefs`는 제외 규칙에 걸리지 않아 따로 적지 않아도 실린다
 * (`DailyQuoteStore`와 같다). 복원이 없으면 빈 채로 시작한다.
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2). 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다
 * (함정 55). **정식 릴리즈 초기화(함정 6)는 건드리지 않는다** — 푼 기록은 권한이 아니라 학습의 진행도다(테스트 기간에 푼 문제를 출시에서 다시
 * 풀게 할 이유가 없다). `LayeringContractTest.releaseResetClearsEntitlementsOnlyAndSparesUserContent`가 이 판단을 못박는다.
 * ⚠️ 문제 id는 저장값이다 — `lifeAndDeathProblems`의 id를 바꾸면 그 문제를 푼 기록이 끊긴다(함정 1과 같은 결).
 */
internal class LifeAndDeathProgressStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun solved(): Set<String> =
        prefs.getString(SolvedKey, null)?.split(Separator)?.filter { it.isNotBlank() }?.toSet().orEmpty()

    /** [problemId]를 푼 것으로 남기고, 남긴 뒤의 집합을 돌려준다(화면이 그것으로 다시 그린다 — 함정 13). */
    fun markSolved(problemId: String): Set<String> {
        val next = solved() + problemId
        prefs.edit { putString(SolvedKey, next.sorted().joinToString(Separator)) }
        return next
    }

    private companion object {
        const val PrefsName = "go_ai_coach_life_and_death"
        const val SolvedKey = "solved"
        const val Separator = ","
    }
}
