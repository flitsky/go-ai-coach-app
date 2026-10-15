package com.worksoc.goaicoach.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [GameSetup]은 판의 정체성 네 값을 **통째로** 나른다(refactor backlog #22). 네 값이 전부 기본값이 아닌 판으로 잰다 —
 * 기본값(9줄·일본·호선·6.5)만 쓰면 한 값이 빠져도 기본 인자가 채워 초록으로 남는다(#1의 덤이 그렇게 숨었다).
 */
class GameSetupTest {
    private val handicapSetup = GameSetup.standard(BoardSize.Thirteen, Ruleset.Chinese, handicapCount = 3, komi = 0.5)
    private val evenSetup = GameSetup.standard(BoardSize.Nineteen, Ruleset.Chinese, handicapCount = 0, komi = 7.5)

    /** 바둑판 사진에서 「이 국면부터 대국」한 판(백로그 #266) — 처음부터 놓인 돌 셋, 백 차례. */
    private val photoSetup = GameSetup(
        BoardSize.Nine,
        Ruleset.Japanese,
        handicapCount = 0,
        komi = 6.5,
        setupStones = mapOf(
            BoardCoordinate.fromLabel("E5", BoardSize.Nine) to StoneColor.Black,
            BoardCoordinate.fromLabel("D4", BoardSize.Nine) to StoneColor.White,
            BoardCoordinate.fromLabel("F6", BoardSize.Nine) to StoneColor.Black,
        ),
        firstPlayer = StoneColor.White,
    )

    @Test
    fun theDerivedSetupCarriesAllFourValues() {
        val state = GameState.withHandicap(BoardSize.Thirteen, Ruleset.Chinese, handicapCount = 3, komi = 0.5)

        assertEquals(handicapSetup, state.setup)
    }

    @Test
    fun aGameStartedFromASetupHasThatSetup() {
        assertEquals(handicapSetup, GameState.withHandicap(handicapSetup).setup)
        assertEquals(evenSetup, GameState.withHandicap(evenSetup).setup)
        assertEquals(StoneColor.White, GameState.withHandicap(handicapSetup).nextPlayer, "접바둑은 백부터")
        assertEquals(StoneColor.Black, GameState.withHandicap(evenSetup).nextPlayer, "호선은 흑부터")
    }

    /** 되살린 판은 저장할 때의 판과 **같다** — 네 값만이 아니라 돌·차례·수순까지. */
    @Test
    fun replayingFromASetupRebuildsTheSameGame() {
        val played = GameState.withHandicap(handicapSetup)
            .play(Move.Play(StoneColor.White, BoardCoordinate(row = 2, column = 3)))
            .play(Move.Pass(StoneColor.Black))

        assertEquals(played, GameStateReplayer.replay(played.setup, played.moves))
    }

    /** 무르기는 판의 정체성을 그대로 둔다 — 예전에는 네 값을 따로 넘기는 자리였다. */
    @Test
    fun undoingMovesKeepsTheSetup() {
        val played = GameState.withHandicap(evenSetup)
            .play(Move.Play(StoneColor.Black, BoardCoordinate(row = 3, column = 3)))
            .play(Move.Play(StoneColor.White, BoardCoordinate(row = 15, column = 15)))

        val undone = played.replayWithoutLastMoves(1)

        assertEquals(evenSetup, undone.setup)
        assertEquals(1, undone.moves.size)
    }

    /** 시작 배치가 있는 판의 첫 국면은 그 돌과 그 차례다 — 다시 세우는 모든 자리가 여기서 시작한다(백로그 #266). */
    @Test
    fun aPhotoSetupStartsFromItsStonesAndTurn() {
        val start = GameState.withHandicap(photoSetup)

        assertEquals(photoSetup.setupStones, start.stones)
        assertEquals(StoneColor.White, start.nextPlayer)
        assertEquals(photoSetup, start.setup)
    }

    /**
     * 사진 국면 위에 둔 판을 수순으로 다시 세워도(이어하기 · 엔진 동기화 · 기록) 사진의 돌이 그대로다 — 예전에는 빈 판에서
     * 수순만 두어 첫 수 뒤 사진의 돌이 사라졌다.
     */
    @Test
    fun replayingAPhotoGameKeepsThePhotoStones() {
        val played = GameState.withHandicap(photoSetup)
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C7", BoardSize.Nine)))
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("G3", BoardSize.Nine)))

        val replayed = GameStateReplayer.replay(played.setup, played.moves)

        assertEquals(played, replayed)
        assertEquals(5, replayed.stones.size)
        assertEquals(photoSetup, replayed.setup)
    }

    /** 무르기도 사진의 돌을 두고 둔 수만 거둔다 — 다 물러도 사진의 국면으로 돌아온다. */
    @Test
    fun undoingAPhotoGameStopsAtThePhoto() {
        val played = GameState.withHandicap(photoSetup)
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C7", BoardSize.Nine)))
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("G3", BoardSize.Nine)))

        val undoneAll = played.replayWithoutLastMoves(2)

        assertEquals(photoSetup.setupStones, undoneAll.stones)
        assertEquals(StoneColor.White, undoneAll.nextPlayer)
    }
}
