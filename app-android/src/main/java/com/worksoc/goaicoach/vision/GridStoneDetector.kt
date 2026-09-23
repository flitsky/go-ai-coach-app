package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import kotlin.math.max
import kotlin.math.min

interface PixelSource {
    val width: Int
    val height: Int
    fun getPixel(x: Int, y: Int): Int
}

class BitmapPixelSource(private val bitmap: Bitmap) : PixelSource {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override fun getPixel(x: Int, y: Int): Int = bitmap.getPixel(x, y)
}

class ArrayPixelSource(
    override val width: Int,
    override val height: Int,
    private val pixels: IntArray,
) : PixelSource {
    override fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]
}

object GridStoneDetector {

    data class DetectionConfig(
        /** 코너 핀이 판의 외곽선(그리드 1-1, 1-N 등 귀 교차점)에 정확히 맞춰진 경우 false, 목재 테두리에 맞춰진 경우 true */
        val hasBorderMargin: Boolean = false,
        /** 테두리 마진 비율 (hasBorderMargin이 true일 때 적용) */
        val borderMarginRatio: Float = 0.04f,
    )

    fun detect(
        squareBitmap: Bitmap,
        boardSize: BoardSize,
        config: DetectionConfig = DetectionConfig(),
    ): DetectedBoard = detect(BitmapPixelSource(squareBitmap), boardSize, config)

    /**
     * 정사각형으로 정규화된 바둑판 픽셀 소스를 분석하여 흑돌, 백돌, 빈칸의 위치를 검출합니다.
     */
    fun detect(
        pixelSource: PixelSource,
        boardSize: BoardSize,
        config: DetectionConfig = DetectionConfig(),
    ): DetectedBoard {
        val width = pixelSource.width
        val height = pixelSource.height
        val n = boardSize.value

        // 교차점 간격 및 시작 오프셋 계산
        val startX: Float
        val startY: Float
        val stepX: Float
        val stepY: Float

        if (config.hasBorderMargin) {
            val marginX = width * config.borderMarginRatio
            val marginY = height * config.borderMarginRatio
            val innerW = width - (marginX * 2)
            val innerH = height - (marginY * 2)
            stepX = innerW / (n - 1)
            stepY = innerH / (n - 1)
            startX = marginX
            startY = marginY
        } else {
            stepX = width.toFloat() / (n - 1)
            stepY = height.toFloat() / (n - 1)
            startX = 0f
            startY = 0f
        }

        val sampleRadius = (min(stepX, stepY) * 0.32f).coerceAtLeast(3f)

        // 1단계: 모든 교차점의 샘플 통계(평균 밝기, 채도, 밝기 분산) 수집
        data class PointStat(
            val coord: BoardCoordinate,
            val avgLuminance: Float,
            val avgSaturation: Float,
            val pixelCount: Int,
        )

        val stats = mutableListOf<PointStat>()

        for (row in 0 until n) {
            for (col in 0 until n) {
                val cx = startX + col * stepX
                val cy = startY + row * stepY
                val stat = sampleIntersection(pixelSource, cx, cy, sampleRadius)
                stats.add(PointStat(BoardCoordinate(row, col), stat.luminance, stat.saturation, stat.count))
            }
        }

        // 2단계: 전체 밝기 분포 분석을 통한 적응형 임계값 산출
        val luminances = stats.map { it.avgLuminance }.sorted()
        val minLum = luminances.firstOrNull() ?: 0f
        val maxLum = luminances.lastOrNull() ?: 255f
        val medianLum = luminances[luminances.size / 2]

        // 흑돌과 백돌의 임계값 계산
        val blackThreshold = min(minLum + (medianLum - minLum) * 0.45f, 95f)
        val whiteThreshold = max(medianLum + (maxLum - medianLum) * 0.45f, 160f)

        val detectedStones = mutableMapOf<BoardCoordinate, StoneColor>()

        for (stat in stats) {
            val lum = stat.avgLuminance
            val sat = stat.avgSaturation

            // 흑돌 판별: 매우 어둡고 채도가 낮음
            if (lum < blackThreshold && sat < 0.45f) {
                detectedStones[stat.coord] = StoneColor.Black
            }
            // 백돌 판별: 매우 밝고 채도가 낮음
            else if (lum > whiteThreshold && sat < 0.35f) {
                detectedStones[stat.coord] = StoneColor.White
            }
        }

        return DetectedBoard(
            boardSize = boardSize,
            stones = detectedStones,
            confidence = 0.95f,
        )
    }

    private data class SampleResult(val luminance: Float, val saturation: Float, val count: Int)

    private fun sampleIntersection(
        source: PixelSource,
        centerX: Float,
        centerY: Float,
        radius: Float,
    ): SampleResult {
        var totalLum = 0f
        var totalSat = 0f
        var count = 0

        val rInt = radius.toInt()
        val width = source.width
        val height = source.height

        val cx = centerX.toInt()
        val cy = centerY.toInt()

        for (dy in -rInt..rInt) {
            val py = cy + dy
            if (py !in 0 until height) continue

            for (dx in -rInt..rInt) {
                val px = cx + dx
                if (px !in 0 until width) continue

                // 원형 영역 내 픽셀만 샘플링
                if (dx * dx + dy * dy <= radius * radius) {
                    val pixel = source.getPixel(px, py)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // ITU-R BT.601 휘도 공식
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    val maxVal = max(r, max(g, b)).toFloat()
                    val minVal = min(r, min(g, b)).toFloat()
                    val sat = if (maxVal > 0f) (maxVal - minVal) / maxVal else 0f

                    totalLum += lum
                    totalSat += sat
                    count++
                }
            }
        }

        if (count == 0) return SampleResult(128f, 0f, 0)
        return SampleResult(totalLum / count, totalSat / count, count)
    }
}
