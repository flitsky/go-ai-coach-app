package com.worksoc.goaicoach.ui.l10n

/**
 * 홈의 아래 탭 이름 가운데 새로 생긴 하나 — 가운데 페이지 「대국」(백로그 #280, 문구는 스레드가 정함). 양옆 「내 정보」 · 「학습 하기」는
 * 그 화면의 이름(`myPageTitle` · `study`)을 그대로 쓴다. [UiStrings]의 생성자가 아니라 곁표인 이유는 `UiStringsStudyCategories.kt`의 KDoc(함정 61).
 */
private val PlayTabLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국",
    UiLanguage.English to "Play",
    UiLanguage.Japanese to "対局",
    UiLanguage.ChineseSimplified to "对弈",
)

internal fun homePagePlayTabFor(language: UiLanguage): String = PlayTabLabels.getValue(language)
