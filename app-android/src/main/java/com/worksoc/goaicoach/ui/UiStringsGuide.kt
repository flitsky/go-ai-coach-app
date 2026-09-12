package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.guide.GuideStep

/**
 * 첫돌이 가이드가 말하는 문구(백로그 #128). 구조는 `UiStringsStudyVideos.kt`와 같다 — **기능 하나가
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
 * ④ 대국 설정 화면 — **한 가지 문구**(백로그 #140, 2026-09-11 사용자 결정).
 *
 * 한국어는 사용자 문구를 **그대로** 쓰고, 다른 셋은 그 뜻을 옮겼다. 첫 줄은 권유, 둘째 줄은
 * *"결과를 보고 접바둑으로 조정할 수 있다"* 는 안내다 — 줄바꿈도 사용자 문구의 일부다.
 *
 * ## ⚠️ 설정을 인용하지 않는다 — 그래서 인자가 없다
 * #128은 이 문구를 **살아 있는 접바둑 값**(`GuideSetupFacts`: 호선/흑 N점/백 N점 세 갈래)에서
 * 파생했다. 랜딩이 실력에 따라 5점·3점·후수를 배정하던 때라 첫 판의 모양이 사람마다 달랐기
 * 때문이다. #140이 랜딩을 없애 **첫 판이 늘 첫돌이와 호선**이 되면서 갈래가 필요 없어졌고,
 * 사용자는 *"일관된 가이드"* 를 원했다 — 그래서 갈래와 `GuideSetupFacts`를 함께 걷어냈다.
 * ⚠️ 이 문구는 **권유**다(*"호선으로 둬봐요"*) — "지금 호선으로 맞춰 뒀다"는 **사실 진술이 아니므로**,
 * 가이드 다시보기에서 접바둑으로 바꿔 둔 사용자가 읽어도 거짓이 되지 않는다. 사실 진술로 바꾸려면
 * 갈래를 되살려야 한다.
 */
private val MatchSetupBody: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "저와 함께 호선으로 둬봐요.\n결과 보시고 접바둑으로 조정도 가능하답니다.",
    UiLanguage.English to "Let's play an even game together.\nOnce you see how it goes, you can switch to a handicap game.",
    UiLanguage.Japanese to "私と一緒に互先で打ってみましょう。\n結果を見て、置碁に調整することもできますよ。",
    UiLanguage.ChineseSimplified to "和我一起下一盘分先吧。\n看看结果，也可以调整为让子棋哦。",
)

/**
 * ⑤ 대국 화면 — **버튼마다 한 마디**(2026-09-09 사용자 지시로 한 장에서 넷으로 쪼갰다).
 *
 * 각 문구는 **그 버튼 옆에서** 뜨고 동그라미가 그 버튼을 가리키므로, 문구가 스스로 *"어느 버튼
 * 이야기인가"* 를 설명할 필요가 없다 — 짧게 **무엇을 해 주는지**만 말한다.
 *
 * ⚠️ 라벨은 **호출부가 화면에서 읽어 넘긴다.** 실기에서 처음에 어긋났다 — 설정 화면 라벨
 * (`돋보기 창 크기`)을 인용했는데 판 위 토글은 `착수 돋보기`였다.
 *
 * ⚠️ **게이트를 정직하게 말하는 것은 뒤의 둘뿐이다**(사용자 확정 ⓓ). 돋보기·바둑판 크기는 그냥
 * 켜지므로 조건을 붙이면 없는 문턱을 만드는 셈이 된다 — 형세 보기·추천 수에만 여는 방법을 적는다.
 */
private fun inGameEvalBody(language: UiLanguage, label: String): String = when (language) {
    UiLanguage.Korean -> "«$label»는 지금 누구 집인지 반상에 색으로 보여 줘요. 출석해서 받은 1회권이나 짧은 광고 한 번으로 여실 수 있어요."
    UiLanguage.English -> "«$label» shades the board to show whose territory is whose. Open it with a ticket from a daily check-in, or one short ad."
    UiLanguage.Japanese -> "「$label」は今どちらの地かを盤上に色で見せます。出席でもらった1回券か、短い広告1本で開けられます。"
    UiLanguage.ChineseSimplified -> "「$label」会在棋盘上用颜色显示当前双方的地。用签到获得的单次券或看一段短广告即可开启。"
}

/**
 * ⚠️ **개수를 말하지 않는다**(2026-09-09 사용자 지적: *"항상 5개를 보여주는 게 아니다"*).
 *
 * 2026-09-09까지 네 언어가 *"자리 다섯 곳 / five spots / 5か所 / 五个点"* 이라고 적고 있었다.
 * `LightweightTopMoveCandidateCount = 5`는 엔진에 **요청하는** 수일 뿐이고, 화면에 찍히는 것은
 * `snapshot.candidatesForDisplay()` — **엔진이 실제로 점수를 매긴 것**이다(끝내기·좁은 판에서는
 * 그보다 적다. 엔진 메시지 자체가 `scored/legal`로 그 차이를 말한다). 그래서 문구는 **후보**라고만
 * 하고 수를 세지 않는다 — 하나가 뜨든 다섯이 뜨든 참이어야 한다.
 *
 * `UiStringsGuideTest`가 개수 낱말을 금지한다.
 */
private fun inGameTopMovesBody(language: UiLanguage, label: String): String = when (language) {
    UiLanguage.Korean -> "«$label»는 제가 보기에 좋은 최적수 후보를 반상에 표시해요. 이것도 1회권이나 광고 한 번으로 열려요."
    UiLanguage.English -> "«$label» marks the moves I'd consider best on the board. Same as Eval — a ticket or one short ad opens it."
    UiLanguage.Japanese -> "「$label」は私が良いと見る候補手を盤上に示します。こちらも1回券か広告1本で開きます。"
    UiLanguage.ChineseSimplified -> "「$label」会在棋盘上标出我认为不错的候选点。同样用单次券或一段短广告即可开启。"
}

/**
 * 단계별 본문. ⑤는 화면의 라벨이 필요하므로 [toolLabels]를 받는다.
 *
 * ⚠️ **`when`을 exhaustive로 유지할 것** — 단계를 더하면서 여기 분기를 빠뜨리면 그 단계가 조용히
 * 빈 문구로 뜬다. `else`를 넣지 말 것(넣는 순간 컴파일러가 알려 주지 않는다).
 */
internal fun guideBodyFor(
    language: UiLanguage,
    step: GuideStep,
    toolLabels: GuideToolLabels? = null,
): String = when (step) {
    GuideStep.AttendanceClaim -> AttendanceClaimBody.getValue(language)
    GuideStep.HomeStartMatch -> HomeStartMatchBody.getValue(language)
    GuideStep.MatchSetup -> MatchSetupBody.getValue(language)
    // ⑤ 넷은 각자 **자기 버튼의 라벨 하나만** 인용한다. 라벨이 없으면 *"«»를 켜 두면…"* 이라는
    // 빈 인용부호가 화면에 나가므로 폴백을 두지 않는다 — 조용한 거짓말보다 시끄러운 실패가 낫다
    // (2026-09-09 감사: 한때 `?: ""`로 조용히 넘어갔다).
    GuideStep.InGameEval -> inGameEvalBody(language, requireLabels(toolLabels).eval)
    GuideStep.InGameTopMoves -> inGameTopMovesBody(language, requireLabels(toolLabels).topMoves)
}

private fun requireLabels(toolLabels: GuideToolLabels?): GuideToolLabels =
    requireNotNull(toolLabels) { "⑤ 문구는 화면에 적힌 라벨 없이는 참일 수 없다(백로그 #128)" }

/**
 * 마이페이지에서 첫돌이가 건네는 한 줄(백로그 #128 ②의 둘째 자리).
 *
 * ⚠️ **단계가 아니다** — 판정에 참여하지 않고 늘 보인다. 마이페이지는 사용자가 *"내가 모은 것"* 을
 * 보러 오는 자리라, 그것을 함께 챙겨 온 상대가 거기 있는 것이 자연스럽다. 나중에 **가이드
 * 다시보기** 행이 이 옆에 붙는다.
 */
private val MyPageGreeting: Map<UiLanguage, String> = mapOf(
    // ⚠️ **이름을 문구에 박지 않는다.** 첫돌이의 이름은 언어마다 다르고(`botCharacterNameFor`)
    // 한국어는 뒤에 조사가 붙는다 — 박아 두면 네 언어 중 하나는 반드시 어색해진다. 옆에 얼굴이
    // 있으므로 누가 말하는지는 이미 보인다.
    UiLanguage.Korean to "여기서 그동안 모은 걸 볼 수 있어요.",
    UiLanguage.English to "Everything you've collected so far shows up here.",
    UiLanguage.Japanese to "ここで今まで集めたものを見られます。",
    UiLanguage.ChineseSimplified to "在这里可以看到您一直收集的内容。",
)

internal fun guideMyPageGreetingFor(language: UiLanguage): String = MyPageGreeting.getValue(language)

/**
 * ⑤가 인용할 **화면에 적힌 그대로의** 라벨 넷.
 *
 * ⚠️ **[boardSubject]는 토글의 현재 라벨이 아니라 "무엇의" 설정인가다**(`boardSizeSubjectFor`).
 * 판 위 토글은 상태에 따라 `바둑판 최대`/`바둑판 여백`으로 **글자가 바뀌므로** 그것을 인용하면
 * 사용자가 여백 상태로 들어온 순간 문구가 화면과 어긋난다 — 실기에서 처음에 그렇게 어긋났다
 * (`돋보기 창 크기`는 설정 화면 라벨이라 판 위 `착수 돋보기`와 다른 이름이었다).
 */
internal data class GuideToolLabels(
    val eval: String,
    val topMoves: String,
)
