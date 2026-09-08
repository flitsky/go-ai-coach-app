package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.guide.GuideSetupFacts
import com.worksoc.goaicoach.application.guide.GuideStep

/**
 * 첫돌이 가이드가 말하는 문구(백로그 #128). 구조는 `UiStringsLanding.kt`와 같다 — **기능 하나가
 * 쓰는 문구를 한 파일에** 모아 네 언어 파일을 통째로 건드리지 않는다.
 *
 * ## ⚠️ 여기 문구는 리플렉션 그물의 **사각지대**다
 *
 * `UiStringsTest`의 누락 그물은 `UiStrings`의 **String 필드**만 훑는다. 이 파일은 함수로 문구를
 * 내므로 그 그물이 못 본다 — `UiStringsBotCharacters`·`UiStringsStudyVideos`가 같은 처지라
 * **손으로 그물을 달았다.** `UiStringsGuideTest`가 그 일을 한다.
 *
 * ## ⚠️ 화면에 적힌 이름을 그대로 부른다
 *
 * ⑤가 설명하는 넷은 앱이 이미 라벨을 갖고 있다 — `strings.eval`(형세 보기),
 * `strings.topMovesAction`(추천 수 보기), `boardModeFull`/`boardModeInset`(최대/여백), 돋보기.
 * 가이드가 **다른 낱말로 부르면** 사용자가 화면에서 그것을 찾지 못한다. 그래서 이 표는 기능 이름을
 * 직접 쓰지 않고 **호출부가 넘겨주는 실제 라벨**을 끼워 넣는다(`inGameToolsBodyFor`).
 *
 * ## ⚠️ 게이트를 정직하게 말한다 (2026-09-09 사용자 결정 ⓓ)
 *
 * 형세 보기·추천 수는 **광고 1시간 또는 1회권**이 있어야 열린다. 기능만 말하고 조건을 빼면 잠긴
 * 사용자에게 거짓이 된다(#87과 같은 부류) — 그래서 ⑤ 문구가 **여는 방법을 함께** 말한다.
 */

/** ② 출석 보상 팝업 안에서 첫돌이가 하는 한 줄. */
private val AttendanceClaimBody: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "매일 들어오면 제가 1회권과 새 친구를 챙겨 드려요.",
    UiLanguage.English to "Drop in daily and I'll set aside tickets and new friends for you.",
    UiLanguage.Japanese to "毎日来てくれたら、1回券と新しい仲間を用意しておきます。",
    UiLanguage.ChineseSimplified to "每天来的话，我会为您准备单次券和新伙伴。",
)

/** ③ 홈 `대국 하기` 카드 우상단 말풍선 — 사용자 원문을 그대로 쓴다. */
private val HomeStartMatchBody: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "대국 하기를 통해 바둑을 즐길 수 있어요",
    UiLanguage.English to "Tap Play a Match to start a game with me",
    UiLanguage.Japanese to "「対局する」から囲碁を楽しめます",
    UiLanguage.ChineseSimplified to "点击「开始对局」就能下棋了",
)

/**
 * ④ 대국 설정 화면.
 *
 * ⚠️ **기력을 말하지 않는다**(2026-09-09 사용자 결정). 사용자 원문은 *"(기력)을 선택하셨으니"* 였는데
 * `applyLandingSetup`이 `SelfRatedSkill`을 **버려서** 앱이 그것을 기억하지 않는다. 대신 **살아 있는
 * 저장값**(접바둑 점수·좌석)에서 파생하므로, 랜딩을 건너뛴 사용자와 나중에 설정을 바꾼 사용자에게도
 * 거짓이 되지 않는다. 사유 전문은 `GuideSetupFacts`의 KDoc.
 */
private fun matchSetupBody(language: UiLanguage, facts: GuideSetupFacts): String = when (language) {
    // ⚠️ 이름·라벨 뒤에 조사를 붙이지 않는다 — 랜딩이 같은 함정을 문장 끊기로 풀었다
    // (`UiStringsLanding.kt`의 `landingSkillResultFor` 주석).
    UiLanguage.Korean -> when (facts.shape) {
        GuideSetupFacts.Shape.Even -> "기본 상대인 저와 먼저 한 판 두시겠어요? 지금은 호선으로 맞춰 뒀어요."
        GuideSetupFacts.Shape.HumanTakesStones ->
            "기본 상대인 저와 먼저 한 판 두시겠어요? 지금은 ${facts.handicapCount}점 접바둑으로 맞춰 뒀어요."
        GuideSetupFacts.Shape.HumanGivesStones ->
            "기본 상대인 저와 먼저 한 판 두시겠어요? 지금은 제가 ${facts.handicapCount}점을 받고 시작해요."
    } + " 실력에 맞춰 언제든 바꾸실 수 있어요."
    UiLanguage.English -> when (facts.shape) {
        GuideSetupFacts.Shape.Even -> "Play me first — I'm the opponent you start with. We're set to an even game."
        GuideSetupFacts.Shape.HumanTakesStones ->
            "Play me first — I'm the opponent you start with. You're set to take ${facts.handicapCount} handicap stones."
        GuideSetupFacts.Shape.HumanGivesStones ->
            "Play me first — I'm the opponent you start with. I'm set to take ${facts.handicapCount} handicap stones."
    } + " Change it any time as you improve."
    UiLanguage.Japanese -> when (facts.shape) {
        GuideSetupFacts.Shape.Even -> "まずは最初の相手である私と一局どうですか。今は互先の設定です。"
        GuideSetupFacts.Shape.HumanTakesStones ->
            "まずは最初の相手である私と一局どうですか。今は${facts.handicapCount}子局の設定です。"
        GuideSetupFacts.Shape.HumanGivesStones ->
            "まずは最初の相手である私と一局どうですか。今は私が${facts.handicapCount}子置く設定です。"
    } + "上達に合わせていつでも変更できます。"
    UiLanguage.ChineseSimplified -> when (facts.shape) {
        GuideSetupFacts.Shape.Even -> "先和我下一局吧，我是您的第一位对手。目前设置为分先。"
        GuideSetupFacts.Shape.HumanTakesStones ->
            "先和我下一局吧，我是您的第一位对手。目前设置为您受${facts.handicapCount}子。"
        GuideSetupFacts.Shape.HumanGivesStones ->
            "先和我下一局吧，我是您的第一位对手。目前设置为我受${facts.handicapCount}子。"
    } + "随着水平提高可随时调整。"
}

/**
 * ⑤ 대국 화면의 도구 넷.
 *
 * 기능 이름을 **호출부가 화면에서 쓰는 실제 라벨로** 넘겨준다 — 가이드가 다른 낱말로 부르면
 * 사용자가 화면에서 그것을 찾지 못한다.
 */
private fun inGameToolsBody(
    language: UiLanguage,
    magnifier: String,
    boardMax: String,
    eval: String,
    topMoves: String,
): String = when (language) {
    UiLanguage.Korean ->
        "돌을 놓을 때 «$magnifier»가 손끝을 확대해 주고, «$boardMax»로 반상을 화면 꽉 채워 볼 수 있어요.\n" +
            "«$eval»는 지금 누구 집인지, «$topMoves»는 제가 추천하는 자리를 보여 줘요 — " +
            "출석해서 받은 1회권이나 짧은 광고 한 번으로 여실 수 있어요."
    UiLanguage.English ->
        "While you place a stone, «$magnifier» zooms in under your finger, and «$boardMax» fills the screen with the board.\n" +
            "«$eval» shows whose territory is whose and «$topMoves» shows where I'd play — " +
            "open either with a ticket from a daily check-in or one short ad."
    UiLanguage.Japanese ->
        "石を置くとき「$magnifier」が指先を拡大し、「$boardMax」で盤面を画面いっぱいに見られます。\n" +
            "「$eval」は今どちらの地かを、「$topMoves」は私のおすすめの場所を見せます — " +
            "出席でもらった1回券か、短い広告1本で開けられます。"
    UiLanguage.ChineseSimplified ->
        "落子时「$magnifier」会放大指尖处，用「$boardMax」可让棋盘铺满屏幕。\n" +
            "「$eval」显示当前双方的地，「$topMoves」显示我推荐的落点 — " +
            "用签到获得的单次券或看一段短广告即可开启。"
}

/**
 * 단계별 본문. ④⑤는 값이 필요하므로 [facts]·[toolLabels]를 받는다.
 *
 * ⚠️ **`when`을 exhaustive로 유지할 것** — 단계를 더하면서 여기 분기를 빠뜨리면 그 단계가 조용히
 * 빈 문구로 뜬다. `else`를 넣지 말 것(넣는 순간 컴파일러가 알려 주지 않는다).
 */
internal fun guideBodyFor(
    language: UiLanguage,
    step: GuideStep,
    facts: GuideSetupFacts? = null,
    toolLabels: GuideToolLabels? = null,
): String = when (step) {
    // ①은 문구 없는 정적 장식이다(판정에도 참여하지 않는다) — 부를 일이 없지만 빈 문자열로 닫는다.
    GuideStep.Landing -> ""
    GuideStep.AttendanceClaim -> AttendanceClaimBody.getValue(language)
    GuideStep.HomeStartMatch -> HomeStartMatchBody.getValue(language)
    GuideStep.MatchSetup -> matchSetupBody(language, facts ?: GuideSetupFacts(0, humanPlaysBlack = true))
    GuideStep.InGameTools -> toolLabels?.let {
        inGameToolsBody(language, it.magnifier, it.boardMax, it.eval, it.topMoves)
    } ?: ""
}

/** ⑤가 인용할 **화면에 적힌 그대로의** 라벨 넷. */
internal data class GuideToolLabels(
    val magnifier: String,
    val boardMax: String,
    val eval: String,
    val topMoves: String,
)
