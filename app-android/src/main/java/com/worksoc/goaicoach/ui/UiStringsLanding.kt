package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterId
import com.worksoc.goaicoach.application.preferences.SelfRatedSkill
import com.worksoc.goaicoach.application.preferences.landingSetupPlan

/**
 * 첫 실행 랜딩 화면의 문구(백로그 #51). 구조는 `UiStringsStudyVideos.kt`와 같다 — 화면 하나가
 * 쓰는 문구를 한 파일에 모아, 네 언어 파일을 통째로 건드리지 않는다.
 *
 * ## ⚠️ 이 화면 문구의 원칙: **상대를 얕잡아 말하지 않는다**
 *
 * 다섯 답이 전부 1단계를 상대로 붙지만, **1단계도 실제 기력은 일반 중급자를 상회한다**
 * (2026-08-31 사용자 확인 — 애초에 그래서 호선이 아니라 접바둑을 넣었다). 그래서 결과 안내를
 * "쉬운 상대를 붙였어요"로 쓰면 **거짓말이 되고, 첫 판에서 진 사용자가 배신감을 느낀다.**
 * 문구는 "상대는 그대로 두고 **돌로 균형을 맞췄다**"는 사실만 말한다.
 *
 * ⚠️ 보기 다섯 개(입문/초급/중급/상급/최상급)는 **봇 5단계 이름(초보/하수/중수/고수/초고수)과
 * 일부러 다른 낱말이다.** 같은 낱말을 쓰면 "최상급"이라 답한 사람이 "초보와 두세요"라는 안내를
 * 받는 꼴이 된다. 봇 이름을 바꾸더라도 **두 축이 겹치지 않는지 먼저 확인할 것.**
 */
private val LandingTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "바둑 AI 앱에 오신 걸 환영합니다",
    UiLanguage.English to "Welcome to Go AI Coach",
    UiLanguage.Japanese to "囲碁AIアプリへようこそ",
    UiLanguage.ChineseSimplified to "欢迎使用围棋 AI 应用",
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
        UiLanguage.Korean to "입문",
        UiLanguage.English to "New to Go",
        UiLanguage.Japanese to "入門",
        UiLanguage.ChineseSimplified to "入门",
    ),
    SelfRatedSkill.Beginner to mapOf(
        UiLanguage.Korean to "초급",
        UiLanguage.English to "Casual",
        UiLanguage.Japanese to "初級",
        UiLanguage.ChineseSimplified to "初级",
    ),
    SelfRatedSkill.Intermediate to mapOf(
        UiLanguage.Korean to "중급",
        UiLanguage.English to "Club level",
        UiLanguage.Japanese to "中級",
        UiLanguage.ChineseSimplified to "中级",
    ),
    SelfRatedSkill.Advanced to mapOf(
        UiLanguage.Korean to "상급",
        UiLanguage.English to "Strong",
        UiLanguage.Japanese to "上級",
        UiLanguage.ChineseSimplified to "高级",
    ),
    SelfRatedSkill.Expert to mapOf(
        UiLanguage.Korean to "최상급",
        UiLanguage.English to "Very strong",
        UiLanguage.Japanese to "最上級",
        UiLanguage.ChineseSimplified to "顶级",
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
    val opponent = botCharacterNameFor(language, firstTierCharacterId)
    val stones = plan.handicapCount
    return when (language) {
        // ⚠️ 이름 뒤에 조사를 붙이지 않는다 — "첫돌이와/를"처럼 붙여 쓰면 받침 있는 이름으로
        // 바뀌는 순간(예: "돌뫼은") 전부 틀린 조사가 된다. "상대는 X," 형태로 끊어 둔다.
        UiLanguage.Korean -> when {
            stones == 0 -> "상대는 $opponent, 호선으로 시작하도록 맞췄어요."
            plan.humanPlaysBlack -> "상대는 $opponent, ${stones}점 접바둑으로 흑을 잡고 시작하도록 맞췄어요."
            else -> "상대는 $opponent, ${stones}점을 접어 주고 백을 잡도록 맞췄어요."
        } + " 대국 설정에서 언제든 바꿀 수 있어요."
        UiLanguage.English -> when {
            stones == 0 -> "Your opponent is $opponent, playing on even terms."
            plan.humanPlaysBlack -> "Your opponent is $opponent. You take Black with $stones handicap stones."
            else -> "Your opponent is $opponent, taking $stones handicap stones. You play White."
        } + " Change it any time in match setup."
        UiLanguage.Japanese -> when {
            stones == 0 -> "相手は$opponent、互先で始める設定にしました。"
            plan.humanPlaysBlack -> "相手は$opponent、${stones}子局で黒番から始める設定にしました。"
            else -> "相手は$opponent、${stones}子置かせて白番で打つ設定にしました。"
        } + "対局設定でいつでも変更できます。"
        UiLanguage.ChineseSimplified -> when {
            stones == 0 -> "对手为$opponent，分先对局。"
            plan.humanPlaysBlack -> "对手为$opponent，您受${stones}子执黑。"
            else -> "对手为$opponent，您让${stones}子执白。"
        } + "可随时在对局设置中修改。"
    }
}

/** 랜딩이 붙여 주는 상대. 카탈로그의 1단계 id와 같아야 한다(`LandingCopyTest`가 확인한다). */
private val firstTierCharacterId = BotCharacterId("fast_beginner_1")
