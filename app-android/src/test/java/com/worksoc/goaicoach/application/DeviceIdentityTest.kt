package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.device.DeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceIdentityTest {
    @Test
    fun rejectsBlankId() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceIdentity(" ")
        }
    }

    @Test
    fun equalIdsAreEqual() {
        assertEquals(DeviceIdentity("device-1"), DeviceIdentity("device-1"))
    }
}
