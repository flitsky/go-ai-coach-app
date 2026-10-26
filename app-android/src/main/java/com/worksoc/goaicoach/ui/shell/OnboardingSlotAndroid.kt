package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.application.auth.port.AuthClientPort
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.platform.GoogleCredentialManagerClient
import com.worksoc.goaicoach.platform.GoogleIdTokenSource
import com.worksoc.goaicoach.ui.account.OnboardingScreen

@Composable
internal actual fun OnboardingSlot(
    authClient: AuthClientPort,
    deviceIdentityStore: DeviceIdentityStorePort,
    credentialManagerClient: GoogleIdTokenSource,
    diagnosticEventLog: DiagnosticEventLogPort,
    onOnboardingComplete: () -> Unit,
) {
    OnboardingScreen(
        authClient = authClient,
        deviceIdentityStore = deviceIdentityStore,
        credentialManagerClient = credentialManagerClient as GoogleCredentialManagerClient,
        diagnosticEventLog = diagnosticEventLog,
        onOnboardingComplete = onOnboardingComplete,
    )
}
