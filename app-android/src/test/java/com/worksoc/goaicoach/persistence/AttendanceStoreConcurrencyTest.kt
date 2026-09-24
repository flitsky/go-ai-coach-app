package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.attendance.AttendanceCheckInRequest
import com.worksoc.goaicoach.application.attendance.AttendanceRewardPolicy
import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.attendance.MillisPerUtcDay
import com.worksoc.goaicoach.application.attendance.runAttendanceCheckIn
import com.worksoc.goaicoach.application.attendance.runAttendanceRewardGrant
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.premium.port.PremiumStateStorePort
import com.worksoc.goaicoach.application.premium.state.PremiumState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 출석 저장소의 **스레드 경합** 재현(refactor backlog #21).
 *
 * ## 실제로 겹치는 두 경로
 * - **체크인**: `AttendanceCheckInCoordinator`가 `Dispatchers.IO`에서 foreground 이벤트마다
 *   `runAttendanceCheckIn`을 돈다 — 읽고(`load`) 하루를 올려 쓴다(`save`).
 * - **Claim**: `AttendanceRewardClaimDialog`의 `onClaim`이 **메인 스레드**에서
 *   `runAttendanceRewardGrant(state = attendanceStore.load(), …)`를 돈다 — 읽은 스냅샷에서 받을
 *   회차를 정하고, 보상 셋을 흘려보낸 뒤 그 스냅샷에 지급 기록을 얹어 쓴다.
 *
 * 둘 다 `AttendanceStore` **다른 인스턴스**로 같은 prefs 키(`attendance_state`)를 읽고-고치고-쓴다.
 * 락이 없으면 늦게 쓰는 쪽이 먼저 쓴 쪽을 지운다. 여기서는 [InMemorySharedPreferences]의 훅으로
 * **읽은 직후** 한 스레드를 붙잡아 그 사이에 다른 스레드를 끝까지 돌려, 그 순서를 결정적으로 만든다.
 *
 * 붙잡힌 스레드는 "상대가 썼다" **또는** "상대가 락에서 막혔다(`BLOCKED`)" 중 먼저 오는 쪽에서 풀린다
 * — 고친 뒤에는 상대가 락에서 기다리므로 앞의 조건은 영영 오지 않는다(오면 그게 곧 경합이다).
 */
class AttendanceStoreConcurrencyTest {

    @Test
    fun claimWrittenWhileCheckInHoldsAStaleReadIsNotErased() {
        // 체크인이 읽은 직후 멈춘 사이 Claim이 끝까지 돈다. 락이 없으면 체크인이 **옛 지급 기록**을 쥔 채
        // 덮어써 4일차 지급 기록이 사라지고, 4일차가 다시 "받을 것"으로 떠 **1회권이 한 번 더 지급된다.**
        val prefs = InMemorySharedPreferences()
        AttendanceStore(prefs).save(BeforeRace)
        val checkInStore = AttendanceStore(prefs) // AttendanceCheckInCoordinator의 인스턴스
        val claimStore = AttendanceStore(prefs) // AttendanceRewardClaimDialog의 인스턴스

        val checkInRead = CountDownLatch(1)
        val claimWrote = CountDownLatch(1)
        val armed = AtomicBoolean(true)
        val checkInThread = Thread { checkIn(checkInStore) }
        val claimThread = Thread { claim(claimStore) }
        prefs.afterRead = {
            if (Thread.currentThread() === checkInThread && armed.compareAndSet(true, false)) {
                checkInRead.countDown()
                waitUntil { claimWrote.count == 0L || claimThread.state == Thread.State.BLOCKED }
            }
        }
        prefs.afterWrite = { if (Thread.currentThread() === claimThread) claimWrote.countDown() }

        checkInThread.start()
        assertTrue("체크인 스레드가 읽기에 도달하지 못했다", checkInRead.await(5, TimeUnit.SECONDS))
        claimThread.start()
        joinBoth(checkInThread, claimThread)

        assertBothSurvived(AttendanceStore(prefs).load())
    }

    @Test
    fun checkInWrittenWhileClaimHoldsAStaleSnapshotIsNotErased() {
        // 거울상 — Claim이 스냅샷을 읽고 멈춘 사이 체크인이 끝까지 돈다. Claim이 그 스냅샷에 지급 기록만
        // 얹어 쓰면 **방금 오른 출석일이 되돌아간다.**
        val prefs = InMemorySharedPreferences()
        AttendanceStore(prefs).save(BeforeRace)
        val checkInStore = AttendanceStore(prefs)
        val claimStore = AttendanceStore(prefs)

        val claimRead = CountDownLatch(1)
        val checkInWrote = CountDownLatch(1)
        val armed = AtomicBoolean(true)
        val checkInThread = Thread { checkIn(checkInStore) }
        val claimThread = Thread { claim(claimStore) }
        prefs.afterRead = {
            if (Thread.currentThread() === claimThread && armed.compareAndSet(true, false)) {
                claimRead.countDown()
                waitUntil { checkInWrote.count == 0L || checkInThread.state == Thread.State.BLOCKED }
            }
        }
        prefs.afterWrite = { if (Thread.currentThread() === checkInThread) checkInWrote.countDown() }

        claimThread.start()
        assertTrue("Claim 스레드가 읽기에 도달하지 못했다", claimRead.await(5, TimeUnit.SECONDS))
        checkInThread.start()
        joinBoth(checkInThread, claimThread)

        assertBothSurvived(AttendanceStore(prefs).load())
    }

    @Test
    fun storeReadsTheOldBlobAndWritesTheUnchangedCodecOutputUnderTheSameKey() {
        // ⚠️ 포맷 불변(함정 69) — 고치기 전 코드가 쓰던 그대로의 문자열을 심어 두고 읽고, 쓴 결과가
        // **바뀌지 않은 코덱의 출력 그 자체**이며 **같은 키**에 있음을 본다.
        val oldBlob = """{"schema":1,"attendanceCount":4,"lastCheckInUtcDay":$Yesterday,"claimedTiers":[1,2,3]}"""
        val prefs = InMemorySharedPreferences(mapOf(StateKey to oldBlob))
        val store = AttendanceStore(prefs)

        assertEquals(BeforeRace, store.load())

        // Claim을 먼저 한다 — 체크인이 먼저면 5일차까지 받을 것이 돼 [AfterBoth]와 달라진다.
        claim(store)
        checkIn(store)

        assertEquals(AttendanceCodec.encode(AfterBoth), prefs.rawString(StateKey))
        assertEquals(setOf(StateKey), prefs.all.keys)
    }

    private fun checkIn(store: AttendanceStore) {
        runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = TodayMillis), store)
    }

    private fun claim(store: AttendanceStore) {
        // `AttendanceRewardClaimDialog.onClaim`과 같은 모양 — 스냅샷을 먼저 읽어 넘긴다.
        runAttendanceRewardGrant(
            state = store.load(),
            attendanceStore = store,
            premiumStore = MemoryPremiumStore(),
            consumableStore = MemoryConsumableStore(),
            botStore = MemoryBotStore(),
        )
    }

    private fun assertBothSurvived(stored: AttendanceState) {
        assertEquals("체크인(출석일 +1)이 사라졌다", 5, stored.attendanceCount)
        assertEquals("체크인(오늘 날짜)이 사라졌다", Today, stored.lastCheckInUtcDay)
        assertTrue(
            "Claim(4일차 지급 기록)이 사라졌다 — 4일차가 다시 받을 것으로 뜬다: ${stored.claimedTiers}",
            4 in stored.claimedTiers,
        )
        assertTrue(AttendanceRewardPolicy.pendingTiers(stored).none { it.tier == 4 })
        assertEquals(AfterBoth, stored)
    }

    private fun joinBoth(first: Thread, second: Thread) {
        first.join(10_000)
        second.join(10_000)
        assertTrue("스레드가 끝나지 않았다(교착?)", !first.isAlive && !second.isAlive)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(1)
    }

    private class MemoryPremiumStore : PremiumStateStorePort {
        @Volatile private var state = PremiumState()
        override fun save(state: PremiumState) { this.state = state }
        override fun load(): PremiumState = state
    }

    private class MemoryConsumableStore : ConsumableStorePort {
        @Volatile private var inventory = ConsumableInventory()
        override fun save(inventory: ConsumableInventory) { this.inventory = inventory }
        override fun load(): ConsumableInventory = inventory
    }

    private class MemoryBotStore : BotCollectionStorePort {
        @Volatile private var state = BotCollectionState()
        override fun save(state: BotCollectionState) { this.state = state }
        override fun load(): BotCollectionState = state
    }

    private companion object {
        const val StateKey = "attendance_state"
        const val Yesterday = 20_000L
        const val Today = Yesterday + 1
        const val TodayMillis = Today * MillisPerUtcDay + 1_234L

        /** 어제 4일차까지 출석했고 1~3일차는 받았다 — 4일차(1회권)가 받을 것으로 떠 있다. */
        val BeforeRace = AttendanceState(attendanceCount = 4, lastCheckInUtcDay = Yesterday, claimedTiers = setOf(1, 2, 3))

        /** 체크인(5일차)과 Claim(4일차 지급 기록)이 **둘 다** 남은 상태. */
        val AfterBoth = AttendanceState(attendanceCount = 5, lastCheckInUtcDay = Today, claimedTiers = setOf(1, 2, 3, 4))

        init {
            // 경합 판정이 클래스 초기화 락(`BLOCKED`로 보인다)에 속지 않도록, 쓰일 클래스를 미리 다 띄운다.
            val warm = AttendanceStore(InMemorySharedPreferences())
            warm.save(BeforeRace)
            runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = TodayMillis), warm)
            runAttendanceRewardGrant(warm.load(), warm, MemoryPremiumStore(), MemoryConsumableStore(), MemoryBotStore())
        }
    }
}
