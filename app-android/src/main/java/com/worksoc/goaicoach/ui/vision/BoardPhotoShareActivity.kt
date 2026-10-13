package com.worksoc.goaicoach.ui.vision

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.worksoc.goaicoach.MainActivity
import com.worksoc.goaicoach.persistence.UiLanguageStore
import com.worksoc.goaicoach.ui.l10n.boardScanStringsFor
import com.worksoc.goaicoach.ui.l10n.savedUiLanguage
import com.worksoc.goaicoach.vision.SharedBoardPhotoInbox
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
            val received = withContext(Dispatchers.IO) { SharedBoardPhotoInbox.put(this@BoardPhotoShareActivity, uri) }
            if (!received) {
                // 조용히 삼키지 않는다 — 앱만 열리고 아무 일도 없으면 사용자는 공유가 됐는지 모른다.
                val language = savedUiLanguage(UiLanguageStore(this@BoardPhotoShareActivity))
                Toast.makeText(this@BoardPhotoShareActivity, boardScanStringsFor(language).photoLoadFailed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            startActivity(
                Intent(this@BoardPhotoShareActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
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
