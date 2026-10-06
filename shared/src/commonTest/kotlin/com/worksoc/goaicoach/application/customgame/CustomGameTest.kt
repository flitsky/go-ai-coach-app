package com.worksoc.goaicoach.application.customgame

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 커스텀 대국(백로그 #217) — 상대의 KGS 급수를 직접 고르고, 연승하면 난이도가 스스로 오른다.
 * 여기서 고정하는 것은 사용자가 정한 셋이다(2026-10-06): 승급 랠리 규칙 · 판 크기별 간격 · 고를 수 있는 범위.
 */
class CustomGameTest {
    private fun afterResults(
        vararg userWon: Boolean,
        start: CustomGameState = CustomGameState(rank = KgsRank.kyu(10)),
        boardSize: BoardSize = BoardSize.Nineteen,
        ceiling: KgsRank = KgsRank.Strongest,
    ): List<CustomGameAdjustment> {
        var state = start
        return userWon.map { won ->
            // 방금 둔 판의 급수는 직전까지의 「다음 판 급수」다 — 새 대국을 시작할 때 좌석에 옮겨 적기 때문이다.
            adjustCustomGameAfterResult(state, playedRank = state.rank, boardSize = boardSize, userWon = won, strongestSelectableRank = ceiling)
                .also { state = it.state }
        }
    }

    /** 2연승이면 한 칸 올리고 랠리에 들어간다. 한 판 이긴 것으로는 오르지 않는다. */
    @Test
    fun twoWinsInARowPromoteAndStartTheRally() {
        val (first, second) = afterResults(true, true)

        assertNull(first.promotedTo)
        assertEquals(KgsRank.kyu(9), second.promotedTo)
        assertEquals(true, second.state.isRally)
        assertEquals(KgsRank.kyu(9), second.state.rank)
    }

    /** 랠리 중에는 **이길 때마다** 또 올린다 — 오른 급수에서도 연승이 이어진 것으로 본다. */
    @Test
    fun duringTheRallyEveryWinPromotesAgain() {
        val results = afterResults(true, true, true, true)

        assertEquals(listOf(null, KgsRank.kyu(9), KgsRank.kyu(8), KgsRank.kyu(7)), results.map { it.promotedTo })
    }

    /** 한 번 지면 랠리가 끝난다 — 그 급수에서 다시 2연승해야 오른다. 져도 내리지는 않는다. */
    @Test
    fun aLossEndsTheRallyAndTwoMoreWinsAreNeededAgain() {
        val results = afterResults(true, true, true, false, true, true)

        assertEquals(listOf(null, KgsRank.kyu(9), KgsRank.kyu(8), null, null, KgsRank.kyu(7)), results.map { it.promotedTo })
        val afterTheLoss = results[3].state
        assertEquals(false, afterTheLoss.isRally)
        assertEquals(0, afterTheLoss.consecutiveWins)
        assertEquals(KgsRank.kyu(8), afterTheLoss.rank, "a loss must not demote")
    }

    /** 판이 작을수록 한 번에 크게 올린다 — 9줄에서 한 급만 올리면 세기가 거의 안 변한다(실험실 #216). */
    @Test
    fun theStepIsLargerOnSmallerBoards() {
        assertEquals(listOf(1, 2, 3), listOf(BoardSize.Nineteen, BoardSize.Thirteen, BoardSize.Nine).map(::customRankStepFor))
        assertEquals(KgsRank.kyu(7), afterResults(true, true, boardSize = BoardSize.Nine).last().promotedTo)
        assertEquals(KgsRank.kyu(8), afterResults(true, true, boardSize = BoardSize.Thirteen).last().promotedTo)
    }

    /** 급에서 단으로 넘어갈 때 0급이 생기지 않는다 — 1급 다음은 1단이다. */
    @Test
    fun promotionCrossesFromKyuToDan() {
        val results = afterResults(true, true, start = CustomGameState(rank = KgsRank.kyu(1)))

        assertEquals(KgsRank.dan(1), results.last().promotedTo)
    }

    /**
     * 고를 수 있는 범위의 끝에서는 오르지 않고 까닭을 알린다 — 무료 사용자는 「더 센 캐릭터를 얻거나 구독하면 열린다」,
     * 9단은 「더 올릴 곳이 없다」. 범위 안에서 한 칸이 모자라면 끝까지만 올린다.
     */
    @Test
    fun promotionStopsAtTheSelectableCeilingAndSaysWhy() {
        val locked = afterResults(true, true, start = CustomGameState(rank = KgsRank.kyu(12)), ceiling = KgsRank.kyu(12)).last()
        assertNull(locked.promotedTo)
        assertEquals(CustomRankPromotionBlock.Locked, locked.blocked)

        val partial = afterResults(true, true, start = CustomGameState(rank = KgsRank.kyu(13)), boardSize = BoardSize.Nine, ceiling = KgsRank.kyu(12)).last()
        assertEquals(KgsRank.kyu(12), partial.promotedTo)

        val top = afterResults(true, true, start = CustomGameState(rank = KgsRank.dan(9))).last()
        assertEquals(CustomRankPromotionBlock.AtTheTop, top.blocked)
    }

    /** 자동 조정을 끄면 아무것도 세지 않는다. 무승부(승자 모름)도 건드리지 않는다. */
    @Test
    fun nothingChangesWhenAutoAdjustIsOffOrThereIsNoWinner() {
        val off = CustomGameState(rank = KgsRank.kyu(10), autoAdjustEnabled = false)
        assertEquals(off, afterResults(true, true, true, start = off).last().state)

        val counting = CustomGameState(rank = KgsRank.kyu(10), consecutiveWins = 1)
        val draw = adjustCustomGameAfterResult(counting, KgsRank.kyu(10), BoardSize.Nineteen, userWon = null, strongestSelectableRank = KgsRank.Strongest)
        assertEquals(counting, draw.state)
    }

    /** 무료 사용자는 가진 캐릭터 가운데 가장 센 캐릭터의 구간 위 끝까지 고른다. 구독은 9단까지 전부다. */
    @Test
    fun theSelectableCeilingFollowsTheStrongestOwnedCharacter() {
        val roster = BotCharacterCatalog.fastBeginnerRoster
        fun owning(vararg tiers: Int) = BotCollectionState(claimedBots = tiers.map { tier -> roster[tier - 1].id }.toSet())

        assertEquals(KgsRank.kyu(12), strongestSelectableCustomRank(BotCollectionState(), subscriptionActive = false))
        assertEquals(KgsRank.kyu(6), strongestSelectableCustomRank(owning(2), subscriptionActive = false))
        assertEquals(KgsRank.kyu(1), strongestSelectableCustomRank(owning(3), subscriptionActive = false), "a gap in the collection does not matter")
        assertEquals(KgsRank.dan(5), strongestSelectableCustomRank(owning(2, 4), subscriptionActive = false))
        assertEquals(KgsRank.dan(9), strongestSelectableCustomRank(owning(5), subscriptionActive = false))
        assertEquals(KgsRank.Strongest, strongestSelectableCustomRank(BotCollectionState(), subscriptionActive = true))
    }

    /** 승급 랠리는 **사람 한 명이 커스텀 AI와 두는 판**만 센다 — AI끼리·사람끼리·캐릭터 상대는 아니다. */
    @Test
    fun onlyAUserAgainstACustomRankOpponentIsACustomGame() {
        assertEquals(CustomGameMatchup(StoneColor.Black, KgsRank.kyu(5)), userVsCustom(KgsRank.kyu(5)).customGameMatchup())
        assertEquals(
            CustomGameMatchup(StoneColor.White, KgsRank.dan(2)),
            PlayerSetup(black = custom(KgsRank.dan(2)), white = SidePlayerSetup(SeatController.Human)).customGameMatchup(),
        )
        assertNull(PlayerSetup(black = custom(KgsRank.kyu(5)), white = custom(KgsRank.kyu(3))).customGameMatchup())
        assertNull(PlayerSetup(black = SidePlayerSetup(SeatController.Human), white = SidePlayerSetup(SeatController.Human)).customGameMatchup())
        assertNull(
            PlayerSetup(
                black = SidePlayerSetup(SeatController.Human),
                white = SidePlayerSetup(SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3)),
            ).customGameMatchup(),
        )
    }

    /** 새 대국을 시작할 때 상대를 다음 판의 급수로 맞춘다 — 자동 조정이 꺼져 있거나 커스텀 대국이 아니면 좌석 그대로다. */
    @Test
    fun theNextGameStartsAtTheAdjustedRank() {
        val setup = userVsCustom(KgsRank.kyu(10))

        assertEquals(userVsCustom(KgsRank.kyu(9)), setup.withCustomRankForNextGame(CustomGameState(rank = KgsRank.kyu(9))))
        assertEquals(setup, setup.withCustomRankForNextGame(CustomGameState(rank = KgsRank.kyu(9), autoAdjustEnabled = false)))
        val aiVsAi = PlayerSetup(black = custom(KgsRank.kyu(5)), white = custom(KgsRank.kyu(3)))
        assertEquals(aiVsAi, aiVsAi.withCustomRankForNextGame(CustomGameState(rank = KgsRank.kyu(9))))
    }

    /**
     * 고를 수 있는 범위를 넘은 급수는 범위 끝으로 내린다 — 구독이 끝났는데 저장된 좌석이 3단이면, 그대로 시작하면 해금이 통째로 우회된다.
     * 다음 판의 급수도 같은 끝을 넘지 못하고, AI끼리 두는 판의 좌석에도 똑같이 건다.
     */
    @Test
    fun aRankBeyondTheSelectableCeilingIsBroughtDown() {
        val ceiling = KgsRank.kyu(6)

        assertEquals(userVsCustom(ceiling), userVsCustom(KgsRank.dan(3)).withCustomRankForNextGame(CustomGameState(autoAdjustEnabled = false), ceiling))
        assertEquals(userVsCustom(ceiling), userVsCustom(KgsRank.kyu(10)).withCustomRankForNextGame(CustomGameState(rank = KgsRank.kyu(2)), ceiling))
        assertEquals(
            PlayerSetup(black = custom(ceiling), white = custom(KgsRank.kyu(9))),
            PlayerSetup(black = custom(KgsRank.dan(1)), white = custom(KgsRank.kyu(9))).withCustomRankForNextGame(CustomGameState(), ceiling),
        )
        val untouched = userVsCustom(KgsRank.kyu(8))
        assertEquals(untouched, untouched.withCustomRankForNextGame(CustomGameState(rank = KgsRank.kyu(8)), ceiling))
    }

    /** 기록된 판 하나를 반영하고 저장한다. 커스텀 대국이 아닌 판은 저장소를 건드리지 않는다. */
    @Test
    fun aRecordedCustomGameIsCountedOnceAndSaved() {
        val store = InMemoryCustomGameStore(CustomGameState(rank = KgsRank.kyu(10), consecutiveWins = 1))
        val wonByUser = entry(userVsCustom(KgsRank.kyu(10)), winner = StoneColor.Black)

        val adjustment = runCustomGameAdjustment(wonByUser, store, BotCollectionState(), subscriptionActive = true)

        assertEquals(KgsRank.kyu(9), adjustment?.promotedTo)
        assertEquals(KgsRank.kyu(9), store.load().rank)

        // 끝난 판의 화면은 여러 번 다시 그려지고 앱을 껐다 켜도 복원된다 — 같은 판을 다시 넘겨도 또 세지 않는다.
        assertNull(runCustomGameAdjustment(wonByUser, store, BotCollectionState(), subscriptionActive = true))
        assertEquals(KgsRank.kyu(9), store.load().rank)

        val characterGame = entry(PlayerSetup(), winner = StoneColor.Black)
        assertNull(runCustomGameAdjustment(characterGame, store, BotCollectionState(), subscriptionActive = true))
        assertEquals(1, store.saves, "a game against a character must not touch the custom game state")
    }

    /** AI가 기권해도 사용자의 승리다 — 기록의 승자를 그대로 읽는다. 사용자가 지면 랠리가 끝난다. */
    @Test
    fun theWinnerComesFromTheRecordWhateverTheUsersColor() {
        val store = InMemoryCustomGameStore(CustomGameState(rank = KgsRank.kyu(10), consecutiveWins = 3, isRally = true))
        val userIsWhite = PlayerSetup(black = custom(KgsRank.kyu(10)), white = SidePlayerSetup(SeatController.Human))

        runCustomGameAdjustment(entry(userIsWhite, winner = StoneColor.Black), store, BotCollectionState(), subscriptionActive = true)

        assertEquals(CustomGameState(rank = KgsRank.kyu(10), consecutiveWins = 0, isRally = false, lastCountedGameId = "game"), store.load())
    }

    private fun custom(rank: KgsRank) = SidePlayerSetup(controller = SeatController.Ai, playLevel = rank.toPlayLevelSetting())

    private fun userVsCustom(rank: KgsRank) = PlayerSetup(black = SidePlayerSetup(SeatController.Human), white = custom(rank))

    private fun entry(playerSetup: PlayerSetup, winner: StoneColor?) = GameHistoryEntry(
        id = "game",
        playedAtMillis = 1L,
        boardSize = 19,
        ruleset = Ruleset.Japanese,
        komi = 6.5,
        handicapCount = 0,
        playerSetup = playerSetup,
        moveCount = 120,
        humanColor = null,
        winner = winner,
    )

    private class InMemoryCustomGameStore(private var state: CustomGameState) : CustomGameStorePort {
        var saves = 0

        override fun load(): CustomGameState = state

        override fun save(state: CustomGameState) {
            saves += 1
            this.state = state
        }
    }
}
