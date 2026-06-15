package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.engine.EngineBenchmarkMetric
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineBenchmarkStoreTest {
    @Test
    fun codecRoundTripsBenchmarkProfile() {
        val profile = EngineBenchmarkProfile(
            createdAtMillis = 1234L,
            samplesPerVisit = 5,
            timeCapMs = 5_000L,
            metrics = listOf(
                EngineBenchmarkMetric(visits = 16, samples = 5, minMs = 11.1, avgMs = 22.2, maxMs = 33.3),
                EngineBenchmarkMetric(visits = 32, samples = 5, minMs = 44.4, avgMs = 55.5, maxMs = 66.6),
            ),
        )

        val restored = EngineBenchmarkCodec.decode(EngineBenchmarkCodec.encode(profile))

        assertEquals(profile, restored)
    }

    @Test
    fun codecRejectsUnknownSchemaAndBrokenJson() {
        assertNull(EngineBenchmarkCodec.decode("""{"schema":99}"""))
        assertNull(EngineBenchmarkCodec.decode("{broken"))
    }
}
