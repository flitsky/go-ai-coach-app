package com.worksoc.goaicoach.ui

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.auth.AuthProvider
import com.worksoc.goaicoach.application.auth.AuthState
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 4계층(External Integration) Extended API 본체 — [AuthClientPort]를 실제 Firebase Auth SDK에
 * 연결하는 어댑터. Google 자격 증명(ID 토큰)을 받는 Credential Manager 호출 자체는 SDK 의존이
 * 달라 `GoogleCredentialManagerClient`로 분리했다 — 이 클래스는 그 결과(토큰)를 받아 Firebase
 * Auth와 연결하는 부분만 담당한다. 이메일/비밀번호는 별도 SDK가 필요 없어(Firebase Auth에
 * 내장) 이 클래스 안에서 바로 처리한다.
 */
internal class AndroidAuthClient : AuthClientPort {
    override suspend fun signInWithGoogle(idToken: String): Result<AuthState> =
        signInWithCredentialOnce(GoogleAuthProvider.getCredential(idToken, null), AuthProvider.Google)

    override suspend fun linkGoogleCredential(idToken: String): Result<AuthState> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: return signInWithCredentialOnce(credential, AuthProvider.Google)

        val linkResult = linkCredentialOnce(currentUser, credential, AuthProvider.Google)
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

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthState> {
        val createResult = createUserWithEmailOnce(email, password)
        val collision = createResult.exceptionOrNull()
        // 먼저 계정 생성을 시도하고, 이미 가입된 이메일이면(충돌) 그 계정으로 로그인한다 —
        // "가입 vs 로그인"을 사용자에게 따로 묻지 않기 위한 순서다. 반대 순서(로그인 먼저
        // 시도 후 실패 시 가입)는 최신 Firebase Auth가 "가입 안 된 이메일"과 "비밀번호
        // 틀림"을 계정 열거 방지를 위해 같은 FirebaseAuthInvalidCredentialsException으로
        // 뭉뚱그려 반환할 수 있어 신뢰할 수 없다 — 반면 이메일 중복(충돌)은 여전히 구분되는
        // 신호라 이 순서가 더 견고하다.
        return if (collision is FirebaseAuthUserCollisionException) {
            signInWithCredentialOnce(EmailAuthProvider.getCredential(email, password), AuthProvider.Email)
        } else {
            createResult
        }
    }

    override suspend fun linkEmailCredential(email: String, password: String): Result<AuthState> {
        val credential = EmailAuthProvider.getCredential(email, password)
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: return signInWithEmail(email, password)

        val linkResult = linkCredentialOnce(currentUser, credential, AuthProvider.Email)
        val collision = linkResult.exceptionOrNull()
        return if (collision is FirebaseAuthUserCollisionException) {
            signInWithCredentialOnce(credential, AuthProvider.Email)
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

    private suspend fun createUserWithEmailOnce(email: String, password: String): Result<AuthState> =
        try {
            suspendCancellableCoroutine { continuation ->
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid
                        val outcome = if (uid != null) {
                            Result.success(AuthState.signedIn(AuthProvider.Email, uid))
                        } else {
                            Result.failure(IllegalStateException("Account creation succeeded without a uid"))
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

    private suspend fun linkCredentialOnce(user: FirebaseUser, credential: AuthCredential, provider: AuthProvider): Result<AuthState> =
        try {
            suspendCancellableCoroutine { continuation ->
                user.linkWithCredential(credential)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid
                        val outcome = if (uid != null) {
                            Result.success(AuthState.signedIn(provider, uid))
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
}
