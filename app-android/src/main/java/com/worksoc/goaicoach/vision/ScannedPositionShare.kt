package com.worksoc.goaicoach.vision

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 인식한 국면을 SGF **파일로** 다른 앱에 보낸다(백로그 #263) — 메신저·드라이브·다른 바둑 앱.
 *
 * ⚠️ **글자(EXTRA_TEXT)로 보내지 않는다** — 메신저가 기보를 긴 글자 한 덩이로 붙여 버려 받는 쪽이 열 수 없다.
 * 파일은 캐시의 `sgf/`에 쓰고 [FileProvider]로 읽기 권한만 빌려준다(매니페스트의 `fileprovider` · `res/xml/sgf_share_paths.xml`).
 * 앞서 보낸 파일은 지운다 — 받는 앱이 이미 복사해 갔고, 남겨 두면 캐시에 쌓인다.
 */
internal object ScannedPositionShare {
    private const val DirName = "sgf"
    private const val MimeType = "application/x-go-sgf"

    fun send(context: Context, sgf: String, chooserTitle: String) {
        val dir = File(context.cacheDir, DirName).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "board-${System.currentTimeMillis()}.sgf").apply { writeText(sgf) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // 고르는 창을 거치면 EXTRA_STREAM의 권한이 받는 앱까지 안 갈 수 있다 — ClipData에도 실어야 함께 넘어간다.
        send.clipData = ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
