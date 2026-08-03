package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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

            // [3] 프리미엄 모드 카드 — 비활성 상태면 탭해서 업셀 팝업(광고/결제/닫기)을 연다.
            // 여기서 활성화를 깜빡하고 넘어가도, 인게임에서 잠긴 버튼을 탭하면 같은 팝업이
            // 다시 뜨는 폴백이 있다(GamePlaySection/KaTrainUxPanels).
            PremiumModeCard(
                isActive = premium.isActive,
                onClick = { if (!premium.isActive) showPremiumUpsellDialog = true },
            )

            // [4] 50% 비율 축소 실시간 바둑판 프리뷰
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

        // [5] 하단 대국 시작하기 버튼 영역
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
 * 대국 설정 화면에서 프리미엄 모드를 선택할 수 있는 카드. 활성화된 상태에서는 안내만
 * 표시하고(비활성화는 지원하지 않음 — [PremiumUiState]에 해제 API가 없다), 비활성 상태에서는
 * 탭하면 [onClick]으로 업셀 팝업을 연다.
 */
@Composable
private fun PremiumModeCard(isActive: Boolean, onClick: () -> Unit) {
    val strings = LocalUiStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .let { rowModifier -> if (isActive) rowModifier else rowModifier.clickable(onClick = onClick) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = strings.premiumModeTitle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isActive) strings.premiumModeActiveSubtitle else strings.premiumModeInactiveSubtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = if (isActive) "✓" else "›",
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}
