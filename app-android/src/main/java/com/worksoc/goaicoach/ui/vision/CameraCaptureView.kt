package com.worksoc.goaicoach.ui.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.VisionPalette
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.boardScanStringsFor
import com.worksoc.goaicoach.vision.BoardPhotoDecoder
import com.worksoc.goaicoach.vision.BoardSteadiness
import java.util.concurrent.Executors

@Composable
internal fun CameraCaptureView(
    onPhotoCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val text = boardScanStringsFor(LocalUiStrings.current.language)
    // 촬영·불러오기 실패를 조용히 삼키지 않는다 — 옛 화면은 셔터를 눌러도 아무 일이 없는 것처럼 보였다.
    var failure by remember { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val camera = remember { BoundCamera() }

    // 갤러리 이미지 선택기
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        // ⚠️ EXIF 회전 + 긴 변 2048px로 줄여 읽는다(백로그 #210) — 원본 그대로는 옆으로 눕거나 메모리가 모자란다.
        val photo = uri?.let { BoardPhotoDecoder.decode(context, it) ?: run { failure = text.photoLoadFailed; null } }
        if (photo != null) onPhotoCaptured(photo) else camera.analyzer?.enabled = true // 고르지 않고 돌아왔다 — 라이브를 다시 켠다
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    var isCapturing by remember { mutableStateOf(false) }
    val currentOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)

    // ── 라이브 카메라(#263 단계 4): 미리보기에서 판을 찾아 테두리를 그리고, 멈추면 자동으로 찍는다 ──
    var liveSighting by remember { mutableStateOf<LiveSighting?>(null) }
    var liveState by remember { mutableStateOf(BoardSteadiness.State.Searching) }
    val steadiness = remember { BoardSteadiness() }
    // ⚠️ 사진 변환(toBitmap·회전·축소)을 메인 스레드에서 하지 않는다 — 1200만 화소를 돌리는 동안 화면이 멎었다.
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // 사진첩을 고르는 동안은 라이브 판정을 멈춘다 — 선택 창은 카메라 화면을 다 가리지 않아 카메라가 계속 돈다. 판을 비춘 채 사진첩을
    // 열면 고르는 사이에 자동 촬영이 끼어들 수 있었다(에뮬레이터: 선택 창 뒤에서 「판을 찾았어요」).
    fun pickPhoto() {
        camera.analyzer?.enabled = false
        steadiness.reset()
        liveSighting = null
        liveState = BoardSteadiness.State.Searching
        galleryLauncher.launch("image/*")
    }

    fun capture() {
        if (isCapturing) return
        isCapturing = true
        camera.analyzer?.enabled = false
        val main = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = runCatching { image.use { BoardPhotoDecoder.limit(it.toBitmap().rotate(it.imageInfo.rotationDegrees)) } }.getOrNull()
                    main.execute {
                        if (camera.disposed) return@execute // 찍는 사이 화면을 떠났다 — 늦게 온 사진으로 다시 열지 않는다
                        isCapturing = false
                        if (bmp != null) {
                            currentOnPhotoCaptured(bmp)
                        } else {
                            camera.analyzer?.enabled = true
                            failure = text.captureFailed
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    main.execute {
                        if (camera.disposed) return@execute
                        isCapturing = false
                        camera.analyzer?.enabled = true
                        steadiness.reset()
                        failure = text.captureFailed
                    }
                }
            },
        )
    }

    // 화면을 떠나면 카메라를 놓는다 — 액티비티 수명에 묶여 있어서, 놓지 않으면 핀·보정 화면에서도 카메라가 켜져 있었다.
    DisposableEffect(Unit) {
        onDispose {
            camera.disposed = true
            camera.analyzer?.enabled = false
            runCatching { camera.provider?.unbindAll() }
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VisionPalette.Backdrop),
    ) {
        if (hasCameraPermission) {
            // CameraX 프리뷰
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        camera.provider = cameraProvider
                        // 미리보기와 분석을 **같은 4:3**으로 — 그래야 분석 프레임의 좌표를 미리보기 위에 그대로 옮겨 그릴 수 있다.
                        val fourByThree = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build()
                        val preview = Preview.Builder().setResolutionSelector(fourByThree).build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val main = ContextCompat.getMainExecutor(ctx)
                        val analyzer = LiveBoardAnalyzer { found ->
                            main.execute {
                                // 끈 뒤에 도착한 프레임(분석 중이던 것)도 버린다 — 사진첩을 고르는 중이거나 찍는 중이다.
                                if (camera.disposed || isCapturing || camera.analyzer?.enabled == false) return@execute
                                // 화면의 작은 일부(타일·체크무늬·키보드)는 판으로 치지 않는다 — 사용자는 판을 비춘다.
                                val sighting = found?.takeIf { BoardSteadiness.isLargeEnough(it.corners) }
                                liveSighting = sighting
                                liveState = steadiness.feed(sighting?.corners, SystemClock.uptimeMillis())
                                if (liveState == BoardSteadiness.State.Steady) capture()
                            }
                        }
                        camera.analyzer = analyzer
                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                    .setResolutionStrategy(
                                        ResolutionStrategy(AndroidSize(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                                    )
                                    .build(),
                            )
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                            .also { it.setAnalyzer(analysisExecutor, analyzer) }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        // 미리보기 + 촬영 + 분석 셋을 함께 못 거는 기기도 있다 — 그때는 라이브 없이 예전처럼 셔터만.
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture, analysis)
                        }.recoverCatching {
                            camera.analyzer = null
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )

            // 라이브로 찾은 판이 있으면 그 테두리를, 없으면 정사각형 안내선을 그린다.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sighting = liveSighting
                if (sighting != null) {
                    // 미리보기는 FILL_CENTER — 4:3 프레임을 화면에 꽉 채우고 넘치는 쪽을 가운데로 자른다.
                    val scale = maxOf(size.width / sighting.frameWidth, size.height / sighting.frameHeight)
                    val ox = (size.width - sighting.frameWidth * scale) / 2f
                    val oy = (size.height - sighting.frameHeight * scale) / 2f
                    val pts = sighting.corners.map { Offset(it.x * sighting.frameWidth * scale + ox, it.y * sighting.frameHeight * scale + oy) }
                    val outline = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    val color = if (liveState == BoardSteadiness.State.Steady) VisionPalette.GuideHighlight else VisionPalette.Guide
                    drawPath(outline, color = color.copy(alpha = 0.18f))
                    drawPath(outline, color = color, style = Stroke(width = 3.dp.toPx()))
                    return@Canvas
                }
                val boxWidth = size.width * 0.85f
                val left = (size.width - boxWidth) / 2f
                val top = (size.height - boxWidth) / 2.3f

                // 가이드라인 사각형 테두리
                drawRect(
                    color = VisionPalette.Guide,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxWidth),
                    style = Stroke(width = 2.5.dp.toPx()),
                )

                // 4개 귀 화점/코너 강조 표시
                val cornerLen = 24.dp.toPx()
                val cornerStroke = 4.dp.toPx()
                val goldColor = VisionPalette.GuideHighlight

                // 좌상
                drawLine(goldColor, Offset(left, top), Offset(left + cornerLen, top), cornerStroke)
                drawLine(goldColor, Offset(left, top), Offset(left, top + cornerLen), cornerStroke)
                // 우상
                drawLine(goldColor, Offset(left + boxWidth, top), Offset(left + boxWidth - cornerLen, top), cornerStroke)
                drawLine(goldColor, Offset(left + boxWidth, top), Offset(left + boxWidth, top + cornerLen), cornerStroke)
                // 좌하
                drawLine(goldColor, Offset(left, top + boxWidth), Offset(left + cornerLen, top + boxWidth), cornerStroke)
                drawLine(goldColor, Offset(left, top + boxWidth), Offset(left, top + boxWidth - cornerLen), cornerStroke)
                // 우하
                drawLine(goldColor, Offset(left + boxWidth, top + boxWidth), Offset(left + boxWidth - cornerLen, top + boxWidth), cornerStroke)
                drawLine(goldColor, Offset(left + boxWidth, top + boxWidth), Offset(left + boxWidth, top + boxWidth - cornerLen), cornerStroke)
            }
        } else {
            // 카메라 권한 미부여 시 안내 화면
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.Space24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = text.cameraPermissionNeeded,
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionPalette.OnBackdrop,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp,
                )
                Spacer(modifier = Modifier.height(AppSpacing.Space16))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(AppRadius.Corner8),
                ) {
                    Text(text.grantPermission)
                }
                Spacer(modifier = Modifier.height(AppSpacing.Space12))
                OutlinedButton(
                    onClick = { pickPhoto() },
                    shape = RoundedCornerShape(AppRadius.Corner8),
                ) {
                    Text(text.loadFromGallery, color = VisionPalette.OnBackdrop)
                }
            }
        }

        // 상단 헤더 (닫기 버튼 및 팁 안내)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                // targetSdk 36부터 앱이 상태 표시줄 밑까지 그린다 — 닫기·안내가 시계·아이콘에 깔리지 않게(#263에서 안내가 길어지며 드러났다).
                .statusBarsPadding()
                .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onClose,
                shape = RoundedCornerShape(AppRadius.Corner8),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VisionPalette.OnBackdrop),
            ) {
                Text(text.close)
            }

            Text(
                text = when {
                    !hasCameraPermission -> text.captureHint
                    isCapturing -> text.liveCapturing
                    liveState == BoardSteadiness.State.Searching -> text.liveSearching
                    else -> text.liveFound
                },
                style = MaterialTheme.typography.bodySmall,
                color = VisionPalette.OnBackdrop,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = AppSpacing.Space12)
                    .background(VisionPalette.Backdrop.copy(alpha = 0.55f), RoundedCornerShape(AppRadius.Corner8))
                    .padding(horizontal = AppSpacing.Space10, vertical = AppSpacing.Space6),
            )
        }

        failure?.let { message ->
            Text(
                text = message,
                color = VisionPalette.OnBackdrop,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 128.dp)
                    .background(VisionPalette.Backdrop.copy(alpha = 0.7f), RoundedCornerShape(AppRadius.Corner8))
                    .clickable { failure = null }
                    .padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space8),
            )
        }

        // 하단 조작 패널 (갤러리 버튼, 촬영 셔터 버튼)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 갤러리 불러오기 버튼
            OutlinedButton(
                onClick = { pickPhoto() },
                shape = RoundedCornerShape(AppRadius.Corner8),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VisionPalette.OnBackdrop),
            ) {
                Text(text.gallery)
            }

            // 셔터 촬영 버튼
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(VisionPalette.OnBackdrop.copy(alpha = 0.3f))
                    .padding(AppSpacing.Space6)
                    .clip(CircleShape)
                    .background(if (isCapturing) VisionPalette.ShutterBusy else VisionPalette.OnBackdrop)
                    .clickable(enabled = hasCameraPermission && !isCapturing) { capture() },
                contentAlignment = Alignment.Center,
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = VisionPalette.ShutterProgress,
                        strokeWidth = 3.dp,
                    )
                }
            }

            // 우측 여백 균형용 빈 공간
            Spacer(modifier = Modifier.width(64.dp))
        }
    }
}

/** 화면이 붙잡은 카메라 — 떠날 때 놓으려고, 라이브 분석을 촬영 중에 끄려고 든다. */
private class BoundCamera {
    var provider: ProcessCameraProvider? = null
    var analyzer: LiveBoardAnalyzer? = null
    var disposed: Boolean = false
}

internal fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
