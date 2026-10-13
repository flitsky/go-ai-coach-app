package com.worksoc.goaicoach.ui.l10n

/**
 * 설정 화면의 앱 업데이트 줄이 쓰는 문구(백로그 #53). 구조는 `UiStringsStudyVideos.kt`와 같다 —
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

/**
 * #255 — 엔진이 없고 새 버전이 있을 때, 받기 전에 업데이트를 권하는 문구(엔진 준비 화면 · 대국 설정의 받기 카드 · 홈 한 줄).
 * ⚠️ 스레드가 정한 문구다(2026-10-13) — 사용자 확인 전. 영어·일본어·중국어는 원어민 확인이 없다.
 */
private val UpdateBeforeEngineLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "새 버전이 있어요. 업데이트한 뒤 AI 엔진을 받아요.",
    UiLanguage.English to "A new version is available. Update first, then get the AI engine.",
    UiLanguage.Japanese to "新しいバージョンがあります。アップデートしてからAIエンジンを受け取ります。",
    UiLanguage.ChineseSimplified to "有新版本。请先更新，再下载 AI 引擎。",
)

/** #255 — 한 줄 알림과 받기 카드의 짧은 버튼. 설정의 「업데이트하러 가기」는 그 자리의 버튼이라 그대로 둔다. */
private val UpdateShortActionLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "업데이트",
    UiLanguage.English to "Update",
    UiLanguage.Japanese to "アップデート",
    UiLanguage.ChineseSimplified to "更新",
)

/** #255 — 업데이트하지 않고 지금 버전으로 엔진을 받는다(사용자 2026-10-13: 「이대로 받기」를 둔다). */
private val DownloadWithoutUpdateLabels: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "이대로 받기",
    UiLanguage.English to "Download anyway",
    UiLanguage.Japanese to "このまま受け取る",
    UiLanguage.ChineseSimplified to "直接下载",
)

internal fun appUpdateBeforeEngineLabelFor(language: UiLanguage): String =
    UpdateBeforeEngineLabels.getValue(language)

internal fun appUpdateShortActionLabelFor(language: UiLanguage): String =
    UpdateShortActionLabels.getValue(language)

internal fun appDownloadWithoutUpdateLabelFor(language: UiLanguage): String =
    DownloadWithoutUpdateLabels.getValue(language)
