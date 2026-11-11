package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 움직임 토큰(백로그 #281) — Material 3 Expressive의 스프링을 **값 그대로** 옮겼다.
 *
 * 출처: 앱이 쓰는 Material3 1.4.0 jar의 `androidx.compose.material3.tokens.ExpressiveMotionTokens`(2026-10-23에 바이트코드로 읽었다).
 * 1.4.0에서는 `MotionScheme.expressive()`가 라이브러리 안에만 열려 있어(internal) 앱이 부를 수 없다 —
 * Expressive API가 정식이 되면(1.5.0) 이 값을 `MaterialTheme.motionScheme`으로 갈아 끼운다.
 *
 * - **Spatial**(자리 · 크기 · 모양): 감쇠가 1보다 작아 끝에서 살짝 튄다.
 * - Effects(색 · 투명도)는 튀지 않는 스프링이다 — 아직 쓰는 곳이 없어 옮기지 않았다(기본 1.0/1600 · 빠른 1.0/3800 · 느린 1.0/800).
 *
 * ⚠️ 시스템 설정 「애니메이션 배율」이 0이면 Compose가 이 스프링들도 곧바로 끝낸다 — 움직임을 끈 사람에게 따로 해 줄 것은 없다.
 */
internal object AppMotion {
    /** 페이지 넘기기 · 탭 알약처럼 화면 안에서 자리를 옮기는 것. */
    fun <T> defaultSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** 누를 때 카드 · 타일 · 단추의 모양과 크기 — 손가락에 바로 따라와야 한다. */
    fun <T> fastSpatial(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /**
     * 눈길을 끄는 반복 움직임(마지막 수 박동 · 「이전 대국 이어하기」 숨쉬기)이 도는 시간 — 지나면 제자리에서 멈춘다(사용자 2026-11-11).
     * 끝없이 돌면 iOS의 Compose가 **창 전체를 매 프레임** 다시 그린다 — 실측(iPhone 11 Pro Max): 가만히 둔 다시보기 화면에서 앱 CPU 45%,
     * 폰이 금방 뜨거워졌다. 안드로이드도 배터리를 그만큼 덜 쓴다. 3초는 스레드가 정했다.
     */
    const val AttentionMillis = 3_000

    /** 한 번 오가는 데 [roundTripMillis]인 움직임을 [AttentionMillis]를 채울 만큼 몇 번 돌릴지 — 끝까지 오간 뒤 멈추도록 올림한다. */
    fun attentionRounds(roundTripMillis: Int): Int = (AttentionMillis + roundTripMillis - 1) / roundTripMillis
}
