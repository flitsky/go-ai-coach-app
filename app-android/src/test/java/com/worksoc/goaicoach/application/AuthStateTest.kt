package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.auth.AuthProvider
import com.worksoc.goaicoach.application.auth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStateTest {
    @Test
    fun defaultStateIsNotSignedIn() {
        val state = AuthState()

        assertFalse(state.isSignedIn)
        assertNull(state.provider)
        assertNull(state.uid)
    }

    @Test
    fun signedInFactoryProducesSignedInStateWithProviderAndUid() {
        val state = AuthState.signedIn(AuthProvider.Anonymous, uid = "uid-123")

        assertTrue(state.isSignedIn)
        assertEquals(AuthProvider.Anonymous, state.provider)
        assertEquals("uid-123", state.uid)
    }
}
