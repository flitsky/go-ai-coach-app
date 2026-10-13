package com.worksoc.goaicoach.engine

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.platform.AppUpdateCheck
import com.worksoc.goaicoach.platform.AppUpdateStatus
import com.worksoc.goaicoach.platform.processAppUpdateCheck
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 실행 게이트(엔진 준비 화면, 백로그 #249)를 띄워야 하는가 — 두 모델 중 하나라도 없으면 그렇다(U-78: 둘 다 필수).
 *
 * 판정은 부트스트랩과 같은 [locateEngineModels] 하나다 — 둘이 갈리면 화면은 「준비됨」인데 엔진은 받는 중이 된다.
 * 디스크 확인과 `getPackLocation`뿐이라 **첫 컴포지션에서 동기로** 부른다 — 비동기 부트스트랩의 답을 기다리면
 * 스플래시가 걷힌 뒤 홈이 먼저 보였다가 가이드로 바뀐다.
 *
 * 디버그 빌드에서는 [DebugEngineSetupPreview]가 걸려 있으면 모델이 있어도 띄운다.
 */
internal fun isEngineSetupRequired(context: Context): Boolean {
    if (BuildConfig.DEBUG && DebugEngineSetupPreview.consume(context.filesDir, currentEngineDownloadTracker(), processAppUpdateCheck(context))) return true
    val katagoDir = File(context.filesDir, "katago")
    // 기기 파일부터 본다(#254) — 1.3.0 이하 사용자는 여기서 끝나 시작 때마다 메인 스레드에서 Play 객체를 만들지 않는다.
    if (locateEngineModels(katagoDir, assetPackManager = null).missingPacks.isEmpty()) return false
    val manager = try {
        AssetPackManagerFactory.getInstance(context)
    } catch (e: Exception) {
        null
    }
    return locateEngineModels(katagoDir, manager).missingPacks.isNotEmpty()
}

/**
 * **디버그 빌드 전용** — 엔진 준비 화면(#249)을 가짜 진행으로 한 번 띄운다. debug 설치는 모델이 이미 있어 받기를 타지
 * 않으므로(진짜 받기는 Play가 아는 설치에서만 돈다) 화면과 진행 카드를 에뮬레이터에서 보려면 이 길뿐이다.
 *
 * ```
 * adb shell "run-as com.zenit9hub.ai.baduk sh -c 'echo ok > files/debug-engine-setup-preview'"
 * ```
 * (바깥 큰따옴표가 없으면 `>`가 기기의 셸 사용자 권한으로 풀려 실패한다.)
 * 다음 실행이 파일을 읽고 **지운 뒤**(한 번만) 화면을 띄우고 받기 상태를 가짜로 움직인다. 모드:
 * - `ok` — 약 15초 동안 0→100% 받고 완료한다.
 * - `fail` — 40%에서 네트워크 오류로 멈춘다. 「다시 시도」를 누르면 이어서 완료한다.
 * - `wifi` — 40%에서 Wi-Fi 대기로 6초 멈췄다가(Wi-Fi가 잡힌 것처럼) 이어서 완료한다.
 * - `update` — 「새 버전 있음」으로 정하고(#255) 받기 전에 업데이트를 권한다. 「이대로 받기」를 누르면 `ok`처럼 받는다.
 *   받은 뒤 홈에는 업데이트 알림이 남는다(Play 설치본이 아니면 「새 버전 있음」을 볼 길이 이것뿐이다).
 *
 * 진짜 엔진은 이와 무관하게 평소대로 뜬다. 화면의 「시작하기」는 가짜 완료가 와야 열린다.
 */
internal object DebugEngineSetupPreview {
    const val ArmFileName = "debug-engine-setup-preview"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun consume(filesDir: File, tracker: EngineDownloadTracker, updateCheck: AppUpdateCheck): Boolean {
        val arm = File(filesDir, ArmFileName)
        if (!arm.isFile) return false
        val mode = runCatching { arm.readText().trim() }.getOrDefault("ok")
        arm.delete()
        scope.launch {
            if (mode == "update") {
                updateCheck.forceStatusForDebug(AppUpdateStatus.Available)
                awaitUpdateBeforeEngineDownload(tracker, checkUpdate = { AppUpdateStatus.Available })
            }
            simulate(mode, tracker)
        }
        return true
    }

    private suspend fun simulate(mode: String, tracker: EngineDownloadTracker) {
        val total = 197_000_000L
        tracker.startNewDownload()
        var percent = 0
        while (percent < 100) {
            if (percent == 40 && mode == "fail") {
                val retried = kotlinx.coroutines.CompletableDeferred<Unit>()
                tracker.registerRetryHandler { retried.complete(Unit) }
                tracker.updateStatus(EngineDownloadStatus.Failed(errorCode = -6, message = "debug preview"))
                retried.await()
            }
            if (percent == 40 && mode == "wifi") {
                tracker.updateStatus(EngineDownloadStatus.WaitingForWifi(bytesDownloaded = total * percent / 100, totalBytesToDownload = total))
                delay(6_000L)
            }
            tracker.updateStatus(EngineDownloadStatus.Downloading(bytesDownloaded = total * percent / 100, totalBytesToDownload = total, percentage = percent))
            delay(150L)
            percent += 1
        }
        tracker.updateStatus(EngineDownloadStatus.Completed)
    }
}
