package com.worksoc.goaicoach.application.botcharacter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeStore(initial: BotCollectionState = BotCollectionState()) : BotCollectionStorePort {
    var stored: BotCollectionState = initial
        private set
    var saveCount: Int = 0
        private set

    override fun save(state: BotCollectionState) {
        stored = state
        saveCount++
    }

    override fun load(): BotCollectionState = stored
}

private val shardCharacter = BotCharacterCatalog.shardPathCharacters().first()
private val shardRequired = (shardCharacter.unlockSource as BotUnlockSource.AdShards).required
private val attendanceCharacter = BotCharacterCatalog.all
    .first { it.unlockSource is BotUnlockSource.Attendance }

/**
 * 개발자 도구용 조각 설정([runBotCharacterShardSet])의 계약(백로그 #70).
 *
 * ⚠️ **이 함수의 값은 "무엇을 할 수 있는가"가 아니라 "무엇을 못 하게 막는가"에 있다.** 화면이
 * `copy(adShards = …)`를 직접 만들면 획득 경계를 우회하는 상태를 손쉽게 만들 수 있고, 그러면
 * 그 경계에 기대고 있던 문구와 테스트가 조용히 무의미해진다.
 */
class BotCharacterShardSetTest {

    /**
     * ⚠️ **핵심 계약** — 필요 수 이상으로는 올라가지 않는다. *"다 모았는데 미획득"* 은
     * [BotCollectionState.withAdShard]가 그 순간 획득으로 넘기기 때문에 **도달할 수 없는
     * 상태**이고, 해금 힌트 문구가 그 사실에 기대고 있다. 개발자 도구가 그 상태를 만들면
     * 경계를 지키던 테스트가 통째로 뜻을 잃는다.
     */
    @Test
    fun theShardCountCanNeverReachTheRequiredAmount() {
        val store = FakeStore()

        runBotCharacterShardSet(shardCharacter, count = shardRequired + 5, store = store)

        assertEquals(shardRequired - 1, store.stored.shardsFor(shardCharacter.id))
        assertTrue(
            !store.stored.isClaimed(shardCharacter.id),
            "조각만 맞췄는데 획득까지 넘어갔다 — 이 함수는 획득을 만들지 않아야 한다(#70).",
        )
    }

    /** "한 개 남기기"가 실제로 한 개를 남긴다 — 그 뒤 광고 한 번으로 획득 루틴이 끝까지 밟힌다. */
    @Test
    fun settingOneShortLeavesExactlyOneAdAwayFromUnlocking() {
        val store = FakeStore()

        runBotCharacterShardSet(shardCharacter, count = shardRequired - 1, store = store)
        val afterOneMoreAd = runBotCharacterShardGrant(shardCharacter, store)

        assertEquals(true, afterOneMoreAd?.unlocked, "한 개 남긴 뒤 광고 한 번으로 획득되지 않았다.")
        assertTrue(store.stored.isClaimed(shardCharacter.id))
    }

    /** 0으로 맞추면 그 줄이 저장에서 아예 사라진다 — 0을 값으로 남기면 정규형이 갈라진다. */
    @Test
    fun clearingRemovesTheEntryRatherThanStoringZero() {
        val store = FakeStore(BotCollectionState(adShards = mapOf(shardCharacter.id to 2)))

        runBotCharacterShardSet(shardCharacter, count = 0, store = store)

        assertEquals(emptyMap(), store.stored.adShards)
    }

    /** 음수도 0과 같이 다룬다 — 호출부가 실수해도 저장이 오염되지 않아야 한다. */
    @Test
    fun aNegativeCountIsTreatedAsClearing() {
        val store = FakeStore(BotCollectionState(adShards = mapOf(shardCharacter.id to 3)))

        runBotCharacterShardSet(shardCharacter, count = -7, store = store)

        assertEquals(0, store.stored.shardsFor(shardCharacter.id))
    }

    /**
     * ⚠️ 조각 경로가 **아닌** 캐릭터(출석 해금)에는 아무것도 하지 않는다. 그쪽에 조각을 심으면
     * 저장에 뜻 없는 줄이 남고, 픽커가 진행도를 그릴 수 없는 캐릭터에 진행도가 생긴다.
     */
    @Test
    fun anAttendanceCharacterIsLeftAlone() {
        val store = FakeStore()

        assertNull(runBotCharacterShardSet(attendanceCharacter, count = 3, store = store))
        assertEquals(BotCollectionState(), store.stored)
        assertEquals(0, store.saveCount, "아무 변화도 없는데 저장까지 했다.")
    }

    /** 이미 획득한 캐릭터에도 손대지 않는다 — 진행도는 보유한 캐릭터에 뜻이 없다. */
    @Test
    fun anAlreadyOwnedCharacterIsLeftAlone() {
        val owned = BotCollectionState(claimedBots = setOf(shardCharacter.id))
        val store = FakeStore(owned)

        assertNull(runBotCharacterShardSet(shardCharacter, count = 1, store = store))
        assertEquals(owned, store.stored)
        assertEquals(0, store.saveCount)
    }

    /** 바뀌는 것이 없으면 저장하지 않는다 — 같은 값을 다시 눌러도 디스크를 건드리지 않는다. */
    @Test
    fun settingTheSameValueDoesNotWrite() {
        val store = FakeStore(BotCollectionState(adShards = mapOf(shardCharacter.id to shardRequired - 1)))

        assertNull(runBotCharacterShardSet(shardCharacter, count = shardRequired - 1, store = store))
        assertEquals(0, store.saveCount)
    }
}
