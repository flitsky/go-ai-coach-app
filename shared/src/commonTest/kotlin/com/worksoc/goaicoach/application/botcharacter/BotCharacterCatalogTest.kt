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
        // 2026-08-29: 5단계가 유료 구매 → 28일차 출석 장기 보상으로 바뀌었다. Play Console의
        // "수익 창출"이 앱 설정 미완료로 막혀 상품 등록 자체가 불가능해졌기 때문이다(#18).
        assertEquals(BotUnlockSource.Attendance(tier = 28), roster[4].unlockSource)
    }

    @Test
    fun everyCharacterIsReachableWithoutPaying() {
        val roster = BotCharacterCatalog.fastBeginnerRoster

        // 2026-08-29부터 **유료 전용 캐릭터가 없다** — 5단계가 28일차 출석으로 옮겨가면서, 돈을
        // 쓰지 않고도 로스터 전체에 닿을 수 있게 됐다. 광고 유인은 조각 경로 2종이 계속 맡는다.
        assertEquals(1, roster.count { it.unlockSource == BotUnlockSource.Default })
        assertEquals(2, roster.count { it.unlockSource is BotUnlockSource.Attendance })
        assertEquals(2, roster.count { it.unlockSource is BotUnlockSource.AdShards })
        assertEquals(0, roster.count { it.unlockSource == BotUnlockSource.Purchase })
    }

    @Test
    fun attendanceLookupYieldsTheDayFourAndDayTwentyEightCharacters() {
        // 1일차는 이제 캐릭터를 주지 않는다 — 1단계가 기본 제공으로 바뀌면서 정책표에 코드를
        // 더하지 않고도 중복 지급이 사라졌다(#19의 선행 조건).
        assertTrue(BotCharacterCatalog.forAttendanceTier(1).isEmpty())
        assertTrue(BotCharacterCatalog.forAttendanceTier(5).isEmpty())
        assertEquals(
            listOf(BotCharacterId("fast_beginner_3")),
            BotCharacterCatalog.forAttendanceTier(4).map { it.id },
        )
        // 최상위 캐릭터는 28일차(7의 배수 중 네 번째)에 걸린다 — 2026-08-29에 유료 구매에서
        // 옮겨왔다. 7·14·21에는 캐릭터가 없어야 반복 회차에서 중복 지급이 생기지 않는다.
        listOf(7, 14, 21).forEach { tier ->
            assertTrue(BotCharacterCatalog.forAttendanceTier(tier).isEmpty(), "tier $tier")
        }
        assertEquals(
            listOf(BotCharacterId("fast_beginner_5")),
            BotCharacterCatalog.forAttendanceTier(28).map { it.id },
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
    fun adShardsAccumulateAndFlipToOwnershipAtTheRequiredCount() {
        val shardBot = BotCharacterCatalog.fastBeginnerRoster[1]   // 연습생 돌뫼 — 광고 5회
        val required = (shardBot.unlockSource as BotUnlockSource.AdShards).required

        var state = BotCollectionState()
        repeat(required - 1) { state = state.withAdShard(shardBot) }

        // 마지막 한 장 전까지는 진행도만 쌓이고 아직 못 고른다.
        assertEquals(required - 1, state.shardsFor(shardBot.id))
        assertFalse(state.isAvailable(shardBot))

        state = state.withAdShard(shardBot)

        assertTrue(state.isAvailable(shardBot))
        // 획득한 뒤에는 진행도가 남지 않는다 — 이미 가진 캐릭터의 조각은 뜻이 없다.
        assertEquals(0, state.shardsFor(shardBot.id))
    }

    @Test
    fun shardsAreIgnoredForCharactersThatDoNotUseThatPath() {
        val default = BotCharacterCatalog.fastBeginnerRoster[0]      // 기본 제공
        val attendance = BotCharacterCatalog.fastBeginnerRoster[2]   // 출석 4일차
        val purchase = BotCharacterCatalog.fastBeginnerRoster[4]     // 유료
        val empty = BotCollectionState()

        // 호출부가 실수로 불러도 상태가 오염되지 않아야 한다.
        listOf(default, attendance, purchase).forEach { character ->
            assertSame(empty, empty.withAdShard(character), character.name)
        }
    }

    @Test
    fun shardsStopAccruingOnceTheCharacterIsOwned() {
        val shardBot = BotCharacterCatalog.fastBeginnerRoster[1]
        val owned = BotCollectionState().withClaimed(shardBot.id)

        assertSame(owned, owned.withAdShard(shardBot))
        assertEquals(0, owned.shardsFor(shardBot.id))
    }

    @Test
    fun unknownIdsFromNewerVersionsSurviveInState() {
        val fromFutureVersion = BotCharacterId("bot_added_in_a_later_release")
        val state = BotCollectionState().withClaimed(fromFutureVersion)

        assertNull(BotCharacterCatalog.byId(fromFutureVersion))
        assertTrue(state.isClaimed(fromFutureVersion))
    }
}
