package com.worksoc.goaicoach.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [GameSetup]은 판의 정체성 네 값을 **통째로** 나른다(refactor backlog #22). 네 값이 전부 기본값이 아닌 판으로 잰다 —
 * 기본값(9줄·일본·호선·6.5)만 쓰면 한 값이 빠져도 기본 인자가 채워 초록으로 남는다(#1의 덤이 그렇게 숨었다).
 */
class GameSetupTest {
    private val handicapSetup = GameSetup(BoardSize.Thirteen, Ruleset.Chinese, handicapCount = 3, komi = 0.5)
    private val evenSetup = GameSetup(BoardSize.Nineteen, Ruleset.Chinese, handicapCount = 0, komi = 7.5)

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
}
