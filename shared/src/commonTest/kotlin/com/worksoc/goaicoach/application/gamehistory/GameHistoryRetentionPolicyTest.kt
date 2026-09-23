package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U-4의 **두 겹 안전장치**(2026-09-18 사용자: *"최대 1000개 && 총 100MB 이하, 안전장치 2개"*).
 *
 * ⚠️ 상한을 느슨하게 고치면 이 테스트가 먼저 깨진다 — 숫자가 사용자 결정이기 때문이다.
 * 바꾸려거든 **사용자에게 물을 것.**
 */
class GameHistoryRetentionPolicyTest {

    private fun entry(id: String) = GameHistoryEntry(
        id = id,
        playedAtMillis = id.toLong(),
        boardSize = 19,
        ruleset = Ruleset.Chinese,
        komi = 6.5,
        handicapCount = 0,
        playerSetup = PlayerSetup(),
        moveCount = 200,
        humanColor = StoneColor.Black,
        winner = StoneColor.Black,
    )

    private fun entries(count: Int) = List(count) { entry((it + 1).toString()) }

    @Test
    fun theUserFixedBothLimitsSoTheyAreNotFreeToDrift() {
        assertEquals(1_000, GameHistoryRetentionPolicy.MaxEntries)
        assertEquals(100L * 1024 * 1024, GameHistoryRetentionPolicy.MaxTotalBytes)
    }

    @Test
    fun nothingIsEvictedWhileBothLimitsHold() {
        val evicted = GameHistoryRetentionPolicy.idsToEvict(entries(10)) { 1_000L }

        assertTrue(evicted.isEmpty())
    }

    @Test
    fun theCountLimitEvictsTheOldestFirst() {
        val all = entries(GameHistoryRetentionPolicy.MaxEntries + 3)

        val evicted = GameHistoryRetentionPolicy.idsToEvict(all) { 1L }

        assertEquals(listOf("1", "2", "3"), evicted, "가장 오래된 셋이 밀려야 한다")
    }

    /**
     * ⚠️ 개수만으로는 못 막는다 — 한 판의 크기가 판 크기·수순 길이에 따라 크게 달라진다.
     * 열 판밖에 없어도 총량이 상한을 넘을 수 있다.
     */
    @Test
    fun theByteLimitEvictsEvenWhenTheCountIsWellUnderTheCap() {
        val fortyMegabytes = 40L * 1024 * 1024

        val evicted = GameHistoryRetentionPolicy.idsToEvict(entries(4)) { fortyMegabytes }

        assertEquals(listOf("1", "2"), evicted, "160MB에서 100MB 아래로 내려올 만큼만 민다")
    }

    /** ⚠️ 방금 둔 판까지 밀면 "저장했다"고 말해 놓고 아무것도 안 남는다. */
    @Test
    fun theNewestEntryIsNeverEvictedEvenIfItAloneExceedsTheLimit() {
        val huge = GameHistoryRetentionPolicy.MaxTotalBytes * 2

        val evicted = GameHistoryRetentionPolicy.idsToEvict(entries(1)) { huge }

        assertTrue(evicted.isEmpty(), "한 판만 남았으면 더 밀지 않는다")
    }

    @Test
    fun anOversizedEntryIsFlaggedButThatIsADiagnosticNotABlock() {
        assertTrue(GameHistoryRetentionPolicy.isOversized(100L * 1024 + 1))
        assertTrue(!GameHistoryRetentionPolicy.isOversized(100L * 1024))
    }
}
