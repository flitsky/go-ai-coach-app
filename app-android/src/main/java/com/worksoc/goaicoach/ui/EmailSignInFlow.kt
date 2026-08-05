package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.auth.AuthState
import com.worksoc.goaicoach.application.auth.isPromotableAnonymousSession
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

/**
 * [OnboardingScreen]과 [SettingsScreen]이 공유하는 이메일+비밀번호 로그인 시도 흐름 —
 * [GoogleSignInFlow]와 동일한 구조다: 지금 세션이 승격 가능한 익명 세션이면
 * [AuthClientPort.linkEmailCredential]을, 아니면 [AuthClientPort.signInWithEmail]을
 * 호출한다. 실패는 조용히 삼키지 않고 진단 로그로 남기며, 호출부가 토스트로 안내한다.
 */
internal suspend fun attemptEmailSignIn(
    authClient: AuthClientPort,
    email: String,
    password: String,
    diagnosticEventLog: DiagnosticEventLogPort,
): Result<AuthState> {
    val signInResult = if (authClient.currentAuthState().isPromotableAnonymousSession) {
        authClient.linkEmailCredential(email, password)
    } else {
        authClient.signInWithEmail(email, password)
    }

    signInResult.exceptionOrNull()?.let { error ->
        diagnosticEventLog.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Warning,
                code = "email_firebase_sign_in_failed",
                message = "Firebase sign-in/link with the email credential failed.",
                context = mapOf("error" to (error.message ?: error.toString())),
            ),
        )
    }
    return signInResult
}
