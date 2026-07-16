package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                canChangeRuleset = true,
                canChangeBoardSize = true,
                canChangeHandicap = true,
                onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
                onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) },
                onHandicapCountChange = { count -> onEvent(GameUiEvent.ChangeHandicapCount(count)) },
            )

            // [3] 50% 비율 축소 실시간 바둑판 프리뷰
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "보드 미리보기 (Board Preview)",
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

        // [4] 하단 대국 시작하기 버튼 영역
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
