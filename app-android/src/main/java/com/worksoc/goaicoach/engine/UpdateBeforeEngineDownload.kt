package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.platform.AppUpdateStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 업데이트 확인을 기다리는 최대 시간(#255). 그동안 엔진 준비 화면은 「준비하고 있어요」를 보인다.
 * ⚠️ 스레드가 정한 숫자다(2026-10-13) — 사용자 확인 전. 넘기면 「모름」으로 보고 바로 받는다.
 */
internal const val UpdateCheckBeforeDownloadTimeoutMillis = 5_000L

/**
 * 엔진 팩을 받기 **전에** — 새 버전이 있으면 업데이트를 먼저 권하고, 사용자가 「이대로 받기」를 고를 때까지 받기를 미룬다(#255).
 *
 * **왜**(사용자 2026-10-13: *"엔진은 용량이 크니, 업데이트 먼저 해서 최신 앱에서 엔진을 받게"*): 엔진(약 197MB)은 어느 버전에서
 * 받든 크기가 같고 업데이트는 6MB대라(#246) 먼저 해도 손해가 없다. 얻는 것 — 옛 앱의 받기 경로 결함을 피하고(#247·#251~#254가
 * 그런 수정이었다), 모델이 바뀌는 버전이 오면 옛 팩을 받았다 다시 받는 일이 없다.
 *
 * ⚠️ **확인이 받기를 막지 않는다** — 「최신」·「모름」(오프라인 · Play 설치본이 아님 · 개발 빌드)·시간 초과면 곧장 돌아가
 * 지금처럼 받는다. 엔진의 「아직」과 「실패」는 다르다 — 확인을 못 했다는 이유로 엔진을 못 받게 하지 않는다.
 *
 * 받을 팩이 있을 때만 부른다(`createEngineBootstrap`의 3단계 앞) — 엔진이 이미 있는 사람은 이 문을 지나지 않는다.
 */
internal suspend fun awaitUpdateBeforeEngineDownload(
    tracker: EngineDownloadTracker,
    checkUpdate: suspend () -> AppUpdateStatus,
    timeoutMillis: Long = UpdateCheckBeforeDownloadTimeoutMillis,
) {
    val update = withTimeoutOrNull(timeoutMillis) { checkUpdate() } ?: AppUpdateStatus.Unknown
    if (update != AppUpdateStatus.Available) return
    val skipped = CompletableDeferred<Unit>()
    tracker.registerSkipUpdateHandler { skipped.complete(Unit) }
    tracker.updateStatus(EngineDownloadStatus.UpdateFirst)
    padLog("update available - waiting for the user before fetching packs")
    skipped.await()
    tracker.registerSkipUpdateHandler {}
    // 받기가 곧 Downloading을 적는다 — 그 사이(수 ms)는 「준비하고 있어요」다.
    tracker.updateStatus(EngineDownloadStatus.Idle)
    padLog("user chose to download without updating")
}
