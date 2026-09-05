package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import kotlinx.coroutines.delay

/**
 * 1 Depth: 대국 설정 화면 (Game Lobby Screen / Match Setup)
 * - 바둑판 크기, 접바둑 유무, AI 난이도, 계가 방식 등을 대국 시작 전 셋업하는 공간입니다.
 * - 중앙에 50% 축소된 실시간 보드 프리뷰를 노출하고, 하단에 "대국 시작하기" 버튼을 배치합니다.
 */
@Composable
internal fun GameSetupLobby(
    screenState: GameScreenState,
    onEvent: (GameUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onStartMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val scrollState = rememberScrollState()
    val premium = LocalPremiumUiState.current
    var showPremiumUpsellDialog by remember { mutableStateOf(false) }

    // 홈 화면에서는 더 이상 이 팝업을 강제로 띄우지 않는다 — 여기 대국 설정 화면에서
    // 사용자가 원할 때(아래 프리미엄 모드 카드 탭) 직접 열도록 한다.
    PremiumUpsellDialogHost(
        visible = showPremiumUpsellDialog,
        onDismiss = { showPremiumUpsellDialog = false },
    )

    // 광고 시청 기반 활성화의 남은 시간을 "대국 시작하기" 버튼에 실시간으로 보여주기 위한
    // 1초 tick. `GoCoachApp.kt`는 상태 훅 예산이 빠듯해 만료 시각만 넘겨주고, 실제 카운트
    // 다운 계산/갱신은 여기(예산 제약이 없는 화면)에서 자체적으로 돈다.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(premium.adGrantExpiresAtMillis) {
        val expiresAt = premium.adGrantExpiresAtMillis ?: return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            if (nowMillis >= expiresAt) break
            delay(1000)
        }
    }
    val remainingAdGrantMillis = premium.adGrantExpiresAtMillis?.minus(nowMillis)?.coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 로비 헤더 영역 (뒤로가기 + 타이틀)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = strings.close,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = strings.matchSetup,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // [1] 플레이어 설정 패널
            PlayerSetupPanel(
                state = screenState.playerSetupUi,
                enabled = true,
                onPlayerSetupChange = { setup -> onEvent(GameUiEvent.ChangePlayerSetup(setup)) },
                onAutoPlayDelayChange = { setting -> onEvent(GameUiEvent.ChangeAutoPlayDelay(setting)) },
            )

            // [2] 룰 및 바둑판 세팅 패널 (계가, 크기, 접바둑).
            // ⚠️ **레이아웃 선택지는 백로그 #73에서 없앴다** — `GameSetupUxMode`의 Simple 쪽은
            // 개발자 토글로만 닿을 수 있었고 기본값은 처음부터 Compact였다. 분기가 사라졌으니
            // 이 화면에서 저장소를 읽을 일도 없다(그 독립 경로가 함께 없어졌다).
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

            // [3] 50% 비율 축소 실시간 바둑판 프리뷰
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.boardPreview,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 50% 비율 축소 렌더링
                Row(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    GoBoard(
                        gameState = screenState.gameState,
                        candidateMoves = emptyList(), // 프리뷰이므로 탐색 추천수 미표시
                        moveReviews = emptyList(),
                        ownershipEstimate = null,
                        uxOptions = screenState.uxOptions.copy(showCoordinates = true), // 좌표 표시 강제 활성화해 시인성 보장
                        inputEnabled = false, // 터치 입력 차단
                        engineActivityIndicator = null,
                        modifier = Modifier.fillMaxWidth(),
                        tentativeMove = null,
                        onCoordinateTap = {}, // 빈 람다
                        isGameEnded = false,
                        isEngineBusy = false
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        // [4] 하단 고정 영역 — 프리미엄 모드 카드 + 대국 시작하기 버튼. 스크롤에 묻히지 않고
        // 시작 버튼 바로 위에 항상 보이도록 고정 영역에 배치한다("체크아웃 직전 업셀"과 같은 자리).
        // 비활성 상태면 탭해서 업셀 팝업(광고/결제/닫기)을 연다. 여기서 활성화를 깜빡하고
        // 넘어가도, 인게임에서 잠긴 버튼을 탭하면 같은 팝업이 다시 뜨는 폴백이 있다
        // (GamePlaySection/KaTrainUxPanels).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 프리미엄이 이미 활성 상태면 카드를 아예 없애고, 아래 "대국 시작하기" 버튼
            // 자체를 프리미엄 스타일로 표현하는 것으로 활성 상태를 알린다(중복 안내 방지).
            if (!premium.isActive) {
                PremiumModeCard(onClick = { showPremiumUpsellDialog = true })
            }

            // ⚠️ **엔진이 준비되기 전에는 시작할 수 없다**(백로그 #101 0단계).
            // 게이트가 없으면 `runStartConfiguredGame`이 AI 대국을 **조용히 로컬 2인 대국으로
            // 강등**한다(`!isEngineReady && targetMode != LocalTwoPlayer` 분기). 그런데
            // `playerSetup`은 HumanVsAi 그대로라 `canAcceptBoardInput`이 false가 되어,
            // 사용자는 **터치가 죽은 판** 앞에 앉는다. 강등 사유(`engine.message`)는 앱 어디에서도
            // 렌더되지 않아 **아무 설명도 없다.**
            // ⚠️ **지금은 준비 화면이 이 구간을 가려 도달 불가지만**, #101이 그 화면을 없애면
            // 신규 사용자의 첫 세 번의 탭(홈 → 대국 설정 → 시작)이 곧바로 이 경로가 된다.
            val engineReady = screenState.engine.isReady
            Button(
                enabled = engineReady,
                onClick = {
                    onEvent(GameUiEvent.StartConfiguredGame)
                    onStartMatch()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (premium.isActive) PremiumGold else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (premium.isActive) "👑 ${strings.startMatchAction}" else strings.startMatchAction,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    // 오른쪽 끝에 현재 프리미엄 상태를 보여준다 — 광고 시청(시간 기반)이면
                    // 남은 시간을 mm:ss로 카운트다운하고, 영구 구매면 시간 해제와 구분되도록
                    // ∞로 표시한다(둘 다 premium.isActive일 때만 렌더링되는 영역이라 별도
                    // isActive 체크는 없다).
                    if (premium.isActive) {
                        Surface(
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            Text(
                                text = remainingAdGrantMillis?.let(::formatRemainingTime) ?: "∞",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            // 잠긴 이유를 말한다 — 이유 없이 안 눌리는 버튼은 고장으로 읽힌다.
            if (!engineReady) {
                Text(
                    text = strings.engineNotReadyToStart,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** 남은 시간을 "M:SS" 형식으로 포맷한다(언어 무관 — 시계 표기라 별도 번역이 필요 없다). */
private fun formatRemainingTime(remainingMillis: Long): String {
    val totalSeconds = remainingMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * 대국 설정 화면 하단에 고정 노출되는 프리미엄 모드 활성화 카드 — 금색 그라디언트 보더로
 * 일반 설정 항목들과 확실히 구분되는 시각적 아이덴티티를 준다([PremiumGoldGradient] 등
 * 참고). 프리미엄이 이미 활성 상태면 호출부([GameSetupLobby])가 이 카드 자체를 렌더링하지
 * 않는다 — 그때는 "대국 시작하기" 버튼이 프리미엄 스타일로 바뀌는 것으로 충분하기 때문에,
 * 이 컴포저블은 "아직 비활성이라 탭하면 활성화할 수 있음" 한 가지 상태만 표현한다.
 */
@Composable
private fun PremiumModeCard(onClick: () -> Unit) {
    val strings = LocalUiStrings.current

    // 제목에 "활성화하기"라는 동사를 담고, 부제는 기능 나열만 하는 짧은 문구로 분리해
    // 카드가 항상 한 줄씩 2줄로만 표시되게 한다. maxLines/ellipsis는 그래도 기기 폭이나
    // 폰트 배율이 작을 때 3번째 줄로 밀리는 걸 막는 안전장치.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PremiumGoldLight.copy(alpha = 0.18f), PremiumCardShape)
            .border(1.5.dp, PremiumGoldGradient, PremiumCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "👑", fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.premiumModeTitle,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = PremiumGoldDeep,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings.premiumModeFeatureList,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "›",
            color = PremiumGold,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
    }
}
