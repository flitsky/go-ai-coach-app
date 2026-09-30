package com.worksoc.goaicoach.ui.l10n

/**
 * 설정 → 「오픈소스 라이선스」 줄과 그 화면이 쓰는 문구(백로그 #195). 구조는 `UiStringsAppUpdate.kt`와 같다 —
 * [UiStrings] 생성자는 여유가 없어(함정 61) 곁표 + 함수로 둔다.
 *
 * ⚠️ **리플렉션 그물 밖이다**(함정 10). `UiStringsTest`는 [UiStrings]의 `String` 필드만 훑으므로
 * 여기 표는 손으로 짠 그물(`OpenSourceLicensesContractTest`)이 지킨다.
 *
 * ⚠️ 라이브러리 이름·라이선스 **원문은 번역하지 않는다** — 법적 고지는 원문 그대로가 정본이다.
 * 여기 있는 것은 화면의 틀(제목·안내·구획 이름)뿐이다.
 */
private val Titles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "오픈소스 라이선스",
    UiLanguage.English to "Open source licenses",
    UiLanguage.Japanese to "オープンソースライセンス",
    UiLanguage.ChineseSimplified to "开源许可",
)

private val Intros: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "이 앱에 포함된 오픈소스 소프트웨어와 그 라이선스입니다. 항목을 누르면 라이선스 전문을 펼치거나 접을 수 있어요.",
    UiLanguage.English to "Open source software included in this app and its licenses. Tap an item to show or hide the full license text.",
    UiLanguage.Japanese to "このアプリに含まれるオープンソースソフトウェアとそのライセンスです。項目をタップするとライセンス全文を表示・非表示にできます。",
    UiLanguage.ChineseSimplified to "本应用包含的开源软件及其许可证。点按条目可展开或收起许可证全文。",
)

/** 목록 맨 위 구획 — Gradle 의존성이 아니라 앱이 직접 싣는 엔진·신경망과 엔진 안에 컴파일된 부품. */
private val EngineSectionTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "AI 엔진 (KataGo)",
    UiLanguage.English to "AI engine (KataGo)",
    UiLanguage.Japanese to "AIエンジン (KataGo)",
    UiLanguage.ChineseSimplified to "AI 引擎 (KataGo)",
)

private val LibrarySectionTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "앱 라이브러리",
    UiLanguage.English to "App libraries",
    UiLanguage.Japanese to "アプリのライブラリ",
    UiLanguage.ChineseSimplified to "应用程序库",
)

/** 원문이 목록에 실리지 않은 라이선스(Google SDK 약관 등)는 그 약관 주소로 보낸다. */
private val ViewLicenseLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "라이선스 원문 보기",
    UiLanguage.English to "View license",
    UiLanguage.Japanese to "ライセンスを表示",
    UiLanguage.ChineseSimplified to "查看许可证",
)

/**
 * ⚠️ 목록이 비었을 때 **빈 화면을 보여 주지 않는다**(함정 40 — 조용한 기본값은 거짓말). 릴리스 빌드에서
 * 리소스가 줄어 사라지는 사고(R8·리소스 축소)가 나면 사용자에게도 보이고 실기 확인에서도 걸린다.
 */
private val LoadFailedMessages: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "라이선스 목록을 불러올 수 없어요.",
    UiLanguage.English to "Couldn't load the license list.",
    UiLanguage.Japanese to "ライセンス一覧を読み込めませんでした。",
    UiLanguage.ChineseSimplified to "无法加载许可证列表。",
)

internal fun openSourceLicensesTitleFor(language: UiLanguage): String = Titles.getValue(language)

internal fun openSourceLicensesIntroFor(language: UiLanguage): String = Intros.getValue(language)

internal fun openSourceLicensesEngineSectionFor(language: UiLanguage): String = EngineSectionTitles.getValue(language)

internal fun openSourceLicensesLibrarySectionFor(language: UiLanguage): String = LibrarySectionTitles.getValue(language)

internal fun openSourceLicensesViewLicenseFor(language: UiLanguage): String = ViewLicenseLabels.getValue(language)

internal fun openSourceLicensesLoadFailedFor(language: UiLanguage): String = LoadFailedMessages.getValue(language)
