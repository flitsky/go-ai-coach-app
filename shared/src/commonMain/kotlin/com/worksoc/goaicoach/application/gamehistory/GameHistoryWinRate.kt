package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.match.isRankMeasure

/**
 * **대국 기록의 승률**(백로그 #227, 사용자 피드백 2026-10-08: *"승률 — 최근 10게임, 누적, 최근 한 달 등 선택 가능"*).
 *
 * 사용자가 정한 것(2026-10-08): **사람 대 AI 판만** 센다 · **10수 이하 판**과 **승자를 모르는 옛 기권 기록**은 뺀다 ·
 * **기력 측정 대국은 따로** 보인다.
 * 스레드가 메운 것(사용자에게 알렸다): 「최근 10판」은 **갈래마다**(일반 대국 10판 · 기력 측정 대국 10판) 센다 ·
 * 「최근 한 달」은 지금부터 30일 · 무승부는 판 수에 넣고 승으로 치지 않는다 · 처음 보이는 기간은 최근 10판이다.
 */
enum class WinRatePeriod {
    LastTenGames,
    LastMonth,
    AllTime,
}

/** 한 갈래의 전적. */
data class WinRateTally(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
) {
    val games: Int get() = wins + losses + draws

    /** 승률(%) — 반올림한 정수. 센 판이 없으면 `null`(0%라고 말하지 않는다). */
    val winRatePercent: Int? get() = if (games == 0) null else (wins * 100 + games / 2) / games
}

/** [regular] = 캐릭터와 둔 일반 대국, [rankMeasure] = 기력 측정 대국. */
data class GameHistoryWinRate(
    val regular: WinRateTally,
    val rankMeasure: WinRateTally,
)

/** 이 수 **이하**인 판은 세지 않는다 — 대국 기록의 「10수 이하 기록 제외하기」(#208)와 같은 잣대다. */
const val WinRateShortGameMaxMoveCount: Int = 10

const val WinRateLastGamesCount: Int = 10

const val WinRateLastMonthMillis: Long = 30L * 24 * 60 * 60 * 1000

/**
 * 승률에 세는 판인가 — 사람이 정확히 한 명이고(사람 대 AI), 10수를 넘겼고, 결과를 안다.
 * ⚠️ 승자가 없는 판은 둘이다: 무승부(센다)와, 누가 기권했는지 적지 않던 옛 기권 기록(승패를 모르니 세지 않는다).
 */
fun GameHistoryEntry.countsForWinRate(): Boolean =
    humanColor != null && moveCount > WinRateShortGameMaxMoveCount && !(winner == null && isResign)

/**
 * [entries] 가운데 [period]에 드는 판의 전적. 판의 순서는 묻지 않는다(둔 시각으로 가린다).
 * ⚠️ 번들 참고 기보처럼 사용자가 두지 않은 기록은 부르는 쪽이 먼저 뺀다 — 이 층은 그 id를 모른다.
 */
fun gameHistoryWinRate(
    entries: List<GameHistoryEntry>,
    period: WinRatePeriod,
    nowMillis: Long,
): GameHistoryWinRate {
    val counted = entries.filter { it.countsForWinRate() }.sortedByDescending { it.playedAtMillis }
    val (rankMeasure, regular) = counted.partition { it.playerSetup.isRankMeasure() }
    return GameHistoryWinRate(regular = regular.within(period, nowMillis).tally(), rankMeasure = rankMeasure.within(period, nowMillis).tally())
}

private fun List<GameHistoryEntry>.within(period: WinRatePeriod, nowMillis: Long): List<GameHistoryEntry> =
    when (period) {
        WinRatePeriod.LastTenGames -> take(WinRateLastGamesCount)
        WinRatePeriod.LastMonth -> filter { it.playedAtMillis >= nowMillis - WinRateLastMonthMillis }
        WinRatePeriod.AllTime -> this
    }

private fun List<GameHistoryEntry>.tally(): WinRateTally =
    WinRateTally(
        wins = count { it.winner != null && it.winner == it.humanColor },
        losses = count { it.winner != null && it.winner != it.humanColor },
        draws = count { it.winner == null },
    )
