package com.worksoc.goaicoach.ui.account

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.application.auth.port.AuthClientPort
import com.worksoc.goaicoach.application.auth.state.AuthProvider
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.platform.GoogleCredentialManagerClient
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.GoogleBrandBlue
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import kotlinx.coroutines.launch

/**
 * 설정 화면의 계정 섹션 호스트(refactor backlog #46·#102).
 *
 * `SettingsScreen`이 쥐고 있던 계정 역할 상태 5개(`authState`, `showEmailDialog`, `isEmailSubmitting`,
 * `showDeleteAccountDialog`, `isDeletingAccount`)와 로그인/계정 삭제 다이얼로그를 `ui.account` 패키지로
 * 캡슐화해, `SettingsScreen`의 `FOREIGN_ROLE_STATE` 부채를 0으로 만든다.
 */
@Composable
internal fun AccountSettingsSection(
    authClient: AuthClientPort,
    credentialManagerClient: GoogleCredentialManagerClient,
    diagnosticEventLog: DiagnosticEventLogPort,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var authState by remember { mutableStateOf(authClient.currentAuthState()) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var isEmailSubmitting by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    val hasRealAccount = authState.provider == AuthProvider.Google || authState.provider == AuthProvider.Email

    fun deleteAccount() {
        isDeletingAccount = true
        scope.launch {
            val result = attemptAccountDeletion(authClient, diagnosticEventLog)
            isDeletingAccount = false
            result
                .onSuccess {
                    showDeleteAccountDialog = false
                    authState = authClient.currentAuthState()
                    Toast.makeText(context, strings.settingsDeleteAccountSuccessMessage, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    val message = if (isRecentLoginRequiredError(error)) {
                        strings.settingsDeleteAccountRecentLoginRequiredMessage
                    } else {
                        strings.settingsDeleteAccountFailedMessage
                    }
                    // 실패 사유는 성공 안내보다 읽고 판단할 시간이 더 필요해 LENGTH_LONG을 쓴다.
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
        }
    }

    fun signInWithGoogle() {
        scope.launch {
            attemptGoogleSignIn(context, authClient, credentialManagerClient, diagnosticEventLog)
                .onSuccess { newState ->
                    authState = newState
                    Toast.makeText(context, strings.googleSignedInToastMessage, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, strings.googleSignInFailedMessage, Toast.LENGTH_LONG).show()
                }
        }
    }

    fun submitEmailSignIn(email: String, password: String) {
        isEmailSubmitting = true
        scope.launch {
            val result = attemptEmailSignIn(authClient, email, password, diagnosticEventLog)
            isEmailSubmitting = false
            result
                .onSuccess { newState ->
                    authState = newState
                    showEmailDialog = false
                    Toast.makeText(context, strings.emailSignedInToastMessage, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    val message = if (isWeakPasswordError(error)) {
                        strings.emailSignInWeakPasswordMessage
                    } else {
                        strings.emailSignInFailedMessage
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
        }
    }

    Text(
        text = strings.settingsAccountSectionTitle,
        fontSize = AppTextSize.Text14,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
    )

    Text(
        text = when (authState.provider) {
            AuthProvider.Google -> strings.settingsGoogleStatusMessage
            AuthProvider.Email -> strings.settingsEmailStatusMessage
            else -> strings.settingsGuestStatusMessage
        },
        fontSize = AppTextSize.Text13,
        color = MaterialTheme.colorScheme.onBackground,
    )

    // 게스트(로컬 ID만 있는 상태)는 삭제할 서버 계정 자체가 없어 숨긴다. Google Play
    // 정책상 계정 생성을 지원하는 앱은 인앱 삭제 경로가 필수라 BuildConfig.DEBUG로
    // 게이팅하지 않는다 — 실제 사용자가 릴리스 빌드에서 항상 쓸 수 있어야 한다.
    if (hasRealAccount) {
        TextButton(
            onClick = { showDeleteAccountDialog = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(strings.settingsDeleteAccountButtonLabel)
        }
    }

    if (!hasRealAccount) {
        SocialLoginButton(
            label = strings.continueWithGoogle,
            leadingGlyph = "G",
            glyphColor = GoogleBrandBlue,
            onClick = ::signInWithGoogle,
        )
    }

    SocialLoginButton(
        label = strings.continueWithApple,
        leadingGlyph = "🍎",
        onClick = { Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show() },
    )

    if (!hasRealAccount) {
        SocialLoginButton(
            label = strings.continueWithEmail,
            leadingGlyph = "✉",
            onClick = { showEmailDialog = true },
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

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            title = { Text(strings.settingsDeleteAccountConfirmTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.settingsDeleteAccountConfirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = ::deleteAccount,
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(strings.settingsDeleteAccountButtonLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isDeletingAccount,
                ) {
                    Text(strings.cancel)
                }
            },
        )
    }
}
