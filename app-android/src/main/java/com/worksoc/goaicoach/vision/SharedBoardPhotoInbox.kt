package com.worksoc.goaicoach.vision

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * 공유로 들어와 아직 열리지 않은 사진 한 장 — **캐시의 파일 하나가 곧 상태다**(백로그 #263).
 *
 * `ui/vision/BoardPhotoShareActivity`가 넣고, 홈은 다시 보일 때마다 [hasPhoto]를 보고 사진 분석으로 가고, 사진 분석 화면이 [take]로 꺼낸다.
 * ⚠️ **컴포즈 상태로 두지 않는다** — 프로세스 전역 컴포즈 상태는 셸 원장이 막는다(`noNewProcessGlobalComposeStateAppears`).
 * 파일이라서 앱이 내려갔다 다시 떠도 받은 사진이 남는다.
 */
internal object SharedBoardPhotoInbox {
    private const val DirName = "board-scan"
    private const val InboxName = "shared-photo"
    private const val OpenedName = "shared-photo-opened"
    private const val LogTag = "BoardPhotoShare"

    private fun dir(context: Context) = File(context.cacheDir, DirName)

    fun hasPhoto(context: Context): Boolean = File(dir(context), InboxName).length() > 0

    /** 보낸 앱의 사진을 받은편지함에 복사한다 — 다 쓴 뒤 이름을 바꿔 넣으므로 반쯤 쓴 파일을 읽지 않는다. 못 받았으면 `false`. */
    fun put(context: Context, uri: Uri): Boolean = runCatching {
        val dir = dir(context).apply { mkdirs() }
        val partial = File(dir, "$InboxName.partial")
        val copied = context.contentResolver.openInputStream(uri)?.use { input -> partial.outputStream().use { input.copyTo(it) } }
        copied != null && copied > 0 && partial.renameTo(File(dir, InboxName))
    }.onFailure { Log.w(LogTag, "공유받은 사진을 복사하지 못했다", it) }.getOrDefault(false)

    /** 받은 사진을 꺼낸다 — 다른 이름으로 옮겨서 주므로, 홈에 다시 와도 같은 사진으로 또 열리지 않는다. */
    fun take(context: Context): File? {
        val inbox = File(dir(context), InboxName)
        if (inbox.length() <= 0) return null
        val opened = File(dir(context), OpenedName).apply { delete() }
        return opened.takeIf { inbox.renameTo(it) }
    }
}
