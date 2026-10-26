package com.worksoc.goaicoach.ui.settings

import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.application.auth.port.AuthClientPort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.platform.GoogleIdTokenSource

// iOS는 로그인이 아직 없다(Sign in with Apple · 계정 연동은 사용자 작업 뒤 — #294 「제가 정한 것」).
@Composable
internal actual fun AccountSettingsSlot(
    authClient: AuthClientPort,
    credentialManagerClient: GoogleIdTokenSource,
    diagnosticEventLog: DiagnosticEventLogPort,
) {}
