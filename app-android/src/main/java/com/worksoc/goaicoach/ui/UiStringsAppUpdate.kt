package com.worksoc.goaicoach.ui

/**
 * 설정 화면의 앱 업데이트 줄이 쓰는 문구(백로그 #53). 구조는 `UiStringsLanding.kt`와 같다 —
 * 화면 한 조각이 쓰는 문구를 한 파일에 모아 네 언어 파일을 건드리지 않는다.
 */
private val UpdateAvailableLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "새 버전이 있어요",
    UiLanguage.English to "A new version is available",
    UiLanguage.Japanese to "新しいバージョンがあります",
    UiLanguage.ChineseSimplified to "有新版本可用",
)

private val UpdateActionLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "업데이트하러 가기",
    UiLanguage.English to "Go to update",
    UiLanguage.Japanese to "アップデートへ",
    UiLanguage.ChineseSimplified to "前往更新",
)

private val UpToDateLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "최신 버전이에요",
    UiLanguage.English to "You're on the latest version",
    UiLanguage.Japanese to "最新バージョンです",
    UiLanguage.ChineseSimplified to "已是最新版本",
)

/**
 * ⚠️ 확인에 실패했을 때 쓰는 문구다. **"확인 실패"라고 쓰지 않는다** — 사용자가 고칠 수 없는
 * 사정(Play 설치본이 아님·네트워크 없음)이라 알려 봐야 불안만 남고, 할 수 있는 일(스토어에서
 * 직접 보기)만 남기는 편이 낫다.
 */
private val CheckStoreLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "스토어에서 버전 확인",
    UiLanguage.English to "Check the store for updates",
    UiLanguage.Japanese to "ストアでバージョンを確認",
    UiLanguage.ChineseSimplified to "在商店查看版本",
)

internal fun appUpdateAvailableLabelFor(language: UiLanguage): String =
    UpdateAvailableLabels.getValue(language)

internal fun appUpdateActionLabelFor(language: UiLanguage): String =
    UpdateActionLabels.getValue(language)

internal fun appUpToDateLabelFor(language: UiLanguage): String =
    UpToDateLabels.getValue(language)

internal fun appUpdateCheckStoreLabelFor(language: UiLanguage): String =
    CheckStoreLabels.getValue(language)
