package com.worksoc.goaicoach.vision

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.BoardVisionScannerPort
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 인식 결과 — 돌 배치 + 실제로 쓴(가까운 격자에 붙인) 모서리 + 반듯하게 편 판 사진(보정 화면의 비교용)
 * + 샘플 위치를 흔들면 답이 바뀌는 교점([ScanStability], #263 — 보정 화면의 「확인 필요」).
 */
internal class BoardScanResult(
    val detected: DetectedBoard,
    val corners: BoardCornerPoints,
    val boardPhoto: Bitmap,
    val uncertain: Set<BoardCoordinate>,
    /** 핀 화면을 건너뛰어도 될 만큼 확실한가 — [ScanStability.canSkipPins]. */
    val isConfident: Boolean,
)

/**
 * ⚠️ **기준 이미지 테스트와 같은 경로를 탄다**(백로그 #210) — 순수 Kotlin [BoardLocator] · [BoardWarp] · [GridStoneDetector].
 * OpenCV는 원근을 걷어낼 틀만 계산한다(#263, [OpenCvPerspective]) — 펴고 찾는 쪽([PerspectiveLocate])은 JVM 테스트가 기록된 틀로 밟는다.
 * 옛 `AndroidBitmapPerspectiveTransformer`(안드로이드 `Matrix`)는 JVM 테스트가 밟을 수 없어서 걷어냈다.
 * 비트맵은 [BoardPhotoDecoder]로 줄여서 넘길 것 — 픽셀을 한 번 배열로 복사한다.
 */
internal class AndroidBoardVisionScanner(
    private val sourceBitmap: Bitmap,
) : BoardVisionScannerPort {

    private val pixels: ArrayPixelSource by lazy { ArrayPixelSource.fromBitmap(sourceBitmap) }

    /**
     * 판을 스스로 찾는다 — 못 찾으면 `null`(수동 핀으로).
     * 원근이 센 사진은 **먼저 펴고** 찾는다(#263 — [OpenCvPerspective]가 틀을 주면 [PerspectiveLocate]). OpenCV를 못 올렸거나 원근이 약하면
     * 틀이 없고, 펴서 못 찾았으면 지금의 격자 찾기가 그대로 돈다.
     */
    suspend fun locate(): LocatedBoard? = withContext(Dispatchers.Default) {
        val frame = runCatching { OpenCvPerspective.frameOf(sourceBitmap) }.getOrNull()
        frame?.let { PerspectiveLocate.locate(pixels, it) } ?: BoardLocator.locate(pixels)
    }

    /**
     * 손으로 놓은 핀을 가까운 격자에 붙이고([BoardLocator.refine]) 돌을 읽는다.
     * [snap]이 `false`면 붙이지 않는다 — 자동 인식이 찾은(또는 지난번에 붙인) 핀을 건드리지 않았을 때. 이미 격자에 붙은 자리다.
     */
    suspend fun scan(corners: BoardCornerPoints, boardSize: BoardSize, snap: Boolean = true): BoardScanResult = withContext(Dispatchers.Default) {
        val refined = if (snap) BoardLocator.refine(pixels, corners, boardSize) else corners
        val board = BoardWarp.rectify(pixels, refined, boardSize)
        val detected = GridStoneDetector.detect(board)
        val photo = createBitmap(board.pixels.width, board.pixels.height)
        photo.setPixels(board.pixels.toArgbArray(), 0, board.pixels.width, 0, 0, board.pixels.width, board.pixels.height)
        val unstable = ScanStability.unstablePoints(pixels, refined, boardSize, detected.stones)
        val confident = ScanStability.canSkipPins(unstable, detected.stones, boardSize, refined, pixels.width, pixels.height)
        BoardScanResult(detected, refined, photo, unstable, confident)
    }

    override suspend fun detectStones(corners: BoardCornerPoints, boardSize: BoardSize): DetectedBoard =
        scan(corners, boardSize).detected
}
