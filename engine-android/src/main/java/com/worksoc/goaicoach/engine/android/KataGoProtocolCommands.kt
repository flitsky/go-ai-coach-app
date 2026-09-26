package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.HandicapBonusRule
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit

internal object KataGoProtocolCommands {
    fun boardSize(boardSize: BoardSize): String = "boardsize ${boardSize.value}"

    fun komi(komi: Double = DefaultKomi): String = "komi $komi"

    /**
     * 대국 시작 때 보내는 룰 명령 — `kata-set-rules <이름>`에, [Ruleset.handicapBonusRule]이 그 이름 룰의
     * 기본값과 다를 때만 `kata-set-rule whiteHandicapBonus <N|N-1|0>`을 잇는다(refactor backlog #106).
     * 지금 룰셋은 전부 기본값과 같아 `kata-set-rules <이름>` 한 줄만 나간다(#106 이전과 같은 명령).
     */
    fun ruleCommands(ruleset: Ruleset): List<String> = ruleCommands(ruleset.katagoName, ruleset.handicapBonusRule)

    /** [ruleCommands]의 본체 — 테스트가 기본값과 다른 조합(지금 룰셋에는 없다)을 넣어 보려고 따로 둔다. */
    fun ruleCommands(
        katagoName: String,
        handicapBonusRule: HandicapBonusRule,
    ): List<String> =
        listOfNotNull(
            "kata-set-rules $katagoName",
            KataGoNamedRules.handicapBonusOverride(katagoName, handicapBonusRule)
                ?.let { override -> "kata-set-rule whiteHandicapBonus ${override.katagoValue}" },
        )

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
 * 좌표 규칙을 여기서 새로 쓰지 않는다. 분석 응답(`KataGoAnalysisParser`·`KataGoJsonAnalysisParser`)처럼
 * 못 읽는 후보 하나만 버리면 되는 자리에서 쓴다. 던지는 짝은 [toGtpMove]다.
 */
internal fun String.toGtpMoveOrNull(
    player: StoneColor,
    boardSize: BoardSize,
): Move? =
    toGtpPassOrResignOrNull(player)
        ?: BoardCoordinate.fromLabelOrNull(this, boardSize)?.let { coordinate -> Move.Play(player, coordinate) }

/**
 * [toGtpMoveOrNull]의 던지는 짝 — 좌표를 못 읽으면 [BoardCoordinate.fromLabel]의 [IllegalArgumentException]을
 * **그 문구 그대로** 던진다(refactor backlog #36).
 *
 * `genmove` 응답처럼 못 읽는 것이 곧 엔진 이상인 자리에서 쓴다. ⚠️ 여기를 `null`·패스로 "관대하게"
 * 바꾸지 마라 — KataGo가 둔 수를 모른 채 대국이 이어진다(`GtpMoveTokenCallSiteFailureTest`가 고정한다).
 */
internal fun String.toGtpMove(
    player: StoneColor,
    boardSize: BoardSize,
): Move =
    toGtpPassOrResignOrNull(player)
        ?: Move.Play(player, BoardCoordinate.fromLabel(this, boardSize))

/** 두 읽기가 함께 쓰는 비착수 토큰 판정 — 대소문자를 가리지 않는다. 착수 토큰이면 `null`. */
private fun String.toGtpPassOrResignOrNull(player: StoneColor): Move? =
    when (lowercase()) {
        "pass" -> Move.Pass(player)
        "resign" -> Move.Resign(player)
        else -> null
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
