package com.worksoc.goaicoach.ui

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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
 * Google 자격 증명(ID 토큰)을 받는 Credential Manager 호출 자체는 SDK 의존이 달라
 * `GoogleCredentialManagerClient`로 분리했다 — 이 클래스는 그 결과(토큰)를 받아 Firebase
 * Auth와 연결하는 부분만 담당한다.
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

    override suspend fun signInWithGoogle(idToken: String): Result<AuthState> =
        signInWithCredentialOnce(GoogleAuthProvider.getCredential(idToken, null), AuthProvider.Google)

    override suspend fun linkGoogleCredential(idToken: String): Result<AuthState> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: return signInWithCredentialOnce(credential, AuthProvider.Google)

        val linkResult = linkCredentialOnce(currentUser, credential)
        val collision = linkResult.exceptionOrNull()
        // 이 Google 계정이 이미 다른 Firebase 사용자에 연결되어 있으면 linkWithCredential이
        // 항상 충돌 예외를 던진다(Firebase 공식 문서의 익명 승격 권장 처리) — Step 4 이전인
        // 지금은 익명 UID에 매달린 서버 데이터가 없으므로, 그 기존 Google 계정으로 그냥
        // 로그인시키는 편이 데이터 유실 없이(애초에 유실될 게 없음) 사용자 기대에 맞다.
        return if (collision is FirebaseAuthUserCollisionException) {
            signInWithCredentialOnce(credential, AuthProvider.Google)
        } else {
            linkResult
        }
    }

    override fun currentAuthState(): AuthState {
        val user = FirebaseAuth.getInstance().currentUser ?: return AuthState()
        val provider = when {
            user.isAnonymous -> AuthProvider.Anonymous
            user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AuthProvider.Google
            else -> AuthProvider.Email
        }
        return AuthState.signedIn(provider, user.uid)
    }

    private suspend fun signInWithCredentialOnce(credential: AuthCredential, provider: AuthProvider): Result<AuthState> =
        try {
            suspendCancellableCoroutine { continuation ->
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid
                        val outcome = if (uid != null) {
                            Result.success(AuthState.signedIn(provider, uid))
                        } else {
                            Result.failure(IllegalStateException("Sign-in succeeded without a uid"))
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

    private suspend fun linkCredentialOnce(user: FirebaseUser, credential: AuthCredential): Result<AuthState> =
        try {
            suspendCancellableCoroutine { continuation ->
                user.linkWithCredential(credential)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid
                        val outcome = if (uid != null) {
                            Result.success(AuthState.signedIn(AuthProvider.Google, uid))
                        } else {
                            Result.failure(IllegalStateException("Credential link succeeded without a uid"))
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
