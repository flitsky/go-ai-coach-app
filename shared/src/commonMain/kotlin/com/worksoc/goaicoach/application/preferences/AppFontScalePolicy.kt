package com.worksoc.goaicoach.application.preferences

/**
 * 앱의 글꼴 배율 — **늘 1.0 하나다**(저장되는 설정이 아니다).
 *
 * ## ⚠️ 휴대폰 글자 크기를 따르지 않는다
 * · 2026-09-04 사용자 결정: 시스템 글꼴 배율(안드로이드 `font_scale`, iOS Dynamic Type)을 따르지 않는다.
 * · 2026-11-23 사용자 결정(QA-05): 앱 설정 「크게」(1.3배)도 없앴다. 대화상자 · 팝업 · 알림 층도 본 화면과 같은 1.0이다.
 *   - 안드로이드: `MainActivity.attachBaseContext`가 액티비티 설정의 `fontScale`을 이 값으로 덮는다 — 대화상자 · 팝업 창은
 *     액티비티 문맥에서 제 `LocalDensity`를 새로 만들어서 컴포지션의 덮어쓰기만으로는 닿지 않는다. `setContent`의 `Density`는
 *     같은 규칙을 한 번 더 적은 것이다. ⚠️ 글자 토스트는 시스템 UI가 그려(targetSdk 30부터) 이 값이 닿지 않는다.
 *   - iOS: `MainViewController`가 앱 · 알림 층(`IosNoticeOverlay`) · 디버그 화면을 함께 감싸고, 대화상자 · 팝업 층은 화면의
 *     Dynamic Type 특성에서 배율을 다시 지으므로 컨트롤러의 `preferredContentSizeCategory`를 기본값으로 묶는다(2026-11-23 시뮬레이터).
 * · ⚠️ **접근성 비용을 알고 고른 것이다.** 글자만 키우는 길은 앱 안에 없다. OS의 「디스플레이 크기」 · 확대(화면 전체)는
 *   `density`를 통해 그대로 적용된다 — 덮는 것은 `fontScale`뿐이다.
 * · 저장된 옛 `"appFontScale"` 값은 읽을 때 무시된다(옮기기 없음 — 디코더가 그 키를 보지 않는다).
 * · 되살리려면: 「크게」 패널 · 저장 필드 · 화면 상태(`FontScaleSettingsPanel` · `AppFontScaleState`)는 `30227cd0`에 있다.
 *   그때는 시스템을 따를지(저장 필드를 nullable로)부터 정한다 — 0.0 같은 마법값을 만들지 말 것.
 *
 * ⚠️ 영어 · iOS 넓은 글꼴 · 360dp를 지키는 레이아웃 처방(`FontScaleLayoutContractTest`)은 1.3을 견디려고 다진 것이지만 그대로 둔다.
 */
const val FixedAppFontScale: Float = 1.0f
