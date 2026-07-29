package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.auth.AuthClientPort
import kotlinx.coroutines.launch

/**
 * 최초 실행 시 한 번만 뜨는 온보딩 화면. Google/이메일 로그인은 버튼만 배치돼 있고 아직
 * 연동되지 않았다("준비 중" 토스트만 표시) — 실제로 동작하는 건 "계정 없이 시작하기"
 * (Firebase 익명 인증)뿐이다.
 *
 * "계정 없이 시작하기"는 익명 로그인이 실패해도(예: google-services.json 미설정) 앱
 * 사용 자체를 막지 않는다 — 실패 시 안내만 띄우고 그대로 홈으로 진입시킨다.
 * [onOnboardingComplete]의 `didSignIn`이 false면 호출부(GoCoachApp.kt)는 "온보딩을
 * 봤다" 플래그를 저장하지 않으므로, 다음 실행 때 이 화면이 다시 뜨며 조용히 재시도된다.
 */
@Composable
internal fun OnboardingScreen(
    authClient: AuthClientPort,
    onOnboardingComplete: (didSignIn: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }

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

        OutlinedButton(
            onClick = { Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text(strings.continueWithGoogle)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text(strings.continueWithEmail)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (!isSigningIn) {
                    isSigningIn = true
                    scope.launch {
                        val outcome = authClient.signInAnonymously()
                        isSigningIn = false
                        if (outcome.isFailure) {
                            Toast.makeText(context, strings.guestConnectionFailedMessage, Toast.LENGTH_SHORT).show()
                        }
                        onOnboardingComplete(outcome.isSuccess)
                    }
                }
            },
            enabled = !isSigningIn,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ),
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White)
            } else {
                Text(strings.continueWithoutAccount, fontWeight = FontWeight.Bold)
            }
        }
    }
}
