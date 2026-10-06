package com.worksoc.goaicoach.application.customgame

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.customRank
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting

/**
 * 6계층 — **커스텀 대국**(백로그 #217): 상대의 KGS 급수를 직접 고르고, 연승하면 난이도가 스스로 오른다.
 *
 * 캐릭터(범위 난이도)와 별개의 진입점이지만 엔진 경로는 같다 — 고른 급수의 사람 모델 프로필이 둔다
 * (`PlayLevelGroup.CustomRank` · `shared.playstyle.humanPlayStyle`).
 *
 * ## 무엇이 어디에 사는가
 * - **지금 두는 판의 급수**는 그 판의 좌석이 갖는다(`SidePlayerSetup.playLevel`) — 이어하기·대국 기록·판정 결과가 그것을 읽는다.
 * - **다음 판의 급수**는 여기([CustomGameState.rank])에 산다. 급수를 고르는 창과 승급 랠리가 이것을 고치고, 새 대국을 시작할 때
 *   좌석에 옮겨 적는다([withCustomRankForNextGame]). 끝난 판의 화면이 방금 오른 급수로 바뀌어 보이지 않게 하려는 분리다.
 *
 * ⚠️ 알려진 틈 하나: 「재 대국」을 누르고 새 판이 준비되는 1~2초 사이에는 끝난 판과 오른 급수의 좌석이 함께 있다. 그 사이에 앱이 죽으면
 * 다시 켰을 때 끝난 판의 상대가 오른 급수로 적혀 보인다(대국 기록과 랠리 상태는 맞다). 좌석 설정을 끝난 뒤에 손으로 바꿔도 같은 일이
 * 생기는 기존 동작이고(끝난 판의 화면은 살아 있는 좌석 설정을 읽는다), 그것을 고치는 일은 이 기능의 범위 밖이다.
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2) — 자동저장이 스냅샷을 처음부터 다시 조립해서, 배선하지 않은 필드는 조용히
 * 초기화된다. 제 저장소([CustomGameStorePort])를 갖는다.
 */
data class CustomGameState(
    /** 다음 커스텀 대국의 상대 급수. */
    val rank: KgsRank = DefaultCustomRank,
    /** 연승하면 스스로 올릴 것인가 — 편의 기능이라 끌 수 있다. */
    val autoAdjustEnabled: Boolean = true,
    /** 지금까지의 연승 수 — 랠리에 들어가기 전 2연승을 세는 데 쓴다. 지면 0으로 돌아간다. */
    val consecutiveWins: Int = 0,
    /**
     * **승급 랠리** 중인가(사용자 2026-10-06). 2연승하면 한 칸 올리며 랠리에 들어가고, 랠리 중에는 **이길 때마다** 한 칸씩 올린다.
     * 한 번 지면 랠리가 끝나고, 그 급수에서 다시 2연승해야 랠리가 된다.
     */
    val isRally: Boolean = false,
    /**
     * 승급 랠리에 **이미 반영한** 마지막 판의 기록 id — 같은 판을 두 번 세지 않는다. 대국이 끝난 화면은 여러 번 다시 그려지고,
     * 앱을 껐다 켜도 끝난 판이 그대로 복원된다.
     */
    val lastCountedGameId: String? = null,
)

/** 처음 커스텀 대국을 열었을 때의 급수 — 첫 캐릭터(초보)의 프로필과 같은 15급. */
val DefaultCustomRank: KgsRank = KgsRank.kyu(15)

/** 랠리에 들어가는 데 필요한 연승 수. */
const val CustomGameRallyWins: Int = 2

/**
 * 한 번 올릴 때의 칸 수 — **판이 작을수록 크게**(실험실 #216: 3급 차 센 쪽 승률이 19줄 77% · 13줄 70% · 9줄 60%).
 * 9줄에서 한 급만 올리면 세기가 거의 안 변한다.
 */
fun customRankStepFor(boardSize: BoardSize): Int =
    when {
        boardSize.value >= 19 -> 1
        boardSize.value >= 13 -> 2
        else -> 3
    }

/**
 * 직접 고를 수 있는 **가장 센 급수**(사용자 2026-10-06: 보유 캐릭터 구간까지 무료, 구독은 전 범위).
 *
 * 무료 사용자는 가진 캐릭터 가운데 가장 센 캐릭터가 맡는 구간의 위 끝까지다 — 초보 12급 · 하수 6급 · 중수 1급 · 고수 5단 · 초고수 9단
 * (U-59의 캐릭터 구간). 약한 쪽 끝은 누구에게나 20급이다. 캐릭터를 모을 이유가 그대로 남는다.
 */
fun strongestSelectableCustomRank(
    collection: BotCollectionState,
    subscriptionActive: Boolean,
): KgsRank {
    if (subscriptionActive) return KgsRank.Strongest
    val strongestOwnedTier = BotCharacterCatalog.fastBeginnerRoster
        .filter { character -> collection.isAvailable(character, subscriptionActive) }
        .maxOfOrNull { character -> character.tierWithinGroup ?: 1 }
        ?: 1
    return strongestRankOfCharacterTier(strongestOwnedTier)
}

/** 캐릭터 구간의 위 끝 — `customRankFallbackTier`와 같은 구간표다(U-59). */
internal fun strongestRankOfCharacterTier(tier: Int): KgsRank =
    when {
        tier >= 5 -> KgsRank.dan(9)
        tier == 4 -> KgsRank.dan(5)
        tier == 3 -> KgsRank.kyu(1)
        tier == 2 -> KgsRank.kyu(6)
        else -> KgsRank.kyu(12)
    }

/**
 * 사람 한 명이 급수를 직접 고른 AI와 두는 판인가 — 그렇다면 사람의 색과 상대의 급수.
 * 승급 랠리는 이런 판만 센다: 사람이 없거나(AI끼리) 둘이면(사람끼리) "내가 이겼다"가 없다.
 */
data class CustomGameMatchup(
    val userColor: StoneColor,
    val opponentRank: KgsRank,
)

fun PlayerSetup.customGameMatchup(): CustomGameMatchup? {
    val blackIsUser = black.controller == SeatController.Human
    val whiteIsUser = white.controller == SeatController.Human
    if (blackIsUser == whiteIsUser) return null
    val (userColor, opponent) = if (blackIsUser) StoneColor.Black to white else StoneColor.White to black
    val rank = opponent.playLevel.customRank() ?: return null
    return CustomGameMatchup(userColor = userColor, opponentRank = rank)
}

/**
 * 새 대국을 시작하기 직전의 좌석 배치 — 커스텀 좌석의 급수를 **지금 고를 수 있는 값**으로 맞춘다.
 *
 * - 사람 대 커스텀 AI이고 자동 조정이 켜져 있으면 상대를 **다음 판의 급수**([CustomGameState.rank])로 — 승급 랠리가 올린 값이다.
 * - 어느 커스텀 좌석이든 고를 수 있는 가장 센 급수([strongestSelectable])를 넘으면 거기까지 내린다 — 구독이 끝났거나, 더 센
 *   캐릭터를 가진 기기에서 저장한 설정을 가져온 경우다.
 *
 * 그 밖의 좌석(사람 · 캐릭터 상대)은 그대로다. 바뀔 것이 없으면 **같은 값**을 돌려준다(부르는 쪽이 `!=`로 가른다).
 */
fun PlayerSetup.withCustomRankForNextGame(
    state: CustomGameState,
    strongestSelectable: KgsRank = KgsRank.Strongest,
): PlayerSetup {
    val nextRankFor: StoneColor? = customGameMatchup()?.takeIf { state.autoAdjustEnabled }?.userColor?.opponent
    fun adjusted(side: SidePlayerSetup, color: StoneColor): SidePlayerSetup {
        val seatRank = side.playLevel.customRank()?.takeIf { side.controller == SeatController.Ai } ?: return side
        val target = minOf(if (color == nextRankFor) state.rank else seatRank, strongestSelectable)
        return if (target == seatRank) side else side.copy(playLevel = target.toPlayLevelSetting())
    }
    val nextBlack = adjusted(black, StoneColor.Black)
    val nextWhite = adjusted(white, StoneColor.White)
    return if (nextBlack == black && nextWhite == white) this else copy(black = nextBlack, white = nextWhite)
}

/** 끝난 판을 반영한 뒤의 상태와, 사용자에게 알릴 일. */
data class CustomGameAdjustment(
    val state: CustomGameState,
    /** 다음 판부터 이 급수로 오른다. 오르지 않았으면 `null`. */
    val promotedTo: KgsRank? = null,
    /** 오를 차례였지만 못 올랐다 — 그 까닭. */
    val blocked: CustomRankPromotionBlock? = null,
)

enum class CustomRankPromotionBlock {
    /** 고를 수 있는 범위의 끝이다 — 더 센 캐릭터를 얻거나 구독하면 열린다. */
    Locked,

    /** 이미 9단이다. */
    AtTheTop,
}

/**
 * 끝난 커스텀 대국 한 판을 승급 랠리에 반영한다.
 *
 * 규칙(사용자 2026-10-06 — *"매우 빠르고 역동적으로"*): **2연승이면 올리고**, 오른 급수에서도 연승이 이어진 것으로 보아
 * **이길 때마다 또 올린다**(랠리). **한 번 지면** 랠리가 끝나고, 그 급수에서 다시 2연승해야 랠리가 된다.
 * ⚠️ 져도 **내리지는 않는다** — 내리는 것은 사용자가 직접 한다(정해지지 않은 것: U-62).
 *
 * @param playedRank 방금 끝난 판의 상대 급수(그 판의 좌석에서 읽는다 — [CustomGameState.rank]가 아니다).
 * @param userWon 사용자가 이겼는가. 무승부이거나 승자를 모르면 `null` — 아무것도 바꾸지 않는다.
 */
fun adjustCustomGameAfterResult(
    state: CustomGameState,
    playedRank: KgsRank,
    boardSize: BoardSize,
    userWon: Boolean?,
    strongestSelectableRank: KgsRank,
): CustomGameAdjustment {
    if (!state.autoAdjustEnabled || userWon == null) return CustomGameAdjustment(state)
    if (!userWon) return CustomGameAdjustment(state.copy(consecutiveWins = 0, isRally = false))

    val wins = state.consecutiveWins + 1
    val counted = state.copy(consecutiveWins = wins)
    if (!state.isRally && wins < CustomGameRallyWins) return CustomGameAdjustment(counted)

    val rally = counted.copy(isRally = true)
    val target = minOf(playedRank.strongerBy(customRankStepFor(boardSize)), strongestSelectableRank)
    return if (target > playedRank) {
        CustomGameAdjustment(state = rally.copy(rank = target), promotedTo = target)
    } else {
        val block = if (strongestSelectableRank < KgsRank.Strongest) CustomRankPromotionBlock.Locked else CustomRankPromotionBlock.AtTheTop
        CustomGameAdjustment(state = rally, blocked = block)
    }
}

/**
 * 끝나서 기록된 판([entry])을 승급 랠리에 반영하고 저장한다. 커스텀 대국이 아니거나 **이미 반영한 판**이면 아무 일도 하지 않고 `null`
 * ([CustomGameState.lastCountedGameId]) — 여러 번 불러도 한 판은 한 번만 센다.
 */
fun runCustomGameAdjustment(
    entry: GameHistoryEntry,
    store: CustomGameStorePort,
    collection: BotCollectionState,
    subscriptionActive: Boolean,
): CustomGameAdjustment? {
    val matchup = entry.playerSetup.customGameMatchup() ?: return null
    val current = store.load()
    if (current.lastCountedGameId == entry.id) return null
    val userWon = entry.winner?.let { winner -> winner == matchup.userColor }
    val adjustment = adjustCustomGameAfterResult(
        state = current,
        playedRank = matchup.opponentRank,
        boardSize = BoardSize(entry.boardSize),
        userWon = userWon,
        strongestSelectableRank = strongestSelectableCustomRank(collection, subscriptionActive),
    )
    val counted = adjustment.copy(state = adjustment.state.copy(lastCountedGameId = entry.id))
    store.save(counted.state)
    return counted
}
