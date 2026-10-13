package com.worksoc.goaicoach.shared.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals

/** 백로그 #263 — 인식한 국면을 SGF로 내보내기, 다음 차례의 기본값. */
class ScannedPositionSgfTest {
    private fun state(stones: Map<BoardCoordinate, StoneColor>, next: StoneColor = StoneColor.Black) = GameState(
        boardSize = BoardSize.Nineteen,
        ruleset = Ruleset.Japanese,
        nextPlayer = next,
        stones = stones,
        moves = emptyList(),
        komi = 6.5,
    )

    @Test
    fun writesASetupRecordWithTopLeftAsAaAndColumnFirst() {
        val stones = mapOf(
            // Q16(오른쪽 위 화점) = 3행 15열 → SGF `pd`
            BoardCoordinate(row = 3, column = 15) to StoneColor.Black,
            BoardCoordinate(row = 0, column = 0) to StoneColor.Black,
            // D4(왼쪽 아래 화점) = 15행 3열 → SGF `dp`
            BoardCoordinate(row = 15, column = 3) to StoneColor.White,
        )
        assertEquals(
            "(;GM[1]FF[4]CA[UTF-8]AP[PocketBadukCoach]SZ[19]KM[6.5]RU[Japanese]PL[W]AB[aa][pd]AW[dp])",
            scannedPositionSgf(state(stones, next = StoneColor.White), application = "PocketBadukCoach"),
        )
    }

    @Test
    fun anEmptyBoardHasNoSetupPropertiesAndTheAppNameIsEscaped() {
        assertEquals(
            "(;GM[1]FF[4]CA[UTF-8]AP[a\\:b\\]c]SZ[19]KM[6.5]RU[Japanese]PL[B])",
            scannedPositionSgf(state(emptyMap()), application = "a:b]c"),
        )
    }

    @Test
    fun nextPlayerGuessFollowsStoneCounts() {
        val b = BoardCoordinate(0, 0) to StoneColor.Black
        val b2 = BoardCoordinate(0, 1) to StoneColor.Black
        val w = BoardCoordinate(1, 0) to StoneColor.White
        assertEquals(StoneColor.Black, guessNextPlayer(emptyMap()))
        assertEquals(StoneColor.White, guessNextPlayer(mapOf(b)))
        assertEquals(StoneColor.Black, guessNextPlayer(mapOf(b, w)))
        assertEquals(StoneColor.White, guessNextPlayer(mapOf(b, b2)), "handicap stones — white plays next")
    }
}
