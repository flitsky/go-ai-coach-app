package com.worksoc.goaicoach.ui

/**
 * 대국 흐름(종료 팝업·나가기·통과)이 쓰는 문구(백로그 #175).
 *
 * ## ⚠️ 왜 [UiStrings] 데이터 클래스가 아니라 여기인가
 * **넣어 봤더니 클래스가 JVM 한계를 넘어 `ClassFormatError`가 났다**(2026-09-18 실측 — 테스트
 * 수십 개가 한꺼번에 터졌고 원인은 컴파일이 아니라 **런타임 클래스 로딩**이었다). 그 클래스는
 * 생성자 파라미터가 이미 200개 가까이라 **더 받을 자리가 없다.**
 * ⚠️ **새 문구를 그 클래스에 더하지 말 것** — `UiStringsAppUpdate.kt`·`UiStringsPremiumSubscription.kt`가
 * 먼저 간 이 길을 따를 것. 덤으로 네 언어 파일을 건드리지 않아도 된다.
 *
 * ⚠️ 표에 키가 빠지면 `getValue`가 **던진다** — 그 언어 사용자는 대국 화면을 못 연다.
 * `UiStringsGameFlowTest`가 그물이다.
 */
private val RematchActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "재 대국",
    UiLanguage.English to "Rematch",
    UiLanguage.Japanese to "再対局",
    UiLanguage.ChineseSimplified to "再来一局",
)

private val ExitGameActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 나가기",
    UiLanguage.English to "Leave game",
    UiLanguage.Japanese to "対局を出る",
    UiLanguage.ChineseSimplified to "退出对局",
)

private val PassNoticeTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "통과",
    UiLanguage.English to "Pass",
    UiLanguage.Japanese to "パス",
    UiLanguage.ChineseSimplified to "停一手",
)

private val ScoreNowPromptTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "계가 하시겠습니까?",
    UiLanguage.English to "Score the game?",
    UiLanguage.Japanese to "計算に進みますか？",
    UiLanguage.ChineseSimplified to "进行点目吗？",
)

/**
 * ⚠️ **"예"가 무엇을 하는지 본문이 말해야 한다**(함정 39). 종국은 **연속 두 번 통과**이지 이
 * 팝업이 아니다 — 본문이 침묵하면 사용자는 *"예를 누르면 지금 끝나는구나"* 로 읽고, 실제로는
 * 자기 통과가 한 번 더 들어간다.
 */
private val ScoreNowPromptBodies: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "상대가 통과했습니다. 예를 누르면 나도 통과해 대국을 마치고 계가합니다. 아니오를 누르면 계속 둡니다.",
    UiLanguage.English to "Your opponent passed. Yes passes too and ends the game for scoring. No keeps playing.",
    UiLanguage.Japanese to "相手がパスしました。はいで自分もパスし、対局を終えて計算します。いいえなら続行します。",
    UiLanguage.ChineseSimplified to "对手停一手。选择「是」则己方也停一手，结束对局并点目。选择「否」则继续对局。",
)

internal fun rematchActionFor(language: UiLanguage): String = RematchActions.getValue(language)

internal fun exitGameActionFor(language: UiLanguage): String = ExitGameActions.getValue(language)

internal fun passNoticeTitleFor(language: UiLanguage): String = PassNoticeTitles.getValue(language)

internal fun scoreNowPromptTitleFor(language: UiLanguage): String =
    ScoreNowPromptTitles.getValue(language)

internal fun scoreNowPromptBodyFor(language: UiLanguage): String =
    ScoreNowPromptBodies.getValue(language)
