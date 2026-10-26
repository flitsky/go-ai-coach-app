package com.worksoc.goaicoach.kv

// 같은 이름의 안드로이드 시험(`persistence/`)을 공용 사본(`kv`)에 그대로 돌린다(백로그 #294 — P2 짝 시험). import만 바꿨다.

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
