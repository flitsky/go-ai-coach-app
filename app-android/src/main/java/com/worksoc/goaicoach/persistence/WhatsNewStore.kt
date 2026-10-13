package com.worksoc.goaicoach.persistence

import android.content.Context

/**
 * 「새로워진 점」을 마지막으로 본 버전(백로그 #257). 홈 한 줄을 열어 보거나 ✕로 닫으면 지금 버전을 적고, 새로 설치한 사람은 처음 확인할 때 적는다
 * (`application/guide/WhatsNewPolicy.kt`가 이 값으로 무엇을 보일지 정한다).
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2 — 자동저장이 스냅샷을 처음부터 다시 조립해 배선되지 않은 필드를 초기화한다).
 * 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다(함정 55). 권한이 아니라 `ReleaseResetCoordinator`의
 * 목록(함정 6)에는 넣지 않는다 — 지워져도 잃는 것이 「새로워진 점」을 한 번 더 보는 것뿐이다.
 */
internal class WhatsNewStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun lastSeenVersionCode(): Int? = prefs.getInt(LastSeenKey, NotSeen).takeIf { it != NotSeen }

    fun markSeen(versionCode: Int) {
        prefs.edit().putInt(LastSeenKey, versionCode).apply()
    }

    /**
     * 이 설치가 한 번도 업데이트되지 않았는가 — 설치한 시각과 마지막으로 바뀐 시각이 같다. 확인하지 못하면 새 설치로 본다
     * (모르는 채로 「새로워진 점」을 띄우는 쪽보다 조용한 쪽이 낫다).
     */
    fun isFreshInstall(): Boolean = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(true)

    private companion object {
        const val PrefsName = "go_ai_coach_whats_new"
        const val LastSeenKey = "last_seen_version_code"
        const val NotSeen = -1
    }
}
