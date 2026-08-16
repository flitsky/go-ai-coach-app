package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.RemoteEngineCandidate
import com.worksoc.goaicoach.application.engine.selectRemoteEngineCandidate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class RemoteEngineCandidateTest {
    @Test
    fun emptyCandidateListSelectsNothing() {
        assertNull(selectRemoteEngineCandidate(emptyList()))
    }

    @Test
    fun disabledCandidateIsNotSelected() {
        val candidate = RemoteEngineCandidate(endpointUrl = "http://example.test/engine", enabled = false)

        assertNull(selectRemoteEngineCandidate(listOf(candidate)))
    }

    @Test
    fun blankEndpointCandidateIsNotSelected() {
        val candidate = RemoteEngineCandidate(endpointUrl = "  ", enabled = true)

        assertNull(selectRemoteEngineCandidate(listOf(candidate)))
    }

    @Test
    fun firstEnabledUsableCandidateWins() {
        val disabled = RemoteEngineCandidate(endpointUrl = "http://a.test/engine", enabled = false)
        val blank = RemoteEngineCandidate(endpointUrl = "", enabled = true)
        val usable = RemoteEngineCandidate(endpointUrl = "http://b.test/engine", enabled = true)
        val secondUsable = RemoteEngineCandidate(endpointUrl = "http://c.test/engine", enabled = true)

        val selected = selectRemoteEngineCandidate(listOf(disabled, blank, usable, secondUsable))

        assertEquals(usable, selected)
    }
}
