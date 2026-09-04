package com.worksoc.goaicoach.application.preferences

/**
 * 앱 글꼴 배율 정책(백로그 #81) — **저장되는 사용자 설정**이다.
 *
 * ## ⚠️ 이 앱은 시스템 글꼴 배율을 따르지 않는다
 * [DefaultAppFontScale]이 **1.0**이고 그 값이 저장에서 읽혀 항상 적용되므로, 시스템 설정에서
 * 글자를 키운 사용자도 이 앱에서는 **1.0으로 보인다**(2026-09-04 사용자 결정: *"바꾼 적 없는
 * 유저는 1.0 배율"*).
 * · ⚠️ **이것은 접근성 비용이 있는 결정이다.** 시스템 배율을 키워 둔 사용자의 설정이 이 앱에서
 *   무시된다. 되돌리려면 기본값을 *"시스템 따름"*(저장값 없음 = 오버라이드 안 함)으로 바꾸고
 *   순환에 그 상태를 하나 더 넣으면 된다 — 그때 [AppFontScales]에 `null`을 섞는 것이 아니라
 *   **저장 필드를 nullable로** 바꾸는 쪽이 맞다(0.0 같은 마법값을 만들지 말 것).
 *
 * ## 값 셋의 근거 — **검증된 배율만 넣는다**
 * [AppFontScales]는 개발자 도구가 순환하는 값이자 앞으로 사용자 설정이 될 값이다. 1.3은
 * **#64가 잘림을 실제로 확인하고 고친 배율**이라 근거가 있다.
 *
 * ⚠️ **1.5는 뺐다**(2026-09-04 사용자 결정). #81이 순환에 넣었지만 **아무도 그 배율에서
 * 레이아웃을 훑지 않았고**, 실제로 #73 검증 중에 Compact 패널의 바둑판 드롭다운이 `13x…`로
 * 줄어드는 것이 바로 나왔다. **검증되지 않은 배율을 순환에 두면 개발자 도구가 없는 결함을
 * 만들어 보여 준다** — 목록에 있다는 것이 곧 "이 배율에서 화면이 성립한다"는 주장이기 때문이다.
 *
 * ⚠️ 여기에 값을 더할 때는 `FontScaleLayoutContractTest`가 지키는 처방들이 그 배율에서도
 * 버티는지 **실기로 4개 언어를 훑어** 확인할 것 — 값만 늘리는 것은 검증이 아니다.
 */
const val DefaultAppFontScale: Float = 1.0f

/** 순환 순서. 마지막 값 다음은 처음으로 돌아온다. */
val AppFontScales: List<Float> = listOf(1.0f, 1.3f)

/**
 * [current] 다음 배율. 목록에 없는 값(저장이 깨졌거나 예전 값이 남은 경우)은 **처음 값**으로
 * 되돌린다 — 모르는 값을 그대로 두면 순환 버튼이 아무 일도 안 하는 것처럼 보인다.
 */
fun nextAppFontScale(current: Float): Float {
    val index = AppFontScales.indexOfFirst { scale -> scale == current }
    if (index < 0) return AppFontScales.first()
    return AppFontScales[(index + 1) % AppFontScales.size]
}

/**
 * 저장에서 읽은 값을 신뢰할 수 있는 배율로 좁힌다.
 *
 * ⚠️ **0이나 음수가 흘러들면 화면이 통째로 사라진다** — 글자 높이가 0이 된다. 저장은 앞으로
 * 사용자 설정이 될 자리라 손으로 편집될 수도 있으니(개발자 도구가 그 파일을 쓴다) 읽는 쪽에서
 * 막는다. 목록에 없는 값도 기본값으로 접는다 — 값 셋이 줄었을 때 옛 저장분이 남는 경우다.
 */
fun sanitizeAppFontScale(stored: Float): Float =
    if (AppFontScales.any { scale -> scale == stored }) stored else DefaultAppFontScale
