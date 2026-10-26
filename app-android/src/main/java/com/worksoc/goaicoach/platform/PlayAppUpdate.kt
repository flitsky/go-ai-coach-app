package com.worksoc.goaicoach.platform

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Play가 준 availability 상수를 상태로 옮긴다. **순수 함수라 테스트로 고정한다** — 이 항목에서 실제로 틀리기 쉬운 곳이
 * 그림이 아니라 이 대응표다.
 *
 * ⚠️ `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`도 [AppUpdateStatus.Available]로 본다. 그 값은 "이미 시작된 업데이트가
 * 진행 중"이라는 뜻이라 **새 버전이 있다는 사실은 여전히 참이고**, 스토어로 보내면 사용자가 그 진행 상황을 그대로 본다.
 */
internal fun appUpdateStatusOf(availability: Int): AppUpdateStatus = when (availability) {
    UpdateAvailability.UPDATE_AVAILABLE,
    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
    -> AppUpdateStatus.Available

    UpdateAvailability.UPDATE_NOT_AVAILABLE -> AppUpdateStatus.UpToDate
    // UNKNOWN(0)과 앞으로 늘어날 값 전부. 모르는 값을 "최신"으로 단정하면 안 된다 —
    // 업데이트가 있는데 없다고 말하는 쪽이 반대보다 나쁘다.
    else -> AppUpdateStatus.Unknown
}

private val processCheckLock = Any()

@Volatile
private var processCheck: AppUpdateCheck? = null

/** 이 프로세스의 [AppUpdateCheck] — `applicationContext`로 만든다(화면의 Context를 붙잡지 않는다). */
internal fun processAppUpdateCheck(context: Context): AppUpdateCheck =
    processCheck ?: synchronized(processCheckLock) {
        processCheck ?: run {
            val appContext = context.applicationContext
            AppUpdateCheck { appUpdateStatusOf(requestUpdateAvailability(appContext)) }.also { processCheck = it }
        }
    }

/**
 * 콜백 기반 `Task`를 코루틴으로 감싼다.
 *
 * ⚠️ **`app-update-ktx`를 쓰지 않는 것은 의도다.** 이 저장소는 Billing·AdMob·Auth 셋 다 콜백 API를
 * `suspendCancellableCoroutine`으로 직접 감싸는 쪽으로 통일돼 있다(`build.gradle.kts`의 billing 주석 참고).
 */
private suspend fun requestUpdateAvailability(context: Context): Int =
    suspendCancellableCoroutine { continuation ->
        AppUpdateManagerFactory.create(context).appUpdateInfo
            .addOnSuccessListener { info -> if (continuation.isActive) continuation.resume(info.updateAvailability()) }
            // 실패도 예외로 던지지 않고 값으로 돌려준다 — Play 서비스가 없는 기기에서는 리스너 등록 자체가 던지기도 한다
            // (그건 [AppUpdateCheck.ensureChecked]가 받는다).
            .addOnFailureListener { if (continuation.isActive) continuation.resume(UpdateAvailability.UNKNOWN) }
    }

/**
 * 이 앱의 스토어 페이지를 연다.
 *
 * ⚠️ **두 겹으로 시도한다.** `market://`은 Play 앱이 있어야 열리고 없으면 `ActivityNotFoundException`을 던진다 —
 * 그때 `https://play.google.com/...`으로 넘어가면 브라우저가 받는다. 둘 다 실패하는 기기에서는 **조용히 아무 일도 없다**.
 *
 * ⚠️ 패키지 이름은 `BuildConfig`가 아니라 [Context.getPackageName]에서 읽는다 — 이 앱은 `applicationId`와
 * `namespace`가 다르고, 스토어가 아는 것은 전자다.
 */
internal fun openStoreListing(context: Context) {
    val id = context.packageName
    val attempts = listOf("market://details?id=$id", "https://play.google.com/store/apps/details?id=$id")
    attempts.firstOrNull { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }.isSuccess
    }
}
