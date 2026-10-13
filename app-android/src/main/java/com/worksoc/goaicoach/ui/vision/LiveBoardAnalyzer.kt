package com.worksoc.goaicoach.ui.vision

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.shared.vision.PointF2D
import com.worksoc.goaicoach.vision.ArrayPixelSource
import com.worksoc.goaicoach.vision.BoardLocator
import kotlin.math.roundToInt

/** 미리보기 프레임 하나에서 찾은 판 — 네 모서리(TL TR BR BL)를 프레임 크기로 나눈 비율(0~1), 프레임은 화면 방향으로 돌린 크기. */
internal class LiveSighting(val corners: List<PointF2D>, val frameWidth: Int, val frameHeight: Int)

/**
 * 라이브 카메라(백로그 #263 단계 4) — 미리보기와 같은 비율의 작은 프레임(640×480 안팎)에서 판을 찾는다.
 *
 * 촬영본과 **같은 격자 찾기**([BoardLocator])를 쓴다 — 미리보기에서 잡혔으면 찍은 사진에서도 잡힌다. 원근 펴기(OpenCV)는 여기서는
 * 하지 않는다(프레임마다 돌리기엔 무겁고, 찍은 뒤 정밀 인식에서 한다). 카메라 스레드에서 돌고, 결과는 [onResult]로 넘긴다.
 * 프레임이 밀리면 CameraX가 최신 것만 남긴다(`STRATEGY_KEEP_ONLY_LATEST`).
 */
internal class LiveBoardAnalyzer(private val onResult: (LiveSighting?) -> Unit) : ImageAnalysis.Analyzer {
    private companion object {
        /**
         * 찾을 때의 긴 변(px). 19줄이 화면에 차면 한 칸이 15px 남짓 — 기준 이미지 04(한 칸 15px)도 찾혔다.
         * 640×480 그대로는 에뮬레이터에서 프레임당 8초였다(폰은 수십 배 빠르지만 라이브로는 둔하다 — 함정 86, 폰에서 `LiveBoard` 로그로 잰다).
         */
        const val LiveMaxDim = 360
    }

    /** 촬영 중에는 끈다 — 사진을 찍는 동안 CPU를 나눠 쓰지 않게. */
    @Volatile var enabled: Boolean = true

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            if (!enabled) return
            val started = SystemClock.elapsedRealtime()
            val upright = runCatching { frame.toBitmap().rotate(frame.imageInfo.rotationDegrees) }
                .onFailure { if (BuildConfig.DEBUG) Log.w("LiveBoard", "프레임을 비트맵으로 못 바꿨다(${frame.format} ${frame.width}x${frame.height})", it) }
                .getOrNull() ?: return
            val w = upright.width
            val h = upright.height
            // 라이브는 판의 자리만 잡으면 된다 — 긴 변 [LiveMaxDim]px로 줄여 찾는다(정밀 인식은 찍은 사진으로 한다).
            val f = minOf(1f, LiveMaxDim.toFloat() / maxOf(w, h))
            val small = if (f < 1f) upright.scale((w * f).roundToInt(), (h * f).roundToInt()) else upright
            val located = runCatching { BoardLocator.locate(ArrayPixelSource.fromBitmap(small)) }.getOrNull()
            // 속도는 에뮬레이터로 판단하지 않는다(함정 86) — 폰에서 이 줄로 잰다: adb logcat -s LiveBoard
            if (BuildConfig.DEBUG) Log.d("LiveBoard", "${w}x$h ${if (located != null) "판 찾음" else "없음"} ${SystemClock.elapsedRealtime() - started}ms")
            onResult(located?.let { board -> LiveSighting(board.corners.toList().map { PointF2D(it.x / small.width, it.y / small.height) }, w, h) })
        }
    }
}
