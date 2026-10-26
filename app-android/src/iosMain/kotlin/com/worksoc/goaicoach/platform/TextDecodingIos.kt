package com.worksoc.goaicoach.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.CFStringConvertIANACharSetNameToEncoding
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingDOSJapanese
import platform.CoreFoundation.kCFStringEncodingDOSKorean
import platform.CoreFoundation.kCFStringEncodingGB_18030_2000
import platform.CoreFoundation.kCFStringEncodingInvalidId
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSStringEncoding
import platform.Foundation.create

/**
 * SGF의 `CA[]`가 적은 글자 집합으로 푼다(#294 — 기보 파일 열기). 안드로이드는 자바의 `Charset.forName`이 이름을 다 알고, 모르는 이름이면 UTF-8이다.
 * iOS도 같게: 이름은 CoreFoundation의 IANA 이름표로 찾고(EUC-JP · windows-1252 · Big5 …), 모르는 이름이거나 이름이 없으면 UTF-8이다.
 * 이름표보다 먼저 상위 집합으로 모으는 것 셋 — 하위 집합 변환기는 확장 글자를 만나면 파일 전체를 거부해 UTF-8로 떨어진다:
 * 한국어 → UHC(CP949 — 안드로이드 `x-windows-949`, EUC-KR을 품는다) · 일본어 Shift_JIS 계열 → Windows-31J · 중국어 간체 → GB18030.
 * 풀지 못하면(글자 집합과 다른 바이트) UTF-8로 푼다 — 안드로이드는 대체 문자로 푸는데, 여기서는 그만큼 섬세하게 맞추지 않았다.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun decodeText(bytes: ByteArray, charsetName: String?): String {
    val encoding = charsetName?.trim()?.takeIf { it.isNotEmpty() }?.let(::nsEncodingFor) ?: return bytes.decodeToString()
    val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
    return NSString.create(data = data, encoding = encoding)?.toString() ?: bytes.decodeToString()
}

@OptIn(ExperimentalForeignApi::class)
private fun nsEncodingFor(name: String): NSStringEncoding? {
    val superset = when (name.uppercase().replace('_', '-')) {
        "EUC-KR", "KS-C-5601-1987", "KSC5601", "CP949", "MS949", "UHC", "WINDOWS-949", "X-WINDOWS-949" -> kCFStringEncodingDOSKorean
        "SHIFT-JIS", "SJIS", "MS932", "CP932", "WINDOWS-31J" -> kCFStringEncodingDOSJapanese
        "GB2312", "GBK", "GB18030", "CP936", "EUC-CN" -> kCFStringEncodingGB_18030_2000
        else -> null
    }
    if (superset != null) return CFStringConvertEncodingToNSStringEncoding(superset.toUInt())
    val cfName = CFStringCreateWithCString(null, name, kCFStringEncodingUTF8) ?: return null
    return try {
        val encoding = CFStringConvertIANACharSetNameToEncoding(cfName)
        if (encoding == kCFStringEncodingInvalidId) null else CFStringConvertEncodingToNSStringEncoding(encoding)
    } finally {
        CFRelease(cfName)
    }
}
