package com.worksoc.goaicoach.ui.l10n

import com.worksoc.goaicoach.shared.policy.NightEndHour
import com.worksoc.goaicoach.shared.policy.NightStartHour
import com.worksoc.goaicoach.shared.policy.ThemeMode

/**
 * 설정의 「화면 모드」(백로그 #272). [UiStrings] 생성자는 여유가 없어(함정 61) 곁표 + 함수로 둔다.
 * ⚠️ 리플렉션 그물 밖이라(함정 10) 네 언어가 다 찼는지는 `ThemeModeContractTest`가 손으로 본다.
 */
private val Titles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "화면 모드",
    UiLanguage.English to "Theme",
    UiLanguage.Japanese to "画面モード",
    UiLanguage.ChineseSimplified to "显示模式",
)

private val Labels: Map<ThemeMode, Map<UiLanguage, String>> = mapOf(
    ThemeMode.System to mapOf(
        UiLanguage.Korean to "시스템 따라가기",
        UiLanguage.English to "Follow system",
        UiLanguage.Japanese to "端末に合わせる",
        UiLanguage.ChineseSimplified to "跟随系统",
    ),
    ThemeMode.Light to mapOf(
        UiLanguage.Korean to "밝게",
        UiLanguage.English to "Light",
        UiLanguage.Japanese to "ライト",
        UiLanguage.ChineseSimplified to "浅色",
    ),
    ThemeMode.Dark to mapOf(
        UiLanguage.Korean to "어둡게",
        UiLanguage.English to "Dark",
        UiLanguage.Japanese to "ダーク",
        UiLanguage.ChineseSimplified to "深色",
    ),
    ThemeMode.NightHours to mapOf(
        UiLanguage.Korean to "밤 시간 자동",
        UiLanguage.English to "Night hours",
        UiLanguage.Japanese to "夜だけダーク",
        UiLanguage.ChineseSimplified to "夜间自动",
    ),
)

internal fun themeModeTitleFor(language: UiLanguage): String = Titles.getValue(language)

internal fun themeModeLabelFor(language: UiLanguage, mode: ThemeMode): String = Labels.getValue(mode).getValue(language)

/** 「밤 시간 자동」을 고르면 아래에 한 줄 — 시각은 [NightStartHour] · [NightEndHour]에서 읽는다(한 곳의 정의). */
internal fun themeModeNightHoursHintFor(language: UiLanguage): String = when (language) {
    UiLanguage.Korean -> "밤 ${NightStartHour - 12}시부터 새벽 ${NightEndHour}시까지 어둡게 보여요."
    UiLanguage.English -> "Dark from ${NightStartHour - 12} pm to $NightEndHour am."
    UiLanguage.Japanese -> "夜${NightStartHour}時から朝${NightEndHour}時までダークになります。"
    UiLanguage.ChineseSimplified -> "晚上${NightStartHour}点到早上${NightEndHour}点使用深色。"
}
