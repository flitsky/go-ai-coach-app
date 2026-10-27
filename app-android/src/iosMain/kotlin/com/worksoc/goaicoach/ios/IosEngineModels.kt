package com.worksoc.goaicoach.ios

import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.engine.EngineDownloadTracker
import com.worksoc.goaicoach.engine.currentEngineDownloadTracker
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo

/**
 * iOS 엔진 모델의 출처(백로그 #293 · #294 — 사용자 2026-10-27: 설치 뒤 Apple이 호스팅하는 팩으로 받는다, 최소 iOS 26). Swift가 Background Assets로
 * 구현해 [IosAppRoot]에 넘긴다(그 API는 Swift 전용이라 Kotlin이 직접 부르지 못한다). 안드로이드의 Play 에셋 팩(PAD on-demand)과 같은 자리다.
 */
interface IosEngineModelSource {
    /** 받은 팩 안의 그 파일 경로 — 아직 받지 않았으면 `null`. */
    fun localFilePath(fileName: String): String?

    /** 받기를 시작한다 — 진행 · 끝 · 실패는 [listener]로(어느 스레드에서 와도 된다). 이미 받았으면 곧바로 끝을 알린다. */
    fun startDownload(listener: IosEngineModelDownloadListener)
}

interface IosEngineModelDownloadListener {
    fun onProgress(bytesDownloaded: Long, totalBytes: Long)

    fun onCompleted()

    fun onFailed(message: String)
}

/** 엔진이 여는 네 파일 — 안드로이드 `files/katago/`와 같은 이름(`seed-katago-model-to-app.sh` · PAD 팩). */
internal data class IosModelFiles(val model: String, val humanModel: String, val config: String, val analysisConfig: String?)

internal object IosModelFileNames {
    const val Model = "model.bin.gz"
    const val HumanModel = "human.bin.gz"
    const val Config = "gtp_learning.cfg"
    const val AnalysisConfig = "analysis_learning.cfg"
}

/**
 * 모델 · 설정을 찾는 차례: ① 환경 변수(`GOCOACH_KATAGO_*` — 시뮬레이터에서 맥의 파일, `make ios-run`) ② 앱 묶음의 `appassets/katago/`
 * ③ 받은 팩([IosEngineModelSource]). 두 모델과 설정이 모두 있어야 한다 — 사람 모델 없이 캐릭터 대국이 돌면 안 된다(#249 U-78: 두 모델 필수).
 */
internal fun locateIosModelFiles(source: IosEngineModelSource?): IosModelFiles? {
    val env = NSProcessInfo.processInfo.environment
    val files = NSFileManager.defaultManager
    fun exists(path: String?): String? = path?.takeIf { it.isNotBlank() && files.fileExistsAtPath(it) }
    val bundled = NSBundle.mainBundle.resourcePath?.let { "$it/appassets/katago" }
    fun find(envKey: String, name: String): String? =
        exists(env[envKey] as? String) ?: exists(bundled?.let { "$it/$name" }) ?: exists(source?.localFilePath(name))
    val model = find("GOCOACH_KATAGO_MODEL", IosModelFileNames.Model) ?: return null
    val human = find("GOCOACH_KATAGO_HUMAN_MODEL", IosModelFileNames.HumanModel) ?: return null
    val config = find("GOCOACH_KATAGO_CONFIG", IosModelFileNames.Config) ?: return null
    return IosModelFiles(model, human, config, find("GOCOACH_KATAGO_ANALYSIS_CONFIG", IosModelFileNames.AnalysisConfig))
}

/**
 * 팩을 받아 끝날 때까지 기다린다 — 안드로이드 `fetchAndAwaitAssetPacksWithRetry`와 같은 상태로 [EngineDownloadTracker]를 채운다(홈 배너 · 엔진 준비
 * 화면이 그대로 그린다): 받는 중 → 끝, 실패면 「다시 시도」를 누를 때까지 기다렸다 다시. 몇 번 실패해도 스텁으로 가지 않는다(#251과 같다).
 */
internal suspend fun awaitIosModelPack(
    source: IosEngineModelSource,
    tracker: EngineDownloadTracker = currentEngineDownloadTracker(),
): IosModelFiles? {
    locateIosModelFiles(source)?.let { return it }
    tracker.startNewDownload()
    while (currentCoroutineContext().isActive) {
        val failure = downloadOnce(source, tracker)
        if (failure == null) {
            val files = locateIosModelFiles(source)
            if (files != null) {
                tracker.updateStatus(EngineDownloadStatus.Completed)
                return files
            }
        }
        // ⚠️ 재시도 핸들러를 Failed보다 먼저 건다 — 반대면 Failed가 보인 직후의 탭이 사라진다(안드로이드와 같은 순서).
        val retry = CompletableDeferred<Unit>()
        tracker.registerRetryHandler { retry.complete(Unit) }
        tracker.updateStatus(EngineDownloadStatus.Failed(errorCode = 0, message = failure ?: "model pack incomplete after download"))
        retry.await()
    }
    return null
}

/** 한 번 받는다 — 끝나면 `null`, 실패면 그 사유. */
private suspend fun downloadOnce(source: IosEngineModelSource, tracker: EngineDownloadTracker): String? = suspendCancellableCoroutine { continuation ->
    source.startDownload(
        object : IosEngineModelDownloadListener {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long) {
                // 진행은 다른 스레드에서 온다 — 끝난 뒤 늦게 온 소식이 「완료」를 「받는 중」으로 되돌리지 않게.
                if (!continuation.isActive) return
                val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                tracker.updateStatus(EngineDownloadStatus.Downloading(bytesDownloaded, totalBytes, percent))
            }

            override fun onCompleted() {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onFailed(message: String) {
                if (continuation.isActive) continuation.resume(message)
            }
        },
    )
}
