package com.worksoc.goaicoach.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import kotlinx.coroutines.launch

/**
 * 최초 실행 시 한 번만 뜨는 온보딩 화면. 완료 조건은 [DeviceIdentityStorePort.loadOrCreate]뿐이다
 * — 네트워크 없이 항상 즉시 성공하므로, 이 화면은 다시 뜨지 않는다(반복 온보딩 없음).
 *
 * Firebase 익명 로그인([authClient])은 여전히 시도하지만 fire-and-forget이다: 화면은 결과를
 * 기다리지도, 실패를 알리지도 않는다 — google-services.json이 없는 현재는 조용히 실패하고,
 * 나중에 추가되면 이 코드 변경 없이 조용히 성공하기 시작한다. Google/이메일 로그인은 더 이상
 * 이 화면에 노출하지 않는다 — 원하는 사용자는 홈 화면의 설정에서 강화할 수 있다([SettingsScreen]).
 */
@Composable
internal fun OnboardingScreen(
    authClient: AuthClientPort,
    deviceIdentityStore: DeviceIdentityStorePort,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val scope = rememberCoroutineScope()

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

        Button(
            onClick = {
                deviceIdentityStore.loadOrCreate()
                scope.launch { authClient.signInAnonymously() }
                onOnboardingComplete()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ),
        ) {
            Text(strings.getStarted, fontWeight = FontWeight.Bold)
        }
    }
}
