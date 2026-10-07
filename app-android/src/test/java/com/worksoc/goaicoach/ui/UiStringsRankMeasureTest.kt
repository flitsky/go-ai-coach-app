package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.rankmeasure.RankMeasureChange
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureDanWindow
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureDanWinsToPromote
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureDemotionLosses
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureKyuPromotionCap
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureMarginPerStep
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
import com.worksoc.goaicoach.ui.l10n.rankMeasureStrongerFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureSubtitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureTitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureWeakerFor
import com.worksoc.goaicoach.ui.l10n.rankedOpponentLabelFor
import com.worksoc.goaicoach.ui.l10n.rankedOpponentLineFor
import com.worksoc.goaicoach.ui.l10n.seatMatchupLabelWithRanksFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 기력 측정 대국(백로그 #219)의 문구 — 급수 표기와, 홈 카드·설정 창·기력 변동 알림·기록 표시. */
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
        ) + popupChanges.flatMap { change ->
            listOfNotNull(rankMeasureChangeTitleFor(language, change), rankMeasureChangeRanksFor(language, change), rankMeasureChangeMessageFor(language, change))
        }

    /** 팝업이 그리는 다섯 경우 — 급 구간 승급(집 차이 있음·없음) · 승단 · 강급 · 이미 9단. */
    private val popupChanges: List<RankMeasureChange> = listOf(
        RankMeasureChange.Promoted(from = KgsRank.kyu(15), to = KgsRank.kyu(9), margin = 61.0),
        RankMeasureChange.Promoted(from = KgsRank.kyu(5), to = KgsRank.kyu(4), margin = null),
        RankMeasureChange.Promoted(from = KgsRank.dan(1), to = KgsRank.dan(2), margin = null),
        RankMeasureChange.Demoted(from = KgsRank.dan(1), to = KgsRank.kyu(1)),
        RankMeasureChange.AtTheTop,
    )

    @Test
    fun everyRankMeasureStringExistsInEveryLanguageWithoutFallingBackToKorean() {
        assertTrue("자기검증 — 한국어 문구에는 한글이 있어야 한다", everyStringIn(UiLanguage.Korean).all { it.containsHangul() })
        UiLanguage.entries.forEach { language ->
            val strings = everyStringIn(language)
            assertEquals("$language: 문구 수가 다르다(팝업의 다섯 경우가 전부 문구를 가져야 한다)", 37, strings.size)
            strings.forEach { text ->
                assertTrue("$language 문구가 비었다", text.isNotBlank())
                if (language != UiLanguage.Korean) assertFalse("$language: 한글이 남았다 — $text", text.containsHangul())
            }
        }
    }

    /** 사용자가 정한 이름 그대로다(2026-10-06) — 홈의 두 번째 메뉴 「기력 측정 대국」. 기록이 없으면 「측정 기록 없음」. */
    @Test
    fun theKoreanNameAndTheNoRecordWordingAreWhatTheUserChose() {
        assertEquals("기력 측정 대국", rankMeasureTitleFor(UiLanguage.Korean))
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
     * **팝업은 몇 단계 움직였는지를 말한다**(사용자 2026-10-07) — 사용자가 든 예: 15급에서 61집 차로 이기면 6단계 올라 9급.
     * 단 구간의 승단은 집 수가 아니라 최근 전적을 말하고, 강급은 2연패를 말한다. 기력이 그대로면 팝업이 없다.
     */
    @Test
    fun thePopupSaysHowManyStepsAndWhy() {
        val sixSteps = RankMeasureChange.Promoted(from = KgsRank.kyu(15), to = KgsRank.kyu(9), margin = 61.0)
        assertEquals("승급!", rankMeasureChangeTitleFor(UiLanguage.Korean, sixSteps))
        assertEquals("15급 → 9급", rankMeasureChangeRanksFor(UiLanguage.Korean, sixSteps))
        assertEquals("61집 차로 이겨 6단계 올랐습니다.", rankMeasureChangeMessageFor(UiLanguage.Korean, sixSteps))
        assertEquals("You won by 61 points and moved up 6 steps.", rankMeasureChangeMessageFor(UiLanguage.English, sixSteps))

        val halfPoint = RankMeasureChange.Promoted(from = KgsRank.kyu(2), to = KgsRank.kyu(1), margin = 6.5)
        assertEquals("6.5집 차로 이겨 1단계 올랐습니다.", rankMeasureChangeMessageFor(UiLanguage.Korean, halfPoint))
        assertEquals("You won by 6.5 points and moved up 1 step.", rankMeasureChangeMessageFor(UiLanguage.English, halfPoint))

        val toTwoDan = RankMeasureChange.Promoted(from = KgsRank.dan(1), to = KgsRank.dan(2), margin = null)
        assertEquals("승단!", rankMeasureChangeTitleFor(UiLanguage.Korean, toTwoDan))
        assertEquals("최근 5판 중 3판을 이겨 한 단 올랐습니다.", rankMeasureChangeMessageFor(UiLanguage.Korean, toTwoDan))

        val backToKyu = RankMeasureChange.Demoted(from = KgsRank.dan(1), to = KgsRank.kyu(1))
        assertEquals("강단", rankMeasureChangeTitleFor(UiLanguage.Korean, backToKyu))
        assertEquals("1단 → 1급", rankMeasureChangeRanksFor(UiLanguage.Korean, backToKyu))
        assertEquals("2연패로 한 단계 내려갑니다.", rankMeasureChangeMessageFor(UiLanguage.Korean, backToKyu))

        UiLanguage.entries.forEach { language ->
            assertNull(rankMeasureChangeTitleFor(language, RankMeasureChange.None))
            assertNull(rankMeasureChangeMessageFor(language, RankMeasureChange.None))
            assertNull(rankMeasureChangeRanksFor(language, RankMeasureChange.AtTheTop))
        }
    }

    /**
     * 설정 창의 규칙 문구는 규칙과 **같은 숫자**를 말한다 — 10집 · 최소 한 단계 · 1단까지 · 5판 중 3판 · 2연패.
     * 화면에서 읽은 것과 실제 동작이 다르면 그것이 가장 나쁜 결함이다(2026-10-07: 창에 없는 폭으로 올라 사용자가 물었다).
     */
    @Test
    fun theRulesTextStatesTheSameNumbersAsTheRule() {
        val rules = rankMeasureRulesFor(UiLanguage.Korean)
        listOf("10집마다 한 단계", "최소 한 단계", "1단까지", "최근 5판 중 3판", "2연패하면 한 단계").forEach { phrase ->
            assertTrue("규칙 문구에 없다: $phrase", rules.contains(phrase))
        }
        assertEquals(10.0, RankMeasureMarginPerStep, 0.0)
        assertEquals(KgsRank.dan(1), RankMeasureKyuPromotionCap)
        assertEquals(5 to 3, RankMeasureDanWindow to RankMeasureDanWinsToPromote)
        assertEquals(2, RankMeasureDemotionLosses)
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
