package com.worksoc.goaicoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.application.preferences.completeFirstRun
import com.worksoc.goaicoach.persistence.GuideProgressStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore

/**
 * 첫 실행을 **앱 화면 전체보다 바깥에서** 끝내는 문(백로그 #140 — 예전의 `LandingGate`, #51).
 *
 * #140이 랜딩 화면(실력·계가 방식 질문)을 없앴다(2026-09-11 사용자 피드백). 그래서 이 문은
 * **아무것도 그리지 않는다** — 첫 실행이면 "봤다"를 저장하고 첫돌이 가이드를 무장한 뒤 곧바로
 * [content]를 연다. 설정은 쓰지 않는다: 신규 설치 기본값이 이미 첫돌이·호선·집 계가다
 * (`completeFirstRun`의 KDoc).
 *
 * ## ⚠️ 이 위치가 그대로 핵심이다 — 화면 **밖**, 그리고 [content]보다 **먼저**
 * `GoCoachScreen`은 컴포지션되는 순간 `preferencesStore.load()`로 게임 상태를 세우고, 자동저장
 * `LaunchedEffect`가 첫 컴포지션에서 곧바로 그 값을 되쓴다. 첫 실행 처리가 그보다 늦으면 자동저장이
 * `hasSeenOnboarding = false`를 되써 **다음 실행에 또 첫 실행이 된다.** 그래서 [content]를 부르기
 * 전에 컴포지션 안에서 동기적으로 끝낸다 — #51이 실기에서 겪은 순서 문제와 같은 모양이다
 * (그때는 랜딩의 답이 자동저장에 덮여 아무것도 반영되지 않았다).
 *
 * ## ⚠️ 가이드 무장은 여기 한 곳뿐이다(백로그 #128)
 * 이미 첫 실행을 지난 사용자에게는 이 처리가 다시 돌지 않으므로 자동 재생이 무장되지 않는다 —
 * 그 성질이 공짜로 얻어지는 것이 이 자리를 고른 이유다(`GuideProgress.armed`).
 */
@Composable
internal fun FirstRunGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // ⚠️ `remember`의 초기화식에서 **한 번만** 한다. 멱등이라(플래그를 보고 건너뛴다) 컴포지션이
    // 버려져 다시 돌아도 안전하다. `LaunchedEffect`·`SideEffect`로 옮기지 말 것 — 둘 다 컴포지션
    // **뒤에** 돌아서 위 순서가 깨진다.
    remember(context) {
        val store = UserPreferencesStore(context)
        val current = store.load()
        if (!current.hasSeenOnboarding) {
            store.save(completeFirstRun(current))
            GuideProgressStore(context).arm()
        }
    }
    content()
}
