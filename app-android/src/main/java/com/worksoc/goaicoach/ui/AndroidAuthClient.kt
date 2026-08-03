package com.worksoc.goaicoach.ui

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.auth.AuthProvider
import com.worksoc.goaicoach.application.auth.AuthState
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 4계층(External Integration) Extended API 본체 — [AuthClientPort]를 실제 Firebase Auth SDK에
 * 연결하는 어댑터. `docs/ARCHITECTURE.md` 2계층 설명대로, SDK 응답을 그대로 상위에 흘려보내지
 * 않고 이 안에서 실패 신뢰도 판단을 흡수한다: 일시적 네트워크 실패([FirebaseNetworkException])만
 * 재시도하고, 자격 증명/설정 오류처럼 재시도해도 같은 결과가 나올 실패는 즉시 반환한다.
 */
internal class AndroidAuthClient : AuthClientPort {
    override suspend fun signInAnonymously(): Result<AuthState> {
        var attempt = 1
        var result = signInAnonymouslyOnce()
        while (attempt < MaxAttempts && result.exceptionOrNull() is FirebaseNetworkException) {
            delay(NetworkRetryDelayMillis)
            attempt += 1
            result = signInAnonymouslyOnce()
        }
        return result
    }

    private suspend fun signInAnonymouslyOnce(): Result<AuthState> =
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

    private companion object {
        const val MaxAttempts = 3
        const val NetworkRetryDelayMillis = 500L
    }
}
