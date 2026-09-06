package com.worksoc.goaicoach.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * 4계층(External Integration) — 앱 기동 직후의 브랜드 모먼트(백로그 #125·#126).
 *
 * 무엇을 그리는지는 [SplashVariant]가 정하고, 지금 쓰는 것은 [SplashVariant.Current]다.
 * ⚠️ **후보 9종을 눈으로 고르는 중이다**(#126) — 개발자 모드 1차 섹션의 `1`~`9` 버튼이 각각을
 * 전체 화면으로 재생한다. 고르고 나면 [SplashVariant.Current]를 그것으로 박고 미리보기를
 * 남길지 지울지 판단한다.
 *
 * ## ⚠️ 이것은 #123의 대책이 아니다
 * 크래시가 나던 자리는 *무엇을 그리는가* 가 아니라 `installDecor`→`generateLayout`,
 * 즉 **창의 decor를 세우는 순간**이었다. 스플래시를 넣어도 그 경로는 똑같이 지난다.
 * 안정성을 준 것은 [MainActivity]의 **호출 순서**다(백로그 함정 35번).
 *
 * ## ⚠️ 이 시간으로 엔진이 준비되지는 않는다 — 로비 게이트를 지울 근거가 아니다
 * 최초 실행은 약 100MB 모델 압축 해제가 돌아 **몇 초**가 걸린다. `GameSetupLobby`의 엔진 준비
 * 게이트(`engineNotReadyToStart`, 백로그 #101 0단계)는 스플래시가 생겨도 **그대로 남아야 한다.**
 *
 * ## 왜 별도 Activity도, `core-splashscreen`도 아닌가
 * - ⚠️ **별도 `SplashActivity`는 창을 하나 더 세운다** — #123이 죽던 그 경로를 한 번 더 지난다.
 * - **시스템 스플래시(API 31+)는 아이콘 하나만 그린다.** 그림 5장을 거기서 표현할 방법이 없으므로
 *   `androidx.core:core-splashscreen`을 넣어도 얻는 것은 이음매뿐인데, 그 이음매는 테마의
 *   `android:windowBackground`를 이 화면의 배경과 같은 색으로 맞추면 사라진다.
 *
 * ## ⚠️ 글자를 넣지 않는다
 * 앱 이름을 여기 적는 순간 **4개 언어 × 함정 26번의 "그물 밖 자리"** 가 하나 더 생긴다.
 *
 * ⚠️ **화면 회전으로 다시 재생되면 안 된다** — [rememberSaveable]로 끝난 상태를 들고 간다.
 */
@Composable
internal fun AppSplash(variant: SplashVariant = SplashVariant.Current) {
    var finished by rememberSaveable { mutableStateOf(false) }
    if (finished) return
    SplashPlayer(variant = variant, onFinished = { finished = true })
}

/**
 * 후보 하나를 **한 번** 재생한다. 앱 기동([AppSplash])과 개발자 모드 미리보기가 **같은 재생기**를
 * 쓰므로, 미리보기에서 본 것이 곧 기동에서 보는 것이다 — 두 벌로 갈라 두면 고른 것과 실린 것이
 * 어긋난다.
 *
 * ⚠️ **터치를 반드시 먹어야 한다** — 기동에서는 아래 홈이 이미 컴포즈돼 있어서, 막지 않으면
 * 재생 중에 누른 것이 홈으로 새어 들어간다.
 */
@Composable
internal fun SplashPlayer(
    variant: SplashVariant,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(variant) { Animatable(0f) }
    LaunchedEffect(variant) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(variant.durationMillis, easing = LinearEasing))
        onFinished()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1f)
            .background(AppLightColorScheme.background)
            .pointerInput(variant) { detectTapGestures { onFinished() } },
        contentAlignment = Alignment.Center,
    ) {
        SplashScene(
            variant = variant,
            elapsedMillis = (progress.value * variant.durationMillis).toInt(),
        )
    }
}
