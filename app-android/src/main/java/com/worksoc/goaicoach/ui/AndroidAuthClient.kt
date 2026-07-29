package com.worksoc.goaicoach.ui

import com.google.firebase.auth.FirebaseAuth
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.auth.AuthProvider
import com.worksoc.goaicoach.application.auth.AuthState
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AndroidAuthClient : AuthClientPort {
    override suspend fun signInAnonymously(): Result<AuthState> =
        try {
            // FirebaseAuth.getInstance()는 google-services.json이 없어 기본 FirebaseApp이
            // 구성되지 않은 경우 즉시(동기적으로) IllegalStateException을 던진다 — 이건
            // addOnFailureListener로는 잡히지 않으므로 바깥에서 별도로 감싸야 한다.
            suspendCancellableCoroutine { continuation ->
                FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid
                        val outcome = if (uid != null) {
                            Result.success(AuthState.signedIn(AuthProvider.Anonymous, uid))
                        } else {
                            Result.failure(IllegalStateException("Anonymous sign-in succeeded without a uid"))
                        }
                        continuation.resume(outcome)
                    }
                    .addOnFailureListener { error ->
                        continuation.resume(Result.failure(error))
                    }
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
}
