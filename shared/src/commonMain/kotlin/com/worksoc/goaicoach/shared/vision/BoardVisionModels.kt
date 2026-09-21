package com.worksoc.goaicoach.shared.vision

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.StoneColor

data class PointF2D(
    val x: Float,
    val y: Float,
)

data class BoardCornerPoints(
    val topLeft: PointF2D,
    val topRight: PointF2D,
    val bottomRight: PointF2D,
    val bottomLeft: PointF2D,
) {
    fun toList(): List<PointF2D> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        fun defaultForSize(width: Float, height: Float, insetRatio: Float = 0.08f): BoardCornerPoints {
            val insetX = width * insetRatio
            val insetY = height * insetRatio
            return BoardCornerPoints(
                topLeft = PointF2D(insetX, insetY),
                topRight = PointF2D(width - insetX, insetY),
                bottomRight = PointF2D(width - insetX, height - insetY),
                bottomLeft = PointF2D(insetX, height - insetY),
            )
        }
    }
}

data class DetectedBoard(
    val boardSize: BoardSize,
    val stones: Map<BoardCoordinate, StoneColor>,
    val confidence: Float = 1.0f,
)

interface BoardVisionScannerPort {
    /**
     * 바둑판 이미지와 4개 모서리 좌표를 입력받아 각 교차점의 돌(흑/백/빈칸)을 검출합니다.
     */
    suspend fun detectStones(
        corners: BoardCornerPoints,
        boardSize: BoardSize,
    ): DetectedBoard
}
