package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 #210 — 인식이 틀렸을 때 흔히 생기는 "숨 없는 돌 무리"를 찾는다. */
class BoardScanValidationTest {

    private fun at(row: Int, col: Int) = BoardCoordinate(row = row, column = col)

    @Test
    fun aSurroundedStoneHasNoLiberties() {
        // 귀의 흑 한 점을 백 둘이 둘러쌌다 — 실제 대국에서는 이미 따냈어야 할 배치.
        val stones = mapOf(
            at(0, 0) to StoneColor.Black,
            at(0, 1) to StoneColor.White,
            at(1, 0) to StoneColor.White,
        )
        assertEquals(setOf(at(0, 0)), stonesWithoutLiberties(stones, BoardSize.Nine))
    }

    @Test
    fun aWholeGroupWithoutLibertiesIsReportedTogether() {
        // 흑 두 점 무리(0,0)-(0,1)을 백 셋이 막았다.
        val stones = mapOf(
            at(0, 0) to StoneColor.Black,
            at(0, 1) to StoneColor.Black,
            at(0, 2) to StoneColor.White,
            at(1, 0) to StoneColor.White,
            at(1, 1) to StoneColor.White,
        )
        assertEquals(setOf(at(0, 0), at(0, 1)), stonesWithoutLiberties(stones, BoardSize.Nine))
    }

    @Test
    fun aNormalPositionHasNone() {
        val stones = mapOf(
            at(3, 3) to StoneColor.Black,
            at(3, 4) to StoneColor.White,
            at(15, 15) to StoneColor.Black,
        )
        assertTrue(stonesWithoutLiberties(stones, BoardSize.Nineteen).isEmpty())
    }
}
