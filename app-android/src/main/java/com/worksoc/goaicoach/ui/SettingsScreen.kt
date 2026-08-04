package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.application.auth.AuthClientPort

/**
 * 홈 화면 상단의 설정 진입점에서 열리는 화면. 게스트(로컬 기기 ID) 상태를 안내하고,
 * 원하는 사용자가 Google/Apple/이메일 로그인으로 강화할 수 있는 선택지를 제공한다 —
 * [OnboardingScreen]과 동일한 3개 버튼을 여기서도 노출해, 온보딩에서 "계정 없이
 * 시작하기"를 고른 사용자가 나중에 아무 때나 같은 선택지로 돌아올 수 있게 한다. 세
 * 버튼 모두 아직 실제 SDK 연동 전이라 "준비 중" 안내만 표시한다. [authClient]는 현재
 * 이 화면에서 직접 쓰이지 않지만, 이후 실제 로그인 강화 플로우(예: 익명 → Google 계정
 * 연결)를 이 화면에 붙일 때의 자리로 시그니처에 남겨 둔다.
 *
 * [BuildConfig.DEBUG]일 때만 "개발자 테스트" 섹션을 추가로 노출한다 — 실제 결제 SDK
 * 연동 전까지 프리미엄 구매 상태를 자유롭게 켜고 끄며 QA할 수 있는 자리다. 릴리스
 * 빌드에서 노출되면 사용자가 무료로 프리미엄을 얻을 수 있게 되므로 반드시 이 게이팅을
 * 유지해야 한다.
 */
@Composable
internal fun SettingsScreen(
    authClient: AuthClientPort,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val premium = LocalPremiumUiState.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = strings.close,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = strings.settingsTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = strings.settingsAccountSectionTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )

            Text(
                text = strings.settingsGuestStatusMessage,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            SocialLoginButton(
                label = strings.continueWithGoogle,
                leadingGlyph = "G",
                glyphColor = GoogleBrandBlue,
                onClick = { Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show() },
            )

            SocialLoginButton(
                label = strings.continueWithApple,
                leadingGlyph = "🍎",
                onClick = { Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show() },
            )

            SocialLoginButton(
                label = strings.continueWithEmail,
                leadingGlyph = "✉",
                onClick = { Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show() },
            )

            // 실 결제 연동 전까지 QA가 프리미엄 구매 상태를 자유롭게 켜고 끄는 자리 —
            // 릴리스 빌드 유출 방지를 위해 반드시 BuildConfig.DEBUG로 게이팅한다.
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.settingsDevSectionTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.settingsDevPremiumToggleTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = strings.settingsDevPremiumToggleSubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Switch(
                        checked = premium.isPurchased,
                        onCheckedChange = { checked -> premium.setPurchased(checked) },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
