package com.worksoc.goaicoach.ui

import android.content.Context
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.auth.AuthState
import com.worksoc.goaicoach.application.auth.isPromotableAnonymousSession
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

/**
 * [OnboardingScreen]과 [SettingsScreen]이 공유하는 Google 로그인 시도 흐름. Credential
 * Manager로 ID 토큰을 받은 뒤, 지금 세션이 승격 가능한 익명 세션이면 [AuthClientPort.linkGoogleCredential]을,
 * 아니면 [AuthClientPort.signInWithGoogle]을 호출한다 — "언제 승격할지"를 SDK 어댑터가 아니라
 * 여기 호출부에서 판단해, `AuthState.isPromotableAnonymousSession`이라는 순수 유스케이스
 * 판단으로 남긴다. 실패/취소는 조용히 삼키지 않고 진단 로그로 남기며, 호출부가 토스트로 안내한다.
 */
internal suspend fun attemptGoogleSignIn(
    context: Context,
    authClient: AuthClientPort,
    credentialManagerClient: GoogleCredentialManagerClient,
    diagnosticEventLog: DiagnosticEventLogPort,
): Result<AuthState> {
    val idToken = credentialManagerClient.requestIdToken(context).getOrElse { error ->
        diagnosticEventLog.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Warning,
                code = "google_credential_request_failed",
                message = "Credential Manager did not return a Google ID token.",
                context = mapOf("error" to (error.message ?: error.toString())),
            ),
        )
        return Result.failure(error)
    }

    val signInResult = if (authClient.currentAuthState().isPromotableAnonymousSession) {
        authClient.linkGoogleCredential(idToken)
    } else {
        authClient.signInWithGoogle(idToken)
    }

    signInResult.exceptionOrNull()?.let { error ->
        diagnosticEventLog.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Warning,
                code = "google_firebase_sign_in_failed",
                message = "Firebase sign-in/link with the Google credential failed.",
                context = mapOf("error" to (error.message ?: error.toString())),
            ),
        )
    }
    return signInResult
}
