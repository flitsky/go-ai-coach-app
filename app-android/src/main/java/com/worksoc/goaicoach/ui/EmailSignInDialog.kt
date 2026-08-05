package com.worksoc.goaicoach.ui

import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private const val MinPasswordLength = 6

/**
 * 7계층(Presentation) — 이메일+비밀번호 입력 다이얼로그. [OnboardingScreen]/[SettingsScreen]이
 * 공유한다. 가입/로그인을 사용자가 직접 고르지 않으므로(자동 분기는 `AuthClientPort` 쪽 책임)
 * 버튼 라벨도 "가입"/"로그인"이 아니라 [continueLabel] 하나뿐이다. 비밀번호 길이(6자)는
 * Firebase 쪽 최소 요구치와 맞춰 버튼을 미리 비활성화하는 용도일 뿐 — 실제 판정은 항상
 * Firebase 응답을 신뢰한다(클라이언트 검증은 왕복 한 번을 아끼는 힌트일 뿐).
 */
@Composable
internal fun EmailSignInDialog(
    titleText: String,
    emailLabel: String,
    passwordLabel: String,
    continueLabel: String,
    cancelLabel: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = !isSubmitting &&
        Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
        password.length >= MinPasswordLength

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(emailLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(passwordLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(email, password) }, enabled = canSubmit) {
                Text(continueLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(cancelLabel)
            }
        },
    )
}
