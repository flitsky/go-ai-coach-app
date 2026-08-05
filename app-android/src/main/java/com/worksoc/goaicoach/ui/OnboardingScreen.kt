package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import kotlinx.coroutines.launch

/**
 * 최초 실행 시 한 번만 뜨는 온보딩 화면. 최초 실행 시점의 "얕은 허들"로 Google/Apple/이메일
 * 로그인과 "계정 없이 시작하기"를 함께 제시한다 — Google/이메일은 실제 연동이고, Apple만
 * 아직 실제 SDK 연동 전이라 [SettingsScreen]과 동일하게 "준비 중" 안내를 표시한다.
 *
 * 완료 조건은 [DeviceIdentityStorePort.loadOrCreate] 또는 Google/이메일 로그인 성공이다 —
 * 게스트 경로는 네트워크 없이 항상 즉시 성공하므로 "계정 없이 시작하기"를 선택하면 이 화면은
 * 다시 뜨지 않는다(반복 온보딩 없음). Firebase 익명 로그인([authClient])도 게스트 선택 시
 * 함께 시도하지만 fire-and-forget이다: 화면은 결과를 기다리지도, 실패를 알리지도 않는다 —
 * 익명 로그인이 콘솔에서 비활성 상태인 현재는 조용히 실패하고, 나중에 활성화되면 이 코드
 * 변경 없이 조용히 성공하기 시작한다. Google/이메일 로그인은 그 반대로 실패/취소를 조용히
 * 삼키지 않고 토스트로 안내한다([attemptGoogleSignIn]/[attemptEmailSignIn]) — 사용자가
 * 명시적으로 시도한 동작이라 결과를 알아야 하기 때문이다.
 */
@Composable
internal fun OnboardingScreen(
    authClient: AuthClientPort,
    deviceIdentityStore: DeviceIdentityStorePort,
    credentialManagerClient: GoogleCredentialManagerClient,
    diagnosticEventLog: DiagnosticEventLogPort,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEmailDialog by remember { mutableStateOf(false) }
    var isEmailSubmitting by remember { mutableStateOf(false) }

    fun showNotImplemented() {
        Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show()
    }

    fun signInWithGoogle() {
        scope.launch {
            attemptGoogleSignIn(context, authClient, credentialManagerClient, diagnosticEventLog)
                .onSuccess {
                    Toast.makeText(context, strings.googleSignedInToastMessage, Toast.LENGTH_SHORT).show()
                    onOnboardingComplete()
                }
                .onFailure {
                    Toast.makeText(context, strings.googleSignInFailedMessage, Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun submitEmailSignIn(email: String, password: String) {
        isEmailSubmitting = true
        scope.launch {
            val result = attemptEmailSignIn(authClient, email, password, diagnosticEventLog)
            isEmailSubmitting = false
            result
                .onSuccess {
                    showEmailDialog = false
                    Toast.makeText(context, strings.emailSignedInToastMessage, Toast.LENGTH_SHORT).show()
                    onOnboardingComplete()
                }
                .onFailure { error ->
                    val message = if (error is FirebaseAuthWeakPasswordException) {
                        strings.emailSignInWeakPasswordMessage
                    } else {
                        strings.emailSignInFailedMessage
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = strings.appTitle,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = strings.onboardingTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.onboardingSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Google "G" 공식 에셋(googleg_standard_color_18)은 Play Services Base AAR에
        // 들어있는데, 이 프로젝트의 Firebase Auth 버전(BOM 34.16.0)은 더 이상 그 모듈을
        // 전이 의존성으로 끌어오지 않아 실제로는 클래스패스에 없다(빌드 시도로 확인함) —
        // 새 의존성을 추가하지 않는 한 지금은 텍스트 placeholder를 유지하고, 실제 Google
        // 로그인 SDK(Step 2)를 붙이는 시점에 그 SDK가 제공하는 아이콘으로 교체한다.
        SocialLoginButton(
            label = strings.continueWithGoogle,
            leadingGlyph = "G",
            glyphColor = GoogleBrandBlue,
            onClick = { signInWithGoogle() },
        )

        Spacer(modifier = Modifier.height(24.dp))

        SocialLoginButton(
            label = strings.continueWithApple,
            leadingGlyph = "🍎", // 🍎 — Apple은 실제 SDK 없이 동등한 공식 에셋이 없어 임시 표기 유지
            onClick = { showNotImplemented() },
        )

        Spacer(modifier = Modifier.height(24.dp))

        SocialLoginButton(
            label = strings.continueWithEmail,
            leadingGlyph = "✉", // ✉
            onClick = { showEmailDialog = true },
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(20.dp))

        // 버튼이 아니라 텍스트 링크로: Spotify/Duolingo류 온보딩에서 흔한 "건너뛰기" 패턴 —
        // 위 3개 로그인 수단은 테두리 있는 버튼으로 동등하게 제시하고, 그 대안(계정 없이)은
        // 테두리/배경 없는 절제된 텍스트로 격 낮춰 보여준다. 터치 영역은 패딩으로 48dp 이상
        // 확보(접근성 최소 권장 크기).
        Text(
            text = strings.continueWithoutAccount,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .clickable(
                    onClick = {
                        deviceIdentityStore.loadOrCreate()
                        scope.launch { authClient.signInAnonymously() }
                        Toast.makeText(context, strings.guestStartedToastMessage, Toast.LENGTH_SHORT).show()
                        onOnboardingComplete()
                    },
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }

    if (showEmailDialog) {
        EmailSignInDialog(
            titleText = strings.continueWithEmail,
            emailLabel = strings.emailFieldLabel,
            passwordLabel = strings.passwordFieldLabel,
            continueLabel = strings.emailSignInSubmitLabel,
            cancelLabel = strings.cancel,
            isSubmitting = isEmailSubmitting,
            onDismiss = { showEmailDialog = false },
            onSubmit = ::submitEmailSignIn,
        )
    }
}
