package com.worksoc.goaicoach.ui

/**
 * 문구가 화면에서 차지하는 **가로 칸 수**를 센다 — 전각(한글·한자·가나·전각기호)은 2, 나머지는 1.
 *
 * ## 왜 필요한가
 * 좁은 칸에 들어가는 문구는 **네 언어가 물리적으로 비슷한 길이**여야 한다. 지금까지 이 저장소는
 * 그것을 손으로 셌다 — `UiStringsTest`·`GameActionButtons.kt`의 주석이 *"전각 8자 = 92dp"* 처럼
 * 계산을 글로 남겼는데, 글로 남긴 계산은 문구가 바뀌어도 **따라오지 않는다.** 실제로 함정 21이
 * *"폭 부족은 레이아웃 비율이 아니라 네 언어 문구 길이부터 보라(CJK=2로 세기)"* 라고 적어 뒀지만
 * 그것을 **재는 도구는 없었다.**
 *
 * ## ⚠️ dp가 아니라 칸이다
 * 실제 폭은 글꼴·자간에 달렸으므로 이 값은 근사다. 그래도 *"어느 언어가 유독 긴가"* 는 정확히
 * 잡아내고, 그것이 잘림의 원인이다 — 2026-09-18 배율 1.3에서 한국어 *"프리미엄 구독 이용 중"* 이
 * 잘린 것이 이 도구를 만든 계기다(#159).
 *
 * ⚠️ 라틴 문자가 실제로는 전각의 절반보다 좁으므로 **영어는 이 계산에서 불리하게 나온다.**
 * 그래서 예산은 "영어가 조금 큰" 상태를 정상으로 보고 잡는다 — 영어만 한 칸씩 깎지 말 것.
 */
internal fun String.displayWidth(): Int = sumOf { char ->
    if (char.isFullWidth()) 2 else 1
}

private fun Char.isFullWidth(): Boolean = code in 0x1100..0x115F || // 한글 자모
    code in 0x2E80..0x303E || // CJK 부수 · 기호/구두점(·「」 등)
    code in 0x3041..0x33FF || // 가나 · 호환 자모 · 각종 CJK 조합 문자
    code in 0x3400..0x4DBF || // CJK 확장 A
    code in 0x4E00..0x9FFF || // CJK 통합 한자
    code in 0xA000..0xA4CF ||
    code in 0xAC00..0xD7A3 || // 한글 음절
    code in 0xF900..0xFAFF || // CJK 호환 한자
    code in 0xFE30..0xFE6F ||
    code in 0xFF00..0xFF60 || // 전각 영숫자·기호
    code in 0xFFE0..0xFFE6
