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
        // ⚠️ 4일차 → **7일차**(#55, 2026-08-31). 도장판 UX에서 1~6일차 행은 소모품·기능만 두고
        // 7·28일차를 캐릭터 회차로 갈랐다.
        assertEquals(BotUnlockSource.Attendance(tier = 7), roster[2].unlockSource)
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
    fun attendanceLookupYieldsTheDaySevenAndDayTwentyEightCharacters() {
        // 1일차는 캐릭터를 주지 않는다 — 1단계가 기본 제공이라 중복 지급이 애초에 없다.
        listOf(1, 4, 5, 6).forEach { tier ->
            assertTrue(BotCharacterCatalog.forAttendanceTier(tier).isEmpty(), "tier $tier")
        }
        // ⚠️ 3단계가 4일차 → **7일차**로 옮겼다(#55).
        assertEquals(
            listOf(BotCharacterId("fast_beginner_3")),
            BotCharacterCatalog.forAttendanceTier(7).map { it.id },
        )
        // 최상위 캐릭터는 28일차. 그 사이의 반복 회차(14·21)에는 캐릭터가 없어야
        // 반복 지급에서 중복이 생기지 않는다 — 정책이 "7의 배수이면서 캐릭터가 없는 회차"를
        // 반복 번들로 삼기 때문에, 여기가 비어 있다는 사실이 그 판정의 근거이기도 하다.
        listOf(14, 21, 35).forEach { tier ->
            assertTrue(BotCharacterCatalog.forAttendanceTier(tier).isEmpty(), "tier $tier")
        }
        assertEquals(
            listOf(BotCharacterId("fast_beginner_5")),
            BotCharacterCatalog.forAttendanceTier(28).map { it.id },
        )
    }

    @Test
    fun rosterCarriesTheConfirmedIdsInTierOrder() {
        // id는 저장 스키마이자 UI 문구 표의 키다 — 바꾸면 수집 기록이 유실되고 이름이 통째로
        // id 문자열로 보인다. 순서도 함께 고정한다(티어 오름차순).
        assertEquals(
            listOf(
                "fast_beginner_1",
                "fast_beginner_2",
                "fast_beginner_3",
                "fast_beginner_4",
                "fast_beginner_5",
            ),
            BotCharacterCatalog.fastBeginnerRoster.map { it.id.raw },
        )
        // 아바타는 #48에서 다섯 종 전부 채워졌다(그 전에는 전부 null이었다).
        // ⚠️ `avatarRef`는 **id와 값이 겹쳐 보이지만 다른 축이다** — id는 저장 스키마라 고정이고,
        // 이쪽은 그림을 갈아끼우면 함께 바뀔 수 있다. 그래서 "id와 같아야 한다"가 아니라
        // "비어 있지 않아야 한다"만 여기서 지키고, 실제 그림 파일과의 대응은 앱 계층의
        // `app-android/.../ui/BotCharacterAvatarTest.kt`가 확인한다(리소스가 거기에만 있다).
        assertTrue(BotCharacterCatalog.all.all { !it.avatarRef.isNullOrBlank() })
        // ⚠️ 이름·설명은 여기서 검증하지 않는다(백로그 #32) — 도메인이 더 이상 갖고 있지 않다.
        // "바둑 도장" 콘셉트가 플레이스홀더로 되돌아가지 않는지는 UI 계층의
        // `app-android/.../ui/UiStringsBotCharacterTest.kt`가 네 언어 전부에 대해 지킨다.
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
            assertSame(empty, empty.withAdShard(character), character.id.raw)
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
