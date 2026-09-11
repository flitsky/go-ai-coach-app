package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.preferences.SelfRatedSkill
import com.worksoc.goaicoach.application.preferences.landingSetupPlan

/**
 * 첫 실행 랜딩 화면의 문구(백로그 #51). 구조는 `UiStringsStudyVideos.kt`와 같다 — 화면 하나가
 * 쓰는 문구를 한 파일에 모아, 네 언어 파일을 통째로 건드리지 않는다.
 *
 * ## ⚠️ 이 화면 문구의 원칙: **상대를 얕잡아 말하지 않는다**
 *
 * 세 답이 전부 1단계를 상대로 붙지만, **1단계도 실제 기력은 일반 중급자를 상회한다**
 * (2026-08-31 사용자 확인 — 애초에 그래서 호선이 아니라 접바둑을 넣었다). 그래서 결과 안내를
 * "쉬운 상대를 붙였어요"로 쓰면 **거짓말이 되고, 첫 판에서 진 사용자가 배신감을 느낀다.**
 * 문구는 "상대는 그대로 두고 **돌로 균형을 맞췄다**"는 사실만 말한다.
 *
 * ⚠️ 보기 셋(입문자/중급자/상급자)은 **봇 5단계 이름(초보/하수/중수/고수/초고수)과 일부러 다른
 * 낱말이다.** 같은 낱말을 쓰면 "고수"라고 답한 사람이 "상대는 첫돌이(초보)"라는 안내를 받는
 * 꼴이 된다. 봇 이름을 바꾸더라도 **두 축이 겹치지 않는지 먼저 확인할 것.**
 * · **2026-09-09 초안이 실제로 여기 걸렸다.** 다섯을 셋으로 줄이며 받은 초안의 낱말이
 *   *"입문자 / 중수 / 고수"* 였는데 **뒤의 둘이 봇 2·4단계 이름과 정확히 겹쳤다.** 사람 어감은
 *   살리고 충돌만 피하려고 `-자`를 붙여 **입문자/중급자/상급자**로 옮겼다.
 *
 * ⚠️ 결과 문구는 **첫돌이가 1인칭으로 말한다**(2026-09-09). 화면에 [FirstDolAvatar]가 함께
 * 떠 있어 화자가 분명하고, 초안 자체가 *"제가 2점을 깔고 도전해볼게요"* 처럼 1인칭이었다.
 */
private val LandingTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "포켓 바둑 코치에 오신 걸 환영합니다",
    UiLanguage.English to "Welcome to Go AI Coach",
    UiLanguage.Japanese to "囲碁AIコーチアプリへようこそ",
    UiLanguage.ChineseSimplified to "欢迎使用围棋 AI 教练应用",
)

private val LandingSubtitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "몇 가지만 고르면 바로 시작할 수 있어요.",
    UiLanguage.English to "Answer a couple of questions and you're ready to play.",
    UiLanguage.Japanese to "いくつか選ぶだけですぐに始められます。",
    UiLanguage.ChineseSimplified to "只需选择几项即可开始。",
)

private val SkillQuestions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "당신의 바둑 실력은?",
    UiLanguage.English to "How would you rate your Go?",
    UiLanguage.Japanese to "あなたの囲碁の実力は？",
    UiLanguage.ChineseSimplified to "您的围棋水平如何？",
)

private val RulesetQuestions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "계가 방식은?",
    UiLanguage.English to "Which scoring rules?",
    UiLanguage.Japanese to "計算方法は？",
    UiLanguage.ChineseSimplified to "采用哪种计算方式？",
)

private val SkipActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "나중에 할게요",
    UiLanguage.English to "Maybe later",
    UiLanguage.Japanese to "あとで設定する",
    UiLanguage.ChineseSimplified to "稍后再说",
)

private val StartActions: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "설정 완료",
    UiLanguage.English to "All set",
    UiLanguage.Japanese to "設定完了",
    UiLanguage.ChineseSimplified to "完成设置",
)

/** 자기 실력 보기. ⚠️ 봇 티어명과 겹치지 않는 어휘를 쓴다(위 주석 참고). */
private val SkillLabels: Map<SelfRatedSkill, Map<UiLanguage, String>> = mapOf(
    SelfRatedSkill.Entry to mapOf(
        UiLanguage.Korean to "입문자",
        UiLanguage.English to "New to Go",
        // ⚠️ **일본어는 `者`를 붙이지 않는다.** 봇 티어명이 `初心者/初級者/中級者/上級者/達人`이라
        // `者`를 붙이는 순간 그대로 겹친다 — 2026-09-09에 실제로 붙였다가
        // `selfRatingLabelsNeverCollideWithBotTierLabels`에 걸렸다. 한국어는 봇 이름이
        // `초보/하수/중수/고수/초고수`라 `-자`가 오히려 충돌을 피하는 장치가 된다(언어마다 다르다).
        UiLanguage.Japanese to "入門",
        UiLanguage.ChineseSimplified to "入门",
    ),
    SelfRatedSkill.Intermediate to mapOf(
        UiLanguage.Korean to "중급자",
        UiLanguage.English to "Club level",
        UiLanguage.Japanese to "中級",
        UiLanguage.ChineseSimplified to "中级",
    ),
    SelfRatedSkill.Advanced to mapOf(
        UiLanguage.Korean to "상급자",
        UiLanguage.English to "Strong",
        UiLanguage.Japanese to "上級",
        UiLanguage.ChineseSimplified to "高级",
    ),
)

internal fun landingTitleFor(language: UiLanguage): String = LandingTitles.getValue(language)

internal fun landingSubtitleFor(language: UiLanguage): String = LandingSubtitles.getValue(language)

internal fun landingSkillQuestionFor(language: UiLanguage): String = SkillQuestions.getValue(language)

internal fun landingRulesetQuestionFor(language: UiLanguage): String = RulesetQuestions.getValue(language)

internal fun landingSkipActionFor(language: UiLanguage): String = SkipActions.getValue(language)

internal fun landingStartActionFor(language: UiLanguage): String = StartActions.getValue(language)

internal fun landingSkillLabelFor(language: UiLanguage, skill: SelfRatedSkill): String =
    SkillLabels.getValue(skill).getValue(language)

/**
 * 답을 고른 뒤 보여 주는 결과 안내. **접바둑 돌 수와 좌석은 문구가 아니라
 * [landingSetupPlan]에서 읽어 온다** — 표와 문구가 따로 놀면 "3점으로 맞췄다"고 해 놓고 5점이
 * 들어가는 사고가 난다.
 *
 * ⚠️ 상대를 "쉬운"이라고 표현하지 않는다(파일 첫머리 원칙).
 */
internal fun landingSkillResultFor(language: UiLanguage, skill: SelfRatedSkill): String {
    val plan = landingSetupPlan(skill)
    val opponent = botCharacterNameFor(language, FirstDolCharacterId)
    val stones = plan.handicapCount
    return when (language) {
        // ⚠️ 이름 뒤에 조사를 붙이지 않는다 — "첫돌이와/를"처럼 붙여 쓰면 받침 있는 이름으로
        // 바뀌는 순간(예: "돌뫼은") 전부 틀린 조사가 된다. **이름 뒤는 콜론으로 끊는다** —
        // 1인칭으로 바뀌면서 "상대는 X," 대신 "X: …" 가 됐지만, 조사를 피한다는 규칙은 그대로다.
        UiLanguage.Korean -> when {
            stones == 0 -> "$opponent: 저와 호선으로 겨뤄 봐요. 접바둑 없이 처음부터 대등하게 시작할게요."
            plan.humanPlaysBlack -> "$opponent: ${stones}점을 깔고 저와 둬 봐요. 두어 보고 버겁다면 돌을 더 늘려도 좋아요."
            else -> "$opponent: 제가 ${stones}점을 깔고 도전해 볼게요. 백을 잡아 주세요."
        } + " 대국 설정에서 언제든 바꿀 수 있어요."
        UiLanguage.English -> when {
            stones == 0 -> "$opponent: let's play an even game — no handicap, level from the very first move."
            plan.humanPlaysBlack -> "$opponent: take $stones handicap stones and play me. If it still feels steep, add a few more."
            else -> "$opponent: I'll take $stones handicap stones and challenge you. You play White."
        } + " Change it any time in match setup."
        UiLanguage.Japanese -> when {
            stones == 0 -> "$opponent：互先で打ち合いましょう。置き石なしで最初から対等に始めます。"
            plan.humanPlaysBlack -> "$opponent：${stones}子置いて打ってみましょう。打ってみて厳しければ石を増やしても大丈夫です。"
            else -> "$opponent：私が${stones}子置いて挑みます。白番でお願いします。"
        } + "対局設定でいつでも変更できます。"
        UiLanguage.ChineseSimplified -> when {
            stones == 0 -> "$opponent：我们分先下一盘吧。不让子，从第一手起就势均力敌。"
            plan.humanPlaysBlack -> "$opponent：您受${stones}子和我下一盘吧。若仍觉得吃力，可以再多加几子。"
            else -> "$opponent：我受${stones}子向您挑战，请您执白。"
        } + "可随时在对局设置中修改。"
    }
}

