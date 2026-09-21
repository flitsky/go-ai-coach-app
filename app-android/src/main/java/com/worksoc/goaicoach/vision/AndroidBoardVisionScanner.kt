package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.BoardVisionScannerPort
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidBoardVisionScanner(
    private val sourceBitmap: Bitmap,
) : BoardVisionScannerPort {

    override suspend fun detectStones(
        corners: BoardCornerPoints,
        boardSize: BoardSize,
    ): DetectedBoard = withContext(Dispatchers.Default) {
        val square = AndroidBitmapPerspectiveTransformer.transformToSquare(
            source = sourceBitmap,
            corners = corners,
            outputSize = 1000,
        )
        try {
            GridStoneDetector.detect(square, boardSize)
        } finally {
            if (!square.isRecycled && square != sourceBitmap) {
                square.recycle()
            }
        }
    }
}
