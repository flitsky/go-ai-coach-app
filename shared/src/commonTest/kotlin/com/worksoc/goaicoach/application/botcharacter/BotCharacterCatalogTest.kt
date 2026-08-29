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
    fun unlockPathsFollowTheConfirmedTable() {
        val roster = BotCharacterCatalog.fastBeginnerRoster

        // 7장 재확정본(2026-08-24). 티어 오름차순이 아닌 것이 의도이므로 표 그대로 고정한다.
        assertEquals(BotUnlockSource.Default, roster[0].unlockSource)
        assertEquals(BotUnlockSource.AdShards(required = 5), roster[1].unlockSource)
        assertEquals(BotUnlockSource.Attendance(tier = 4), roster[2].unlockSource)
        assertEquals(BotUnlockSource.AdShards(required = 10), roster[3].unlockSource)
        assertEquals(BotUnlockSource.Purchase, roster[4].unlockSource)
    }

    @Test
    fun exactlyOneCharacterIsFreeAndOneComesFromAttendance() {
        val roster = BotCharacterCatalog.fastBeginnerRoster

        // 무료 사용자가 얻는 것은 기본 제공 1종 + 출석 1종뿐이고, 그 사이 2단계가 비어 있는
        // 것이 광고 유인이다(7장). 이 균형이 조용히 깨지지 않게 개수로 고정한다.
        assertEquals(1, roster.count { it.unlockSource == BotUnlockSource.Default })
        assertEquals(1, roster.count { it.unlockSource is BotUnlockSource.Attendance })
        assertEquals(2, roster.count { it.unlockSource is BotUnlockSource.AdShards })
        assertEquals(1, roster.count { it.unlockSource == BotUnlockSource.Purchase })
    }

    @Test
    fun attendanceLookupOnlyYieldsTheDayFourCharacter() {
        // 1일차는 이제 캐릭터를 주지 않는다 — 1단계가 기본 제공으로 바뀌면서 정책표에 코드를
        // 더하지 않고도 중복 지급이 사라졌다(#19의 선행 조건).
        assertTrue(BotCharacterCatalog.forAttendanceTier(1).isEmpty())
        assertTrue(BotCharacterCatalog.forAttendanceTier(5).isEmpty())
        assertEquals(
            listOf(BotCharacterId("fast_beginner_3")),
            BotCharacterCatalog.forAttendanceTier(4).map { it.id },
        )
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
        unlockSource = BotUnlockSource.AdShards(required = 5),
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
    fun theDefaultCharacterIsSelectableWithoutAnyClaim() {
        val empty = BotCollectionState()
        val default = BotCharacterCatalog.fastBeginnerRoster.first()

        // #16이 없앤 것이 바로 이 지점의 빈 상태다 — 획득 기록이 하나도 없어도 고를 상대가 있다.
        assertEquals(BotUnlockSource.Default, default.unlockSource)
        assertTrue(empty.isAvailable(default))
        assertFalse(empty.isClaimed(default.id))

        // 나머지 4종은 여전히 잠겨 있다.
        assertTrue(BotCharacterCatalog.all.filter { it != default }.none { empty.isAvailable(it) })
    }

    @Test
    fun claimingOneLockedCharacterOpensOnlyThatOne() {
        val default = BotCharacterCatalog.fastBeginnerRoster.first()
        val locked = BotCharacterCatalog.fastBeginnerRoster[2]
        val state = BotCollectionState().withClaimed(locked.id)

        assertTrue(state.isAvailable(locked))
        assertTrue(state.isAvailable(default))
        assertTrue(
            BotCharacterCatalog.all
                .filter { it != default && it != locked }
                .none { state.isAvailable(it) },
        )
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
