package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.BoardVisionScannerPort
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ⚠️ **기준 이미지 테스트와 같은 경로를 탄다**(백로그 #210) — 순수 Kotlin [BoardWarp] → [GridStoneDetector].
 * 옛 `AndroidBitmapPerspectiveTransformer`(안드로이드 `Matrix`)는 JVM 테스트가 밟을 수 없어서 걷어냈다.
 */
class AndroidBoardVisionScanner(
    private val sourceBitmap: Bitmap,
) : BoardVisionScannerPort {

    override suspend fun detectStones(
        corners: BoardCornerPoints,
        boardSize: BoardSize,
    ): DetectedBoard = withContext(Dispatchers.Default) {
        val board = BoardWarp.rectify(ArrayPixelSource.fromBitmap(sourceBitmap), corners, boardSize)
        GridStoneDetector.detect(board)
    }
}
