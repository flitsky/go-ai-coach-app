package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.device.DeviceIdentity
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test

class DeviceIdentityTest {
    @Test
    fun rejectsBlankId() {
        assertFailsWith<IllegalArgumentException> {
            DeviceIdentity(" ")
        }
    }

    @Test
    fun equalIdsAreEqual() {
        assertEquals(DeviceIdentity("device-1"), DeviceIdentity("device-1"))
    }
}
