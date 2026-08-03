package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.application.engine.EngineSessionBackend
import com.worksoc.goaicoach.application.engine.RemoteEngineCandidate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RemoteEngineSessionBootstrapTest {
    @Test
    fun noUsableCandidateReturnsNull() {
        val client = createRemoteEngineSessionClient(candidates = emptyList())

        assertNull(client)
    }

    @Test
    fun usableCandidateBuildsRemoteBackedSessionClientWithoutTouchingNetwork() {
        val client = createRemoteEngineSessionClient(
            candidates = listOf(
                RemoteEngineCandidate(endpointUrl = "http://example.test/engine", enabled = true),
            ),
        )

        assertNotNull(client)
        assertEquals(EngineSessionBackend.RemoteServer, client!!.capabilities.backend)
        // 벤치마크는 "이 기기 하드웨어"를 재는 것이라 원격 엔진에는 의미가 없다 — 게이팅이 꺼져
        // 있어야 EngineDeviceBenchmarkApplication이 runStartupBenchmark를 호출하지 않는다.
        assertFalse(client.capabilities.supportsDeviceBenchmark)
    }
}
