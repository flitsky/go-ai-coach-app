package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.ui.play.nextTentativeMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 착수 확인 모드의 가늠 자리(backlog #223) — 둘 수 있는 자리만 잡히고, 둘 수 없는 자리를 누르면 잡아 둔 자리가 남는다. */
class TentativeMoveTest {
    private val occupied = BoardCoordinate(row = 2, column = 2)
    private val empty = BoardCoordinate(row = 4, column = 4)
    private val elsewhere = BoardCoordinate(row = 6, column = 6)
    private val state = GameState.empty(boardSize = BoardSize.Nine).play(Move.Play(StoneColor.Black, occupied))

    @Test
    fun aLegalPointBecomesTheTentativeMoveAndReplacesTheOldOne() {
        assertEquals(empty, nextTentativeMove(current = null, tapped = empty, state = state))
        assertEquals(elsewhere, nextTentativeMove(current = empty, tapped = elsewhere, state = state))
    }

    /** 놓인 돌 위를 누르면 가늠돌이 그 돌에 겹쳐 그려졌다(2026-10-08 에뮬레이터) — `착수`를 눌러도 아무 일도 없다. */
    @Test
    fun anOccupiedPointIsNotTakenAndTheEarlierChoiceStays() {
        assertNull(nextTentativeMove(current = null, tapped = occupied, state = state))
        assertEquals(empty, nextTentativeMove(current = empty, tapped = occupied, state = state))
    }
}
