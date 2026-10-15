package com.worksoc.goaicoach.ui.vision

import android.content.Intent
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
 * 1.5.0부터 늘 공유 목록에 뜬다(사진 분석이 본 기능이 됐다 — 사용자 2026-10-15). 그 전 실험실 시절에는 스위치로 컴포넌트를 켜고 껐는데,
 * 껐던 사람의 컴포넌트 상태는 「기본값」으로 돌려 두었으므로 매니페스트(켜짐)를 따른다 — 업데이트만으로 모두 켜진다.
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
