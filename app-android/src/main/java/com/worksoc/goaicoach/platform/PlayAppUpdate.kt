package com.worksoc.goaicoach.platform

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 4계층(External Integration) — "새 버전이 있는가"를 Play에 묻는 유일한 지점(백로그 #53 → #255).
 *
 * #53에서는 설정 화면(`ui.settings`)에만 있었다. #255에서 **홈의 한 줄 알림**과 **엔진 받기 전의 확인**(엔진이 없고 새 버전이
 * 있으면 업데이트를 먼저 권한다 — 사용자 2026-10-13)이 같은 답을 써야 해서 UI 밖으로 옮겼다. 판정은 [appUpdateStatusOf] 하나다.
 *
 * ## 이 항목이 **확인만** 하고 **설치는 스토어에 넘기는** 이유
 * Play In-App Update API는 확인(`appUpdateInfo`)과 설치(`startUpdateFlowForResult`) 둘 다 하지만, 여기서는 **확인만 쓴다.**
 * 사용자가 요구한 것이 *"업데이트하러 **가기**"* 버튼이고(#53), #255에서도 스토어 페이지로 보내기로 다시 정했다(사용자 2026-10-13).
 * 설치 플로우는 `Activity` 결과 콜백·다운로드 진행 상태·재시작 처리를 끌고 들어온다.
 *
 * ## ⚠️ 실기 검증의 한계
 * 이 API는 **Play로 설치된 앱에서만** 동작한다. `adb install`한 빌드에서는 조회가 실패하고 [AppUpdateStatus.Unknown]으로
 * 떨어진다 — 에뮬레이터에서는 **폴백 경로만** 볼 수 있다. 그래서 판정을 순수 함수로 떼어 테스트로 고정했다.
 */
internal enum class AppUpdateStatus {
    /** 아직 묻지 않았거나 묻는 중. 화면에는 아무것도 그리지 않는다 — 들어올 때마다 깜빡이면 소음이다. */
    Checking,

    /** 새 버전이 있다. */
    Available,

    /** 이미 최신이다. */
    UpToDate,

    /**
     * 확인하지 못했다 — Play 설치본이 아니거나, 네트워크가 없거나, Play 서비스가 없다.
     * ⚠️ **오류를 띄우지 않는다.** 설정은 "스토어에서 확인" 링크로 폴백하고, 엔진 받기는 기다리지 않고 그대로 받는다.
     */
    Unknown,
}

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

/**
 * **프로세스마다 한 번** 묻고 그 답을 모두가 나눠 쓴다 — 엔진 받기 전의 확인 · 홈 알림 · 설정의 줄(#255).
 *
 * 한 번만 묻는 이유: 홈 알림은 「콜드 스타트마다 다시 뜬다」(사용자 2026-10-13)는 단위로 움직이고, 엔진 받기와 홈이 같은 순간에
 * 물으면 답이 갈릴 수 있다. 실패한 조회(취소 포함)는 [AppUpdateStatus.Checking]으로 남지 않게 [AppUpdateStatus.Unknown]으로
 * 적는다 — 단, 바깥의 시간 제한이 조회를 **취소**하면 아무것도 적지 않아 다음 호출이 다시 묻는다.
 */
internal class AppUpdateCheck(private val request: suspend () -> AppUpdateStatus) {
    private val _status = MutableStateFlow(AppUpdateStatus.Checking)
    val status: StateFlow<AppUpdateStatus> = _status.asStateFlow()
    private val mutex = Mutex()

    /** 아직 답이 없으면 묻고, 있으면 그 답을 돌려준다. 동시에 부르면 뒤의 호출은 앞의 답을 기다린다. */
    suspend fun ensureChecked(): AppUpdateStatus = mutex.withLock {
        _status.value.takeIf { it != AppUpdateStatus.Checking }?.let { return@withLock it }
        val result = try {
            request()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppUpdateStatus.Unknown
        }
        _status.value = result
        result
    }

    private val _noticeDismissed = MutableStateFlow(false)

    /**
     * 홈의 업데이트 알림을 ✕로 닫았는가. **저장하지 않는다** — 이 객체는 프로세스와 함께 사라지므로 **콜드 스타트마다 다시 뜬다**
     * (사용자 2026-10-13). 답(「새 버전 있음」)과 수명이 같아 여기 둔다 — 셸의 상태 훅 예산이 0이고(함정 3) 전역 상태는 동결이다.
     */
    val noticeDismissed: StateFlow<Boolean> = _noticeDismissed.asStateFlow()

    fun dismissNotice() {
        _noticeDismissed.value = true
    }

    /** **디버그 빌드 전용** — Play 설치본이 아니면 「새 버전 있음」을 볼 길이 없어, 미리보기가 답을 정해 준다. */
    fun forceStatusForDebug(status: AppUpdateStatus) {
        _status.value = status
    }
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
