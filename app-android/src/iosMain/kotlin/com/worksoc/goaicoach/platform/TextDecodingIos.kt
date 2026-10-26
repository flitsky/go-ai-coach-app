package com.worksoc.goaicoach.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.kCFStringEncodingBig5
import platform.CoreFoundation.kCFStringEncodingEUC_KR
import platform.CoreFoundation.kCFStringEncodingGB_18030_2000
import platform.CoreFoundation.kCFStringEncodingShiftJIS
import platform.Foundation.NSData
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSString
import platform.Foundation.create

/**
 * SGF가 흔히 적는 글자 집합만 안다(EUC-KR · Shift_JIS · GB2312/GBK/GB18030 · Big5 · ISO-8859-1) — 나머지와 이름이 없으면 UTF-8이다.
 * 안드로이드는 자바의 `Charset.forName`이 모든 이름을 안다(#294 — 기보 파일 열기).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun decodeText(bytes: ByteArray, charsetName: String?): String {
    val name = charsetName?.trim()?.uppercase().orEmpty().replace("_", "-")
    val encoding = when (name) {
        "EUC-KR", "KS-C-5601-1987", "CP949", "MS949" -> CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingEUC_KR.toUInt())
        "SHIFT-JIS", "SJIS", "MS932", "CP932" -> CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingShiftJIS.toUInt())
        "GB2312", "GBK", "GB18030", "CP936" -> CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingGB_18030_2000.toUInt())
        "BIG5" -> CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingBig5.toUInt())
        "ISO-8859-1", "LATIN1" -> NSISOLatin1StringEncoding
        else -> return bytes.decodeToString()
    }
    val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
    return NSString.create(data = data, encoding = encoding)?.toString() ?: bytes.decodeToString()
}
