package com.worksoc.goaicoach.ui.l10n

/**
 * 설정의 「소리」 절(백로그 #244, 사용자 2026-10-18 — 첫 절 바로 아래의 새 절). 지금은 「앱 배경음악」 한 줄이고,
 * 나중에 대국 중 음악 설정도 이 절에 온다.
 *
 * [UiStrings] 생성자는 여유가 없어(함정 61) 곁표 + 함수로 둔다. ⚠️ 리플렉션 그물 밖이라(함정 10) 네 언어가 다 찼는지는
 * `BackgroundMusicContractTest`가 손으로 본다.
 */
private val SoundSectionTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "소리",
    UiLanguage.English to "Sound",
    UiLanguage.Japanese to "サウンド",
    UiLanguage.ChineseSimplified to "声音",
)

private val BackgroundMusicLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "앱 배경음악",
    UiLanguage.English to "App background music",
    UiLanguage.Japanese to "アプリのBGM",
    UiLanguage.ChineseSimplified to "应用背景音乐",
)

internal fun settingsSoundSectionFor(language: UiLanguage): String = SoundSectionTitles.getValue(language)

internal fun backgroundMusicLabelFor(language: UiLanguage): String = BackgroundMusicLabels.getValue(language)
