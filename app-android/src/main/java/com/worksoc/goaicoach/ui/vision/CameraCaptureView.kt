package com.worksoc.goaicoach.ui.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

    // 갤러리 이미지 선택기
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        // ⚠️ EXIF 회전 + 긴 변 2048px로 줄여 읽는다(백로그 #210) — 원본 그대로는 옆으로 눕거나 메모리가 모자란다.
        uri?.let { BoardPhotoDecoder.decode(context, it) ?: run { failure = text.photoLoadFailed; null } }?.let(onPhotoCaptured)
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    var isCapturing by remember { mutableStateOf(false) }

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
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture,
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )

            // 가이드라인 오버레이 (정사각형 바둑판 안내선)
            Canvas(modifier = Modifier.fillMaxSize()) {
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
                    onClick = { galleryLauncher.launch("image/*") },
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
                .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space20)
                .align(Alignment.TopCenter),
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
                text = text.captureHint,
                style = MaterialTheme.typography.bodySmall,
                color = VisionPalette.OnBackdrop,
                fontWeight = FontWeight.Medium,
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
                onClick = { galleryLauncher.launch("image/*") },
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
                    .clickable(enabled = hasCameraPermission && !isCapturing) {
                        if (isCapturing) return@clickable
                        isCapturing = true
                        val executor = ContextCompat.getMainExecutor(context)
                        imageCapture.takePicture(
                            executor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val rotation = image.imageInfo.rotationDegrees
                                    val bmp = BoardPhotoDecoder.limit(image.toBitmap().rotate(rotation))
                                    image.close()
                                    isCapturing = false
                                    onPhotoCaptured(bmp)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    failure = text.captureFailed
                                }
                            },
                        )
                    },
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

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
