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

/** 셋째 페이지 「실험실」(#280, 사용자 2026-10-22 — 「학습 하기」 자리를 실험실 기능으로 채웠다) — 탭 이름과 그 페이지의 문구. 영 · 일 · 중은 스레드가 정한 표기. */
private val LabTabLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "실험실",
    UiLanguage.English to "Lab",
    UiLanguage.Japanese to "実験室",
    UiLanguage.ChineseSimplified to "实验室",
)

internal fun homePageLabTabFor(language: UiLanguage): String = LabTabLabels.getValue(language)

/**
 * 명언록의 이름 — 오늘의 명언에서 시작해 옆으로 밀어 지난 · 다음 날의 명언을 본다.
 * 「오늘」 표시와 「옆으로 밀어…」 안내는 없앴다(사용자 2026-10-23, #281) — 양옆 카드가 보이는 모양이 그 안내를 대신한다.
 */
private val QuoteBookTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "명언록",
    UiLanguage.English to "Quote book",
    UiLanguage.Japanese to "名言録",
    UiLanguage.ChineseSimplified to "名言录",
)

internal fun labQuoteBookTitleFor(language: UiLanguage): String = QuoteBookTitles.getValue(language)

/** 명언록 제목 줄의 예고 「+ 나만의 명언 추가 (준비 중)」의 가운데 말(#281, 사용자 2026-10-23). 영 · 일 · 중은 스레드가 정한 표기. */
private val AddMyQuoteTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "나만의 명언 추가",
    UiLanguage.English to "Add your own",
    UiLanguage.Japanese to "自分の名言を追加",
    UiLanguage.ChineseSimplified to "添加我的名言",
)

internal fun labAddMyQuoteTitleFor(language: UiLanguage): String = AddMyQuoteTitles.getValue(language)

private val FortuneTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "오늘의 운세",
    UiLanguage.English to "Today's fortune",
    UiLanguage.Japanese to "今日の運勢",
    UiLanguage.ChineseSimplified to "今日运势",
)

internal fun labFortuneTitleFor(language: UiLanguage): String = FortuneTitles.getValue(language)
