package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.BoardVisionScannerPort
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 인식 결과 — 돌 배치 + 실제로 쓴(가까운 격자에 붙인) 모서리 + 반듯하게 편 판 사진(보정 화면의 비교용). */
internal class BoardScanResult(
    val detected: DetectedBoard,
    val corners: BoardCornerPoints,
    val boardPhoto: Bitmap,
)

/**
 * ⚠️ **기준 이미지 테스트와 같은 경로를 탄다**(백로그 #210) — 순수 Kotlin [BoardLocator] · [BoardWarp] · [GridStoneDetector].
 * 옛 `AndroidBitmapPerspectiveTransformer`(안드로이드 `Matrix`)는 JVM 테스트가 밟을 수 없어서 걷어냈다.
 * 비트맵은 [BoardPhotoDecoder]로 줄여서 넘길 것 — 픽셀을 한 번 배열로 복사한다.
 */
internal class AndroidBoardVisionScanner(
    sourceBitmap: Bitmap,
) : BoardVisionScannerPort {

    private val pixels: ArrayPixelSource by lazy { ArrayPixelSource.fromBitmap(sourceBitmap) }

    /** 판을 스스로 찾는다 — 못 찾으면 `null`(수동 핀으로). */
    suspend fun locate(): LocatedBoard? = withContext(Dispatchers.Default) { BoardLocator.locate(pixels) }

    /** 손으로 놓은 핀을 가까운 격자에 붙이고([BoardLocator.refine]) 돌을 읽는다. */
    suspend fun scan(corners: BoardCornerPoints, boardSize: BoardSize): BoardScanResult = withContext(Dispatchers.Default) {
        val refined = BoardLocator.refine(pixels, corners, boardSize)
        val board = BoardWarp.rectify(pixels, refined, boardSize)
        val detected = GridStoneDetector.detect(board)
        val photo = createBitmap(board.pixels.width, board.pixels.height)
        photo.setPixels(board.pixels.toArgbArray(), 0, board.pixels.width, 0, 0, board.pixels.width, board.pixels.height)
        BoardScanResult(detected, refined, photo)
    }

    override suspend fun detectStones(corners: BoardCornerPoints, boardSize: BoardSize): DetectedBoard =
        scan(corners, boardSize).detected
}
