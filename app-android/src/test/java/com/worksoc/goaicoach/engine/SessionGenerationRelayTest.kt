package com.worksoc.goaicoach.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SessionGenerationRelay]의 계약(refactor backlog #18).
 *
 * ⚠️ 이 중계기의 일은 **읽을 때마다 이은 곳에 다시 묻는 것**이 전부다. 값을 한 번 받아 담아 두면
 * 무르기로 세대가 넘어가도 로그가 옛 세대로 찍힌다 — #18이 고친 `g0` 고정과 같은 모양이다.
 */
class SessionGenerationRelayTest {

    @Test
    fun answersZeroBeforeAnythingIsBound() {
        // 예전에 3계층이 박아 넣던 값과 같다 — 잇기 전의 로그는 #18 이전과 똑같이 찍힌다.
        assertEquals(0L, SessionGenerationRelay().current())
    }

    @Test
    fun asksTheBoundReaderOnEveryReadSoAGenerationBumpShowsUp() {
        val relay = SessionGenerationRelay()
        var generation = 2L
        relay.bind { generation }

        assertEquals(2L, relay.current())

        generation = 3L // 무르기 한 번.

        assertEquals(3L, relay.current())
    }

    @Test
    fun theLatestBindingWinsWhenTheSessionHolderIsRebuilt() {
        val relay = SessionGenerationRelay()
        relay.bind { 7L }
        relay.bind { 1L } // 화면이 다시 만들어져 홀더가 새로 생겼다.

        assertEquals(1L, relay.current())
    }
}
