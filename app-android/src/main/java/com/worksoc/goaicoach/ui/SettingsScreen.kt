package com.worksoc.goaicoach.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeout
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
import androidx.compose.ui.platform.LocalDensity
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.runConsumableGrant
import com.worksoc.goaicoach.persistence.ConsumableInventoryStore
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterShardSet
import com.worksoc.goaicoach.persistence.BotCollectionStore
import com.worksoc.goaicoach.application.attendance.isRewardedTier
import com.worksoc.goaicoach.application.attendance.runAttendanceDevDayRewind
import com.worksoc.goaicoach.persistence.AttendanceStore
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.auth.AuthProvider
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.preferences.GameSetupUxMode
import com.worksoc.goaicoach.persistence.DeveloperModeStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import kotlinx.coroutines.launch

// AdMob·Play Billing 등 Play Console 필수 제출 항목(개인정보처리방침 URL)과 동일한 문서를
// 가리킨다 — Play Console "앱 콘텐츠" 폼에도 이 URL을 별도로 등록해야 한다(코드 배선과
// 무관한 콘솔 설정, launch-plan/README.md 참고).
private const val PRIVACY_POLICY_URL = "https://rezen.dev/go-ai-coach/privacy/"

// 버전 텍스트를 이만큼 두드리면 개발자 모드가 활성화된다.
private const val DeveloperModeTapsRequired = 10

// 활성화까지 남은 탭 수가 이 값 이하로 줄어들면 카운트다운 토스트를 보여준다(첫 탭부터
// 매번 토스트를 띄우면 실수로 두 번 눌렀을 때도 소란스러워 초반 탭은 조용히 넘어간다).
private const val DeveloperModeTapCountdownThreshold = 5

/**
 * 2차(고급) 개발자 테스트를 여는 **길게 누르기** 시간(백로그 #77).
 *
 * ⚠️ **`combinedClickable`의 `onLongClick`으로는 이 값을 표현할 수 없다** — 그쪽은
 * `viewConfiguration.longPressTimeoutMillis`(약 500ms)에 하드와이어돼 있다. 그래서 아래
 * 버전 텍스트는 `pointerInput` 제스처 하나로 **탭과 길게 누르기를 함께** 판정한다.
 */
private const val AdvancedDeveloperModeHoldMillis = 3_000L

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
 * "개발자 테스트" 섹션은 평소에는 숨겨져 있고, 버전 정보 카드의 버전 텍스트를 10번
 * 두드려야만(Debug/Release 빌드 공통) 나타난다 — 실제 결제 SDK 연동 전까지 프리미엄
 * 구매 상태를 자유롭게 켜고 끄며 QA할 수 있는 자리다. 릴리스 빌드에서도 접근 가능해진
 * 만큼, 우연히 10번 두드릴 가능성은 낮다고 보고 진입 장벽만으로 게이팅한다.
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
    val developerModeStore = remember(context) { DeveloperModeStore(context) }
    var isDeveloperModeEnabled by remember { mutableStateOf(developerModeStore.isEnabled()) }
    // ⚠️ **2차는 저장하지 않는다**(백로그 #77). 1차와 달리 `DeveloperModeStore`에 남기지 않으므로
    // 화면을 벗어나거나 앱을 다시 켜면 꺼진다 — 한 번 켠 기기가 영구히 열린 상태로 남지 않게
    // 하는 것이 이 항목의 안전장치 중 하나다.
    var isAdvancedDeveloperModeEnabled by remember { mutableStateOf(false) }
    var showDiagnosticLog by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableStateOf(0) }
    val consumables = LocalConsumableUiState.current
    val bots = LocalBotCharacterUiState.current
    // 개발자 2차의 프리미엄 부제가 읽는 값. `null`이면 지금 꺼져 있다는 뜻이다.
    // ⚠️ 초 단위로 갱신하지 않는다 — 화면을 다시 그릴 때만 맞다. 만료를 정확히 재는 것은
    // `PremiumExpiryAutoDisableEffect`의 몫이고, 여기 숫자는 "대략 얼마 남았나"의 안내다.
    val premiumRemainingMinutes = premium.adGrantExpiresAtMillis
        ?.minus(System.currentTimeMillis())
        ?.takeIf { it > 0L }
        ?.let { millis -> (millis / 60_000L).toInt() }
    // 개발자 2차의 출석 부제가 읽는 값.
    // ⚠️ **버튼을 누른 직후에 저장소를 읽으면 한 일차 뒤처진다** — 되감기는 표시만 지우고 실제
    // 증가는 `AttendanceRewardClaimDialog`의 effect가 하기 때문이다. 그래서 증가를 아는 쪽이
    // 알려 주는 값(`lastCheckedInDay`)을 우선 쓰고, 아직 없으면 저장소를 읽는다.
    val attendanceDay = AttendanceClaimReplaySignal.lastCheckedInDay
        ?: remember(context) { AttendanceStore(context).load().attendanceCount }
    // 백로그 #53 — 화면이 열릴 때 한 번만 묻고, 결과가 오면 그때 아래 줄이 나타난다.
    // 실패는 조용히 넘어간다(`AppUpdateRow`의 폴백 경로).
    val updateStatus = rememberAppUpdateStatus()
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
                    val message = if (error is FirebaseAuthWeakPasswordException) {
                        strings.emailSignInWeakPasswordMessage
                    } else {
                        strings.emailSignInFailedMessage
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
                        // ⚠️ **`clickable`이 아니라 제스처 하나로 탭과 길게 누르기를 함께 판정한다**
                        // (백로그 #77). `clickable`은 **누른 시간과 무관하게** 손을 떼는 순간
                        // onClick을 부르므로, 그 위에 홀드 감지를 얹으면 3초를 누른 사용자가
                        // "탭 한 번"으로도 세어진다. `combinedClickable`도 답이 아니다 —
                        // `onLongClick`이 약 500ms에 하드와이어돼 있어 3초를 표현할 수 없다.
                        // ⚠️ 그리고 감지기를 **두 개 겹치면 한쪽이 굶는다**(`GoBoard.kt`가 같은
                        // 이유로 단일 `pointerInput`을 쓴다) — 그래서 하나로 합쳤다.
                        modifier = Modifier.pointerInput(isDeveloperModeEnabled) {
                            awaitEachGesture {
                                // ⚠️ **down을 소비하지 않는다.** 이 텍스트는 세로 스크롤 안에 있어,
                                // 소비하면 여기서 시작한 드래그로 화면을 못 굴린다. 스크롤이
                                // 제스처를 가져가면 아래 `waitForUpOrCancellation()`이 null을 준다.
                                awaitFirstDown(requireUnconsumed = false)
                                var heldPastThreshold = false
                                val releasedEarly = try {
                                    withTimeout(AdvancedDeveloperModeHoldMillis) { waitForUpOrCancellation() }
                                } catch (_: PointerEventTimeoutCancellationException) {
                                    heldPastThreshold = true
                                    null
                                }
                                when {
                                    heldPastThreshold -> {
                                        // 손을 뗄 때까지 기다린다 — 떼는 순간을 탭으로 세지 않기 위함이다.
                                        waitForUpOrCancellation()
                                        onVersionLongHold(
                                            isDeveloperModeEnabled = isDeveloperModeEnabled,
                                            isAdvancedEnabled = isAdvancedDeveloperModeEnabled,
                                            onEnable = { isAdvancedDeveloperModeEnabled = true },
                                            context = context,
                                            strings = strings,
                                        )
                                    }
                                    // null이면 스크롤 등 다른 제스처가 가져갔다 — 탭이 아니다.
                                    releasedEarly != null -> {
                                        if (isDeveloperModeEnabled) return@awaitEachGesture
                                        versionTapCount++
                                        val remainingTaps = DeveloperModeTapsRequired - versionTapCount
                                        if (remainingTaps <= 0) {
                                            isDeveloperModeEnabled = true
                                            developerModeStore.setEnabled(true)
                                            Toast.makeText(
                                                context,
                                                strings.settingsDeveloperModeEnabledMessage,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else if (remainingTaps <= DeveloperModeTapCountdownThreshold) {
                                            Toast.makeText(
                                                context,
                                                strings.settingsDeveloperModeCountdownMessage(remainingTaps),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            }
                        },
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
                    // 백로그 #53. ⚠️ **위 버전 텍스트 옆에 붙이지 말 것** — 그 텍스트는 10번
                    // 두드리면 개발자 모드가 켜지는 숨은 제스처를 갖고 있어, 업데이트 버튼을
                    // 곁에 두면 오탭 하나가 개발자 모드를 연다. 링크를 사이에 두고 맨 아래에 둔다.
                    AppUpdateRow(status = updateStatus)
                }
            }

            // ## 개발자 테스트는 **두 단**이다(백로그 #77, 2026-09-03 사용자 결정)
            //
            // **1차(기본)** — 버전 텍스트 **10탭**. `DeveloperModeStore`에 저장되고 **release
            // 빌드에도 실린다.** 그래서 여기에는 **권한을 만들지 않는 것만** 둔다: 읽기 전용
            // 정보와, 이미 출석 보상으로 흔하게 들어오는 1회권 한 장 지급 정도.
            //
            // **2차(고급)** — 1차가 켜진 상태에서 버전 텍스트를 **3초 이상 길게** 누른다.
            // 저장하지 않으므로 화면을 벗어나면 꺼지고, **`BuildConfig.DEBUG`로 감싸** release·
            // playInternal에는 아예 들어가지 않는다. 프리미엄 부여·출석일 조작처럼 **권한을
            // 무료로 찍어내는** 것들이 여기 온다.
            //
            // ⚠️ **길게 누르기는 은닉이지 경계가 아니다** — 제스처를 아는 사람은 그대로 한다.
            // 실제 안전장치는 `BuildConfig.DEBUG` 하나뿐이므로, **새 컨트롤을 어느 단에 둘지는
            // 라벨이 아니라 "그것이 무엇을 저장하는가"로 정한다.** 저장소에 권한(프리미엄·출석·
            // 컬렉션·소모품 대량)을 쓰면 2차다.
            if (isDeveloperModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.settingsDevTierBasicTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )

                // 읽기 전용 ① — **어느 빌드를 보고 있는가.** 실기에서 이걸 못 봐서 치른 값이
                // 있다(#47, `launch-plan/README.md` §0 B-3의 808 vs 810). 버전만으로는
                // 빌드타입과 광고 ID 종류를 알 수 없다.
                DeveloperInfoRow(
                    title = strings.settingsDevBuildInfoTitle,
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                        "${BuildConfig.BUILD_TYPE} · ${if (BuildConfig.USE_TEST_ADS) "test ads" else "REAL ADS"}",
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 읽기 전용 ② — **지금 글꼴 배율.** #64가 배율 관련 잘림을 네 자리에서 밟았는데,
                // 재현할 때마다 시스템 설정을 왕복해야 했다.
                DeveloperInfoRow(
                    title = strings.settingsDevFontScaleTitle,
                    value = "×${LocalDensity.current.fontScale}",
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1회권 한 장 지급 — 출석 1일차가 30장을 주므로 한 장은 경제에 영향이 없다.
                // ⚠️ **`consumables.refresh()`를 반드시 함께 부른다.** `runConsumableGrant`는
                // 저장소에 **직접** 쓰고 화면 사본은 나가는 것만 알기 때문에(그 KDoc이 못박고
                // 있다) 빠뜨리면 마이 페이지가 옛 재고를 계속 보여준다.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.settingsDevGrantTicketTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = strings.settingsDevGrantTicketSubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    TextButton(
                        onClick = {
                            val store = ConsumableInventoryStore(context)
                            listOf(
                                ConsumableCatalog.EvalOnce,
                                ConsumableCatalog.TopMovesOnce,
                                ConsumableCatalog.PremiumOnce,
                            ).forEach { item -> runConsumableGrant(item, amount = 1, consumableStore = store) }
                            consumables.refresh()
                        },
                    ) {
                        Text(strings.settingsDevGrantAction)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 읽기 전용 ③ — **진단 로그를 앱 안에서 본다**(백로그 #79). `DiagnosticEventLog`가
                // 계속 쌓고 있는데 앱에서 볼 길이 없어, 폰만 손에 있으면 확인이 불가능했다.
                // ⚠️ 화면을 새로 만들지 않고 다이얼로그로 둔 이유는 셸 라인 예산이다(함정 3번).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.settingsDevDiagnosticLogTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = strings.settingsDevDiagnosticLogSubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    TextButton(onClick = { showDiagnosticLog = true }) {
                        Text(strings.settingsDevDiagnosticLogOpenAction)
                    }
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

                // ⚠️ **`BuildConfig.DEBUG`가 이 배치의 유일한 실제 경계다**(위 머리말 참고).
                // `DEBUG`는 `static final boolean`이라 release·playInternal에서는 컴파일 시점에
                // `false`로 접히고, 이 블록은 도달 불가가 된다.
                // · ⚠️ **다만 문구는 바이너리에 남는다** — 2026-09-03 release APK의 dex에서
                //   실제로 확인했다. `UiStringsKo` 등이 **데이터 클래스 생성자 인자**로 모든 문구를
                //   항상 만들기 때문에, 분기가 죽어도 문자열 상수는 살아 있다.
                //   **경로가 없는 것과 이름이 안 보이는 것은 다르다** — 이 배치가 보장하는 것은
                //   앞의 것뿐이고, APK를 뜯으면 2차의 존재는 드러난다. 그것으로 충분하다는 것이
                //   이 설계의 전제다(길게 누르기는 애초에 은닉이지 경계가 아니다).
                if (BuildConfig.DEBUG && isAdvancedDeveloperModeEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = strings.settingsDevTierAdvancedTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )

                    // ⚠️ **영구 활성화 토글이 여기 있었다 — 버튼으로 바꿨다**(백로그 #78,
                    // 2026-09-03 사용자 결정). 그 토글은 `PremiumSource.Purchase`를 저장소에
                    // **영구 기록**했고, #26이 프리미엄을 월간 구독으로 옮기면서 판정 기준이
                    // *"영구히 샀는가"* 가 아니라 **"지금 유효한가"** 로 바뀐다 — 사라질 상태를
                    // 계속 테스트하게 두지 않는다.
                    // ⚠️ **토글이 아니라 버튼인 이유**: 1시간 부여는 껐다 켜는 상태가 아니라
                    // **사건**이다. Switch로 두면 "끄기"가 무엇을 뜻하는지 정의되지 않는다.
                    // ✅ **부수 이득**: 이 버튼은 광고를 **띄우지 않고** 보상 경로만 밟으므로,
                    // 실기에서 실제 AdMob 노출(자기 노출 = 정책 위반)을 만들지 않는다.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.settingsDevAdGrantTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = strings.settingsDevAdGrantSubtitle(premiumRemainingMinutes),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        TextButton(onClick = premium.simulateAdGrant) {
                            Text(strings.settingsDevAdGrantAction)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ⚠️ **2차인 이유**: 조각은 **광고 시청분**이다(#11) — 여기서 채워 주는 것은
                    // 곧 광고를 건너뛰고 캐릭터를 얻는 무료 경로다. 1차(release에 실림)에 두면
                    // 그것이 그대로 출시된다.
                    // ⚠️ **획득으로 바로 넘기는 버튼은 없다** — `runBotCharacterShardSet`이
                    // `required - 1`로 자른다. 캐릭터를 직접 심으면 유령 보상(#68)이 도달
                    // 가능해지고 7·28일차 대체 보상이라는 미해결 결정을 끌어온다. "한 개 남기기"
                    // 뒤에 광고를 한 번 보면 획득 루틴이 끝까지 밟힌다.
                    BotCharacterCatalog.shardPathCharacters().forEach { character ->
                        val required = (character.unlockSource as? BotUnlockSource.AdShards)?.required ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${strings.settingsDevShardTitle} · ${strings.botCharacterName(character)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (bots.isAvailable(character)) {
                                        strings.botCharacterLabel(character)
                                    } else {
                                        "${bots.shardsFor(character)} / $required"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            TextButton(
                                onClick = {
                                    runBotCharacterShardSet(character, required - 1, BotCollectionStore(context))
                                    bots.refresh()
                                },
                            ) {
                                Text(strings.settingsDevShardAlmostAction)
                            }
                            TextButton(
                                onClick = {
                                    runBotCharacterShardSet(character, 0, BotCollectionStore(context))
                                    bots.refresh()
                                },
                            ) {
                                Text(strings.settingsDevShardClearAction)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ⚠️ **2차인 이유**: 이 버튼은 누를 때마다 1회권·캐릭터·무르기 영구 해금을
                    // 실질적으로 **무료로 찍어낸다.** 기존 프리미엄 토글보다 악용 가치가 크다.
                    // ⚠️ **부제에 지금 일차를 적는 것이 중요하다.** 8~13·15~20·22~27일차는
                    // `isRewardedTier`가 false라 **원래 팝업이 안 뜬다.** 5·6일차도 그 조각
                    // 캐릭터를 이미 다 모았으면 회차가 통째로 걸러진다. 숫자를 안 보여주면
                    // "버튼이 고장났다"로 오진하게 되는 자리다.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.settingsDevAttendanceTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = strings.settingsDevAttendanceSubtitle(
                                    current = attendanceDay,
                                    next = attendanceDay + 1,
                                    nextIsRewarded = isRewardedTier(attendanceDay + 1),
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        TextButton(
                            onClick = {
                                // 되감기만으로는 팝업이 안 뜬다 — 신호까지 올려야 계산이 다시 돈다
                                // (`AttendanceClaimReplaySignal`의 KDoc 참고).
                                runAttendanceDevDayRewind(AttendanceStore(context))
                                AttendanceClaimReplaySignal.request()
                            },
                        ) {
                            Text(strings.settingsDevAttendanceAdvanceAction)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ⚠️ **스크롤 Column 밖, 다른 다이얼로그들과 형제 위치에 emit한다**(백로그 #79).
    // 스크롤 안에 두면 별도 윈도우인데도 그 자리에 레이아웃 슬롯을 하나 차지한다.
    if (showDiagnosticLog) {
        DiagnosticLogDialog(context = context, onDismiss = { showDiagnosticLog = false })
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

/**
 * 버전 텍스트를 3초 이상 눌렀을 때(백로그 #77) — **2차(고급) 개발자 테스트**를 연다.
 *
 * ⚠️ **`BuildConfig.DEBUG`가 유일한 안전장치다.** 길게 누르기는 *은닉*이지 *경계*가 아니다 —
 * 제스처를 아는 사람은 그대로 실행한다. 2차에 붙는 것들(프리미엄 부여, 출석일 조작, 조각 수
 * 조절)은 **권한을 무료로 찍어내는** 것들이라, release·playInternal에서는 아예 존재하지 않아야
 * 한다. `isMinifyEnabled`가 켜진 그 둘에서는 R8이 이 분기를 통째로 지운다.
 * · `BuildConfig.DEBUG`는 **debug와 friend에서 true**, **playInternal과 release에서 false**다
 *   (`playInternal`만 `isDebuggable = false`로 되돌린다 — `build.gradle.kts`).
 *
 * ⚠️ **release에서는 토스트도 띄우지 않는다.** 안내를 띄우면 2차의 존재 자체를 광고하는 셈이라,
 * 조건이 맞지 않으면 **아무 일도 일어나지 않는 것**이 맞다.
 */
private fun onVersionLongHold(
    isDeveloperModeEnabled: Boolean,
    isAdvancedEnabled: Boolean,
    onEnable: () -> Unit,
    context: Context,
    strings: UiStrings,
) {
    if (!BuildConfig.DEBUG) return
    // 1차가 먼저다 — 10탭을 거치지 않은 사람에게 2차가 열리면 1차의 의미가 없어진다.
    if (!isDeveloperModeEnabled || isAdvancedEnabled) return
    onEnable()
    Toast.makeText(context, strings.settingsAdvancedDeveloperModeEnabledMessage, Toast.LENGTH_SHORT).show()
}

/**
 * 개발자 테스트 1차의 **읽기 전용 한 줄**(백로그 #77). 아무것도 쓰지 않으므로 release에 실려도
 * 무해하고, 그래서 1차에 둔다.
 *
 * ⚠️ 고정 `dp` 높이를 주지 않는다 — 글꼴 배율이 커지면 상자가 함께 자라야 한다(함정 9번).
 * 기존 두 컨트롤과 같은 `Row` + `Column(weight(1f))` 골격이라 배율에 저절로 따라간다.
 */
@Composable
private fun DeveloperInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
