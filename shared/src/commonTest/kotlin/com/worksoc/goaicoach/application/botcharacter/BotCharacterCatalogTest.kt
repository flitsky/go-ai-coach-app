package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.fastBeginnerRank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 저장 id로 캐릭터를 찾는다 — 목록의 자리는 세기의 차례라 단계 번호와 다르다(#301). */
internal fun botTier(tier: Int): BotCharacter = BotCharacterCatalog.byRawId("fast_beginner_$tier")!!

/** 세기의 차례(약한 쪽부터)로 늘어선 단계 번호 — 처음 다섯(1 ~ 5)의 사이사이에 6 ~ 10이 선다(#301). */
private val LadderTiers = listOf(6, 1, 7, 2, 8, 9, 3, 4, 10, 5)

class BotCharacterCatalogTest {

    @Test
    fun rosterCoversEveryFastBeginnerTierExactlyOnce() {
        val tiers = BotCharacterCatalog.fastBeginnerRoster.map { it.tierWithinGroup }

        assertEquals((1..PlayLevelGroup.FastBeginner.maxLevel).toList(), tiers.map { it!! }.sorted())
        assertTrue(BotCharacterCatalog.fastBeginnerRoster.all { it.linkedPlayLevel == PlayLevelGroup.FastBeginner })
    }

    @Test
    fun rosterOrderIsTheOrderOfStrength() {
        // 목록의 차례가 곧 세기의 차례다(화면 차례 · 잠긴 상대 낮추기). 급수는 `fastBeginnerRank` 한 곳에서 읽는다 —
        // 그쪽을 밸런스 패치로 고쳐 차례가 어긋나면 여기서 먼저 깨진다.
        val ranks = BotCharacterCatalog.fastBeginnerRoster.map { fastBeginnerRank(it.tierWithinGroup!!) }

        assertEquals(ranks.sorted(), ranks)
        assertEquals(ranks.size, ranks.toSet().size)
        assertEquals(LadderTiers, BotCharacterCatalog.fastBeginnerRoster.map { it.tierWithinGroup })
        assertEquals(
            listOf("rank_18k", "rank_15k", "rank_12k", "rank_9k", "rank_6k", "rank_3k", "rank_1k", "rank_3d", "rank_5d", "rank_7d"),
            ranks.map { it.profile },
        )
        assertEquals(LadderTiers.indices.toList(), BotCharacterCatalog.fastBeginnerRoster.map { BotCharacterCatalog.ladderIndexOf(it) })
    }

    @Test
    fun theFirstFiveAreTheOriginalRosterInLadderOrder() {
        // #301을 가린 출시가 보이는 목록(사용자 2026-11-06) — #301 전의 다섯과 같은 차례다.
        assertEquals((1..5).map { "fast_beginner_$it" }, BotCharacterCatalog.firstFive.map { it.id.raw })
    }

    @Test
    fun characterIdsAreUniqueAndStable() {
        val ids = BotCharacterCatalog.all.map { it.id.raw }

        assertEquals(ids.size, ids.toSet().size)
        // id는 저장 스키마의 일부라 바뀌면 기존 컬렉션이 유실된다 — 값 자체를 고정한다.
        assertEquals(LadderTiers.map { tier -> "fast_beginner_$tier" }, ids)
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

        assertEquals(BotCharacterId("fast_beginner_10"), BotCharacterCatalog.forPlayLevel(tooHigh)?.id)
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
        // 7장 재확정본(2026-08-24). 티어 오름차순이 아닌 것이 의도이므로 표 그대로 고정한다.
        assertEquals(BotUnlockSource.Default, botTier(1).unlockSource)
        assertEquals(BotUnlockSource.AdShards(required = 5), botTier(2).unlockSource)
        // ⚠️ 4일차 → **7일차**(#55, 2026-08-31). 도장판 UX에서 1~6일차 행은 소모품·기능만 두고
        // 7·28일차를 캐릭터 회차로 갈랐다.
        assertEquals(BotUnlockSource.Attendance(tier = 7), botTier(3).unlockSource)
        assertEquals(BotUnlockSource.AdShards(required = 10), botTier(4).unlockSource)
        // 2026-08-29: 5단계가 유료 구매 → 28일차 출석 장기 보상으로 바뀌었다. Play Console의
        // "수익 창출"이 앱 설정 미완료로 막혀 상품 등록 자체가 불가능해졌기 때문이다(#18).
        assertEquals(BotUnlockSource.Attendance(tier = 28), botTier(5).unlockSource)
        // 사이사이에 더한 다섯은 무료다(#301 — 사용자 2026-11-02). 화점(10)의 매판 광고 관문(U-89)은 여기가 아니라 대국 시작에 선다.
        (6..10).forEach { tier -> assertEquals(BotUnlockSource.Default, botTier(tier).unlockSource, "tier $tier") }
    }

    @Test
    fun everyCharacterIsReachableWithoutPaying() {
        val roster = BotCharacterCatalog.fastBeginnerRoster

        // 2026-08-29부터 **유료 전용 캐릭터가 없다** — 5단계가 28일차 출석으로 옮겨가면서, 돈을
        // 쓰지 않고도 로스터 전체에 닿을 수 있게 됐다. 광고 유인은 조각 경로 2종이 계속 맡는다.
        assertEquals(6, roster.count { it.unlockSource == BotUnlockSource.Default })
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
    fun rosterCarriesTheConfirmedIdsInLadderOrder() {
        // id는 저장 스키마이자 UI 문구 표의 키다 — 바꾸면 수집 기록이 유실되고 이름이 통째로
        // id 문자열로 보인다. 순서도 함께 고정한다(세기의 차례 — #301부터 단계 번호 순이 아니다).
        assertEquals(
            LadderTiers.map { tier -> "fast_beginner_$tier" },
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
        // 열 명(#301)은 단계 번호가 아니라 제 급수가 든 구간의 이름을 쓴다(`fastBeginnerFallbackTier`).
        assertEquals(
            listOf("초보", "초보", "하수", "하수", "중수", "중수", "중수", "고수", "초고수", "초고수"),
            BotCharacterCatalog.fastBeginnerRoster.map { character ->
                assertNotNull(character.toPlayLevelSetting()).tierLabel
            },
        )
    }

    @Test
    fun byIdAndByRawIdFindKnownCharactersAndRejectUnknownOnes() {
        assertEquals(3, BotCharacterCatalog.byRawId("fast_beginner_3")?.tierWithinGroup)
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
        val default = botTier(1)

        // #16이 없앤 것이 바로 이 지점의 빈 상태다 — 획득 기록이 하나도 없어도 고를 상대가 있다.
        assertEquals(BotUnlockSource.Default, default.unlockSource)
        assertTrue(empty.isAvailable(default))
        assertFalse(empty.isClaimed(default.id))

        // 기본 제공은 판다와 사이에 더한 다섯(#301)이고, 나머지 넷(돌뫼 · 반상 · 꼬북 · 천원)은 여전히 잠겨 있다.
        val free = BotCharacterCatalog.all.filter { it.unlockSource == BotUnlockSource.Default }
        assertEquals(listOf(6, 1, 7, 8, 9, 10), free.map { it.tierWithinGroup })
        assertTrue(free.all { empty.isAvailable(it) })
        assertTrue(BotCharacterCatalog.all.filter { it !in free }.none { empty.isAvailable(it) })
    }

    @Test
    fun claimingOneLockedCharacterOpensOnlyThatOne() {
        val default = botTier(1)
        val locked = botTier(3)
        val state = BotCollectionState().withClaimed(locked.id)

        assertTrue(state.isAvailable(locked))
        assertTrue(state.isAvailable(default))
        assertTrue(
            BotCharacterCatalog.all
                .filter { it.unlockSource != BotUnlockSource.Default && it != locked }
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
        val shardBot = botTier(2)   // 문하생 돌뫼 — 광고 5회
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
        val default = botTier(1)      // 기본 제공
        val attendance = botTier(3)   // 출석 7일차
        val purchase = botTier(5)     // 출석 28일차(옛 유료)
        val empty = BotCollectionState()

        // 호출부가 실수로 불러도 상태가 오염되지 않아야 한다.
        listOf(default, attendance, purchase).forEach { character ->
            assertSame(empty, empty.withAdShard(character), character.id.raw)
        }
    }

    @Test
    fun shardsStopAccruingOnceTheCharacterIsOwned() {
        val shardBot = botTier(2)
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
