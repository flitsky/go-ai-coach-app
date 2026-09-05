package com.worksoc.goaicoach.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 4계층(External Integration) — 설정 화면이 "새 버전이 있는가"를 묻는 유일한 지점(백로그 #53).
 *
 * ## 이 항목이 **확인만** 하고 **설치는 스토어에 넘기는** 이유
 * Play In-App Update API는 확인(`appUpdateInfo`)과 설치(`startUpdateFlowForResult`) 둘 다 하지만,
 * 여기서는 **확인만 쓴다.** 사용자가 요구한 것이 *"업데이트하러 **가기**"* 버튼이고, 설치 플로우는
 * `Activity` 결과 콜백·다운로드 진행 상태·재시작 처리를 끌고 들어오는데 **그 경로는 이 저장소에서
 * 실기로 밟아 볼 방법이 없다**(아래 함정 참고). 확인 결과만 쓰면 잘못될 수 있는 곳이 훨씬 적다.
 *
 * ## ⚠️ 실기 검증의 한계 — 착수 전부터 알고 있던 함정
 * 이 API는 **Play로 설치된 앱에서만** 동작한다. `adb install`한 빌드에서는
 * 조회가 실패하고 [AppUpdateStatus.Unknown]으로 떨어진다. 즉 에뮬레이터에서는 **폴백 경로만**
 * 볼 수 있고 "새 버전 있음"이 뜨는 모습은 볼 수 없다 — 그래서 **폴백이 죽은 화면이 되지 않는 것**이
 * 이 구현에서 가장 중요한 성질이고, 상태 판정을 순수 함수로 떼어 테스트로 고정했다.
 */
internal enum class AppUpdateStatus {
    /** 조회 중. 화면에는 아무것도 그리지 않는다 — 설정에 들어올 때마다 깜빡이면 소음이다. */
    Checking,

    /** 새 버전이 있다. */
    Available,

    /** 이미 최신이다. */
    UpToDate,

    /**
     * 확인하지 못했다 — Play 설치본이 아니거나, 네트워크가 없거나, Play 서비스가 없다.
     * ⚠️ **오류를 띄우지 않는다.** 대신 "스토어에서 확인" 링크로 폴백한다(선택지 ⓒ).
     */
    Unknown,
}

/**
 * Play가 준 availability 상수를 화면 상태로 옮긴다. **순수 함수라 테스트로 고정한다** —
 * 이 항목에서 실제로 틀리기 쉬운 곳이 그림이 아니라 이 대응표다.
 *
 * ⚠️ `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`도 [AppUpdateStatus.Available]로 본다. 그 값은
 * "이미 시작된 업데이트가 진행 중"이라는 뜻이라 **새 버전이 있다는 사실은 여전히 참이고**,
 * 스토어로 보내면 사용자가 그 진행 상황을 그대로 본다.
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
 * 설정 화면이 열릴 때 한 번 조회한다.
 *
 * ⚠️ **화면을 막지 않는다**(사용자 지시). `LaunchedEffect`로 비동기 조회하고 결과가 오면 그때
 * 상태가 바뀐다. 실패는 조용히 [AppUpdateStatus.Unknown]이 된다 — 업데이트 확인 실패로 설정
 * 화면이 경고를 띄우면 그건 소음이다.
 */
@Composable
internal fun rememberAppUpdateStatus(): AppUpdateStatus {
    val context = LocalContext.current
    var status by remember(context) { mutableStateOf(AppUpdateStatus.Checking) }
    LaunchedEffect(context) {
        status = runCatching { appUpdateStatusOf(requestUpdateAvailability(context)) }
            .getOrElse { AppUpdateStatus.Unknown }
    }
    return status
}

/**
 * 콜백 기반 `Task`를 코루틴으로 감싼다.
 *
 * ⚠️ **`app-update-ktx`를 쓰지 않는 것은 의도다.** 이 저장소는 Billing·AdMob·Auth 셋 다 콜백
 * API를 `suspendCancellableCoroutine`으로 직접 감싸는 쪽으로 통일돼 있다(`build.gradle.kts`의
 * billing 주석 참고) — 여기만 `-ktx`를 쓰면 같은 일을 두 방식으로 하게 된다.
 */
private suspend fun requestUpdateAvailability(context: Context): Int =
    suspendCancellableCoroutine { continuation ->
        AppUpdateManagerFactory.create(context).appUpdateInfo
            .addOnSuccessListener { info -> continuation.resume(info.updateAvailability()) }
            // 실패도 예외로 던지지 않고 값으로 돌려준다 — 호출부의 `runCatching`과 합쳐 두 겹이
            // 되지만, Play 서비스가 없는 기기에서는 리스너 등록 자체가 던지기도 한다.
            .addOnFailureListener { continuation.resume(UpdateAvailability.UNKNOWN) }
    }

/**
 * 이 앱의 스토어 페이지를 연다.
 *
 * ⚠️ **두 겹으로 시도한다.** `market://`은 Play 앱이 있어야 열리고 없으면
 * `ActivityNotFoundException`을 던진다 — 그때 `https://play.google.com/...`으로 넘어가면
 * 브라우저가 받는다. 둘 다 실패하는 기기(브라우저조차 없음)에서는 **조용히 아무 일도 없다**.
 *
 * ⚠️ 패키지 이름은 `BuildConfig`가 아니라 [Context.getPackageName]에서 읽는다 — 이 앱은
 * `applicationId`와 `namespace`가 다르고, 스토어가 아는 것은 전자다.
 */
internal fun openStoreListing(context: Context) {
    val id = context.packageName
    val attempts = listOf("market://details?id=$id", "https://play.google.com/store/apps/details?id=$id")
    attempts.firstOrNull { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    }
}

/**
 * 버전 정보 카드 아래에 붙는 업데이트 줄(백로그 #53).
 *
 * ⚠️ **버전 텍스트와 붙여 놓지 말 것.** 그 텍스트에는 **10번 두드리면 개발자 모드가 켜지는**
 * 숨은 제스처가 있다(`SettingsScreen.kt`) — 업데이트 버튼을 그 옆이나 위에 붙이면 오탭 한 번이
 * 개발자 모드를 연다. 그래서 개인정보처리방침 링크를 사이에 두고 **맨 아래 별도 행**으로 둔다.
 *
 * 조회 중에는 **아무것도 그리지 않는다** — 설정에 들어올 때마다 줄이 나타났다 바뀌면 소음이다.
 */
@Composable
internal fun AppUpdateRow(status: AppUpdateStatus, modifier: Modifier = Modifier) {
    if (status == AppUpdateStatus.Checking) return
    val context = LocalContext.current
    val language = LocalUiStrings.current.language
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        when (status) {
            AppUpdateStatus.Available -> {
                Text(
                    text = appUpdateAvailableLabelFor(language),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                // 새 버전이 있을 때만 버튼을 쓴다 — 나머지 상태는 알림이지 할 일이 아니다.
                Button(onClick = { openStoreListing(context) }) {
                    Text(appUpdateActionLabelFor(language), fontSize = 13.sp)
                }
            }

            AppUpdateStatus.UpToDate -> Text(
                text = appUpToDateLabelFor(language),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            // 확인하지 못한 경우의 폴백(선택지 ⓒ). 개인정보처리방침 링크와 같은 모양이라
            // "누를 수 있는 것"임이 이 화면 안에서 이미 학습돼 있다.
            AppUpdateStatus.Unknown -> Text(
                text = appUpdateCheckStoreLabelFor(language),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { openStoreListing(context) },
            )

            AppUpdateStatus.Checking -> Unit // 위에서 이미 걸렀다.
        }
    }
}
