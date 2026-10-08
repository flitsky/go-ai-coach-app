package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.premium.state.AllowedVia
import com.worksoc.goaicoach.application.premium.state.FeatureAccess
import com.worksoc.goaicoach.application.premium.state.UnlockOption
import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.freeAnalysisMarkFor
import com.worksoc.goaicoach.ui.l10n.freeAnalysisUsedToastFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 대국 한 판의 무료 사용(backlog #228)의 문구와, 대국 화면이 그것을 **1회권보다 먼저** 쓰는지. */
class UiStringsFreeAnalysisTest {
    private val locked = FeatureAccess.Locked(setOf(UnlockOption.AdGrant, UnlockOption.Purchase))

    @Test
    fun everyFreeUseStringExistsInEveryLanguageWithoutFallingBackToKorean() {
        fun all(language: UiLanguage) =
            listOf(freeAnalysisMarkFor(language, 3), freeAnalysisUsedToastFor(language, 2), freeAnalysisUsedToastFor(language, 0))
        assertTrue("자기검증 — 한국어 문구에는 한글이 있어야 한다", all(UiLanguage.Korean).all { it.containsHangul() })
        UiLanguage.entries.forEach { language ->
            all(language).forEach { text ->
                assertTrue("$language 문구가 비었다", text.isNotBlank())
                assertFalse("$language: 토스트는 한 줄이다(안내와 함께 두 줄까지) — $text", text.contains('\n'))
                if (language != UiLanguage.Korean) assertFalse("$language: 한글이 남았다 — $text", text.containsHangul())
            }
        }
    }

    /** 쓴 직후의 한 줄은 이번 판에 몇 번 남았는지 말하고, 다 썼으면 그것을 말한다. */
    @Test
    fun theToastSaysHowManyAreLeftThisGame() {
        assertEquals("무료로 사용했어요 · 이번 판 2회 남음", freeAnalysisUsedToastFor(UiLanguage.Korean, 2))
        assertEquals("무료로 사용했어요 · 이번 판의 무료 횟수를 다 썼어요", freeAnalysisUsedToastFor(UiLanguage.Korean, 0))
    }

    /**
     * 버튼의 표기 — 잠긴 기능에 무료 사용이 남아 있으면 **그것부터** 말한다(다음 탭이 쓰는 것이 1회권이 아니라 무료분이다).
     * 다 쓰면 예전처럼 1회권 수, 구독·광고로 열려 있으면 무료 횟수를 말하지 않는다(쓰지 않으니까).
     */
    @Test
    fun theButtonMarkShowsTheFreeUsesBeforeTheTickets() {
        val korean = UiStrings.forLanguage(UiLanguage.Korean)

        assertEquals("무료 3", korean.featureButtonMark(access = locked, remaining = 30, freeRemaining = 3))
        assertEquals("30", korean.featureButtonMark(access = locked, remaining = 30, freeRemaining = 0))
        assertEquals(null, korean.featureButtonMark(access = locked, remaining = 0, freeRemaining = 0))
        val subscribed = FeatureAccess.Allowed(AllowedVia.Purchase)
        assertEquals(
            korean.featureButtonMark(access = subscribed, remaining = 30),
            korean.featureButtonMark(access = subscribed, remaining = 30, freeRemaining = 3),
        )
    }

    /**
     * 대국 화면의 관문은 **무료 사용 → 1회권 → 업셀** 순서다(사용자 2026-10-08: *"3회가 끝나면 현재와 같이"*). 순서가 뒤집히면 표가 있는
     * 사람은 무료분을 영영 못 쓰고 표부터 닳는다. 구독·광고로 열린 사람은 그 앞의 `Allowed` 분기로 가서 횟수가 닳지 않는다.
     */
    @Test
    fun theGateSpendsTheFreeUsesBeforeATicketAndOnlyWhenLocked() {
        val gate = RepoPaths.uiFile("GamePlaySection.kt").readContractSource()
            .substringAfter("fun featureGated(").substringBefore("PremiumUpsellDialogHost(")
        val locked = gate.substringAfter("is FeatureAccess.Locked ->")
        val free = locked.indexOf("consumables.useFree(featureId)")
        val ticket = locked.indexOf("ticket != null ->")
        val upsell = locked.indexOf("showPremiumUpsellDialog = true")

        assertTrue("관문이 무료 사용을 쓰지 않는다(#228).", free >= 0)
        assertTrue("무료 사용이 1회권보다 뒤에 있다 — 표부터 닳는다(#228).", free < ticket)
        assertTrue("1회권이 업셀보다 뒤에 있다.", ticket < upsell)
        assertFalse(
            "구독·광고로 이미 열린 길(`Allowed`)에서 무료 사용을 쓴다 — 횟수가 억울하게 닳는다(#228).",
            gate.substringAfter("is FeatureAccess.Allowed ->").substringBefore("is FeatureAccess.Locked ->").contains("useFree"),
        )
    }

    /** 다시보기에서는 주지 않는다(사용자 2026-10-08) — 다시보기의 분석은 제 원장으로 값을 받고, 이 판의 무료 사용을 모른다. */
    @Test
    fun theReplayScreenNeverTouchesTheFreeUses() {
        listOf("ReplayAnalysis.kt", "ReplayAnalysisLedger.kt", "GameReplayScreen.kt").forEach { name ->
            val source = RepoPaths.uiFile(name).readContractSource()
            assertFalse("$name 이 무료 사용을 쓴다 — 다시보기에서는 주지 않기로 했다(#228).", source.contains("useFree") || source.contains("freeUsesRemaining"))
        }
    }
}
