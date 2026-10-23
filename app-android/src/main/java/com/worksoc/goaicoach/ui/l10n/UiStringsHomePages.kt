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

/** 명언록 카드의 이름 — 오늘의 명언에서 시작해 옆으로 밀어 지난 · 다음 날의 명언을 본다. */
private val QuoteBookTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "명언록",
    UiLanguage.English to "Quote book",
    UiLanguage.Japanese to "名言録",
    UiLanguage.ChineseSimplified to "名言录",
)

internal fun labQuoteBookTitleFor(language: UiLanguage): String = QuoteBookTitles.getValue(language)

/** 명언록 카드 아래의 안내 — 옆으로 밀면 된다는 것. */
private val QuoteBookHints: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "옆으로 밀어 지난 명언 · 다음 명언",
    UiLanguage.English to "Swipe for earlier and later quotes",
    UiLanguage.Japanese to "横にスワイプして前後の名言へ",
    UiLanguage.ChineseSimplified to "左右滑动查看前后的名言",
)

internal fun labQuoteBookHintFor(language: UiLanguage): String = QuoteBookHints.getValue(language)

/** 명언록에서 오늘의 명언에만 붙는 표시 — 날짜는 붙이지 않는다(사용자 2026-10-22: 본 날을 적지 않아 지난 명언의 날짜는 맞출 수 없다). */
private val QuoteBookTodayLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "오늘",
    UiLanguage.English to "Today",
    UiLanguage.Japanese to "今日",
    UiLanguage.ChineseSimplified to "今天",
)

internal fun labQuoteTodayFor(language: UiLanguage): String = QuoteBookTodayLabels.getValue(language)

/** 아직 없는 실험(#280 — 「준비 중」 배지와 함께 눌리지 않는다, U-16과 같은 결). */
private val AddMyQuoteTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "나만의 명언 추가하기",
    UiLanguage.English to "Add your own quote",
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
