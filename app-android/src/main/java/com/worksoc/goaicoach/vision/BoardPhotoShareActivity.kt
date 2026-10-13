package com.worksoc.goaicoach.vision

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.worksoc.goaicoach.MainActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 다른 앱의 「공유」로 바둑판 사진을 받는 문(백로그 #263) — 중계 화면 캡처 · 메신저로 받은 사진을 바로 사진 분석으로.
 *
 * 받은 사진을 [SharedBoardPhotoInbox]에 **복사해 두고** 앱 본 화면을 앞으로 부른 뒤 곧바로 닫힌다. 화면은 없다.
 * ⚠️ **복사하는 이유**: 보낸 앱이 빌려준 읽기 권한은 이 액티비티가 닫히면 사라질 수 있다 — 주소만 넘기면 본 화면이
 * 읽으려 할 때 이미 늦는다.
 * ⚠️ **실험실 스위치가 꺼져 있으면 공유 목록에 이 앱이 안 뜬다** — 매니페스트에서 꺼 두고([BoardPhotoShareTarget]) 스위치로 켠다.
 * 본 화면은 `singleTop`이 아니라서 `CLEAR_TOP | SINGLE_TOP`로 **있던 화면을 앞으로** 부른다(새 화면을 하나 더 쌓지 않게).
 */
class BoardPhotoShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (intent?.action != Intent.ACTION_SEND || uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { SharedBoardPhotoInbox.put(this@BoardPhotoShareActivity, uri) }
            startActivity(
                Intent(this@BoardPhotoShareActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
    }
}

/**
 * 공유로 들어와 아직 열리지 않은 사진 한 장 — **캐시의 파일 하나가 곧 상태다**(백로그 #263).
 *
 * [BoardPhotoShareActivity]가 넣고, 홈은 다시 보일 때마다 [hasPhoto]를 보고 사진 분석으로 가고, 사진 분석 화면이 [take]로 꺼낸다.
 * ⚠️ **컴포즈 상태로 두지 않는다** — 프로세스 전역 컴포즈 상태는 셸 원장이 막는다(`noNewProcessGlobalComposeStateAppears`).
 * 파일이라서 앱이 내려갔다 다시 떠도 받은 사진이 남는다.
 */
internal object SharedBoardPhotoInbox {
    private const val DirName = "board-scan"
    private const val InboxName = "shared-photo"
    private const val OpenedName = "shared-photo-opened"

    private fun dir(context: Context) = File(context.cacheDir, DirName)

    fun hasPhoto(context: Context): Boolean = File(dir(context), InboxName).length() > 0

    /** 보낸 앱의 사진을 받은편지함에 복사한다 — 다 쓴 뒤 이름을 바꿔 넣으므로 반쯤 쓴 파일을 읽지 않는다. */
    fun put(context: Context, uri: Uri) {
        runCatching {
            val dir = dir(context).apply { mkdirs() }
            val partial = File(dir, "$InboxName.partial")
            context.contentResolver.openInputStream(uri)?.use { input -> partial.outputStream().use { input.copyTo(it) } } ?: return
            if (partial.length() > 0) partial.renameTo(File(dir, InboxName))
        }
    }

    /** 받은 사진을 꺼낸다 — 다른 이름으로 옮겨서 주므로, 홈에 다시 와도 같은 사진으로 또 열리지 않는다. */
    fun take(context: Context): File? {
        val inbox = File(dir(context), InboxName)
        if (inbox.length() <= 0) return null
        val opened = File(dir(context), OpenedName).apply { delete() }
        return opened.takeIf { inbox.renameTo(it) }
    }
}

/**
 * 공유 목록에 이 앱을 띄울지를 실험실 스위치와 맞춘다(백로그 #263).
 *
 * 매니페스트의 [BoardPhotoShareActivity]는 `enabled="false"`로 시작한다. 스위치를 바꿀 때와 **앱이 뜰 때마다** 맞춘다 —
 * 컴포넌트 상태는 백업으로 돌아오지 않으므로, 재설치 뒤 복원된 스위치가 켜져 있어도 공유 목록에는 안 뜰 수 있다.
 */
internal object BoardPhotoShareTarget {
    fun sync(context: Context, enabled: Boolean) {
        val component = ComponentName(context, BoardPhotoShareActivity::class.java)
        val wanted = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        if (context.packageManager.getComponentEnabledSetting(component) == wanted) return
        context.packageManager.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
    }
}
