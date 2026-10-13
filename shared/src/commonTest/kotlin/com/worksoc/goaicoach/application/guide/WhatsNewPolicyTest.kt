package com.worksoc.goaicoach.application.guide

import kotlin.test.Test
import kotlin.test.assertEquals

/** 백로그 #257 — 업데이트한 뒤 「새로워진 점」을 무엇을 보일지(사용자 2026-10-13: 문구를 준비한 버전만, 밀린 것은 함께). */
class WhatsNewPolicyTest {
    private val available = listOf(10400, 10500, 10600)

    @Test
    fun aFreshInstallSeesNothing() {
        assertEquals(emptyList(), whatsNewPending(available, lastSeenVersionCode = null, currentVersionCode = 10600, isFreshInstall = true))
    }

    @Test
    fun anUpdateShowsTheUnseenNotesNewestFirstAndNothingNewerThanTheApp() {
        assertEquals(listOf(10400), whatsNewPending(available, lastSeenVersionCode = 10306, currentVersionCode = 10400, isFreshInstall = false))
        assertEquals(listOf(10600, 10500), whatsNewPending(available, lastSeenVersionCode = 10400, currentVersionCode = 10601, isFreshInstall = false))
        assertEquals(emptyList(), whatsNewPending(available, lastSeenVersionCode = 10400, currentVersionCode = 10401, isFreshInstall = false), "a small fix has no notes")
        assertEquals(emptyList(), whatsNewPending(available, lastSeenVersionCode = 10600, currentVersionCode = 10600, isFreshInstall = false), "already seen")
    }

    @Test
    fun anUpdateFromBeforeThisFeatureShowsOnlyTheNewestNote() {
        assertEquals(listOf(10500), whatsNewPending(available, lastSeenVersionCode = null, currentVersionCode = 10500, isFreshInstall = false))
        assertEquals(emptyList(), whatsNewPending(available, lastSeenVersionCode = null, currentVersionCode = 10306, isFreshInstall = false))
    }
}
