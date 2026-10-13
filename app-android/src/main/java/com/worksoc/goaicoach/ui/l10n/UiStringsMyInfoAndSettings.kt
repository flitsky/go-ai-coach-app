package com.worksoc.goaicoach.ui.l10n

/**
 * 백로그 #256 — 설정 화면의 절 머리말과 「내 정보」의 새 줄(내 캐릭터 · 출석 판 접기)이 쓰는 문구.
 * 구조는 `UiStringsAppUpdate.kt`와 같다 — 화면 한 조각이 쓰는 문구를 한 파일에 모아 네 언어 파일을 건드리지 않는다.
 * ⚠️ 스레드가 정한 문구다(2026-10-13) — 영어·일본어·중국어는 원어민 확인이 없다.
 */
private val SettingsDisplaySection: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "화면",
    UiLanguage.English to "Display",
    UiLanguage.Japanese to "表示",
    UiLanguage.ChineseSimplified to "显示",
)

private val SettingsGameSection: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국",
    UiLanguage.English to "Games",
    UiLanguage.Japanese to "対局",
    UiLanguage.ChineseSimplified to "对局",
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

/** 대국 화면 표시 옵션(바둑판 · 착수 · AI 코치 토글 묶음)을 접어 둔 줄의 이름. */
private val SettingsBoardDisplayOptions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 화면 표시",
    UiLanguage.English to "Game screen display",
    UiLanguage.Japanese to "対局画面の表示",
    UiLanguage.ChineseSimplified to "对局界面显示",
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

internal fun settingsGameSectionFor(language: UiLanguage): String = SettingsGameSection.getValue(language)

internal fun settingsHelpSectionFor(language: UiLanguage): String = SettingsHelpSection.getValue(language)

internal fun settingsAboutSectionFor(language: UiLanguage): String = SettingsAboutSection.getValue(language)

internal fun settingsBoardDisplayOptionsFor(language: UiLanguage): String = SettingsBoardDisplayOptions.getValue(language)

internal fun expandLabelFor(language: UiLanguage): String = ShowAll.getValue(language)

internal fun collapseLabelFor(language: UiLanguage): String = Collapse.getValue(language)

internal fun myCharactersTitleFor(language: UiLanguage): String = MyCharacters.getValue(language)
