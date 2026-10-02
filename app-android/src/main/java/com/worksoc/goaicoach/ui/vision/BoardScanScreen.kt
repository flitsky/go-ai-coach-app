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
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.application.engine.EngineOperationBusy
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.premium.state.FeatureAccess
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.VisionPalette
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.boardScanStringsFor
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import com.worksoc.goaicoach.ui.monetization.PremiumUpsellDialogHost
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
 * 백로그 #179 — 카메라로 바둑판 인식해 분석 (Board Scan & Analysis Screen). #210에서 다시 지었다.
 *
 * 전체 플로우:
 * 1. [CameraCaptureView]로 바둑판 촬영 (또는 갤러리 이미지 선택 — EXIF 회전·축소)
 * 2. **판 자동 인식**([AndroidBoardVisionScanner.locate]) — 찾으면 그 모서리·판 크기로 핀 화면을 연다
 * 3. [CornerPinAdjustmentOverlay]로 어긋난 핀만 미세조정(못 찾았으면 네 핀을 직접)
 * 4. 핀을 **가까운 격자에 붙이고**([AndroidBoardVisionScanner.scan]) 돌 검출
 * 5. [BoardCorrectionEditor]에서 원본 사진과 비교하며 터치 편집, 흑/백 차례, 덤 설정
 * 6. KataGo 비동기 형세 분석 실행 및 '이 국면부터 대국 시작' 연계
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
    // 자동 인식(또는 지난 인식이 붙인) 모서리 — 핀 화면이 여기서 시작한다. `null`이면 기본 위치에서.
    var pinCorners by remember { mutableStateOf<BoardCornerPoints?>(null) }
    var pinBoardSize by remember { mutableStateOf(BoardSize.Nineteen) }
    var boardPhoto by remember { mutableStateOf<Bitmap?>(null) }

    var isLocating by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var scoreEstimate by remember { mutableStateOf<ScoreEstimate?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showPremiumUpsellDialog by remember { mutableStateOf(false) }

    val premiumUiState = LocalPremiumUiState.current
    val strings = LocalUiStrings.current
    val text = boardScanStringsFor(strings.language)
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
                pinCorners = null
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
                        pinCorners = null
                        pinBoardSize = BoardSize.Nineteen
                        isLocating = true
                        step = ScanStep.PinAdjustment
                        scope.launch {
                            // 판을 스스로 찾는다 — 못 찾으면 핀 화면이 "직접 맞춰 주세요"로 연다. 실패가 아니다.
                            val located = runCatching { AndroidBoardVisionScanner(bitmap).locate() }.getOrNull()
                            if (located != null && capturedBitmap === bitmap) {
                                pinCorners = located.corners
                                pinBoardSize = located.boardSize
                            }
                            isLocating = false
                        }
                    },
                    onClose = onBackClick,
                )
            }

            ScanStep.PinAdjustment -> {
                val bitmap = capturedBitmap
                if (bitmap != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isLocating) {
                            CornerPinAdjustmentOverlay(
                                bitmap = bitmap,
                                initialCorners = pinCorners,
                                initialBoardSize = pinBoardSize,
                                onCornersConfirmed = { corners, boardSize ->
                                    isScanning = true
                                    scope.launch {
                                        try {
                                            val result = AndroidBoardVisionScanner(bitmap).scan(corners, boardSize)
                                            // 핀이 붙은 자리를 기억해 둔다 — 되돌아오면 붙은 핀에서 다시 시작한다.
                                            pinCorners = result.corners
                                            pinBoardSize = boardSize
                                            boardPhoto = result.boardPhoto
                                            detectedGameState = result.detected.toGameState()
                                            step = ScanStep.Correction
                                        } catch (e: Exception) {
                                            errorMessage = text.scanFailed
                                        } finally {
                                            isScanning = false
                                        }
                                    }
                                },
                                onRetake = {
                                    step = ScanStep.Capture
                                    capturedBitmap = null
                                    pinCorners = null
                                },
                            )
                        }

                        if (isLocating || isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(VisionPalette.Backdrop.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = VisionPalette.OnBackdrop)
                                    Spacer(modifier = Modifier.height(AppSpacing.Space16))
                                    Text(
                                        text = if (isLocating) text.locating else text.scanning,
                                        color = VisionPalette.OnBackdrop,
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
                        boardPhoto = boardPhoto,
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
                                        errorMessage = boardScanAnalysisErrorMessage(e, strings.language)
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
                title = { Text(text.notice, fontWeight = FontWeight.Bold) },
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
 * 스캔한 국면의 AI 분석이 끝나지 못했을 때 보일 문구.
 *
 * 엔진이 다른 오퍼레이션(대국의 AI 차례, 첫 실행의 엔진 기동·설치 등)을 하고 있으면 형세 추정·분석은 기다리지 않고
 * [EngineOperationBusy]로 포기한다(refactor backlog #15 — `LocalEngineSessionClient`의 오퍼레이션 락). 실패가 아니므로
 * "실패했습니다"와 예외 원문(영어)을 보이지 않고, 잠시 뒤 다시 누르라고 알린다. 예전에는 두 오퍼레이션이 한 엔진에서
 * 섞여 돌아 남의 판을 분석할 수 있었다.
 */
internal fun boardScanAnalysisErrorMessage(error: Throwable, language: UiLanguage = UiLanguage.Korean): String {
    val text = boardScanStringsFor(language)
    return if (error is EngineOperationBusy) {
        text.engineBusy
    } else {
        text.analysisFailed(error.localizedMessage ?: error.javaClass.simpleName)
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
