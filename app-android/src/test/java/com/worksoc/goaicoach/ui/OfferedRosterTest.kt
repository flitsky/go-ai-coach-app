package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.ui.foundation.FeatureFlags
import com.worksoc.goaicoach.ui.monetization.offeredFastBeginnerRoster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** #301의 새 다섯을 출시 빌드에서 가린다(사용자 2026-11-06: *"캐릭터 추가 #301을 잠시 가려두고 #303만 먼저"*). */
class OfferedRosterTest {

    @Test
    fun aReleaseBuildOffersOnlyTheFirstFive() {
        assertEquals(BotCharacterCatalog.firstFive, offeredFastBeginnerRoster(showAddedFive = false))
        assertEquals((1..5).map { "fast_beginner_$it" }, offeredFastBeginnerRoster(showAddedFive = false).map { it.id.raw })
    }

    @Test
    fun aDebugBuildOrTheFlagOffersAllTen() {
        assertEquals(BotCharacterCatalog.fastBeginnerRoster, offeredFastBeginnerRoster(showAddedFive = true))
        assertEquals(10, offeredFastBeginnerRoster(showAddedFive = true).size)
    }

    @Test
    fun theAddedFiveStayHiddenInReleaseUntilBacklog301IsDone() {
        // 켜기 전에 #301이 끝났는지 본다 — 화점(5단)의 매판 광고 · 새 다섯의 그림(`BotAvatarRefsAwaitingArtwork`가 비었는가) · 내 캐릭터 배치 · 스토어 글.
        assertFalse(FeatureFlags.isAddedCharactersEnabled)
    }
}
