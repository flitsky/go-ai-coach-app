package com.worksoc.goaicoach.ui.l10n

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

/**
 * 끝난 대국을 다시보기로 여는 버튼(백로그 #185) — **계가 팝업과 하단 액션바가 같은 문구를 쓴다.**
 *
 * ⚠️ 목적지 화면의 제목은 「대국 다시보기」(`gameReplayTitleFor`)로 **문구가 다르다.** 이것은
 * 실수가 아니라 사용자 어휘를 따른 것이다 — 등재문 부제가 *"오프라인 AI 대국·복기"* 이고
 * 2026-09-22 지시도 「복기 하기」였다. 둘을 통일하려면 **화면 제목 쪽을 고쳐야** 하고,
 * 그것은 #156이 정한 이름을 바꾸는 별도 결정이다.
 *
 * 한국어는 붙여 쓴 「복기하기」다(QA-23a, 사용자 2026-11-23) — 「복기하다」가 한 낱말이고, 같은 말풍선의
 * 「…복기하기를 추천드려요.」와 단추 글자가 어긋났다. 폭: 한 8 · 한도 10 · 일 8 · 영 6 · 중 4.
 */
private val ReviewGameActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "복기하기",
    UiLanguage.English to "Review",
    UiLanguage.Japanese to "検討する",
    UiLanguage.ChineseSimplified to "复盘",
)

private val ExitGameActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 나가기",
    UiLanguage.English to "Leave game",
    UiLanguage.Japanese to "対局を出る",
    UiLanguage.ChineseSimplified to "退出对局",
)

/**
 * 끝난 판 윗줄 왼쪽의 「← 홈」(QA-01, 사용자 2026-11-23 ⓐ안) — 좁은 칸(360dp에서 55dp쯤)에 서므로 한 낱말이다.
 * 네 언어의 표시폭을 맞춘다(전각 2 · 반각 1): 홈 2 · Home 4 · 戻る 4 · 主页 4. 일본어는 처음 「ホーム」(6칸)이었다가
 * 1.3배 · 360dp에서 옆의 수순과 겹쳐 「戻る」로 줄였다 — 언어마다 실기로 재는 대신 길이를 먼저 맞춘다(사용자 2026-11-23).
 */
private val FinishedGameHomeActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "홈",
    UiLanguage.English to "Home",
    UiLanguage.Japanese to "戻る",
    UiLanguage.ChineseSimplified to "主页",
)

/**
 * 대국 중 메뉴 「대국 나가기」 · 뒤로가 여는 창(QA-04, 사용자 2026-11-23 — 「취소 · 기권」 두 단추, 말만 동작에 맞춘다).
 * 묻는 말이 「정말 기권하시겠습니까?」뿐이라 「나가기」를 눌렀는데 「기권」을 묻는 꼴이었다. 판을 두고 나가는 셋째 단추는 두지 않는다 —
 * 판은 자동 저장되어 앱을 닫아도 「이어하기」로 돌아오고, 남으려면 「취소」다. 대국 화면 아래 「기권」 단추의 창은 그대로(`resignConfirm…`).
 * 네 언어의 폭을 한국어에 맞췄다(`DECISIONS.md` §1).
 */
private val LeaveGameConfirmTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국을 나갈까요?",
    UiLanguage.English to "Leave the game?",
    UiLanguage.Japanese to "対局を出ますか？",
    UiLanguage.ChineseSimplified to "要退出对局吗？",
)

private val LeaveGameConfirmBodies: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "나가려면 기권해야 해요.\n기권하면 패로 기록됩니다.",
    UiLanguage.English to "To leave, you must resign.\nResigning counts as a loss.",
    UiLanguage.Japanese to "出るには投了が必要です。\n投了は負けとして記録されます。",
    UiLanguage.ChineseSimplified to "退出需要先认输。\n认输将记为负局。",
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

/**
 * 계가 팝업 백 줄의 **접바둑 보정** 항(refactor backlog #89) — 면적계가 접바둑에서 백이 받는
 * 접바둑 돌 수(N)만큼의 점수다. [UiStrings.scoreTextDetailAreaKomi]가 "덤 k" 뒤에 붙인다.
 *
 * ⚠️ 이 항이 없으면 합계가 "돌 + 집 + 덤"보다 N 크게 찍혀 **계산이 틀린 것처럼** 읽힌다 —
 * 보정이 있는 판에서는 반드시 보여야 한다(`UiStringsGameFlowTest`가 네 언어를 지킨다).
 */
private val WhiteHandicapBonusTerms: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "접바둑 보정",
    UiLanguage.English to "Handicap bonus",
    UiLanguage.Japanese to "置き石補正",
    UiLanguage.ChineseSimplified to "让子补偿",
)

internal fun rematchActionFor(language: UiLanguage): String = RematchActions.getValue(language)

internal fun reviewGameActionFor(language: UiLanguage): String = ReviewGameActions.getValue(language)

internal fun exitGameActionFor(language: UiLanguage): String = ExitGameActions.getValue(language)

internal fun finishedGameHomeActionFor(language: UiLanguage): String = FinishedGameHomeActions.getValue(language)

internal fun leaveGameConfirmTitleFor(language: UiLanguage): String = LeaveGameConfirmTitles.getValue(language)

internal fun leaveGameConfirmBodyFor(language: UiLanguage): String = LeaveGameConfirmBodies.getValue(language)

internal fun passNoticeTitleFor(language: UiLanguage): String = PassNoticeTitles.getValue(language)

internal fun scoreNowPromptTitleFor(language: UiLanguage): String =
    ScoreNowPromptTitles.getValue(language)

internal fun scoreNowPromptBodyFor(language: UiLanguage): String =
    ScoreNowPromptBodies.getValue(language)

internal fun whiteHandicapBonusTermFor(language: UiLanguage): String =
    WhiteHandicapBonusTerms.getValue(language)
