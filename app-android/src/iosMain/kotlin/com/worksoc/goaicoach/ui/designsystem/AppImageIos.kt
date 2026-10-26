package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * iOS의 그림 — 보상 아이콘은 코드로 옮긴 벡터, 나머지는 앱 묶음의 비트맵(`scripts/ios-bundle-resources.sh`가 복사한다).
 * 비트맵은 한 번 읽어 프로세스 동안 들고 있다(안드로이드 리소스 캐시와 같은 몫). 파일이 없으면 투명 칸이다.
 */
@Composable
actual fun painterFor(image: AppImage): Painter {
    val vector = when (image) {
        AppImage.reward_eval -> RewardEval
        AppImage.reward_top_moves -> RewardTopMoves
        AppImage.reward_ad_skip -> RewardAdSkip
        AppImage.reward_undo -> RewardUndo
        AppImage.reward_move_review -> RewardMoveReview
        else -> null
    }
    if (vector != null) return rememberVectorPainter(vector)
    val bitmap = remember(image) { bundledBitmap(image) }
    return remember(bitmap) { bitmap?.let(::BitmapPainter) ?: ColorPainter(androidx.compose.ui.graphics.Color.Transparent) }
}

private val loaded = mutableMapOf<AppImage, ImageBitmap?>()

private fun bundledBitmap(image: AppImage): ImageBitmap? = loaded.getOrPut(image) {
    val path = listOf("webp", "jpg", "png").firstNotNullOfOrNull { extension ->
        NSBundle.mainBundle.pathForResource(image.name, ofType = extension, inDirectory = "appassets/drawable")
    } ?: return@getOrPut null
    val bytes = NSData.dataWithContentsOfFile(path)?.toByteArray() ?: return@getOrPut null
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    return bytes
}
