package com.worksoc.goaicoach.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.worksoc.goaicoach.application.score.ProvisionalScoreRefineAttempt
import com.worksoc.goaicoach.application.score.ProvisionalScoreRefineInput
import com.worksoc.goaicoach.application.score.shouldRefineProvisionalScore

/**
 * 형세 보기를 **켜 둔 채** 급수 캐릭터와 두는 동안, 화면의 형세를 주 모델 값으로 유지한다(백로그 #215 보강 ②).
 * 언제 다시 재는지는 [shouldRefineProvisionalScore]가 정한다 — 여기는 그 판단을 상태 변화에 걸어 둘 뿐이다.
 *
 * `GoCoachApp.kt`의 상태 훅 예산이 꽉 차 있어 셸에 `LaunchedEffect`를 새로 두지 않고 이 컴포저블로 감쌌다 —
 * 셸에서는 호출 한 줄만 보인다(`OneShotAnalysisAutoClear`와 같은 이유).
 *
 * @param isEngineBusyNow 효과가 **도는 순간**의 엔진 상태. [input]의 값은 컴포지션 때의 것이라, 같은 컴포지션에서 먼저 뜬
 *   효과(켜 둔 추천 수의 자동 분석)가 방금 엔진을 잡았으면 낡았다 — 그대로 요청하면 "엔진이 바쁘다"는 문구만 뜬다.
 *   그 분석이 끝나면 [input]이 바뀌어 이 효과가 다시 돈다.
 * @param refine 지금 국면의 형세를 다시 요청한다 — 형세 보기를 누른 것과 같은 길이라 주 모델이 답한다.
 */
@Composable
internal fun ProvisionalScoreRefineEffect(
    input: ProvisionalScoreRefineInput,
    isEngineBusyNow: () -> Boolean,
    refine: () -> Unit,
) {
    var lastAttempt by remember { mutableStateOf<ProvisionalScoreRefineAttempt?>(null) }
    LaunchedEffect(input) {
        if (!shouldRefineProvisionalScore(input.copy(isEngineBusy = isEngineBusyNow()), lastAttempt)) return@LaunchedEffect
        lastAttempt = input.attempt
        refine()
    }
}
