package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.customgame.CustomRankPromotionBlock
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.customGameAutoAdjustDescriptionFor
import com.worksoc.goaicoach.ui.l10n.customGameAutoAdjustLabelFor
import com.worksoc.goaicoach.ui.l10n.customGameOpponentLabelFor
import com.worksoc.goaicoach.ui.l10n.customGameOpponentLineFor
import com.worksoc.goaicoach.ui.l10n.customGamePickRankFor
import com.worksoc.goaicoach.ui.l10n.customGamePromotedFor
import com.worksoc.goaicoach.ui.l10n.customGamePromotionBlockedFor
import com.worksoc.goaicoach.ui.l10n.customRankDialogTitleFor
import com.worksoc.goaicoach.ui.l10n.customRankLockedNoteFor
import com.worksoc.goaicoach.ui.l10n.customRankOfficialNoteFor
import com.worksoc.goaicoach.ui.l10n.customRankStrongerFor
import com.worksoc.goaicoach.ui.l10n.customRankWeakerFor
import com.worksoc.goaicoach.ui.l10n.kgsRankLabelFor
import com.worksoc.goaicoach.ui.l10n.seatMatchupLabelWithRanksFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 커스텀 대국(백로그 #217)의 문구 — 급수 표기와, 고르는 창·승급 알림·기록 표시. */
class UiStringsCustomGameTest {
    private fun everyStringIn(language: UiLanguage): List<String> =
        listOf(
            kgsRankLabelFor(language, KgsRank.kyu(5)),
            kgsRankLabelFor(language, KgsRank.dan(3)),
            customGameOpponentLabelFor(language, KgsRank.kyu(5)),
            customGamePickRankFor(language),
            customRankDialogTitleFor(language),
            customRankOfficialNoteFor(language),
            customRankWeakerFor(language),
            customRankStrongerFor(language),
            customRankLockedNoteFor(language, KgsRank.kyu(12)),
            customGameAutoAdjustLabelFor(language),
            customGameAutoAdjustDescriptionFor(language),
            customGamePromotedFor(language, KgsRank.kyu(4)),
            customGameOpponentLineFor(language, KgsRank.kyu(5)),
        ) + CustomRankPromotionBlock.entries.map { block -> customGamePromotionBlockedFor(language, block) }

    @Test
    fun everyCustomGameStringExistsInEveryLanguageWithoutFallingBackToKorean() {
        assertTrue("자기검증 — 한국어 문구에는 한글이 있어야 한다", everyStringIn(UiLanguage.Korean).all { it.containsHangul() })
        UiLanguage.entries.forEach { language ->
            everyStringIn(language).forEach { text ->
                assertTrue("$language 문구가 비었다", text.isNotBlank())
                if (language != UiLanguage.Korean) assertFalse("$language: 한글이 남았다 — $text", text.containsHangul())
            }
        }
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

    /** 고르는 창은 숫자가 **공인 기력이 아니라는 것**을 밝힌다(사용자 2026-10-04) — 그 한 줄이 빠지면 급수가 실력 인증처럼 읽힌다. */
    @Test
    fun theDialogSaysTheRankIsNotACertifiedOne() {
        assertTrue(customRankOfficialNoteFor(UiLanguage.Korean).contains("공인 기력이 아닙니다"))
        assertTrue(customRankOfficialNoteFor(UiLanguage.English).contains("not a certified rank"))
    }

    /** 좌석 요약·대국 기록에 그 판의 급수가 남는다 — 캐릭터와 둔 판의 줄은 예전 그대로다. */
    @Test
    fun aCustomRankOpponentIsNamedByItsRankInSummariesAndHistoryRows() {
        val korean = UiStrings.forLanguage(UiLanguage.Korean)
        val human = SidePlayerSetup(SeatController.Human)
        fun custom(rank: KgsRank) = SidePlayerSetup(SeatController.Ai, playLevel = rank.toPlayLevelSetting())

        assertEquals("커스텀 5급", korean.sideSummary(custom(KgsRank.kyu(5)), engineName = "KataGo"))
        assertEquals("사람:AI 5급", seatMatchupLabelWithRanksFor(korean, PlayerSetup(black = human, white = custom(KgsRank.kyu(5)))))
        assertEquals("AI 3급:AI 1단", seatMatchupLabelWithRanksFor(korean, PlayerSetup(black = custom(KgsRank.kyu(3)), white = custom(KgsRank.dan(1)))))
        val againstACharacter = PlayerSetup()
        assertEquals(korean.seatMatchupLabel(againstACharacter), seatMatchupLabelWithRanksFor(korean, againstACharacter))
    }
}
