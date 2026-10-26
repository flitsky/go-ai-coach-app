package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.worksoc.goaicoach.application.auth.port.AuthClientPort
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.platform.GoogleIdTokenSource

@Composable
internal actual fun OnboardingSlot(
    authClient: AuthClientPort,
    deviceIdentityStore: DeviceIdentityStorePort,
    credentialManagerClient: GoogleIdTokenSource,
    diagnosticEventLog: DiagnosticEventLogPort,
    onOnboardingComplete: () -> Unit,
) {
    LaunchedEffect(Unit) {
        deviceIdentityStore.loadOrCreate()
        onOnboardingComplete()
    }
}
