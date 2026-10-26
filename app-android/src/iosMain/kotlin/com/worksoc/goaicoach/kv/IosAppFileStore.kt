package com.worksoc.goaicoach.kv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.posix.O_APPEND
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.fsync
import platform.posix.open
import platform.posix.rename
import platform.posix.unlink
import platform.posix.write

/**
 * iOS의 [AppFileStore](백로그 #294 — S33) — Application Support 아래 `files/`가 뿌리다(안드로이드 `filesDir`의 자리).
 * 원자적 쓰기는 POSIX로 한다: 임시 파일에 쓰고 `fsync` 뒤 `rename` — 안드로이드 `GameHistoryStore.writeAtomically`와 같은 순서.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosAppFileStore(private val rootPath: String = defaultRoot()) : AppFileStore {
    private val fileManager = NSFileManager.defaultManager

    init {
        fileManager.createDirectoryAtPath(rootPath, withIntermediateDirectories = true, attributes = null, error = null)
    }

    private fun absolute(path: String) = "$rootPath/$path"

    override fun exists(path: String): Boolean = fileManager.fileExistsAtPath(absolute(path))

    override fun readText(path: String): String? =
        NSString.stringWithContentsOfFile(absolute(path), encoding = NSUTF8StringEncoding, error = null)

    override fun writeAtomically(path: String, text: String): Boolean {
        val target = absolute(path)
        val temp = "$target.tmp"
        val written = writeBytes(temp, text.encodeToByteArray(), O_WRONLY or O_CREAT or O_TRUNC, sync = true)
        if (written && rename(temp, target) == 0) return true
        unlink(temp)
        return false
    }

    override fun appendText(path: String, text: String): Boolean =
        writeBytes(absolute(path), text.encodeToByteArray(), O_WRONLY or O_CREAT or O_APPEND, sync = false)

    override fun length(path: String): Long {
        val attributes = fileManager.attributesOfItemAtPath(absolute(path), error = null) ?: return 0L
        return (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
    }

    override fun list(path: String): List<String>? =
        fileManager.contentsOfDirectoryAtPath(absolute(path), error = null)?.filterIsInstance<String>()

    override fun delete(path: String): Boolean = fileManager.removeItemAtPath(absolute(path), error = null)

    override fun deleteRecursively(path: String): Boolean =
        !exists(path) || fileManager.removeItemAtPath(absolute(path), error = null)

    override fun mkdirs(path: String): Boolean =
        fileManager.createDirectoryAtPath(absolute(path), withIntermediateDirectories = true, attributes = null, error = null)

    private fun writeBytes(path: String, bytes: ByteArray, flags: Int, sync: Boolean): Boolean {
        val fd = open(path, flags, 0x1A4) // 0644
        if (fd < 0) return false
        try {
            var offset = 0
            while (offset < bytes.size) {
                val count = bytes.usePinned { pinned -> write(fd, pinned.addressOf(offset), (bytes.size - offset).convert()) }
                if (count <= 0) return false
                offset += count.toInt()
            }
            return !sync || fsync(fd) == 0
        } finally {
            close(fd)
        }
    }

    companion object {
        fun defaultRoot(): String {
            val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
                .firstOrNull() as? String ?: error("no Application Support directory")
            return "$support/files"
        }
    }
}
