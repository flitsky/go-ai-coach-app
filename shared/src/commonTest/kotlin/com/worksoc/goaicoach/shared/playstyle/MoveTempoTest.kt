package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 캐릭터의 착수 템포(백로그 #212) — 판의 10%까지 1초 · 20%까지 2초 · 그 뒤 캐릭터마다 · 10집 이상 뒤지면 처음부터 제 템포 ·
 * 상한은 사용자의 최대 탐색 시간. 구간과 숫자는 사용자가 정했고(2026-10-20), 캐릭터마다의 제 템포는 스레드가 정했다.
 */
class MoveTempoTest {
    private val tempo = MoveTempo(ownMillis = 4_000L)

    private fun bands(size: BoardSize, vararg moveNumbers: Int) =
        moveNumbers.map { moveNumber -> tempo.bandAt(moveNumber, size, ownScoreLead = null) }

    /** 사용자의 예(19줄 36수)와 같은 잣대가 9줄 · 13줄에도 — 이번 수의 번호 ÷ 자리 수가 10% · 20% 이하. */
    @Test
    fun theBandsEndAtTenAndTwentyPercentOfTheBoard() {
        assertEquals(
            listOf(TempoBand.Quick, TempoBand.Quick, TempoBand.Brisk, TempoBand.Brisk, TempoBand.Own),
            bands(BoardSize.Nineteen, 1, 36, 37, 72, 73),
        )
        assertEquals(
            listOf(TempoBand.Quick, TempoBand.Brisk, TempoBand.Brisk, TempoBand.Own),
            bands(BoardSize.Thirteen, 16, 17, 33, 34),
        )
        assertEquals(
            listOf(TempoBand.Quick, TempoBand.Brisk, TempoBand.Brisk, TempoBand.Own),
            bands(BoardSize.Nine, 8, 9, 16, 17),
        )
    }

    @Test
    fun aTenPointDeficitLiftsTheQuickOpening() {
        assertEquals(TempoBand.Own, tempo.bandAt(moveNumber = 5, BoardSize.Nineteen, ownScoreLead = -10.0))
        assertEquals(TempoBand.Own, tempo.bandAt(moveNumber = 50, BoardSize.Nineteen, ownScoreLead = -25.0))
        assertEquals(TempoBand.Quick, tempo.bandAt(moveNumber = 5, BoardSize.Nineteen, ownScoreLead = -9.9))
        assertEquals(TempoBand.Quick, tempo.bandAt(moveNumber = 5, BoardSize.Nineteen, ownScoreLead = 30.0), "앞서면 그대로 빠르다")
    }

    @Test
    fun theTargetNeverExceedsTheUsersMaximumSearchTime() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L), TempoBand.entries.map { tempo.targetMillis(it, capMillis = null) }, "「끔」이면 상한이 없다")
        assertEquals(
            listOf(1_000L, 2_000L, 3_000L),
            TempoBand.entries.map { tempo.targetMillis(it, SearchTimeLimit.WithinThreeSeconds.maximumMillis) },
        )
        assertEquals(
            listOf(1_000L, 1_000L, 1_000L),
            TempoBand.entries.map { tempo.targetMillis(it, SearchTimeLimit.WithinOneSecond.maximumMillis) },
        )
    }

    /** 형세는 가장 최근의 신경망 평가를 AI 쪽에서 읽는다 — 국소 계가는 쓰지 않는다. */
    @Test
    fun theDeficitIsReadFromTheLatestNetworkEstimateOnTheAiSide() {
        val snapshots = listOf(
            ScoreSnapshot(moveNumber = 10, whiteScoreLead = 3.0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 12, whiteScoreLead = -12.0, source = ScoreSnapshotSource.HumanNetworkEstimate),
            ScoreSnapshot(moveNumber = 13, whiteScoreLead = 40.0, source = ScoreSnapshotSource.LocalAreaEstimate),
        )

        assertEquals(-12.0, MoveTempo.ownScoreLead(StoneColor.White, snapshots))
        assertEquals(12.0, MoveTempo.ownScoreLead(StoneColor.Black, snapshots))
        assertNull(MoveTempo.ownScoreLead(StoneColor.White, emptyList()))
    }

    /** 다섯 캐릭터의 제 템포 — 셀수록 신중하다. 숨긴 그룹과 승급 대국은 중립이다. */
    @Test
    fun eachCharacterHasItsOwnTempoAndOthersStayNeutral() {
        assertEquals(
            listOf(2_000L, 2_500L, 3_000L, 4_000L, 5_000L),
            (1..5).map { level -> PlayLevelSetting(PlayLevelGroup.FastBeginner, level).moveTempo()?.ownMillis },
        )
        PlayLevelGroup.entries
            .filter { group -> group != PlayLevelGroup.FastBeginner }
            .forEach { group -> assertNull(PlayLevelSetting(group, 1).moveTempo(), "$group") }
        assertNull(KgsRank.kyu(5).toPlayLevelSetting().moveTempo())
    }
}
