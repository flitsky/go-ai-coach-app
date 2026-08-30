package com.worksoc.goaicoach.ui

/**
 * "이 문자열에 한글이 남아 있는가" 하나를 두 회귀 그물이 **같은 뜻으로** 쓰게 하려고 뺐다.
 *
 * `UiStringsTest`는 [UiStrings]의 String **필드**를 리플렉션으로 훑고(#31),
 * `UiStringsBotCharacterTest`는 **함수**가 돌려주는 캐릭터 문구를 훑는다(#32) — 검사 대상이
 * 달라 그물이 둘로 갈렸는데, 판정 기준까지 각자 갖고 있으면 한쪽만 넓혀 놓고 다른 쪽은
 * 구멍이 난 채로 통과하는 일이 생긴다.
 *
 * ⚠️ 완성형 음절뿐 아니라 **자모 영역까지** 본다. 지금 값에는 자모만 있는 문자열이 없지만,
 * 의도가 "한글 잔재가 하나라도 있으면"이므로 범위를 좁히면 그물에 구멍이 생긴다.
 */
internal val HangulRanges = CharRange('가', '힣') +   // 완성형 음절
    CharRange('ᄀ', 'ᇿ') +                            // 자모
    CharRange('㄰', '㆏') +                            // 호환 자모
    CharRange('ꥠ', '꥿') +                            // 자모 확장-A
    CharRange('ힰ', '퟿') +                            // 자모 확장-B
    CharRange('ﾠ', 'ￜ')                              // 반각 자모

internal fun String.containsHangul(): Boolean = any { it in HangulRanges }
