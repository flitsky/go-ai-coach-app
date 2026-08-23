package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.attendance.AttendanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceCodecTest {
    @Test
    fun roundTripRestoresAllFields() {
        val state = AttendanceState(
            attendanceCount = 9,
            lastCheckInUtcDay = 20323L,
            claimedTiers = setOf(1, 2, 3),
        )

        val decoded = AttendanceCodec.decode(AttendanceCodec.encode(state))

        assertEquals(state, decoded)
    }

    @Test
    fun roundTripPreservesNullLastCheckInDay() {
        val state = AttendanceState()

        val decoded = AttendanceCodec.decode(AttendanceCodec.encode(state))

        assertEquals(state, decoded)
        assertNull(decoded?.lastCheckInUtcDay)
    }

    @Test
    fun decodeReturnsNullForUnknownSchema() {
        val raw = """{"schema":999,"attendanceCount":1}"""

        assertNull(AttendanceCodec.decode(raw))
    }

    @Test
    fun decodeReturnsNullForGarbageInput() {
        assertNull(AttendanceCodec.decode("not json"))
    }

    @Test
    fun decodeDefaultsMissingFieldsOnPartialJson() {
        val raw = """{"schema":1}"""

        val decoded = AttendanceCodec.decode(raw)

        assertEquals(AttendanceState(), decoded)
    }
}
