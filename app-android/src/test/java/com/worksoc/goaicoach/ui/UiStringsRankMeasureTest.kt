package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.rankmeasure.RankMeasureChange
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureDemotionLosses
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureKyuPromotionCap
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureMarginPerStep
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureMaxDanPromotionSteps
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureMaxPromotionSteps
import com.worksoc.goaicoach.application.rankmeasure.RankPromotionOffer
import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePlayerSetup
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.kgsRankLabelFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureBoardChangedFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureBoardLimitNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureBoardSizeLabelFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChangeMessageFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChangeRanksFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChangeTitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChooseStartingRankFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureCurrentRankFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureKyuCapNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureNoAssistNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureOfficialNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureOverwriteWarningFor
import com.worksoc.goaicoach.ui.l10n.rankMeasurePeakRankFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureRulesFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureSideLabelFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureStartFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureStepCapNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureStrongerFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureSubtitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureTitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureWeakerFor
import com.worksoc.goaicoach.ui.l10n.rankPromotionChoiceReasonFor
import com.worksoc.goaicoach.ui.l10n.rankPromotionChoiceTitleFor
import com.worksoc.goaicoach.ui.l10n.rankPromotionStayFor
import com.worksoc.goaicoach.ui.l10n.rankPromotionUpFor
import com.worksoc.goaicoach.ui.l10n.rankedOpponentLabelFor
import com.worksoc.goaicoach.ui.l10n.rankedOpponentLineFor
import com.worksoc.goaicoach.ui.l10n.seatMatchupLabelWithRanksFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승급 대국(백로그 #219)의 문구 — 급수 표기와, 홈 카드·설정 창·기력 변동 알림·기록 표시. */
class UiStringsRankMeasureTest {
    private fun everyStringIn(language: UiLanguage): List<String> =
        listOf(
            kgsRankLabelFor(language, KgsRank.kyu(5)),
            kgsRankLabelFor(language, KgsRank.dan(3)),
            rankedOpponentLabelFor(language, KgsRank.kyu(5)),
            rankedOpponentLineFor(language, KgsRank.kyu(5)),
            rankMeasureTitleFor(language),
            rankMeasureSubtitleFor(language),
            rankMeasureCurrentRankFor(language, KgsRank.kyu(18)),
            rankMeasurePeakRankFor(language, KgsRank.kyu(15)),
            rankMeasurePeakRankFor(language, null),
            rankMeasureChooseStartingRankFor(language),
            rankMeasureWeakerFor(language),
            rankMeasureStrongerFor(language),
            rankMeasureRulesFor(language),
            rankMeasureNoAssistNoteFor(language),
            rankMeasureOfficialNoteFor(language),
            rankMeasureOverwriteWarningFor(language),
            rankMeasureSideLabelFor(language),
            rankMeasureBoardSizeLabelFor(language),
            rankMeasureBoardLimitNoteFor(language, KgsRank.kyu(9)),
            rankMeasureBoardLimitNoteFor(language, KgsRank.dan(1)),
            rankMeasureStartFor(language),
            rankMeasureBoardChangedFor(language, KgsRank.kyu(9), BoardSize(13)),
            rankMeasureKyuCapNoteFor(language),
            rankMeasureStepCapNoteFor(language),
        ) + popupChanges.flatMap { change ->
            listOfNotNull(rankMeasureChangeTitleFor(language, change), rankMeasureChangeRanksFor(language, change), rankMeasureChangeMessageFor(language, change))
        } + listOf(KgsRank.kyu(15), KgsRank.dan(3)).flatMap { rank ->
            listOf(
                rankPromotionChoiceTitleFor(language, rank),
                rankPromotionStayFor(language, rank),
                rankPromotionUpFor(language, rank, 1),
            )
        } + listOf(
            rankPromotionChoiceReasonFor(language, RankPromotionOffer(steps = 3, margin = 61.0)),
            rankPromotionChoiceReasonFor(language, RankPromotionOffer(steps = 2, margin = 41.0, byResignation = true)),
        )

    /** 팝업이 그리는 두 경우 — 강급 · 이미 9단. 오를 수 있는 승리는 팝업이 아니라 고르는 창이다(#268). */
    private val popupChanges: List<RankMeasureChange> = listOf(
        RankMeasureChange.Demoted(from = KgsRank.dan(1), to = KgsRank.kyu(1)),
        RankMeasureChange.AtTheTop,
    )

    @Test
    fun everyRankMeasureStringExistsInEveryLanguageWithoutFallingBackToKorean() {
        assertTrue("자기검증 — 한국어 문구에는 한글이 있어야 한다", everyStringIn(UiLanguage.Korean).all { it.containsHangul() })
        UiLanguage.entries.forEach { language ->
            val strings = everyStringIn(language)
            assertEquals("$language: 문구 수가 다르다(팝업과 고르는 창이 전부 문구를 가져야 한다)", 37, strings.size)
            strings.forEach { text ->
                assertTrue("$language 문구가 비었다", text.isNotBlank())
                if (language != UiLanguage.Korean) assertFalse("$language: 한글이 남았다 — $text", text.containsHangul())
            }
        }
    }

    /** 사용자가 정한 이름 그대로다 — 「승급 대국」(2026-10-09, 처음 이름은 「기력 측정 대국」이었다 · 백로그 #238). 기록이 없으면 「측정 기록 없음」(2026-10-06). */
    @Test
    fun theKoreanNameAndTheNoRecordWordingAreWhatTheUserChose() {
        assertEquals("승급 대국", rankMeasureTitleFor(UiLanguage.Korean))
        assertEquals("최고 기력: 측정 기록 없음", rankMeasurePeakRankFor(UiLanguage.Korean, null))
        assertEquals("현재 기력: 20급", rankMeasureCurrentRankFor(UiLanguage.Korean, KgsRank.kyu(20)))
    }

    /** 급과 단을 가른다 — 숫자만 같고 뜻이 반대라, 표기가 섞이면 5급과 5단이 같은 글자가 된다. */
    @Test
    fun kyuAndDanAreSpelledDifferentlyInEveryLanguage() {
        assertEquals(
            listOf("5급" to "5단", "5 kyu" to "5 dan", "5級" to "5段", "5级" to "5段"),
            listOf(UiLanguage.Korean, UiLanguage.English, UiLanguage.Japanese, UiLanguage.ChineseSimplified)
                .map { language -> kgsRankLabelFor(language, KgsRank.kyu(5)) to kgsRankLabelFor(language, KgsRank.dan(5)) },
        )
    }

    /** 설정 창은 숫자가 **공인 기력이 아니라는 것**을 밝힌다(사용자 2026-10-04) — 그 한 줄이 빠지면 급수가 실력 인증처럼 읽힌다. */
    @Test
    fun theDialogSaysTheRankIsNotACertifiedOne() {
        assertTrue(rankMeasureOfficialNoteFor(UiLanguage.Korean).contains("공인 기력이 아닙니다"))
        assertTrue(rankMeasureOfficialNoteFor(UiLanguage.English).contains("not a certified rank"))
    }

    /**
     * **승급을 고르는 창**(사용자 2026-10-15, #268) — 제목은 「승급하시겠습니까?」, 버튼은 사용자의 말 그대로 「현재 급수에 머무른다」 · 「+1급 승급한다」,
     * 괄호 안은 닿는 급수(스레드가 더했다). 단 구간은 「승단」·「단」이다. 까닭 한 줄은 이긴 집 수 차이(기권승이면 「약」).
     */
    @Test
    fun theChoiceDialogUsesTheUsersWords() {
        assertEquals("승급하시겠습니까?", rankPromotionChoiceTitleFor(UiLanguage.Korean, KgsRank.kyu(15)))
        assertEquals("현재 급수에 머무른다", rankPromotionStayFor(UiLanguage.Korean, KgsRank.kyu(15)))
        assertEquals(
            listOf("+1급 승급한다 (14급)", "+2급 승급한다 (13급)", "+3급 승급한다 (12급)"),
            (1..3).map { steps -> rankPromotionUpFor(UiLanguage.Korean, KgsRank.kyu(15), steps) },
        )
        assertEquals("+2급 승급한다 (1단)", rankPromotionUpFor(UiLanguage.Korean, KgsRank.kyu(2), 2))
        assertEquals("Up 1 rank (14 kyu)", rankPromotionUpFor(UiLanguage.English, KgsRank.kyu(15), 1))
        assertEquals("Up 3 ranks (12 kyu)", rankPromotionUpFor(UiLanguage.English, KgsRank.kyu(15), 3))

        assertEquals("승단하시겠습니까?", rankPromotionChoiceTitleFor(UiLanguage.Korean, KgsRank.dan(3)))
        assertEquals("현재 단에 머무른다", rankPromotionStayFor(UiLanguage.Korean, KgsRank.dan(3)))
        assertEquals("+1단 승단한다 (4단)", rankPromotionUpFor(UiLanguage.Korean, KgsRank.dan(3), 1))

        assertEquals("61집 차로 이겼습니다.", rankPromotionChoiceReasonFor(UiLanguage.Korean, RankPromotionOffer(steps = 3, margin = 61.0)))
        assertEquals("15.5집 차로 이겼습니다.", rankPromotionChoiceReasonFor(UiLanguage.Korean, RankPromotionOffer(steps = 1, margin = 15.5)))
        assertEquals(
            "상대가 약 41집 뒤진 형세에서 기권했습니다.",
            rankPromotionChoiceReasonFor(UiLanguage.Korean, RankPromotionOffer(steps = 2, margin = 41.0, byResignation = true)),
        )
    }

    /** 팝업은 강급(몇 연패로)과 이미 9단만 알린다. 기력이 그대로면 팝업이 없다. */
    @Test
    fun thePopupSaysWhyTheRankMoved() {
        val backToKyu = RankMeasureChange.Demoted(from = KgsRank.dan(1), to = KgsRank.kyu(1))
        assertEquals("강단", rankMeasureChangeTitleFor(UiLanguage.Korean, backToKyu))
        assertEquals("1단 → 1급", rankMeasureChangeRanksFor(UiLanguage.Korean, backToKyu))
        assertEquals("3연패로 한 단계 내려갑니다.", rankMeasureChangeMessageFor(UiLanguage.Korean, backToKyu))
        assertEquals("3 losses in a row — you move down one step.", rankMeasureChangeMessageFor(UiLanguage.English, backToKyu))

        UiLanguage.entries.forEach { language ->
            assertNull(rankMeasureChangeTitleFor(language, RankMeasureChange.None))
            assertNull(rankMeasureChangeMessageFor(language, RankMeasureChange.None))
            assertNull(rankMeasureChangeRanksFor(language, RankMeasureChange.AtTheTop))
        }
    }

    /**
     * 설정 창의 규칙 문구는 규칙과 **같은 숫자**를 말한다 — 15집마다 한 급 · 한 판에 최대 3급 · 고른다 · 15집 미만이면 그대로 · 1단까지 · 단은 15집 이상 ·
     * 3연패(사용자 2026-10-15, #268). 화면에서 읽은 것과 실제 동작이 다르면 그것이 가장 나쁜 결함이다(2026-10-07: 창에 없는 폭으로 올라 사용자가 물었다).
     */
    @Test
    fun theRulesTextStatesTheSameNumbersAsTheRule() {
        val rules = rankMeasureRulesFor(UiLanguage.Korean)
        listOf("15집마다 한 급씩", "최대 3급까지", "직접 고릅니다", "15집 미만이면 그대로", "1단까지", "15집 이상 차이로 이기면 한 단 오를지 고릅니다", "3연패하면 한 단계").forEach { phrase ->
            assertTrue("규칙 문구에 없다: $phrase", rules.contains(phrase))
        }
        assertFalse("옛 규칙(최근 5판 중 3판)이 문구에 남았다", rules.contains("5판"))
        assertFalse("옛 규칙(2연패)이 문구에 남았다", rules.contains("2연패"))
        assertEquals(15.0, RankMeasureMarginPerStep, 0.0)
        assertEquals(3, RankMeasureMaxPromotionSteps)
        assertEquals(1, RankMeasureMaxDanPromotionSteps)
        assertEquals(KgsRank.dan(1), RankMeasureKyuPromotionCap)
        assertEquals(3, RankMeasureDemotionLosses)
        UiLanguage.entries.forEach { language ->
            listOf(rankMeasureRulesFor(language), rankMeasureKyuCapNoteFor(language), rankMeasureStepCapNoteFor(language)).forEach { line ->
                assertFalse("$language: 옛 숫자(10집)가 남았다 — $line", line.contains("10"))
                assertTrue("$language: 빈 줄", line.isNotBlank())
            }
            assertTrue("$language: 규칙 문구에 15가 없다", rankMeasureRulesFor(language).contains("15"))
            assertTrue("$language: 최대 급 안내에 숫자가 없다", rankMeasureStepCapNoteFor(language).contains("3"))
            assertTrue("$language: 1단 안내에 15가 없다", rankMeasureKyuCapNoteFor(language).contains("15"))
        }
    }

    /** 좌석 요약·대국 기록에 그 판의 급수가 남는다 — 캐릭터와 둔 판의 줄은 예전 그대로다. */
    @Test
    fun aRankedOpponentIsNamedByItsRankInSummariesAndHistoryRows() {
        val korean = UiStrings.forLanguage(UiLanguage.Korean)
        fun ranked(rank: KgsRank) = SidePlayerSetup(SeatController.Ai, playLevel = rank.toPlayLevelSetting())

        assertEquals("18급 AI", korean.sideSummary(ranked(KgsRank.kyu(18)), engineName = "KataGo"))
        assertEquals("상대: 18급 AI", rankedOpponentLineFor(UiLanguage.Korean, KgsRank.kyu(18)))
        assertEquals("사람:AI 5급", seatMatchupLabelWithRanksFor(korean, rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(5))))
        assertEquals("AI 1단:사람", seatMatchupLabelWithRanksFor(korean, rankMeasurePlayerSetup(StoneColor.White, KgsRank.dan(1))))
        val againstACharacter = PlayerSetup()
        assertEquals(korean.seatMatchupLabel(againstACharacter), seatMatchupLabelWithRanksFor(korean, againstACharacter))
    }
}
