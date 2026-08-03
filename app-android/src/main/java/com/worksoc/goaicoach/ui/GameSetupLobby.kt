package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

            // [2] 룰 및 바둑판 세팅 패널 (계가, 크기, 접바둑)
            ScoringAndBoardSettingsPanel(
                ruleset = screenState.gameState.ruleset,
                boardSize = screenState.gameState.boardSize,
                handicapCount = screenState.handicapCount,
                komi = screenState.gameState.komi,
                canChangeRuleset = true,
                canChangeBoardSize = true,
                canChangeHandicap = true,
                canChangeKomi = true,
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
            PremiumModeCard(
                isActive = premium.isActive,
                onClick = { if (!premium.isActive) showPremiumUpsellDialog = true },
            )

            Button(
                onClick = {
                    onEvent(GameUiEvent.StartConfiguredGame)
                    onStartMatch()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = strings.startMatchAction,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 대국 설정 화면 하단에 고정 노출되는 프리미엄 모드 카드 — 금색 그라디언트로 일반 설정
 * 항목들과 확실히 구분되는 시각적 아이덴티티를 준다([PremiumGoldGradient] 등 참고).
 * 활성화된 상태에서는 안내만 표시하고(비활성화는 지원하지 않음 — [PremiumUiState]에 해제
 * API가 없다), 비활성 상태에서는 탭하면 [onClick]으로 업셀 팝업을 연다.
 */
@Composable
private fun PremiumModeCard(isActive: Boolean, onClick: () -> Unit) {
    val strings = LocalUiStrings.current

    val cardModifier = if (isActive) {
        Modifier.background(PremiumGoldGradient, PremiumCardShape)
    } else {
        Modifier
            .background(PremiumGoldLight.copy(alpha = 0.18f), PremiumCardShape)
            .border(1.5.dp, PremiumGoldGradient, PremiumCardShape)
            .clickable(onClick = onClick)
    }

    // 제목에 활성/비활성 상태(동사)를 담고, 부제는 기능 나열만 하는 짧은 문구로 분리해
    // 카드가 항상 한 줄씩 2줄로만 표시되게 한다. maxLines/ellipsis는 그래도 기기 폭이나
    // 폰트 배율이 작을 때 3번째 줄로 밀리는 걸 막는 안전장치.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(cardModifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "👑", fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isActive) strings.premiumModeTitleActive else strings.premiumModeTitleInactive,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = if (isActive) Color.White else PremiumGoldDeep,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings.premiumModeFeatureList,
                fontSize = 12.sp,
                color = if (isActive) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isActive) "✓" else "›",
            color = if (isActive) Color.White else PremiumGold,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
    }
}
