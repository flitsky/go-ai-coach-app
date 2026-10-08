package com.worksoc.goaicoach.application.rankmeasure

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.isRankMeasure
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.customRank
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting
import kotlin.math.floor

/**
 * 6계층 — **기력 측정 대국**(백로그 #219): 내 기력을 알아보고, 비슷한 상대와 되풀이해 두며 배운다.
 *
 * 사람 한 명이 **자기 기력과 같은 급수의 AI**와 둔다. 이기면 오르고 지면 내려서, 이기고 지기를 되풀이하는 급수가 곧 그 사람의
 * 실력 구간이다(사용자 2026-10-06). 오르내리는 규칙은 [adjustRankAfterResult]에 있다(사용자 2026-10-07). 상대는 그 급수의 사람 모델 프로필이 둔다 — 캐릭터(#215)와 같은 엔진 경로다
 * (`PlayLevelGroup.CustomRank` · `shared.playstyle.humanPlayStyle`).
 *
 * ## 무엇이 어디에 사는가
 * - **이 판이 기력 측정 대국이라는 것**은 사람 좌석의 대국 종류([HumanGameType.RankMeasure])가 말한다 — 좌석 설정에 실려
 *   이어하기·대국 기록까지 간다(`PlayerSetup.isRankMeasure`, 5계층).
 * - **지금 두는 판의 급수**는 그 판의 AI 좌석이 갖는다(`SidePlayerSetup.playLevel`) — 판정 결과·대국 기록이 그것을 읽는다.
 * - **내 기력**(= 다음 판의 상대 급수)은 여기([RankMeasureState.rank])에 산다. 끝난 판이 이것을 고치고, 새 대국을 시작할 때
 *   좌석에 옮겨 적는다([withRankForNextMeasureGame]). 끝난 판의 화면이 방금 바뀐 급수로 바뀌어 보이지 않게 하려는 분리다.
 *
 * ⚠️ 알려진 틈 하나: 「재 대국」을 누르고 새 판이 준비되는 1~2초 사이에는 끝난 판과 바뀐 급수의 좌석이 함께 있다. 그 사이에 앱이 죽으면
 * 다시 켰을 때 끝난 판의 상대가 바뀐 급수로 적혀 보인다(대국 기록과 기력은 맞다). 끝난 판의 화면이 살아 있는 좌석 설정을 읽는
 * 기존 동작에서 오는 것이고, 그것을 고치는 일은 이 기능의 범위 밖이다.
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2) — 자동저장이 스냅샷을 처음부터 다시 조립해서, 배선하지 않은 필드는 조용히
 * 초기화된다. 제 저장소([RankMeasureStorePort])를 갖는다.
 */
data class RankMeasureState(
    /** 내 기력 — 다음 기력 측정 대국의 상대 급수다. 기록이 없으면 20급에서 시작한다. */
    val rank: KgsRank = KgsRank.Weakest,
    /**
     * 기력 측정을 **시작했는가**. 시작하기 전에만 — 곧 최초 1회에 한해 — 기력을 스스로 고를 수 있다([chooseStartingRank]).
     * 첫 판을 시작하는 순간 잠기고, 그 뒤로는 이기고 지는 것만이 기력을 옮긴다.
     */
    val hasStarted: Boolean = false,
    /**
     * **최고 기력** — 이긴 적이 있거나 승급으로 닿은 가장 높은 급수. 아직 없으면 `null`(화면은 「측정 기록 없음」).
     * 스스로 고른 시작 급수는 거기서 한 판 이기기 전에는 기록이 아니다 — 고른 것이지 잰 것이 아니다.
     */
    val peakRank: KgsRank? = null,
    /** 지금까지의 연패 수 — 2연패마다 한 단계 내리고 다시 0부터 센다. 이기면 0으로 돌아간다. 급·단 구간이 함께 쓴다. */
    val consecutiveLosses: Int = 0,
    /**
     * **단 구간의 최근 전적** — 지금의 단에서 둔 판의 승패(이겼으면 `true`), 오래된 것부터 최대 [RankMeasureDanWindow]판.
     * 승단은 이 가운데 [RankMeasureDanWinsToPromote]판을 이겼을 때다. 단이 바뀌면(오르든 내리든) 비우고 새로 센다 —
     * 다른 단에서 이긴 판을 지금 단의 전적으로 치지 않는다. 급 구간에서는 늘 비어 있다.
     */
    val recentDanResults: List<Boolean> = emptyList(),
    /**
     * 기력에 **이미 반영한** 마지막 판의 기록 id — 같은 판을 두 번 세지 않는다. 끝난 판의 화면은 여러 번 다시 그려지고,
     * 계가한 판은 앱을 껐다 켜도 그대로 복원된다(함정 88).
     */
    val lastCountedGameId: String? = null,
) {
    /** 기력을 스스로 고를 수 있는가 — 측정을 시작하기 전, 최초 1회뿐이다. */
    val canChooseStartingRank: Boolean get() = !hasStarted
}

/** 스스로 고를 수 있는 시작 기력의 범위 — 20급부터 1급까지(사용자 2026-10-06). 단은 이겨서 오른다. */
val StrongestStartingRank: KgsRank = KgsRank.kyu(1)

/** 급 구간에서 한 단계 더 오르는 데 필요한 집 수 차이 — 10집마다 한 단계(사용자 2026-10-07). */
const val RankMeasureMarginPerStep: Double = 10.0

/** 한 단계 내리는 데 필요한 연패 수 — 급·단 구간 공통(사용자 2026-10-07). */
const val RankMeasureDemotionLosses: Int = 2

/** 단 구간의 승단을 가리는 최근 전적의 판 수와, 그 가운데 이겨야 하는 판 수 — 「최근 5판 중 3판」(사용자 2026-10-07). */
const val RankMeasureDanWindow: Int = 5
const val RankMeasureDanWinsToPromote: Int = 3

/** 급 구간의 빠른 승급이 닿는 끝 — 아무리 크게 이겨도 1단에서 멈추고, 그 위는 단 구간의 규칙으로 오른다(사용자 2026-10-07). */
val RankMeasureKyuPromotionCap: KgsRank = KgsRank.dan(1)

/**
 * 최초 1회의 기력 선택. 이미 측정을 시작했으면 아무것도 바꾸지 않는다 — 그 뒤로는 이기고 지는 것만이 기력을 옮긴다.
 * 범위(20급~1급)를 넘은 값은 끝으로 당긴다.
 */
fun RankMeasureState.chooseStartingRank(chosen: KgsRank): RankMeasureState =
    if (canChooseStartingRank) copy(rank = minOf(chosen, StrongestStartingRank)) else this

/** 기력 측정 대국을 시작했다 — 이제 기력을 스스로 고를 수 없다. */
fun RankMeasureState.started(): RankMeasureState = if (hasStarted) this else copy(hasStarted = true)

/**
 * 급 구간에서 **한 판 이겼을 때 오르는 단계 수**(사용자 2026-10-07) — 이긴 집 수 차이를 10으로 나눈 몫, 최소 1.
 * 61집 차로 이기면 6단계(15급 → 9급), 아슬아슬하게 이겨도 1단계다. 크게 이길수록 제 급수를 빨리 찾아간다.
 *
 * ⚠️ 판 크기로 가르지 않는다 — 어느 판이든 같은 식이다(예전의 「9줄 3급 · 13줄 2급 · 19줄 1급」은 스레드가 넣었던 규칙이고
 * 사용자가 일관되지 않다고 거뒀다). 큰 판은 집 차이가 커서 한 번에 더 많이 오른다 — 그것까지가 이 규칙이다.
 *
 * @param margin 이긴 집 수 차이. 집 차이가 없는 승리(상대의 기권 — 지금 AI는 기권하지 않는다)는 `null`이고 1단계로 친다.
 */
fun rankMeasurePromotionSteps(margin: Double?): Int =
    if (margin == null) 1 else (kotlin.math.abs(margin) / RankMeasureMarginPerStep).toInt().coerceAtLeast(1)

/**
 * 그 기력에서 둘 수 있는 판 크기(사용자 2026-10-06) — 기력이 오를수록 큰 판으로 좁힌다: 20~11급은 9·13·19줄 전부,
 * 10~1급은 13·19줄, 1~9단은 19줄만. 급수 차이는 판이 작을수록 덜 드러나서, 센 기력을 작은 판으로 재면 재는 것이 아니게 된다.
 */
fun rankMeasureBoardSizesFor(rank: KgsRank): List<BoardSize> =
    when {
        rank.isDan -> listOf(BoardSize.Nineteen)
        rank >= KgsRank.kyu(10) -> listOf(BoardSize.Thirteen, BoardSize.Nineteen)
        else -> listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen)
    }

/** [preferred]를 그 기력에서 둘 수 있으면 그대로, 아니면 둘 수 있는 **가장 작은** 판(가장 가까운 것이다 — 제한은 작은 판부터 닫힌다). */
fun rankMeasureBoardSizeFor(rank: KgsRank, preferred: BoardSize): BoardSize {
    val allowed = rankMeasureBoardSizesFor(rank)
    return if (preferred in allowed) preferred else allowed.first()
}

/** 기력 측정 대국의 좌석 배치 — 사람([userColor], 대국 종류 = 기력 측정) 대 그 급수의 AI. */
fun rankMeasurePlayerSetup(userColor: StoneColor, rank: KgsRank): PlayerSetup {
    val user = SidePlayerSetup(controller = SeatController.Human, humanGameType = HumanGameType.RankMeasure)
    val opponent = SidePlayerSetup(controller = SeatController.Ai, playLevel = rank.toPlayLevelSetting())
    return if (userColor == StoneColor.Black) PlayerSetup(black = user, white = opponent) else PlayerSetup(black = opponent, white = user)
}

/** 기력 측정 대국의 두 좌석 — 사람의 색과 상대의 급수. */
data class RankMeasureMatchup(
    val userColor: StoneColor,
    val opponentRank: KgsRank,
)

/**
 * 이 좌석 배치가 기력 측정 대국이면 그 두 좌석, 아니면 `null`. 사람 좌석이 기력 측정이라고 표시돼 있고([HumanGameType.RankMeasure])
 * 상대가 급수를 직접 정한 AI여야 한다 — 표시 없이 급수만 직접 정한 AI(나중의 인공지능 캐릭터, #220)는 기력 측정이 아니다.
 */
fun PlayerSetup.rankMeasureMatchup(): RankMeasureMatchup? {
    val blackIsUser = black.isRankMeasureUser()
    val whiteIsUser = white.isRankMeasureUser()
    if (blackIsUser == whiteIsUser) return null
    val (userColor, opponent) = if (blackIsUser) StoneColor.Black to white else StoneColor.White to black
    if (opponent.controller != SeatController.Ai) return null
    val rank = opponent.playLevel.customRank() ?: return null
    return RankMeasureMatchup(userColor = userColor, opponentRank = rank)
}

private fun SidePlayerSetup.isRankMeasureUser(): Boolean =
    controller == SeatController.Human && humanGameType == HumanGameType.RankMeasure

/**
 * 새 기력 측정 대국을 시작하기 직전의 좌석 배치 — 상대를 **내 기력**([RankMeasureState.rank])으로 맞춘다.
 * 기력 측정 대국이 아니면 그대로 돌려준다. 바뀔 것이 없으면 **같은 값**이다(부르는 쪽이 `!=`로 가른다).
 */
fun PlayerSetup.withRankForNextMeasureGame(state: RankMeasureState): PlayerSetup {
    val matchup = rankMeasureMatchup() ?: return this
    return if (matchup.opponentRank == state.rank) this else rankMeasurePlayerSetup(matchup.userColor, state.rank)
}

/**
 * **끝난 기력 측정 대국을 되살릴 때**의 설정 — 결과 창이 떠 있는 채로 프로세스가 죽었다 돌아온 경우다.
 *
 * 끝난 판의 복원은 판과 판정만 되살리고 좌석은 살아 있는 설정의 것을 쓴다. 일반 대국은 그 설정이 곧 그 판의 설정이라 문제가 없었지만,
 * 기력 측정 대국의 좌석·판 크기·호선은 **일반 설정에 저장되지 않는다**([rankMeasurePlayerSetup] 참고) — 그대로 두면 되살아난 판이
 * 캐릭터와 둔 일반 대국으로 보이고(상대 이름·진영이 틀린다), 「재 대국」이 일반 대국으로 시작하며, 이어하기 저장분도 그렇게 덮인다
 * (2026-10-06 에뮬레이터). 그래서 그 판의 것을 설정에 다시 올린다. 일반 대국이거나 아직 진행 중인 판이면 그대로 돌려준다.
 */
fun GameSessionSettingsState.withEndedRankMeasureGame(snapshot: SavedGameSnapshot): GameSessionSettingsState =
    if (snapshot.finalScoreJudgement == null || !snapshot.playerSetup.isRankMeasure()) {
        this
    } else {
        copy(playerSetup = snapshot.playerSetup, boardSize = snapshot.gameState.boardSize, handicapCount = 0, komi = snapshot.gameState.komi)
    }

/** 끝난 판이 기력을 어떻게 옮겼는가 — 사용자에게 **팝업으로** 알릴 일이다(몇 단계 움직였는지까지). */
sealed interface RankMeasureChange {
    /** 그대로다(졌지만 아직 2연패가 아니거나, 단 구간에서 아직 5판 중 3승이 아니다). */
    data object None : RankMeasureChange

    /**
     * 올랐다. 급 구간에서 왔으면 한 판의 승리가 올린 것이고([margin]이 그 판의 집 수 차이), 단 구간에서 왔으면 최근 전적이 올린 것이다.
     * @property margin 그 판을 이긴 집 수 차이 — 집 차이가 없는 승리면 `null`.
     */
    data class Promoted(
        val from: KgsRank,
        val to: KgsRank,
        val margin: Double?,
        /** 상대(AI)가 기권해 이긴 판인가 — 그러면 [margin]은 계가한 집 수 차이가 아니라 **기권한 순간의 형세 점수차**다. */
        val byResignation: Boolean = false,
    ) : RankMeasureChange {
        /** 실제로 오른 단계 수 — 1단에서 멈췄으면 식의 값보다 작다. */
        val steps: Int get() = to.step - from.step
    }

    /** 2연패로 한 단계 내렸다. */
    data class Demoted(val from: KgsRank, val to: KgsRank) : RankMeasureChange

    /** 오를 차례였지만 이미 9단이다. */
    data object AtTheTop : RankMeasureChange
}

/** 끝난 판을 반영한 뒤의 상태와, 사용자에게 알릴 일. */
data class RankMeasureAdjustment(
    val state: RankMeasureState,
    val change: RankMeasureChange = RankMeasureChange.None,
)

/**
 * 끝난 기력 측정 대국 한 판을 기력에 반영한다 — **사용자가 정한 규칙**(2026-10-07)이다.
 *
 * - **급 구간의 승급**: 이길 때마다 오른다. 오르는 폭은 이긴 집 수 차이를 10으로 나눈 몫, 최소 1([rankMeasurePromotionSteps]).
 *   아무리 크게 이겨도 **1단에서 멈춘다**([RankMeasureKyuPromotionCap]).
 * - **단 구간의 승단**: 그 단에서 둔 **최근 5판 중 3판**을 이기면 한 단 오른다. 집 수 차이는 보지 않는다.
 * - **강급·강단**: 어느 구간이든 **2연패**하면 한 단계 내린다(1단에서 내리면 1급). 내린 뒤에는 다시 0부터 센다.
 * - 20급 아래·9단 위로는 가지 않는다. 무승부(승자 모름)는 세지 않는다.
 *
 * ⚠️ 여기에 조건·폭·예외를 더하려면 **사용자에게 먼저 묻는다** — 이 규칙은 한 번 스레드가 임의로 바꿔 넣었다가(판 크기별 폭)
 * 사용자가 폰에서 보고서야 안 적이 있다(2026-10-07: *"승강급은 일관된 룰이 있어야 한다. 임의로 정했다면 꼭 말해 줘야 한다"*).
 * 스레드가 메운 빈 곳은 셋이다: 집 차이가 없는 승리는 1단계 · 단 구간의 최근 전적은 그 단에서 둔 판만 세고 단이 바뀌면 비운다 ·
 * 9단에서 승단 조건을 채우면 전적을 비우고 「이미 가장 높다」고 알린다.
 *
 * @param playedRank 방금 끝난 판의 상대 급수(그 판의 좌석에서 읽는다 — [RankMeasureState.rank]가 아니다). 지금 기력과 다르면
 *   연패·전적도 기력도 건드리지 않는다.
 * @param margin 이긴 집 수 차이 — 급 구간의 승급 폭을 정한다. 졌거나 집 차이가 없으면 쓰지 않는다.
 * @param userWon 사용자가 이겼는가. 무승부이거나 승자를 모르면 `null` — 아무것도 바꾸지 않는다.
 * @param byResignation 상대가 기권해 이겼는가 — 그때 [margin]은 기권한 순간의 형세 점수차다([resignationMarginFor]).
 */
fun adjustRankAfterResult(
    state: RankMeasureState,
    playedRank: KgsRank,
    margin: Double?,
    userWon: Boolean?,
    byResignation: Boolean = false,
): RankMeasureAdjustment {
    if (userWon == null) return RankMeasureAdjustment(state)
    // 기력은 **그 기력으로 둔 판**만 옮긴다. 정상 흐름에서는 둘이 늘 같다(판을 열 때 좌석을 내 기력에 맞추고, 기력은 판을 반영할 때만
    // 바뀐다) — 다르다면 뒤늦게 반영된 옛 판이다. 그것을 지금의 전적에 섞으면 20급을 이긴 판이 12급인 사람의 기력을 옮긴다
    // (2026-10-06 에뮬레이터 — 저장분을 손으로 고친 판에서 드러났다). 이긴 급수는 그래도 잰 급수라 최고 기력에는 든다.
    if (playedRank != state.rank) {
        return RankMeasureAdjustment(if (userWon) state.copy(peakRank = maxOfNullable(state.peakRank, playedRank)) else state)
    }
    val rank = state.rank

    if (!userWon) {
        val losses = state.consecutiveLosses + 1
        if (losses < RankMeasureDemotionLosses) {
            return RankMeasureAdjustment(state.copy(consecutiveLosses = losses, recentDanResults = state.recentDanResultsWith(won = false)))
        }
        val target = rank.weakerBy(1)
        val settled = state.copy(rank = target, consecutiveLosses = 0, recentDanResults = emptyList())
        return RankMeasureAdjustment(settled, if (target < rank) RankMeasureChange.Demoted(from = rank, to = target) else RankMeasureChange.None)
    }

    // 이긴 급수는 잰 급수다 — 최고 기력에 든다.
    val afterWin = state.copy(consecutiveLosses = 0, peakRank = maxOfNullable(state.peakRank, rank))
    if (!rank.isDan) {
        val target = minOf(rank.strongerBy(rankMeasurePromotionSteps(margin)), RankMeasureKyuPromotionCap)
        val promoted = afterWin.copy(rank = target, peakRank = maxOfNullable(afterWin.peakRank, target), recentDanResults = emptyList())
        return RankMeasureAdjustment(promoted, RankMeasureChange.Promoted(from = rank, to = target, margin = margin, byResignation = byResignation))
    }

    val recent = state.recentDanResultsWith(won = true)
    if (recent.count { it } < RankMeasureDanWinsToPromote) return RankMeasureAdjustment(afterWin.copy(recentDanResults = recent))
    val target = rank.strongerBy(1)
    val settled = afterWin.copy(recentDanResults = emptyList())
    return if (target > rank) {
        RankMeasureAdjustment(settled.copy(rank = target, peakRank = maxOfNullable(settled.peakRank, target)), RankMeasureChange.Promoted(from = rank, to = target, margin = null))
    } else {
        RankMeasureAdjustment(settled, RankMeasureChange.AtTheTop)
    }
}

/** 단 구간이면 최근 전적에 이번 판을 더한 것(최대 [RankMeasureDanWindow]판), 급 구간이면 빈 목록. */
private fun RankMeasureState.recentDanResultsWith(won: Boolean): List<Boolean> =
    if (rank.isDan) (recentDanResults + won).takeLast(RankMeasureDanWindow) else emptyList()

private fun maxOfNullable(current: KgsRank?, candidate: KgsRank): KgsRank =
    if (current == null || candidate > current) candidate else current

/**
 * **상대가 기권한 판의 집 수 차이** — 기권한 순간의 형세 점수차(그 판의 마지막 형세 기록)를 [winner] 기준으로 돌려준다.
 *
 * 기권으로 끝난 판에는 계가한 집 수 차이가 없다. 그대로 두면 AI의 기권 제안(백로그 #213)을 받아들인 사람은 1단계만 오르고,
 * 거절하고 끝까지 둔 사람은 집 수 차이만큼 오른다 — 뜻 없는 판을 끝까지 두는 쪽이 이득이 된다. 그래서 기권한 순간의 형세를 집 수
 * 차이로 친다(스레드가 정했다 — 사용자에게 알렸다). 집 단위로 내린 값이고, 형세 기록이 없거나 그 형세가 이긴 쪽에게 한 집도 유리하지
 * 않으면 `null`(1단계).
 */
fun resignationMarginFor(replay: GameReplayData?, winner: StoneColor): Double? {
    val whiteLead = replay?.scoreSnapshots
        ?.lastOrNull { snapshot -> snapshot.source.isNetworkEstimate }
        ?.whiteScoreLead
        ?: return null
    // 형세는 어림값이라 소수는 뜻이 없다 — 집 단위로 내린다(팝업이 말하는 숫자와 승급 폭이 같은 값에서 나오게).
    return floor(if (winner == StoneColor.White) whiteLead else -whiteLead).takeIf { it > 0.0 }
}

/**
 * 끝나서 기록된 판([entry])을 기력에 반영하고 저장한다. 기력 측정 대국이 아니거나 **이미 반영한 판**이면 아무 일도 하지 않고 `null`
 * ([RankMeasureState.lastCountedGameId]) — 여러 번 불러도 한 판은 한 번만 센다.
 *
 * @param replay 그 판의 다시보기 본문 — 상대가 기권한 판의 집 수 차이를 여기서 읽는다([resignationMarginFor]). 없으면 1단계로 친다.
 */
fun runRankMeasureAdjustment(
    entry: GameHistoryEntry,
    store: RankMeasureStorePort,
    replay: GameReplayData? = null,
): RankMeasureAdjustment? {
    val matchup = entry.playerSetup.rankMeasureMatchup() ?: return null
    val current = store.load()
    if (current.lastCountedGameId == entry.id) return null
    val userWon = entry.winner?.let { winner -> winner == matchup.userColor }
    val adjustment = adjustRankAfterResult(
        // 기록이 붙었다는 것은 그 판을 시작했다는 것이다 — 어떤 길로 왔든 여기서 최초 1회의 선택은 끝난다.
        state = current.started(),
        playedRank = matchup.opponentRank,
        margin = if (entry.isResign) resignationMarginFor(replay, matchup.userColor) else entry.margin,
        userWon = userWon,
        byResignation = entry.isResign,
    )
    val counted = adjustment.copy(state = adjustment.state.copy(lastCountedGameId = entry.id))
    store.save(counted.state)
    return counted
}
