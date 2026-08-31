package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.preferences.SelfRatedSkill
import com.worksoc.goaicoach.application.preferences.landingSetupPlan
import com.worksoc.goaicoach.shared.PlayLevelGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 #51 — 랜딩 문구가 네 언어에 빠짐없이 있고, 다른 축과 말이 어긋나지 않는지 본다. */
class UiStringsLandingTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyLandingStringExistsInEveryLanguage() {
        // `getValue`가 빠진 키에서 던지므로, 네 언어를 전부 훑는 것만으로 누락이 잡힌다.
        languages.forEach { language ->
            listOf(
                landingTitleFor(language),
                landingSubtitleFor(language),
                landingSkillQuestionFor(language),
                landingRulesetQuestionFor(language),
                landingSkipActionFor(language),
                landingStartActionFor(language),
            ).forEach { text -> assertTrue("$language 문구가 비었다", text.isNotBlank()) }

            SelfRatedSkill.entries.forEach { skill ->
                assertTrue(
                    "$language / $skill 보기가 비었다",
                    landingSkillLabelFor(language, skill).isNotBlank(),
                )
                assertTrue(
                    "$language / $skill 결과 안내가 비었다",
                    landingSkillResultFor(language, skill).isNotBlank(),
                )
            }
        }
    }

    /**
     * ⚠️ **이 항목에서 가장 중요한 테스트다.** 자기평가 보기(입문~최상급)와 봇 5단계 이름
     * (초보~초고수)이 **한 낱말도 겹치면 안 된다** — 겹치면 "최상급"이라 답한 사용자가 "초보와
     * 두세요"라는 안내를 받는 화면이 된다(2026-08-31 사용자와 확정한 사안).
     *
     * 봇 티어명을 나중에 바꾸더라도 이 테스트가 먼저 막아 준다.
     */
    @Test
    fun selfRatingLabelsNeverCollideWithBotTierLabels() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            val tierLabels = (1..PlayLevelGroup.FastBeginner.maxLevel)
                .map { level -> strings.fastBeginnerTierLabel(level) }
                .toSet()
            SelfRatedSkill.entries.forEach { skill ->
                val label = landingSkillLabelFor(language, skill)
                assertFalse(
                    "$language: 자기평가 '$label'이 봇 티어명과 겹친다 ($tierLabels)",
                    label in tierLabels,
                )
            }
        }
    }

    /** 랜딩이 이름을 불러오는 상대가 카탈로그의 1단계와 같은지 — id가 어긋나면 문구에 id가 그대로 뜬다. */
    @Test
    fun theLandingNamesTheFirstTierCharacter() {
        val firstTier = BotCharacterCatalog.fastBeginnerRoster.first()
        val name = botCharacterNameFor(UiLanguage.Korean, firstTier.id)
        assertEquals("첫돌이", name)
        assertTrue(landingSkillResultFor(UiLanguage.Korean, SelfRatedSkill.Entry).contains(name))
    }

    /**
     * ⚠️ 문구의 숫자는 손으로 적은 것이 아니라 [landingSetupPlan]에서 읽어 온다. 표와 문구가
     * 따로 놀면 "3점으로 맞췄다"고 안내해 놓고 5점이 저장되는 사고가 난다.
     */
    @Test
    fun resultCopyQuotesTheStoneCountThePlanActuallyUses() {
        SelfRatedSkill.entries.forEach { skill ->
            val stones = landingSetupPlan(skill).handicapCount
            val copy = landingSkillResultFor(UiLanguage.Korean, skill)
            if (stones == 0) {
                assertTrue("호선인데 점수가 적혔다: $copy", copy.contains("호선"))
            } else {
                assertTrue("$skill: '${stones}점'이 문구에 없다 — $copy", copy.contains("${stones}점"))
            }
        }
    }

    /**
     * ⚠️ 상대를 얕잡아 말하지 않는다 — 1단계도 실기력은 일반 중급자를 상회한다(사용자 확인).
     * "쉬운/약한" 류의 표현이 들어가면 첫 판에서 진 사용자가 속았다고 느낀다.
     */
    @Test
    fun resultCopyNeverCallsTheOpponentEasy() {
        val banned = listOf("쉬운", "약한", "easy", "weak", "簡単", "弱い", "简单", "弱")
        languages.forEach { language ->
            SelfRatedSkill.entries.forEach { skill ->
                val copy = landingSkillResultFor(language, skill)
                banned.forEach { word ->
                    assertFalse(
                        "$language / $skill 문구가 상대를 얕잡아 말한다('$word'): $copy",
                        copy.contains(word, ignoreCase = true),
                    )
                }
            }
        }
    }
}
