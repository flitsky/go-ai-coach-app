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

/** 착수 확인 모드의 가착수 자리(backlog #223 → 2026-10-09) — 둘 수 있는 자리만 잡히고, 같은 자리를 다시 누르거나 둘 수 없는 자리를 누르면 거둔다. */
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

    /** 놓인 돌 위를 누르면 가늠돌이 그 돌에 겹쳐 그려졌다(2026-10-08 에뮬레이터) — 잡히지 않는다. 잡아 둔 가착수가 있었으면 **거둔다**(2026-10-09 사용자). */
    @Test
    fun anOccupiedPointIsNotTakenAndItPutsTheEarlierChoiceBack() {
        assertNull(nextTentativeMove(current = null, tapped = occupied, state = state))
        assertNull(nextTentativeMove(current = empty, tapped = occupied, state = state))
    }

    /** 잡아 둔 그 자리를 한 번 더 누르면 거둔다(2026-10-09 사용자) — 가착수를 물리는 길이다. 그 뒤에 다시 누르면 다시 잡힌다. */
    @Test
    fun tappingTheTentativePointAgainPutsItBack() {
        assertNull(nextTentativeMove(current = empty, tapped = empty, state = state))
        assertEquals(empty, nextTentativeMove(current = null, tapped = empty, state = state))
    }
}
