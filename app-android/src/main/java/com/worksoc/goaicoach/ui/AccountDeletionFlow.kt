package com.worksoc.goaicoach.ui

import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

/**
 * [SettingsScreen]의 계정 삭제 확인 다이얼로그가 쓰는 흐름 — [EmailSignInFlow]/[GoogleSignInFlow]와
 * 동일하게 실패를 조용히 삼키지 않고 진단 로그로 남긴다. [FirebaseAuthRecentLoginRequiredException]은
 * 로그인이 오래돼 Firebase가 재인증을 요구하는, 흔히 발생하는 예상된 실패 케이스라 별도 코드로
 * 구분해 둔다 — 호출부가 이 경우만 "재로그인 후 다시 시도" 안내로 갈라 보여줄 수 있게 한다.
 */
internal suspend fun attemptAccountDeletion(
    authClient: AuthClientPort,
    diagnosticEventLog: DiagnosticEventLogPort,
): Result<Unit> {
    val result = authClient.deleteAccount()

    result.exceptionOrNull()?.let { error ->
        val isRecentLoginRequired = error is FirebaseAuthRecentLoginRequiredException
        diagnosticEventLog.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Warning,
                code = if (isRecentLoginRequired) "account_deletion_recent_login_required" else "account_deletion_failed",
                message = "Firebase account deletion failed.",
                context = mapOf("error" to (error.message ?: error.toString())),
            ),
        )
    }
    return result
}
