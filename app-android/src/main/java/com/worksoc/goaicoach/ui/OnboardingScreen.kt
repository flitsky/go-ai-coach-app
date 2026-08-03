package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import kotlinx.coroutines.launch

/**
 * 최초 실행 시 한 번만 뜨는 온보딩 화면. 최초 실행 시점의 "얕은 허들"로 Google/Apple/이메일
 * 로그인과 "계정 없이 시작하기"를 함께 제시한다 — Google/Apple/이메일은 아직 실제 SDK
 * 연동 전이라 [SettingsScreen]과 동일하게 "준비 중" 안내만 표시한다.
 *
 * 완료 조건은 [DeviceIdentityStorePort.loadOrCreate]뿐이다 — 네트워크 없이 항상 즉시
 * 성공하므로 "계정 없이 시작하기"를 선택하면 이 화면은 다시 뜨지 않는다(반복 온보딩 없음).
 * Firebase 익명 로그인([authClient])도 함께 시도하지만 fire-and-forget이다: 화면은 결과를
 * 기다리지도, 실패를 알리지도 않는다 — google-services.json이 없는 현재는 조용히 실패하고,
 * 나중에 추가되면 이 코드 변경 없이 조용히 성공하기 시작한다.
 */
@Composable
internal fun OnboardingScreen(
    authClient: AuthClientPort,
    deviceIdentityStore: DeviceIdentityStorePort,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun showNotImplemented() {
        Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show()
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

        SocialLoginButton(
            label = strings.continueWithGoogle,
            leadingGlyph = "G",
            glyphColor = GoogleBrandBlue,
            onClick = { showNotImplemented() },
        )

        Spacer(modifier = Modifier.height(12.dp))

        SocialLoginButton(
            label = strings.continueWithApple,
            leadingGlyph = "🍎", // 🍎 — 실제 Apple 로고 벡터 에셋으로 교체 전 임시 표기
            onClick = { showNotImplemented() },
        )

        Spacer(modifier = Modifier.height(12.dp))

        SocialLoginButton(
            label = strings.continueWithEmail,
            leadingGlyph = "✉", // ✉
            onClick = { showNotImplemented() },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 다른 3개 버튼과 동일한 무채색 톤 — "얕은 허들"의 선택지 중 하나일 뿐, 권장 경로처럼
        // 강조되면 안 된다는 피드백에 따라 진한 배경색(Button)이 아닌 OutlinedButton으로 통일.
        SocialLoginButton(
            label = strings.continueWithoutAccount,
            leadingGlyph = "👤", // 👤
            onClick = {
                deviceIdentityStore.loadOrCreate()
                scope.launch { authClient.signInAnonymously() }
                Toast.makeText(context, strings.guestStartedToastMessage, Toast.LENGTH_SHORT).show()
                onOnboardingComplete()
            },
        )
    }
}
