package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.auth.state.AuthProvider
import com.worksoc.goaicoach.application.auth.state.AuthState
import com.worksoc.goaicoach.application.auth.state.isPromotableAnonymousSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun anonymousSignedInSessionIsPromotable() {
        val state = AuthState.signedIn(AuthProvider.Anonymous, uid = "uid-123")

        assertTrue(state.isPromotableAnonymousSession)
    }

    @Test
    fun notSignedInSessionIsNotPromotable() {
        assertFalse(AuthState().isPromotableAnonymousSession)
    }

    @Test
    fun googleSignedInSessionIsNotPromotable() {
        val state = AuthState.signedIn(AuthProvider.Google, uid = "uid-123")

        assertFalse(state.isPromotableAnonymousSession)
    }
}
