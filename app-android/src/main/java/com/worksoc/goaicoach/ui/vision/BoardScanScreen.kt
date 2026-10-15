package com.worksoc.goaicoach.ui.vision

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.worksoc.goaicoach.application.engine.EngineOperationBusy
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.premium.state.FeatureAccess
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
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
import com.worksoc.goaicoach.shared.vision.guessNextPlayer
import com.worksoc.goaicoach.shared.vision.scannedPositionSgf
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.VisionPalette
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.boardScanStringsFor
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import com.worksoc.goaicoach.ui.monetization.PremiumUpsellDialogHost
import com.worksoc.goaicoach.vision.AndroidBoardVisionScanner
import com.worksoc.goaicoach.vision.BoardPhotoDecoder
import com.worksoc.goaicoach.vision.BoardScanResult
import com.worksoc.goaicoach.vision.ScannedPositionShare
import com.worksoc.goaicoach.vision.SharedBoardPhotoInbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * 2. **판 자동 인식**([AndroidBoardVisionScanner.locate] — 원근이 세면 먼저 편다, #263) — 찾은 판을 바로 읽어 보고
 *    **확실하면 3을 건너뛰어 5로**([BoardScanResult.isConfident], #263). 아니면 찾은 모서리·판 크기로 핀 화면을 연다
 * 3. [CornerPinAdjustmentOverlay]로 어긋난 핀만 미세조정(못 찾았으면 네 핀을 직접)
 * 4. 핀을 **가까운 격자에 붙이고**([AndroidBoardVisionScanner.scan]) 돌 검출
 * 5. [BoardCorrectionEditor]에서 원본 사진과 비교하며 터치 편집, 흑/백 차례, 덤 설정
 * 6. KataGo 비동기 형세 분석 실행 및 '이 국면부터 대국 시작' 연계 · SGF 파일로 보내기(#263)
 *
 * 사진은 다른 앱의 「공유」로도 들어온다([SharedBoardPhotoInbox], #263) — 그때는 촬영을 건너뛰고 2부터.
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
    // 인식이 확신하지 못한 교점(#263) — 보정 화면에 「확인 필요」로.
    var attentionPoints by remember { mutableStateOf<Set<BoardCoordinate>>(emptySet()) }

    var isLocating by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    // 보정 화면의 AI 분석 둘 — 결과는 그것을 낸 국면과 함께 기억한다(판을 고치면 보이지 않고, 같은 국면이면 다시 계산하지 않는다).
    var analyzing by remember { mutableStateOf<ScanAnalysisView?>(null) }
    var topMoves by remember { mutableStateOf<AnalyzedPosition<AnalysisResult>?>(null) }
    var score by remember { mutableStateOf<AnalyzedPosition<ScoreEstimate>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showPremiumUpsellDialog by remember { mutableStateOf(false) }

    val premiumUiState = LocalPremiumUiState.current
    val strings = LocalUiStrings.current
    val text = boardScanStringsFor(strings.language)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 사진 하나에 스캐너 하나 — 픽셀을 배열로 한 번만 복사하고, 자동 인식이 고른 판독을 핀 확인이 다시 쓴다(찾기·읽기·핀 붙이기가 함께 쓴다).
    var scanner by remember { mutableStateOf<AndroidBoardVisionScanner?>(null) }
    // 그 사진의 찾기·읽기 작업 — 사진을 버리거나 바꾸면 끊는다(버린 사진의 결과가 뒤늦게 화면을 바꾸지 않게, #263).
    var photoWork by remember { mutableStateOf<Job?>(null) }

    fun dropPhoto() {
        photoWork?.cancel()
        photoWork = null
        capturedBitmap = null
        scanner = null
        pinCorners = null
        isLocating = false
        isScanning = false
    }

    fun showResult(result: BoardScanResult, boardSize: BoardSize) {
        // 핀이 붙은 자리를 기억해 둔다 — 핀 화면으로 돌아오면 붙은 핀에서 다시 시작한다.
        pinCorners = result.corners
        pinBoardSize = boardSize
        boardPhoto = result.boardPhoto
        detectedGameState = result.detected.toGameState()
        attentionPoints = result.uncertain
        step = ScanStep.Correction
    }

    // 찍었거나 골랐거나 공유로 받은 사진 한 장에서 시작한다 — 판을 스스로 찾고, **확실하면 핀 화면 없이 바로 결과로**(#263),
    // 아니면 핀 화면을 연다.
    fun startFromPhoto(bitmap: Bitmap) {
        photoWork?.cancel()
        capturedBitmap = bitmap
        pinCorners = null
        pinBoardSize = BoardSize.Nineteen
        attentionPoints = emptySet()
        topMoves = null
        score = null
        isLocating = true
        step = ScanStep.PinAdjustment
        val photoScanner = AndroidBoardVisionScanner(bitmap)
        scanner = photoScanner
        photoWork = scope.launch {
            // 판을 스스로 찾는다 — 못 찾으면 핀 화면이 "직접 맞춰 주세요"로 연다. 실패가 아니다.
            val located = runCatching { photoScanner.locate() }.getOrNull()
            if (located != null && capturedBitmap === bitmap) {
                pinCorners = located.corners
                pinBoardSize = located.boardSize
                // 찾은 판을 바로 읽어 본다 — 샘플 위치를 흔들어도 답이 거의 같으면(ScanStability) 핀 화면을 건너뛴다.
                val result = runCatching { photoScanner.scan(located.corners, located.boardSize, snap = false) }.getOrNull()
                if (result != null && result.isConfident && capturedBitmap === bitmap) showResult(result, located.boardSize)
            }
            if (capturedBitmap === bitmap) isLocating = false
        }
    }

    // 다른 앱의 「공유」로 받은 사진(백로그 #263) — 화면이 다시 보일 때마다 받은편지함을 본다. 이 화면에 있는 동안
    // 새로 받아도 그 사진으로 다시 시작한다.
    // ⚠️ 받은 사진을 여는 동안은 촬영 화면을 그리지 않는다 — 그리면 카메라 권한부터 묻는다(공유로 들어온 사람에게는 필요 없는 창).
    var isOpeningSharedPhoto by remember { mutableStateOf(SharedBoardPhotoInbox.hasPhoto(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val file = SharedBoardPhotoInbox.take(context)
        if (file == null) {
            isOpeningSharedPhoto = false
            return@LifecycleEventEffect
        }
        isOpeningSharedPhoto = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { BoardPhotoDecoder.decode(context, Uri.fromFile(file)) }
            isOpeningSharedPhoto = false
            if (bitmap == null) errorMessage = text.photoLoadFailed else startFromPhoto(bitmap)
        }
    }

    // 단계별 뒤로가기 처리
    BackHandler {
        when (step) {
            ScanStep.Correction -> {
                step = ScanStep.PinAdjustment
                topMoves = null
                score = null
            }
            ScanStep.PinAdjustment -> {
                step = ScanStep.Capture
                dropPhoto()
            }
            ScanStep.Capture -> {
                onBackClick()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (step) {
            ScanStep.Capture -> {
                if (isOpeningSharedPhoto) {
                    ScanProgressBackdrop(text.locating)
                } else {
                    CameraCaptureView(
                        onPhotoCaptured = ::startFromPhoto,
                        onClose = onBackClick,
                    )
                }
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
                                    val photoScanner = scanner ?: AndroidBoardVisionScanner(bitmap).also { scanner = it }
                                    photoWork = scope.launch {
                                        try {
                                            // 핀을 안 건드렸으면 자동 인식(또는 지난 인식)이 이미 격자에 붙인 자리다 — 다시 붙이지 않는다.
                                            val untouched = corners == pinCorners && boardSize == pinBoardSize
                                            val result = photoScanner.scan(corners, boardSize, snap = !untouched)
                                            if (capturedBitmap === bitmap) showResult(result, boardSize)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            if (capturedBitmap === bitmap) errorMessage = text.scanFailed
                                        } finally {
                                            if (capturedBitmap === bitmap) isScanning = false
                                        }
                                    }
                                },
                                onRetake = {
                                    step = ScanStep.Capture
                                    dropPhoto()
                                },
                            )
                        }

                        if (isLocating || isScanning) {
                            ScanProgressBackdrop(if (isLocating) text.locating else text.scanning)
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
                        attentionPoints = attentionPoints,
                        analyzing = analyzing,
                        topMoves = topMoves,
                        score = score,
                        onAnalyze = { view, stateToAnalyze ->
                            val access = premiumUiState.resolve(FeatureId.BoardScan)
                            if (access !is FeatureAccess.Allowed) {
                                showPremiumUpsellDialog = true
                            } else {
                                analyzing = view
                                scope.launch {
                                    try {
                                        when (view) {
                                            // 「형세 분석」 — 영역(소유) · 승률 · 집 차이. 평가 한 번이다.
                                            ScanAnalysisView.Score -> score = AnalyzedPosition(
                                                stateToAnalyze,
                                                withContext(Dispatchers.Default) {
                                                    engineClient.estimateScoreForState(
                                                        state = stateToAnalyze,
                                                        profile = EngineProfile(analysisLimit = ScanAnalysisLimit),
                                                        syncFirst = true,
                                                    )
                                                },
                                            )
                                            // 「AI 추천수」 — 후보 수 탐색.
                                            ScanAnalysisView.TopMoves -> topMoves = AnalyzedPosition(
                                                stateToAnalyze,
                                                withContext(Dispatchers.Default) {
                                                    engineClient.analyzePosition(
                                                        state = stateToAnalyze,
                                                        limit = ScanAnalysisLimit,
                                                        searchMode = EngineSearchMode.JsonPositionAnalysis,
                                                    )
                                                },
                                            )
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        errorMessage = boardScanAnalysisErrorMessage(e, strings.language)
                                    } finally {
                                        analyzing = null
                                    }
                                }
                            }
                        },
                        onPlayFromHere = { stateToPlay ->
                            onStartGameWithState(stateToPlay)
                        },
                        onExportSgf = { stateToSend ->
                            runCatching {
                                ScannedPositionShare.send(context, scannedPositionSgf(stateToSend, SgfApplicationName), text.exportSgfChooser)
                            }.onFailure { errorMessage = text.exportSgfFailed }
                        },
                        onRetake = {
                            step = ScanStep.PinAdjustment
                            topMoves = null
                            score = null
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

/** 판을 찾거나 돌을 읽는 동안 화면을 덮는 진행 표시. */
@Composable
private fun ScanProgressBackdrop(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionPalette.Backdrop.copy(alpha = 0.6f))
            // 도는 동안 아래 핀 화면의 「확인」·「다시 찍기」가 눌리지 않게 손가락을 받아 둔다
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = VisionPalette.OnBackdrop)
            Spacer(modifier = Modifier.height(AppSpacing.Space16))
            Text(text = label, color = VisionPalette.OnBackdrop, fontWeight = FontWeight.Medium)
        }
    }
}

/** 보정 화면의 AI 분석 깊이 — 「AI 추천수」·「형세 분석」 둘 다(나누기 전과 같은 값). */
private val ScanAnalysisLimit = AnalysisLimit(visits = 120, timeMillis = 3500)

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

/** SGF의 `AP`(만든 앱) — 영어 앱 이름(`values-en`의 `app_name`)과 같게. */
private const val SgfApplicationName = "Go AI Coach"

/**
 * [DetectedBoard] 비전 검출 모델을 게임 상태 [GameState]로 변환.
 * 다음 차례는 돌 개수로 짐작한 **기본값**이다([guessNextPlayer], 백로그 #263) — 보정 화면에서 고친다.
 */
private fun DetectedBoard.toGameState(
    ruleset: Ruleset = Ruleset.Japanese,
    nextPlayer: StoneColor = guessNextPlayer(stones),
    komi: Double = DefaultKomi,
): GameState = GameState(
    boardSize = boardSize,
    ruleset = ruleset,
    nextPlayer = nextPlayer,
    stones = stones,
    moves = emptyList(),
    komi = komi,
)
