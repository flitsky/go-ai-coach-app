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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.wipeToFreshInstall
import com.worksoc.goaicoach.application.preferences.isBoardSetupLockedDuringGame
import com.worksoc.goaicoach.application.auth.AuthClientPort
import com.worksoc.goaicoach.application.auth.AuthProvider
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.persistence.DeveloperModeStore
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    val scope = rememberCoroutineScope()
    // ⚠️ **저장된 대국도 함께 본다**(백로그 #75). 앱을 다시 켠 직후에는 저장된 대국이 아직
    // `screenState`로 올라오지 않아 둔 수가 0으로 보인다 — 그때 잠그지 않으면 **껐다 켜는 것만으로
    // 잠금이 우회된다**(2026-09-05 실기에서 실제로 밟았다).
    // ⚠️ 인자로 받지 않고 여기서 읽는 이유: `GoCoachApp.kt`가 라인 예산 **880/880**이라 여유가
    // 정확히 0이다(함정 3번). 이 화면은 이미 `context`로 저장소를 만들고 있어 자리가 맞다.
    // ⚠️ `remember`로 한 번만 읽는다 — 컴포지션마다 읽으면 스크롤할 때마다 디스크를 때린다.
    val resumableSavedGame = remember(context) { GameSessionStore(context).load()?.isResumable == true }
    val developerModeStore = remember(context) { DeveloperModeStore(context) }
    var isDeveloperModeEnabled by remember { mutableStateOf(developerModeStore.isEnabled()) }
    // ⚠️ **2차는 저장하지 않는다**(백로그 #77). 1차와 달리 `DeveloperModeStore`에 남기지 않으므로
    // 화면을 벗어나거나 앱을 다시 켜면 꺼진다 — 한 번 켠 기기가 영구히 열린 상태로 남지 않게
    // 하는 것이 이 항목의 안전장치 중 하나다.
    var isAdvancedDeveloperModeEnabled by remember { mutableStateOf(false) }
    var showDiagnosticLog by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableStateOf(0) }
    // 개발자 모드 진입/해제 확인 팝업(백로그 #99). 저장하지 않는다 — 화면을 벗어나면 닫힌다.
    var showDeveloperModeOptIn by remember { mutableStateOf(false) }
    var showDeveloperModeOptOut by remember { mutableStateOf(false) }
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

            // 룰 및 바둑판 세팅 패널. 레이아웃 선택지는 #73이 없앴고, 심플 레이아웃 자체는 #76이 지웠다.
            //
            // ⚠️ **진행 중인 대국이 있으면 판 크기·접바둑이 잠긴다**(#75, 2026-09-05 사용자 결정).
            // 잠그기 전에는 **조작은 되는데 진행 중 대국은 안 바뀌는** 상태였다(실기로 확인했다) —
            // 설정에는 19x19라고 쓰여 있는데 이어서 여는 대국은 13x13이라 어긋나 보였다.
            // 바뀌던 것은 *다음 대국의 기본값*뿐이었고, 그건 **로비에서 하면 될 일**이라는 것이
            // 사용자 판단이다. 그래서 여기서만 잠그고 로비는 그대로 둔다.
            // ⚠️ 조건을 여기서 인라인으로 쓰지 말 것 — `isGameEnded` 하나로 보면 **대국을 한 번도
            // 하지 않은 사용자에게도 잠긴다**(시작한 적이 없으면 끝난 적도 없다).
            CompactScoringAndBoardSettingsPanel(
                ruleset = screenState.gameState.ruleset,
                boardSize = screenState.gameState.boardSize,
                handicapCount = screenState.handicapCount,
                komi = screenState.gameState.komi,
                onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
                onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) },
                onHandicapCountChange = { count -> onEvent(GameUiEvent.ChangeHandicapCount(count)) },
                onKomiChange = { komi -> onEvent(GameUiEvent.ChangeKomi(komi)) },
                canChangeBoardShape = !isBoardSetupLockedDuringGame(
                    moveCount = screenState.gameState.moves.size,
                    isGameEnded = screenState.isGameEnded,
                    hasResumableSavedGame = resumableSavedGame,
                ),
            )

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
                        // ⚠️ **다시 `clickable`이다 — 홀드 감지를 걷어냈다**(2026-09-04, #84).
                        // #77이 여기에 3초 홀드를 얹은 것은 2차 진입을 숨기기 위해서였는데,
                        // **세로 스크롤 안에서 3초 홀드는 신뢰할 수 없다.** `waitForUpOrCancellation()`은
                        // 다른 핸들러가 제스처를 가져가면 3초 전에 `null`을 돌려주고, 그러면
                        // 어느 분기에도 걸리지 않아 **조용히 아무 일도 일어나지 않는다.**
                        // `down`을 소비해도 이후 MOVE 경쟁은 막지 못해서 실기에서 계속 샜다.
                        // · 2차 진입은 **'빌드 정보' 행 10탭**으로 옮겼다(아래). 탭은 터치 슬롭을
                        //   넘기 전에 끝나므로 이 경쟁을 **아예 겪지 않는다.**
                        // · 홀드가 없어지자 `clickable`의 원래 문제(누른 시간과 무관하게 릴리즈에서
                        //   onClick을 부른다)도 문제가 아니게 됐다 — 구분할 것이 없다.
                        modifier = Modifier.clickable {
                            if (isDeveloperModeEnabled) return@clickable
                            versionTapCount++
                            val remainingTaps = DeveloperModeTapsRequired - versionTapCount
                            if (remainingTaps <= 0) {
                                // ⚠️ **여기서 곧바로 켜지 않는다**(백로그 #99). 켜는 순간 이 기기는
                                // "데이터가 3시간마다 버려지는 기기"가 되므로, **사용자가 그 사실을
                                // 알고 고르게** 한다. 10탭은 이제 "열겠는가"를 묻는 문이다.
                                versionTapCount = 0
                                showDeveloperModeOptIn = true
                            } else if (remainingTaps <= DeveloperModeTapCountdownThreshold) {
                                Toast.makeText(
                                    context,
                                    strings.settingsDeveloperModeCountdownMessage(remainingTaps),
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                // ⚠️ 이 섹션은 **자기 상태를 스스로 갖는다**(#102). 여기서 넘기는 셋은 전부
                // 이 섹션 **바깥에 사는 것**들이다 — 2차 활성 여부(해제 팝업이 되돌린다),
                // 진단 로그 팝업, 개발자 모드 끄기 요청.
                // ⚠️ **새 개발자 컨트롤을 이 화면에 직접 넣지 말 것** — `DeveloperTestSection.kt`로.
                DeveloperTestSection(
                    isAdvancedDeveloperModeEnabled = isAdvancedDeveloperModeEnabled,
                    onAdvancedEnabledChange = { enabled -> isAdvancedDeveloperModeEnabled = enabled },
                    onShowDiagnosticLog = { showDiagnosticLog = true },
                    onRequestDeveloperModeOff = { showDeveloperModeOptOut = true },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ⚠️ **스크롤 Column 밖, 다른 다이얼로그들과 형제 위치에 emit한다**(백로그 #79).
    // 스크롤 안에 두면 별도 윈도우인데도 그 자리에 레이아웃 슬롯을 하나 차지한다.
    // ⓐ **진입 확인**(백로그 #99). 켜는 순간 이 기기는 "데이터가 버려지는 기기"가 되므로,
    // 그 사실을 **켜기 전에** 말한다. 확인을 눌러야 비로소 켜진다.
    if (showDeveloperModeOptIn) {
        AlertDialog(
            onDismissRequest = { showDeveloperModeOptIn = false },
            title = { Text(strings.settingsDeveloperModeOptInTitle) },
            text = { Text(strings.settingsDeveloperModeOptInMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeveloperModeOptIn = false
                        isDeveloperModeEnabled = true
                        developerModeStore.setEnabled(true)
                        // ⚠️ **기준 시각을 반드시 심는다** — 없으면 주기 초기화가 **조용히 동작하지
                        // 않는다**(`DeveloperModeResetPolicy`가 `null`에서는 절대 초기화하지 않는다).
                        developerModeStore.markResetBaseline(System.currentTimeMillis())
                        Toast.makeText(
                            context,
                            strings.settingsDeveloperModeEnabledMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) { Text(strings.settingsDeveloperModeOptInConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showDeveloperModeOptIn = false }) { Text(strings.cancel) }
            },
        )
    }

    // ⓑ **해제 확인**(백로그 #99). 끄기는 단순히 플래그를 내리는 것이 아니라 **최초 설치 상태로
    // 되돌리는 것**이다(사용자 확정) — 그래서 무슨 일이 일어나는지 먼저 말한다.
    if (showDeveloperModeOptOut) {
        AlertDialog(
            onDismissRequest = { showDeveloperModeOptOut = false },
            title = { Text(strings.settingsDeveloperModeOffTitle) },
            text = { Text(strings.settingsDeveloperModeOffMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeveloperModeOptOut = false
                        // ⚠️ **주기 초기화(ⓒ)와 같은 함수로 수렴한다** — 둘은 같은 일이라
                        // 나눠 쓰면 한쪽만 고쳐진다.
                        wipeToFreshInstall(context)
                        isDeveloperModeEnabled = false
                        isAdvancedDeveloperModeEnabled = false
                        Toast.makeText(
                            context,
                            strings.settingsDeveloperModeOffDoneMessage,
                            Toast.LENGTH_LONG,
                        ).show()
                        onBackClick()
                    },
                ) { Text(strings.settingsDeveloperModeOffAction) }
            },
            dismissButton = {
                TextButton(onClick = { showDeveloperModeOptOut = false }) { Text(strings.cancel) }
            },
        )
    }

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
