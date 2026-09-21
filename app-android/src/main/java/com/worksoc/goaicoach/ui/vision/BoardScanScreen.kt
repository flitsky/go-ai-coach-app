package com.worksoc.goaicoach.ui.vision

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DefaultKomi
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import com.worksoc.goaicoach.ui.LocalPremiumUiState
import com.worksoc.goaicoach.ui.LocalUiStrings
import com.worksoc.goaicoach.ui.PremiumUpsellDialogHost
import com.worksoc.goaicoach.vision.AndroidBoardVisionScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 바둑판 스캔 및 분석 단계.
 */
internal enum class ScanStep {
    Capture,        // 사진 촬영 및 갤러리 선택
    PinAdjustment,  // 4점 코너 핀 조정 및 왜곡 보정
    Correction,     // 대화형 국면 확인, 터치 수정 및 AI 분석/대국 연계
}

/**
 * 백로그 #179 — 카메라로 바둑판 인식해 분석 (Board Scan & Analysis Screen)
 *
 * 전체 플로우:
 * 1. [CameraCaptureView]로 바둑판 촬영 (또는 갤러리 이미지 선택)
 * 2. [CornerPinAdjustmentOverlay]로 4점 꼭짓점 핀 수동 미세조정
 * 3. 온디바이스 [AndroidBoardVisionScanner]로 왜곡 보정(Perspective Warp) 및 돌 검출
 * 4. [BoardCorrectionEditor]에서 터치 편집, 흑/백 차례, 덤 설정
 * 5. KataGo 비동기 형세 분석 실행 및 '이 국면부터 대국 시작' 연계
 */
@Composable
internal fun BoardScanScreen(
    engineClient: EngineSessionClient,
    onBackClick: () -> Unit,
    onStartGameWithState: (GameState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(ScanStep.Capture) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedGameState by remember { mutableStateOf<GameState?>(null) }

    var isScanning by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var scoreEstimate by remember { mutableStateOf<ScoreEstimate?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showPremiumUpsellDialog by remember { mutableStateOf(false) }

    val premiumUiState = LocalPremiumUiState.current
    val strings = LocalUiStrings.current
    val scope = rememberCoroutineScope()

    // 단계별 뒤로가기 처리
    BackHandler {
        when (step) {
            ScanStep.Correction -> {
                step = ScanStep.PinAdjustment
                analysisResult = null
                scoreEstimate = null
            }
            ScanStep.PinAdjustment -> {
                step = ScanStep.Capture
                capturedBitmap = null
            }
            ScanStep.Capture -> {
                onBackClick()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (step) {
            ScanStep.Capture -> {
                CameraCaptureView(
                    onPhotoCaptured = { bitmap ->
                        capturedBitmap = bitmap
                        step = ScanStep.PinAdjustment
                    },
                    onClose = onBackClick,
                )
            }

            ScanStep.PinAdjustment -> {
                val bitmap = capturedBitmap
                if (bitmap != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CornerPinAdjustmentOverlay(
                            bitmap = bitmap,
                            initialBoardSize = BoardSize.Nineteen,
                            onCornersConfirmed = { corners, boardSize ->
                                isScanning = true
                                scope.launch {
                                    try {
                                        val scanner = AndroidBoardVisionScanner(bitmap)
                                        val detected = scanner.detectStones(corners, boardSize)
                                        detectedGameState = detected.toGameState()
                                        step = ScanStep.Correction
                                    } catch (e: Exception) {
                                        errorMessage = "바둑판 인식에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                                    } finally {
                                        isScanning = false
                                    }
                                }
                            },
                            onRetake = {
                                step = ScanStep.Capture
                                capturedBitmap = null
                            },
                        )

                        if (isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "온디바이스 돌 배치 인식 중...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    step = ScanStep.Capture
                }
            }

            ScanStep.Correction -> {
                val initialGameState = detectedGameState
                if (initialGameState != null) {
                    BoardCorrectionEditor(
                        initialGameState = initialGameState,
                        isAnalyzing = isAnalyzing,
                        analysisResult = analysisResult,
                        scoreEstimate = scoreEstimate,
                        onStartAnalysis = { stateToAnalyze ->
                            val access = premiumUiState.resolve(FeatureId.BoardScan)
                            if (access !is FeatureAccess.Allowed) {
                                showPremiumUpsellDialog = true
                            } else {
                                isAnalyzing = true
                                scope.launch {
                                    try {
                                        val estimate = withContext(Dispatchers.Default) {
                                            engineClient.estimateScoreForState(
                                                state = stateToAnalyze,
                                                profile = EngineProfile(
                                                    analysisLimit = AnalysisLimit(visits = 120, timeMillis = 3500)
                                                ),
                                                syncFirst = true,
                                            )
                                        }
                                        val analysis = withContext(Dispatchers.Default) {
                                            engineClient.analyzePosition(
                                                state = stateToAnalyze,
                                                limit = AnalysisLimit(visits = 120, timeMillis = 3500),
                                                searchMode = EngineSearchMode.JsonPositionAnalysis,
                                            )
                                        }
                                        scoreEstimate = estimate
                                        analysisResult = analysis
                                    } catch (e: Exception) {
                                        errorMessage = "AI 분석에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                            }
                        },
                        onPlayFromHere = { stateToPlay ->
                            onStartGameWithState(stateToPlay)
                        },
                        onRetake = {
                            step = ScanStep.PinAdjustment
                            analysisResult = null
                            scoreEstimate = null
                        },
                    )
                } else {
                    step = ScanStep.PinAdjustment
                }
            }
        }

        // 프리미엄 업셀 다이얼로그 (광고 시청 1시간 또는 구독)
        PremiumUpsellDialogHost(
            visible = showPremiumUpsellDialog,
            onDismiss = { showPremiumUpsellDialog = false },
        )

        // 에러 알림 다이얼로그
        errorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("안내", fontWeight = FontWeight.Bold) },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text(strings.confirm)
                    }
                },
            )
        }
    }
}

/**
 * [DetectedBoard] 비전 검출 모델을 게임 상태 [GameState]로 변환.
 */
private fun DetectedBoard.toGameState(
    ruleset: Ruleset = Ruleset.Japanese,
    nextPlayer: StoneColor = StoneColor.Black,
    komi: Double = DefaultKomi,
): GameState = GameState(
    boardSize = boardSize,
    ruleset = ruleset,
    nextPlayer = nextPlayer,
    stones = stones,
    moves = emptyList(),
    komi = komi,
)
