package com.worksoc.goaicoach.ui

/**
 * 학습 화면 강좌 세 편의 **소개 문구**(백로그 #33). 구조는 `UiStringsBotCharacters.kt`와 같다 —
 * 짧은 id를 키로 네 언어를 들고, 목록([studyVideoEntries])은 URL·썸네일만 갖는다.
 *
 * ## ⚠️ 강의 자체는 한국어다
 *
 * 이 셋은 한국어 유튜브 강의(바둑에듀)라, **소개만 번역하면 못 알아들을 영상을 그 언어로
 * 권하는 셈**이 된다. 그래서 착수 전에 셋 중 하나를 골라야 했다 — ⓐ 번역하되 한국어임을
 * 밝힌다 / ⓑ 언어별로 다른 영상을 고른다 / ⓒ 비한국어에서는 학습 카드를 숨긴다.
 *
 * **ⓐ로 갔다(2026-08-30 사용자 결정).** 근거는 유튜브가 자동 번역 자막을 제공한다는 것이다 —
 * 자막을 켜면 실제로 따라갈 수 있으므로, 카드를 숨기거나(ⓒ) 새 영상을 찾을 때까지(ⓑ) 비워
 * 두는 것보다 낫다. 다만 **자동 자막은 품질이 들쭉날쭉하고 영상에 따라 없을 수도 있어**,
 * 들어가 보고 알게 하는 대신 **비한국어 문구 끝에 `· 한국어` 표기를 붙여** 미리 알린다.
 * 채널명 뒤 괄호 안에 얹어 두 글자만 늘렸다 — 줄이 길어지면 한 줄 소개가 말줄임된다.
 *
 * ⚠️ **한국어 문구에는 그 표기가 없다.** 한국어 사용자에게 "한국어"라고 알릴 이유가 없고,
 * 붙이면 오히려 무슨 뜻인지 되묻게 된다.
 *
 * 영상을 다른 언어권 강의로 교체한다면 그 언어의 `· 한국어` 표기부터 걷어낼 것.
 */
private val StudyVideoDescriptions: Map<String, Map<UiLanguage, String>> = mapOf(
    "rules" to mapOf(
        UiLanguage.Korean to "바둑을 처음 배우는 분을 위한 10분 기초 규칙 강의 (바둑에듀)",
        UiLanguage.English to "Ten-minute intro to the basic rules, for absolute beginners (Baduk Edu · Korean)",
        UiLanguage.Japanese to "囲碁を初めて学ぶ方向け、10分の基本ルール講座（バドゥクエデュ・韓国語）",
        UiLanguage.ChineseSimplified to "面向零基础的十分钟围棋规则入门（Baduk Edu · 韩语）",
    ),
    "shapes" to mapOf(
        UiLanguage.Korean to "입구자·날일자 등 바둑 기초 행마를 10분에 익히기 (바둑에듀)",
        UiLanguage.English to "Basic shapes and how stones move, in ten minutes (Baduk Edu · Korean)",
        UiLanguage.Japanese to "コスミ・ケイマなど基本の打ち方を10分で（バドゥクエデュ・韓国語）",
        UiLanguage.ChineseSimplified to "十分钟掌握尖、飞等基本行棋（Baduk Edu · 韩语）",
    ),
    "life_and_death" to mapOf(
        UiLanguage.Korean to "삶의 조건과 빅, 입문자가 꼭 알아야 할 바둑 기초 개념 (바둑에듀)",
        UiLanguage.English to "Life and death and seki — the concepts every beginner needs (Baduk Edu · Korean)",
        UiLanguage.Japanese to "生き死にとセキ、入門者が必ず知るべき基本概念（バドゥクエデュ・韓国語）",
        UiLanguage.ChineseSimplified to "死活与双活，入门者必知的基本概念（Baduk Edu · 韩语）",
    ),
)

/**
 * 표에 없는 id면 [id]를 그대로 돌려준다 — `UiStringsBotCharacters.kt`의 폴백과 같은 이유다.
 * 빈칸이면 조용히 지나가지만, 화면에 `shapes`라고 뜨면 눈에 띄어 바로 고친다.
 */
internal fun studyVideoDescriptionFor(language: UiLanguage, id: String): String =
    StudyVideoDescriptions[id]?.get(language) ?: id
