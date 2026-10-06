package com.worksoc.goaicoach.application.rankmeasure

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
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

/**
 * 6계층 — **기력 측정 대국**(백로그 #219): 내 기력을 알아보고, 비슷한 상대와 되풀이해 두며 배운다.
 *
 * 사람 한 명이 **자기 기력과 같은 급수의 AI**와 둔다. 이기면 오르고 지면 내려서, 이기고 지기를 되풀이하는 급수가 곧 그 사람의
 * 실력 구간이다(사용자 2026-10-06). 상대는 그 급수의 사람 모델 프로필이 둔다 — 캐릭터(#215)와 같은 엔진 경로다
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
    /** 지금까지의 연승 수 — 랠리에 들어가기 전 2연승을 세는 데 쓴다. 지면 0으로 돌아간다. */
    val consecutiveWins: Int = 0,
    /**
     * **승급 랠리** 중인가. 2연승하면 한 칸 올리며 랠리에 들어가고, 랠리 중에는 **이길 때마다** 또 올린다.
     * 한 번 지면 랠리가 끝나고, 그 급수에서 다시 2연승해야 랠리가 된다.
     */
    val isRally: Boolean = false,
    /** 지금까지의 연패 수 — 2연패마다 한 칸 내리고 다시 0부터 센다. 이기면 0으로 돌아간다. */
    val consecutiveLosses: Int = 0,
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

/** 랠리에 들어가는 데 필요한 연승 수. */
const val RankMeasureRallyWins: Int = 2

/** 한 칸 내리는 데 필요한 연패 수. */
const val RankMeasureDemotionLosses: Int = 2

/**
 * 최초 1회의 기력 선택. 이미 측정을 시작했으면 아무것도 바꾸지 않는다 — 그 뒤로는 이기고 지는 것만이 기력을 옮긴다.
 * 범위(20급~1급)를 넘은 값은 끝으로 당긴다.
 */
fun RankMeasureState.chooseStartingRank(chosen: KgsRank): RankMeasureState =
    if (canChooseStartingRank) copy(rank = minOf(chosen, StrongestStartingRank)) else this

/** 기력 측정 대국을 시작했다 — 이제 기력을 스스로 고를 수 없다. */
fun RankMeasureState.started(): RankMeasureState = if (hasStarted) this else copy(hasStarted = true)

/**
 * 한 번에 움직이는 칸 수 — **판이 작을수록 크게**(실험실 #216: 3급 차 센 쪽 승률이 19줄 77% · 13줄 70% · 9줄 60%).
 * 9줄에서 한 급만 움직이면 세기가 거의 안 변한다.
 */
fun rankMeasureStepFor(boardSize: BoardSize): Int =
    when {
        boardSize.value >= 19 -> 1
        boardSize.value >= 13 -> 2
        else -> 3
    }

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

/** 끝난 판이 기력을 어떻게 옮겼는가 — 사용자에게 알릴 일이다. */
sealed interface RankMeasureChange {
    /** 그대로다(한 판 이겼거나 한 판 졌다 — 아직 2연승·2연패가 아니다). */
    data object None : RankMeasureChange

    data class Promoted(val to: KgsRank) : RankMeasureChange

    data class Demoted(val to: KgsRank) : RankMeasureChange

    /** 오를 차례였지만 이미 9단이다. */
    data object AtTheTop : RankMeasureChange
}

/** 끝난 판을 반영한 뒤의 상태와, 사용자에게 알릴 일. */
data class RankMeasureAdjustment(
    val state: RankMeasureState,
    val change: RankMeasureChange = RankMeasureChange.None,
)

/**
 * 끝난 기력 측정 대국 한 판을 기력에 반영한다(사용자 2026-10-06).
 *
 * - **올림은 랠리다**: 2연승이면 올리고, 오른 급수에서도 연승이 이어진 것으로 보아 **이길 때마다 또 올린다.**
 *   한 번 지면 랠리가 끝나고, 그 급수에서 다시 2연승해야 랠리가 된다.
 * - **내림은 2연패마다 한 칸이다**: 2연패하면 내리고 연패 수를 다시 0부터 센다. 내림에는 랠리가 없다 — 오르기는 쉽고 내려가기는 느리다.
 * - 한 번에 움직이는 폭은 판 크기별이다([rankMeasureStepFor]). 20급 아래·9단 위로는 가지 않는다.
 * - 이기고 지기를 번갈아 하면 어느 쪽도 2연속이 안 되어 **그 급수에 머문다** — 그 급수가 실력 구간이다.
 *
 * @param playedRank 방금 끝난 판의 상대 급수(그 판의 좌석에서 읽는다 — [RankMeasureState.rank]가 아니다). 지금 기력과 다르면
 *   연승·연패도 기력도 건드리지 않는다.
 * @param userWon 사용자가 이겼는가. 무승부이거나 승자를 모르면 `null` — 아무것도 바꾸지 않는다.
 */
fun adjustRankAfterResult(
    state: RankMeasureState,
    playedRank: KgsRank,
    boardSize: BoardSize,
    userWon: Boolean?,
): RankMeasureAdjustment {
    if (userWon == null) return RankMeasureAdjustment(state)
    // 기력은 **그 기력으로 둔 판**만 옮긴다. 정상 흐름에서는 둘이 늘 같다(판을 열 때 좌석을 내 기력에 맞추고, 기력은 판을 반영할 때만
    // 바뀐다) — 다르다면 뒤늦게 반영된 옛 판이다. 그것을 지금의 연승·연패에 섞으면 20급을 이긴 판이 12급인 사람을 17급으로 "올린다"
    // (2026-10-06 에뮬레이터 — 저장분을 손으로 고친 판에서 드러났다). 이긴 급수는 그래도 잰 급수라 최고 기력에는 든다.
    if (playedRank != state.rank) {
        return RankMeasureAdjustment(if (userWon) state.copy(peakRank = maxOfNullable(state.peakRank, playedRank)) else state)
    }
    val step = rankMeasureStepFor(boardSize)

    if (!userWon) {
        val losses = state.consecutiveLosses + 1
        val afterLoss = state.copy(consecutiveWins = 0, isRally = false, consecutiveLosses = losses)
        if (losses < RankMeasureDemotionLosses) return RankMeasureAdjustment(afterLoss)
        val target = playedRank.weakerBy(step)
        val settled = afterLoss.copy(consecutiveLosses = 0, rank = target)
        return RankMeasureAdjustment(settled, if (target < playedRank) RankMeasureChange.Demoted(target) else RankMeasureChange.None)
    }

    val wins = state.consecutiveWins + 1
    // 이긴 급수는 잰 급수다 — 최고 기력에 든다.
    val afterWin = state.copy(consecutiveWins = wins, consecutiveLosses = 0, peakRank = maxOfNullable(state.peakRank, playedRank))
    if (!state.isRally && wins < RankMeasureRallyWins) return RankMeasureAdjustment(afterWin)

    val rally = afterWin.copy(isRally = true)
    val target = playedRank.strongerBy(step)
    return if (target > playedRank) {
        RankMeasureAdjustment(rally.copy(rank = target, peakRank = maxOfNullable(rally.peakRank, target)), RankMeasureChange.Promoted(target))
    } else {
        RankMeasureAdjustment(rally, RankMeasureChange.AtTheTop)
    }
}

private fun maxOfNullable(current: KgsRank?, candidate: KgsRank): KgsRank =
    if (current == null || candidate > current) candidate else current

/**
 * 끝나서 기록된 판([entry])을 기력에 반영하고 저장한다. 기력 측정 대국이 아니거나 **이미 반영한 판**이면 아무 일도 하지 않고 `null`
 * ([RankMeasureState.lastCountedGameId]) — 여러 번 불러도 한 판은 한 번만 센다.
 */
fun runRankMeasureAdjustment(
    entry: GameHistoryEntry,
    store: RankMeasureStorePort,
): RankMeasureAdjustment? {
    val matchup = entry.playerSetup.rankMeasureMatchup() ?: return null
    val current = store.load()
    if (current.lastCountedGameId == entry.id) return null
    val userWon = entry.winner?.let { winner -> winner == matchup.userColor }
    val adjustment = adjustRankAfterResult(
        // 기록이 붙었다는 것은 그 판을 시작했다는 것이다 — 어떤 길로 왔든 여기서 최초 1회의 선택은 끝난다.
        state = current.started(),
        playedRank = matchup.opponentRank,
        boardSize = BoardSize(entry.boardSize),
        userWon = userWon,
    )
    val counted = adjustment.copy(state = adjustment.state.copy(lastCountedGameId = entry.id))
    store.save(counted.state)
    return counted
}
