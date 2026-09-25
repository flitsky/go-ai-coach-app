package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor

internal object KataGoProtocolCommands {
    fun boardSize(boardSize: BoardSize): String = "boardsize ${boardSize.value}"

    fun komi(komi: Double = DefaultKomi): String = "komi $komi"

    fun rules(ruleset: Ruleset): String = "kata-set-rules ${ruleset.katagoName}"

    fun clearBoard(): String = "clear_board"

    fun setFreeHandicap(positions: List<BoardCoordinate>, boardSize: BoardSize): String {
        val vertices = positions.joinToString(" ") { it.label(boardSize) }
        return "set_free_handicap $vertices"
    }

    fun play(move: Move, boardSize: BoardSize): String =
        "play ${move.player.toGtpColor()} ${move.toGtpVertex(boardSize)}"

    fun genMove(player: StoneColor): String = "genmove ${player.toGtpColor()}"

    fun undo(): String = "undo"

    fun clearSearchCache(): String = "clear_cache"

    fun rawNn(): String = "kata-raw-nn 0"

    fun searchAnalyze(player: StoneColor, limit: AnalysisLimit): String {
        val timeMillis = limit.timeMillis ?: return "kata-search_analyze ${player.toGtpColor()}"
        val centiseconds = ((timeMillis + 9) / 10).coerceAtLeast(1)
        return "kata-search_analyze ${player.toGtpColor()} $centiseconds"
    }

    fun setMaxVisits(visits: Int): String = "kata-set-param maxVisits $visits"

    fun setMaxTime(timeMillis: Long): String {
        val seconds = (timeMillis / 1_000.0).coerceAtLeast(0.001)
        return "kata-set-param maxTime $seconds"
    }

    /** KataGo clears a dynamic config override when kata-set-param has no value. */
    fun clearMaxTime(): String = "kata-set-param maxTime"

    fun finalScore(): String = "final_score"

    fun finalStatusList(status: String): String = "final_status_list $status"

    fun quit(): String = "quit"
}

internal fun KataGoProtocolCommands.searchLimitCommands(limit: AnalysisLimit): List<String> =
    listOf(
        setMaxVisits(limit.visits),
        limit.timeMillis?.let(::setMaxTime) ?: clearMaxTime(),
    )

internal fun StoneColor.toGtpColor(): String =
    when (this) {
        StoneColor.Black -> "B"
        StoneColor.White -> "W"
    }

internal fun Move.toGtpVertex(boardSize: BoardSize): String =
    when (this) {
        is Move.Play -> coordinate.label(boardSize)
        is Move.Pass -> "pass"
        is Move.Resign -> "resign"
    }

/**
 * [toGtpVertex]의 역함수 — GTP 수 토큰(`D4`·`pass`·`resign`)을 [Move]로 읽고, 못 읽으면 `null`(refactor backlog #36).
 *
 * `pass`/`resign`을 대소문자 없이 먼저 보고, 나머지는 [BoardCoordinate.fromLabelOrNull]에 넘긴다 —
 * 좌표 규칙을 여기서 새로 쓰지 않는다. `KataGoAnalysisParser`·`KataGoJsonAnalysisParser`의 private
 * `String.toMove`와 같은 판정이다(셋을 이것으로 합치는 건 다음 단계이고, 예외를 던지는
 * `KataGoProcessEngineAdapter` 쪽은 그때도 실패 의미를 보존해야 한다).
 */
internal fun String.toGtpMoveOrNull(
    player: StoneColor,
    boardSize: BoardSize,
): Move? =
    when (lowercase()) {
        "pass" -> Move.Pass(player)
        "resign" -> Move.Resign(player)
        else -> BoardCoordinate.fromLabelOrNull(this, boardSize)?.let { coordinate -> Move.Play(player, coordinate) }
    }

/** Generous ceiling for GTP commands that carry no search-time budget of their own (e.g. play, undo, komi). */
internal const val DefaultCommandTimeoutMillis: Long = 30_000L

/** Overhead allowance added on top of a configured search-time cap, to absorb KataGo's own bookkeeping beyond its internal cap. */
private const val SearchTimeoutSlackMillis: Long = 20_000L

/** Ceiling used when the user has chosen an uncapped ("Off") search time, since KataGo is still bounded by maxVisits in that mode. */
private const val UnboundedSearchTimeoutMillis: Long = 120_000L

/** Derives the client-side wait deadline for a search-bound GTP/JSON call from its configured time cap, if any. */
internal fun searchTimeoutMillisFor(timeMillis: Long?): Long =
    timeMillis?.let { it + SearchTimeoutSlackMillis } ?: UnboundedSearchTimeoutMillis
