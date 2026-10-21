package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 벤슨 판정(백로그 #277) — 「산다」고 하면 반드시 사는 쪽으로만 틀려야 한다. 풀이기는 이 판정이 참이면 그 무리를 더 읽지 않는다 —
 * 죽을 수 있는 무리를 산다고 하면 잡기 문제의 정답이 사라진다.
 */
class UnconditionalLifeTest {
    @Test
    fun twoSeparateEyesLive() {
        assertTrue(
            alive(
                ". X . X O",
                "X X X X O",
                "O O O O O",
            ),
        )
    }

    @Test
    fun oneEyeDoesNotLive() {
        assertFalse(
            alive(
                ". X X X O",
                "X X X X O",
                "O O O O O",
            ),
        )
    }

    /** 끊기는 자리가 남은 눈(옥집) — 오른쪽 눈의 대각선이 상대 돌이다. 둘째 「눈」을 둘러싼 무리가 따로라 한 무리의 눈은 하나뿐이다. */
    @Test
    fun aFalseEyeIsNotAnEye() {
        assertFalse(
            alive(
                ". X . X O",
                "X X X O O",
                "O O O O ,",
            ),
        )
    }

    /** 셋이 한 줄(직삼)의 큰 눈은 구역이 하나다 — 가운데를 두면 산다는 것과 「지금 무조건 산다」는 다르다. */
    @Test
    fun oneBigEyeIsNotYetAlive() {
        assertFalse(
            alive(
                ". . . X O",
                "X X X X O",
                "O O O O O",
            ),
        )
    }

    /** 눈이 둘이어도 그 가운데 한 눈을 상대 돌과 나눠 가지면(눈 안의 빈 점이 내 공배가 아니다) 무조건은 아니다. */
    @Test
    fun anEyeWithARoomTheGroupDoesNotTouchIsNotVital() {
        assertFalse(
            alive(
                ". X . . . X O",
                "X X X O . X O",
                "O O X X X X O",
                ", O O O O O O",
            ),
        )
    }

    /** 위 그림에서 흑이 두 줄로 넉넉히 두 눈을 가지면 산다. */
    @Test
    fun twoEyesWithRoomLive() {
        assertTrue(
            alive(
                ". X . X . X O",
                "X X X X X X O",
                "O O O O O O O",
            ),
        )
    }

    /** 위 줄부터 9줄 판에 놓고, 맨 위 · 맨 왼쪽의 흑 돌이 속한 무리를 묻는다. 적지 않은 칸과 `,`는 빈 점이다. */
    private fun alive(vararg rows: String): Boolean {
        val stones = buildMap {
            rows.forEachIndexed { row, line ->
                line.split(' ').forEachIndexed { column, cell ->
                    when (cell) {
                        "X" -> put(BoardCoordinate(row, column), StoneColor.Black)
                        "O" -> put(BoardCoordinate(row, column), StoneColor.White)
                    }
                }
            }
        }
        val first = stones.filterValues { it == StoneColor.Black }.keys.minWith(compareBy({ it.row }, { it.column }))
        return isUnconditionallyAlive(stones, size = 9, color = StoneColor.Black, point = first)
    }
}
