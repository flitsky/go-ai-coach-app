package com.worksoc.goaicoach.ui.l10n

/**
 * 백로그 #256·#261 — 설정 화면의 절 머리말과 「내 정보」의 새 줄(내 캐릭터 · 출석 판 접기)이 쓰는 문구.
 * 구조는 `UiStringsAppUpdate.kt`와 같다 — 화면 한 조각이 쓰는 문구를 한 파일에 모아 네 언어 파일을 건드리지 않는다.
 * ⚠️ 스레드가 정한 문구다(2026-10-13) — 영어·일본어·중국어는 원어민 확인이 없다.
 */
/** 첫 절(#261 — 사용자 2026-10-13: 「화면」은 *"무슨 화면?"* 이라 「언어 및 글자 크기」로). */
private val SettingsDisplaySection: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "언어 및 글자 크기",
    UiLanguage.English to "Language and text size",
    UiLanguage.Japanese to "言語と文字サイズ",
    UiLanguage.ChineseSimplified to "语言和字号",
)

/**
 * 대국 쪽 절 셋(#261 — 사용자 2026-10-13: 「대국」은 *"이건 무엇?"* 이라 셋으로). 대국 메뉴의 소제목(「바둑판 · 착수 · AI 코치」, `menuSectionTitleFor`)과
 * 같은 묶음이지만, 설정에서는 대국 밖에서 읽히므로 「대국 …」을 붙여 무엇의 설정인지 말한다.
 */
private val SettingsGameBoardSection: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 바둑판 설정",
    UiLanguage.English to "Game board settings",
    UiLanguage.Japanese to "対局の盤の設定",
    UiLanguage.ChineseSimplified to "对局棋盘设置",
)

private val SettingsGamePlacingSection: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 착수 설정",
    UiLanguage.English to "Stone placement settings",
    UiLanguage.Japanese to "対局の着手の設定",
    UiLanguage.ChineseSimplified to "对局落子设置",
)

private val SettingsGameAiCoachSection: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 내 AI 코치 설정",
    UiLanguage.English to "In-game AI coach settings",
    UiLanguage.Japanese to "対局中のAIコーチ設定",
    UiLanguage.ChineseSimplified to "对局中 AI 教练设置",
)

private val SettingsHelpSection: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "도움말",
    UiLanguage.English to "Help",
    UiLanguage.Japanese to "ヘルプ",
    UiLanguage.ChineseSimplified to "帮助",
)

private val SettingsAboutSection: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "앱 정보",
    UiLanguage.English to "About",
    UiLanguage.Japanese to "アプリ情報",
    UiLanguage.ChineseSimplified to "关于应用",
)

private val ShowAll: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "펼치기",
    UiLanguage.English to "Show",
    UiLanguage.Japanese to "開く",
    UiLanguage.ChineseSimplified to "展开",
)

private val Collapse: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "접기",
    UiLanguage.English to "Hide",
    UiLanguage.Japanese to "閉じる",
    UiLanguage.ChineseSimplified to "收起",
)

/** 내 정보의 「내 캐릭터」 줄과, 그 줄이 여는 카드 캐러셀의 제목. */
private val MyCharacters: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "내 캐릭터",
    UiLanguage.English to "My characters",
    UiLanguage.Japanese to "マイキャラクター",
    UiLanguage.ChineseSimplified to "我的角色",
)

internal fun settingsDisplaySectionFor(language: UiLanguage): String = SettingsDisplaySection.getValue(language)

internal fun settingsGameSectionFor(language: UiLanguage, section: MenuSection): String = when (section) {
    MenuSection.Board -> SettingsGameBoardSection
    MenuSection.Placing -> SettingsGamePlacingSection
    MenuSection.AiCoach -> SettingsGameAiCoachSection
}.getValue(language)

internal fun settingsHelpSectionFor(language: UiLanguage): String = SettingsHelpSection.getValue(language)

internal fun settingsAboutSectionFor(language: UiLanguage): String = SettingsAboutSection.getValue(language)

internal fun expandLabelFor(language: UiLanguage): String = ShowAll.getValue(language)

internal fun collapseLabelFor(language: UiLanguage): String = Collapse.getValue(language)

internal fun myCharactersTitleFor(language: UiLanguage): String = MyCharacters.getValue(language)
