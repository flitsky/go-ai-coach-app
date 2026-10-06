package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.rankmeasure.RankMeasureChange
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
import com.worksoc.goaicoach.ui.l10n.rankMeasureChangeNoticeFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChooseStartingRankFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureCurrentRankFor
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
        ) + listOf(RankMeasureChange.Promoted(KgsRank.kyu(4)), RankMeasureChange.Demoted(KgsRank.kyu(6)), RankMeasureChange.AtTheTop)
            .mapNotNull { change -> rankMeasureChangeNoticeFor(language, change) }

    @Test
    fun everyRankMeasureStringExistsInEveryLanguageWithoutFallingBackToKorean() {
        assertTrue("자기검증 — 한국어 문구에는 한글이 있어야 한다", everyStringIn(UiLanguage.Korean).all { it.containsHangul() })
        UiLanguage.entries.forEach { language ->
            val strings = everyStringIn(language)
            assertEquals("$language: 문구 수가 다르다(알림 셋이 전부 문구를 가져야 한다)", 25, strings.size)
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

    /** 기력이 그대로면 알리지 않는다 — 한 판 이기고 진 것마다 알림이 뜨면 정작 바뀐 순간이 묻힌다. */
    @Test
    fun anUnchangedRankIsNotAnnounced() {
        UiLanguage.entries.forEach { language -> assertNull(rankMeasureChangeNoticeFor(language, RankMeasureChange.None)) }
        assertEquals("연승! 기력이 15급으로 올랐습니다.", rankMeasureChangeNoticeFor(UiLanguage.Korean, RankMeasureChange.Promoted(KgsRank.kyu(15))))
        assertEquals("2연패 — 기력이 3단으로 내려갑니다.", rankMeasureChangeNoticeFor(UiLanguage.Korean, RankMeasureChange.Demoted(KgsRank.dan(3))))
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
