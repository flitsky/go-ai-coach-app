package com.worksoc.goaicoach.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 바둑판 사진을 인식에 알맞게 읽는다(백로그 #210).
 *
 * ⚠️ **긴 변 [MaxDimension]px로 줄인다** — 폰 카메라 원본(1200만 화소)을 그대로 두면 인식이 픽셀 배열로 한 번 복사할 때
 * 48MB가 넘어 메모리가 모자랄 수 있고, 인식은 그만큼의 해상도가 필요 없다(내부 처리는 900px).
 * ⚠️ **갤러리 사진은 EXIF 회전을 적용한다** — 안 하면 세로로 찍은 사진이 옆으로 누운 채 들어와 핀 맞추기부터 어긋난다.
 * 카메라 촬영본은 CameraX가 준 회전값을 호출부가 이미 적용한다.
 */
internal object BoardPhotoDecoder {

    const val MaxDimension: Int = 2048

    fun decode(context: Context, uri: Uri, maxDimension: Int = MaxDimension): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        limit(applyOrientation(decoded, orientation), maxDimension)
    }.getOrNull()

    /** 긴 변이 [maxDimension]을 넘으면 비율을 지켜 줄인다. */
    fun limit(bitmap: Bitmap, maxDimension: Int = MaxDimension): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val f = maxDimension.toFloat() / longest
        return bitmap.scale((bitmap.width * f).roundToInt(), (bitmap.height * f).roundToInt())
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f)
                m.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
