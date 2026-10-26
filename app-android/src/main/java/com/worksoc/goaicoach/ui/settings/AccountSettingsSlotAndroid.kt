package com.worksoc.goaicoach.ui.settings

import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.application.auth.port.AuthClientPort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.platform.GoogleCredentialManagerClient
import com.worksoc.goaicoach.platform.GoogleIdTokenSource
import com.worksoc.goaicoach.ui.account.AccountSettingsSection

@Composable
internal actual fun AccountSettingsSlot(
    authClient: AuthClientPort,
    credentialManagerClient: GoogleIdTokenSource,
    diagnosticEventLog: DiagnosticEventLogPort,
) {
    AccountSettingsSection(
        authClient = authClient,
        credentialManagerClient = credentialManagerClient as GoogleCredentialManagerClient,
        diagnosticEventLog = diagnosticEventLog,
    )
}
