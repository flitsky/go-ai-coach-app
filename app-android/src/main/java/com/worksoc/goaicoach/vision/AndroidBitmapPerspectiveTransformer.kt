package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints

object AndroidBitmapPerspectiveTransformer {
    /**
     * 원본 비트맵에서 지정된 4개 꼭짓점(topLeft, topRight, bottomRight, bottomLeft) 영역을
     * [outputSize] x [outputSize] 크기의 반듯한 정사각형 비트맵으로 투시 변환(Perspective Warp)합니다.
     */
    fun transformToSquare(
        source: Bitmap,
        corners: BoardCornerPoints,
        outputSize: Int = 1000,
    ): Bitmap {
        val srcPoints = floatArrayOf(
            corners.topLeft.x, corners.topLeft.y,
            corners.topRight.x, corners.topRight.y,
            corners.bottomRight.x, corners.bottomRight.y,
            corners.bottomLeft.x, corners.bottomLeft.y,
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            outputSize.toFloat(), 0f,
            outputSize.toFloat(), outputSize.toFloat(),
            0f, outputSize.toFloat(),
        )

        val matrix = Matrix()
        // setPolyToPoly with 4 points computes the exact perspective transform matrix
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val warpedBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warpedBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        canvas.drawBitmap(source, matrix, paint)
        return warpedBitmap
    }
}
