package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BotCharacterCatalogTest {

    @Test
    fun rosterCoversEveryFastBeginnerTierExactlyOnce() {
        val tiers = BotCharacterCatalog.fastBeginnerRoster.map { it.tierWithinGroup }

        assertEquals((1..PlayLevelGroup.FastBeginner.maxLevel).toList(), tiers)
        assertTrue(BotCharacterCatalog.fastBeginnerRoster.all { it.linkedPlayLevel == PlayLevelGroup.FastBeginner })
    }

    @Test
    fun characterIdsAreUniqueAndStable() {
        val ids = BotCharacterCatalog.all.map { it.id.raw }

        assertEquals(ids.size, ids.toSet().size)
        // id는 저장 스키마의 일부라 바뀌면 기존 컬렉션이 유실된다 — 값 자체를 고정한다.
        assertEquals(
            listOf(
                "fast_beginner_1",
                "fast_beginner_2",
                "fast_beginner_3",
                "fast_beginner_4",
                "fast_beginner_5",
            ),
            ids,
        )
    }

    @Test
    fun forPlayLevelResolvesEveryFastBeginnerTier() {
        for (tier in 1..PlayLevelGroup.FastBeginner.maxLevel) {
            val setting = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = tier)
            val character = assertNotNull(BotCharacterCatalog.forPlayLevel(setting))

            assertEquals(tier, character.tierWithinGroup)
        }
    }

    @Test
    fun forPlayLevelNormalizesOutOfRangeLevel() {
        val tooHigh = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 99)
        val tooLow = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 0)

        assertEquals(BotCharacterId("fast_beginner_5"), BotCharacterCatalog.forPlayLevel(tooHigh)?.id)
        assertEquals(BotCharacterId("fast_beginner_1"), BotCharacterCatalog.forPlayLevel(tooLow)?.id)
    }

    @Test
    fun forPlayLevelReturnsNullForGroupsWithoutCharactersYet() {
        val groupsWithoutCharacters = PlayLevelGroup.entries.filter { it != PlayLevelGroup.FastBeginner }

        assertTrue(groupsWithoutCharacters.isNotEmpty())
        for (group in groupsWithoutCharacters) {
            assertNull(BotCharacterCatalog.forPlayLevel(PlayLevelSetting(group = group, level = 1)))
        }
    }

    @Test
    fun toPlayLevelSettingRoundTripsThroughCatalog() {
        for (character in BotCharacterCatalog.all) {
            val setting = assertNotNull(character.toPlayLevelSetting())

            assertSame(character, BotCharacterCatalog.forPlayLevel(setting))
        }
    }

    @Test
    fun toPlayLevelSettingIsNullWhenNotBoundToATier() {
        val groupWideCharacter = BotCharacterCatalog.fastBeginnerRoster.first().copy(tierWithinGroup = null)

        assertNull(groupWideCharacter.toPlayLevelSetting())
    }

    @Test
    fun firstTwoCharactersComeFromTheConfirmedAttendanceDays() {
        val roster = BotCharacterCatalog.fastBeginnerRoster

        // 4.2절 보상 정책표: 1일차 = 첫 번째 캐릭터, 5일차 = 두 번째 캐릭터.
        assertEquals(BotUnlockSource.Attendance(tier = 1), roster[0].unlockSource)
        assertEquals(BotUnlockSource.Attendance(tier = 5), roster[1].unlockSource)
        // 3~5번째는 아직 확정 전이라 출석 보상으로 잡혀 있으면 안 된다.
        assertTrue(roster.drop(2).none { it.unlockSource is BotUnlockSource.Attendance })
    }

    @Test
    fun rosterCarriesConfirmedNamesAndDescriptions() {
        // 백로그 #9에서 사용자가 확정한 "바둑 도장" 콘셉트 — 플레이스홀더로 되돌아가지 않게 고정한다.
        assertEquals(
            listOf("첫돌이", "연습생 돌뫼", "도장생 반상", "사범 묘수", "관장 천원"),
            BotCharacterCatalog.fastBeginnerRoster.map { it.name },
        )
        assertTrue(BotCharacterCatalog.all.all { it.description.isNotBlank() })
        // 아바타는 아직 플레이스홀더 단계라 전부 비어 있다.
        assertTrue(BotCharacterCatalog.all.all { it.avatarRef == null })
    }

    @Test
    fun eachCharacterPairsWithItsTierLabelForThePicker() {
        // 픽커는 캐릭터 이름 옆에 티어명을 병기해 강함 서열을 드러낸다(#9 확정, #10에서 사용).
        assertEquals(
            listOf("초보", "하수", "중수", "고수", "초고수"),
            BotCharacterCatalog.fastBeginnerRoster.map { character ->
                assertNotNull(character.toPlayLevelSetting()).tierLabel
            },
        )
    }

    @Test
    fun byIdAndByRawIdFindKnownCharactersAndRejectUnknownOnes() {
        assertEquals(
            BotCharacterCatalog.fastBeginnerRoster[2],
            BotCharacterCatalog.byRawId("fast_beginner_3"),
        )
        assertNull(BotCharacterCatalog.byId(BotCharacterId("no_such_bot")))
        assertNull(BotCharacterCatalog.byRawId(""))
    }
}

class BotCollectionStateTest {

    private val adBot = BotCharacter(
        id = BotCharacterId("ad_bot"),
        name = "광고 봇",
        description = "광고 시청으로 획득",
        linkedPlayLevel = PlayLevelGroup.FastBeginner,
        tierWithinGroup = 3,
        unlockSource = BotUnlockSource.AdWatch,
    )

    private val attendanceBot = adBot.copy(
        id = BotCharacterId("attendance_bot"),
        unlockSource = BotUnlockSource.Attendance(tier = 7),
    )

    @Test
    fun claimingIsIdempotentAndKeepsEarlierClaims() {
        val once = BotCollectionState().withClaimed(adBot.id)
        val twice = once.withClaimed(adBot.id)

        assertSame(once, twice)
        assertTrue(twice.isClaimed(adBot.id))
        assertEquals(setOf(adBot.id), twice.claimedBots)

        val both = twice.withClaimed(attendanceBot.id)
        assertEquals(setOf(adBot.id, attendanceBot.id), both.claimedBots)
    }

    @Test
    fun nothingIsSelectableBeforeAnyCharacterIsClaimed() {
        val empty = BotCollectionState()

        // 5단계가 조건 없이 선택되던 기존 UX가 좁아지는 것은 의도된 방향이다(2026-08-24 확정).
        // 첫 캐릭터는 출석 1일차 보상으로 들어온다 — 픽커(#10)는 이 빈 상태를 처리해야 한다.
        assertTrue(BotCharacterCatalog.all.none { empty.isAvailable(it) })
    }

    @Test
    fun claimingOneCharacterMakesOnlyThatOneSelectable() {
        val first = BotCharacterCatalog.fastBeginnerRoster.first()
        val state = BotCollectionState().withClaimed(first.id)

        assertTrue(state.isAvailable(first))
        assertTrue(BotCharacterCatalog.all.filter { it != first }.none { state.isAvailable(it) })
    }

    @Test
    fun unlockableCharactersRequireAClaim() {
        val empty = BotCollectionState()

        assertFalse(empty.isAvailable(adBot))
        assertFalse(empty.isAvailable(attendanceBot))

        val claimed = empty.withClaimed(adBot.id).withClaimed(attendanceBot.id)

        assertTrue(claimed.isAvailable(adBot))
        assertTrue(claimed.isAvailable(attendanceBot))
    }

    @Test
    fun unknownIdsFromNewerVersionsSurviveInState() {
        val fromFutureVersion = BotCharacterId("bot_added_in_a_later_release")
        val state = BotCollectionState().withClaimed(fromFutureVersion)

        assertNull(BotCharacterCatalog.byId(fromFutureVersion))
        assertTrue(state.isClaimed(fromFutureVersion))
    }
}
