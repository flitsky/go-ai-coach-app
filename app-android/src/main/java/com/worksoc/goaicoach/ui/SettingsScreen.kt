package com.worksoc.goaicoach.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.auth.AuthProvider
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.preferences.GameSetupUxMode
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import kotlinx.coroutines.launch

// AdMob·Play Billing 등 Play Console 필수 제출 항목(개인정보처리방침 URL)과 동일한 문서를
// 가리킨다 — Play Console "앱 콘텐츠" 폼에도 이 URL을 별도로 등록해야 한다(코드 배선과
// 무관한 콘솔 설정, launch-plan/README.md 참고).
private const val PRIVACY_POLICY_URL = "https://rezen.dev/go-ai-coach/privacy/"

/**
 * 홈 화면 상단의 설정 진입점에서 열리는 화면. 게스트(로컬 기기 ID)/Google/이메일 로그인
 * 상태를 안내하고, 원하는 사용자가 Google/Apple/이메일 로그인으로 강화할 수 있는 선택지를
 * 제공한다 — [OnboardingScreen]과 동일한 3개 버튼을 여기서도 노출해, 온보딩에서 "계정
 * 없이 시작하기"를 고른 사용자가 나중에 아무 때나 같은 선택지로 돌아올 수 있게 한다.
 * Google/이메일은 실제 연동이고(익명 세션이면 각각 [AuthClientPort.linkGoogleCredential]/
 * `linkEmailCredential`로 UID를 유지한 채 승격), Apple만 아직 "준비 중" 스텁이다. 이미
 * 실계정(Google 또는 이메일)으로 로그인된 상태에서는 두 버튼을 함께 숨긴다 — 그렇지 않으면
 * 예를 들어 Google로 로그인한 뒤 실수로 이메일 버튼을 눌러 완전히 다른 계정으로 조용히
 * 전환돼버릴 수 있다(로그아웃 UI가 없는 지금은 되돌릴 방법도 없다).
 *
 * [BuildConfig.DEBUG]일 때만 "개발자 테스트" 섹션을 추가로 노출한다 — 실제 결제 SDK
 * 연동 전까지 프리미엄 구매 상태를 자유롭게 켜고 끄며 QA할 수 있는 자리다. 릴리스
 * 빌드에서 노출되면 사용자가 무료로 프리미엄을 얻을 수 있게 되므로 반드시 이 게이팅을
 * 유지해야 한다.
 *
 * 계정 섹션이 [FeatureFlags.isLoginEnabled]로 꺼져 있고 개발자 섹션도 릴리스 빌드에서는
 * 숨겨지는 지금 상태에서, 언어/대국 설정 없이는 이 화면이 완전히 빈 화면으로 보이는
 * 문제가 있었다(사용자 피드백, 2026-08-12) — 그래서 홈 화면의 언어 선택기, 대국 설정
 * 로비([GameSetupLobby])의 플레이어/규칙 패널을 여기서도 그대로 재사용해 노출한다.
 * 다소 중복이지만, 설정 화면 하나에서 일괄 관리할 수 있다는 이점이 더 크다는 사용자 판단.
 * 같은 [screenState]/[onEvent]를 씀으로써 여기서 바꾼 값이 곧바로 대국 설정 로비/게임
 * 메뉴에도 반영된다(별도 저장소를 새로 만들지 않음).
 */
@Composable
internal fun SettingsScreen(
    authClient: AuthClientPort,
    credentialManagerClient: GoogleCredentialManagerClient,
    diagnosticEventLog: DiagnosticEventLogPort,
    screenState: GameScreenState,
    onEvent: (GameUiEvent) -> Unit,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val premium = LocalPremiumUiState.current
    val scope = rememberCoroutineScope()
    val preferencesStore = remember(context) { UserPreferencesStore(context) }
    var gameSetupUxMode by remember { mutableStateOf(preferencesStore.load().gameSetupUxMode) }
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
                    val message = if (error is FirebaseAuthRecentLoginRequiredException) {
                        strings.settingsDeleteAccountRecentLoginRequiredMessage
                    } else {
                        strings.settingsDeleteAccountFailedMessage
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                .onSuccess { newState ->
                    authState = newState
                    showEmailDialog = false
                    Toast.makeText(context, strings.emailSignedInToastMessage, Toast.LENGTH_SHORT).show()
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
            LanguageSettingsPanel(
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
            )

            Text(
                text = strings.matchSetup,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )

            PlayerSetupPanel(
                state = screenState.playerSetupUi,
                enabled = !screenState.engine.isBusy,
                onPlayerSetupChange = { setup -> onEvent(GameUiEvent.ChangePlayerSetup(setup)) },
                onAutoPlayDelayChange = { setting -> onEvent(GameUiEvent.ChangeAutoPlayDelay(setting)) },
            )

            // 대국 진행 중(게임 종료 전)에는 바둑판 크기·접바둑을 바꿀 수 없다 — 게임 메뉴
            // ([ExpandedGameMenuSection])와 동일한 게이팅. 여기는 대국 설정 로비와 달리
            // "항상 대국 시작 전"이 아니라(뒤로 가기로 진행 중인 대국을 둔 채 홈→설정으로도
            // 올 수 있음) 로비의 canChangeBoardSize/canChangeHandicap=true를 그대로 쓰면 안 된다.
            if (gameSetupUxMode == GameSetupUxMode.Compact) {
                CompactScoringAndBoardSettingsPanel(
                    ruleset = screenState.gameState.ruleset,
                    boardSize = screenState.gameState.boardSize,
                    handicapCount = screenState.handicapCount,
                    komi = screenState.gameState.komi,
                    onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
                    onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) },
                    onHandicapCountChange = { count -> onEvent(GameUiEvent.ChangeHandicapCount(count)) },
                    onKomiChange = { komi -> onEvent(GameUiEvent.ChangeKomi(komi)) },
                )
            } else {
                ScoringAndBoardSettingsPanel(
                    ruleset = screenState.gameState.ruleset,
                    boardSize = screenState.gameState.boardSize,
                    handicapCount = screenState.handicapCount,
                    komi = screenState.gameState.komi,
                    canChangeRuleset = true,
                    canChangeBoardSize = screenState.isGameEnded,
                    canChangeHandicap = screenState.isGameEnded,
                    canChangeKomi = true,
                    onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
                    onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) },
                    onHandicapCountChange = { count -> onEvent(GameUiEvent.ChangeHandicapCount(count)) },
                    onKomiChange = { komi -> onEvent(GameUiEvent.ChangeKomi(komi)) },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()

            // 로그인 기능 자체가 꺼져 있으면(FeatureFlags.isLoginEnabled = false) 계정 섹션을
            // 통째로 숨긴다 — 로그인 수단이 하나도 없는데 "게스트로 이용 중입니다. 로그인하면..."
            // 안내만 남아있으면 존재하지 않는 기능을 홍보하는 셈이 된다.
            if (FeatureFlags.isLoginEnabled) {
                Text(
                    text = strings.settingsAccountSectionTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )

                Text(
                    text = when (authState.provider) {
                        AuthProvider.Google -> strings.settingsGoogleStatusMessage
                        AuthProvider.Email -> strings.settingsEmailStatusMessage
                        else -> strings.settingsGuestStatusMessage
                    },
                    fontSize = 13.sp,
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
            }

            if (FeatureFlags.isLoginEnabled && !hasRealAccount) {
                SocialLoginButton(
                    label = strings.continueWithGoogle,
                    leadingGlyph = "G",
                    glyphColor = GoogleBrandBlue,
                    onClick = { signInWithGoogle() },
                )
            }

            if (FeatureFlags.isLoginEnabled) {
                SocialLoginButton(
                    label = strings.continueWithApple,
                    leadingGlyph = "🍎",
                    onClick = { Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show() },
                )
            }

            if (FeatureFlags.isLoginEnabled && !hasRealAccount) {
                SocialLoginButton(
                    label = strings.continueWithEmail,
                    leadingGlyph = "✉",
                    onClick = { showEmailDialog = true },
                )
            }

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

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.settingsDevGameSetupUxToggleTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = strings.settingsDevGameSetupUxToggleSubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Switch(
                        checked = gameSetupUxMode == GameSetupUxMode.Compact,
                        onCheckedChange = { checked ->
                            gameSetupUxMode = if (checked) GameSetupUxMode.Compact else GameSetupUxMode.Simple
                            preferencesStore.save(preferencesStore.load().copy(gameSetupUxMode = gameSetupUxMode))
                        },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = strings.appTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${strings.settingsVersionLabel} ${BuildConfig.VERSION_NAME} · " +
                            "${strings.settingsBuildTimeLabel} ${BuildConfig.BUILD_TIME}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.settingsPrivacyPolicyLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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
