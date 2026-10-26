package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.application.auth.port.AuthClientPort
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.platform.GoogleIdTokenSource

/**
 * 첫 실행의 로그인 안내 자리(백로그 #294 — S24). 안드로이드는 `OnboardingScreen`(Firebase · Credential Manager), iOS는 로그인이 없어
 * 곧바로 끝낸다. 로그인이 꺼져 있으면(`FeatureFlags.isLoginEnabled`) 두 플랫폼 모두 이 자리에 오지 않는다(`initialDestination`).
 */
@Composable
internal expect fun OnboardingSlot(
    authClient: AuthClientPort,
    deviceIdentityStore: DeviceIdentityStorePort,
    credentialManagerClient: GoogleIdTokenSource,
    diagnosticEventLog: DiagnosticEventLogPort,
    onOnboardingComplete: () -> Unit,
)
