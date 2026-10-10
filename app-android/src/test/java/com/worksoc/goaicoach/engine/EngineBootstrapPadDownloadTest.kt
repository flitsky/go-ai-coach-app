package com.worksoc.goaicoach.engine

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.Task
import com.google.android.play.core.assetpacks.AssetLocation
import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackLocation
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.AssetPackStates
import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineBootstrapPadDownloadTest {

    private fun newAssetPackException(errorCode: Int): AssetPackException {
        val constructor = AssetPackException::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        return constructor.newInstance(errorCode)
    }

    private fun createFakeAssetPackState(
        name: String,
        status: Int,
        errorCode: Int = 0,
        bytesDownloaded: Long = 0L,
        totalBytesToDownload: Long = 100_000_000L,
    ): AssetPackState = object : AssetPackState() {
        override fun name(): String = name
        override fun status(): Int = status
        override fun errorCode(): Int = errorCode
        override fun bytesDownloaded(): Long = bytesDownloaded
        override fun totalBytesToDownload(): Long = totalBytesToDownload
        override fun transferProgressPercentage(): Int =
            if (totalBytesToDownload > 0) ((bytesDownloaded * 100) / totalBytesToDownload).toInt() else 0
        override fun updateAvailability(): Int = 0
        override fun availableVersionTag(): String = ""
        override fun installedVersionTag(): String = ""
    }

    private fun createFakeAssetPackStates(packStates: Map<String, AssetPackState>): AssetPackStates =
        object : AssetPackStates() {
            override fun totalBytes(): Long = packStates.values.sumOf { it.totalBytesToDownload() }
            override fun packStates(): Map<String, AssetPackState> = packStates
        }

    private class FakeTask<T>(
        private val result: T? = null,
        private val exception: Exception? = null,
    ) : Task<T>() {
        override fun isComplete(): Boolean = true
        override fun isSuccessful(): Boolean = exception == null
        override fun isCanceled(): Boolean = false
        override fun getResult(): T = if (exception != null) throw exception else result!!
        override fun <X : Throwable?> getResult(p0: Class<X>): T = getResult()
        override fun getException(): Exception? = exception

        override fun addOnSuccessListener(listener: com.google.android.gms.tasks.OnSuccessListener<in T>): Task<T> {
            if (exception == null && result != null) {
                listener.onSuccess(result)
            }
            return this
        }

        override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: com.google.android.gms.tasks.OnSuccessListener<in T>): Task<T> =
            addOnSuccessListener(listener)

        override fun addOnSuccessListener(activity: Activity, listener: com.google.android.gms.tasks.OnSuccessListener<in T>): Task<T> =
            addOnSuccessListener(listener)

        override fun addOnFailureListener(listener: com.google.android.gms.tasks.OnFailureListener): Task<T> {
            if (exception != null) {
                listener.onFailure(exception)
            }
            return this
        }

        override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: com.google.android.gms.tasks.OnFailureListener): Task<T> =
            addOnFailureListener(listener)

        override fun addOnFailureListener(activity: Activity, listener: com.google.android.gms.tasks.OnFailureListener): Task<T> =
            addOnFailureListener(listener)
    }

    private inner class FakeAssetPackManager(
        var fetchTaskProvider: ((List<String>) -> Task<AssetPackStates>)? = null,
    ) : AssetPackManager {
        val registeredListeners = mutableListOf<AssetPackStateUpdateListener>()
        var fetchInvocationCount = 0

        fun emitState(state: AssetPackState) {
            registeredListeners.toList().forEach { it.onStateUpdate(state) }
        }

        override fun registerListener(listener: AssetPackStateUpdateListener) {
            registeredListeners.add(listener)
        }

        override fun unregisterListener(listener: AssetPackStateUpdateListener) {
            registeredListeners.remove(listener)
        }

        override fun fetch(packs: List<String>): Task<AssetPackStates> {
            fetchInvocationCount++
            return fetchTaskProvider?.invoke(packs)
                ?: FakeTask(createFakeAssetPackStates(emptyMap()))
        }

        override fun clearListeners() {
            registeredListeners.clear()
        }

        override fun getPackStates(packs: List<String>): Task<AssetPackStates> =
            FakeTask(createFakeAssetPackStates(emptyMap()))

        override fun removePack(pack: String): Task<Void> = FakeTask(null)
        override fun showCellularDataConfirmation(activity: Activity): Task<Int> = FakeTask(0)
        override fun showConfirmationDialog(activity: Activity): Task<Int> = FakeTask(0)
        override fun getAssetLocation(p0: String, p1: String): AssetLocation? = null
        override fun getPackLocation(pack: String): AssetPackLocation? = null
        override fun cancel(packs: List<String>): AssetPackStates = createFakeAssetPackStates(emptyMap())
        override fun getPackLocations(): Map<String, AssetPackLocation> = emptyMap()
        override fun showCellularDataConfirmation(p0: ActivityResultLauncher<IntentSenderRequest>): Boolean = false
        override fun showConfirmationDialog(p0: ActivityResultLauncher<IntentSenderRequest>): Boolean = false
    }

    @Test
    fun testErrorCodeClassification() {
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.API_NOT_AVAILABLE))
        assertTrue(isPermanentAssetPackErrorCode(-11))
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.APP_UNAVAILABLE))
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.PACK_UNAVAILABLE))
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.INVALID_REQUEST))
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.ACCESS_DENIED))
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.DOWNLOAD_NOT_FOUND))
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.APP_NOT_OWNED))
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.UNRECOGNIZED_INSTALLATION))
        assertTrue(isPermanentAssetPackErrorCode(AssetPackErrorCode.INTERNAL_ERROR))

        assertFalse(isPermanentAssetPackErrorCode(AssetPackErrorCode.NETWORK_ERROR))
        assertFalse(isPermanentAssetPackErrorCode(AssetPackErrorCode.INSUFFICIENT_STORAGE))
        assertFalse(isPermanentAssetPackErrorCode(CustomErrorCodeStalled))
        assertFalse(isPermanentAssetPackErrorCode(CustomErrorCodeImmediateFailure))

        assertTrue(isRecoverableAssetPackErrorCode(AssetPackErrorCode.NETWORK_ERROR))
        assertTrue(isRecoverableAssetPackErrorCode(AssetPackErrorCode.INSUFFICIENT_STORAGE))
        assertTrue(isRecoverableAssetPackErrorCode(CustomErrorCodeStalled))
        assertTrue(isRecoverableAssetPackErrorCode(CustomErrorCodeImmediateFailure))
    }

    @Test
    fun testDetermineErrorClassificationWithAssetPackException() {
        val ex = newAssetPackException(AssetPackErrorCode.API_NOT_AVAILABLE)
        val (code, isPermanent) = determineErrorClassification(ex)
        assertEquals(AssetPackErrorCode.API_NOT_AVAILABLE, code)
        assertTrue(isPermanent)

        val networkEx = newAssetPackException(AssetPackErrorCode.NETWORK_ERROR)
        val (netCode, netPermanent) = determineErrorClassification(networkEx)
        assertEquals(AssetPackErrorCode.NETWORK_ERROR, netCode)
        assertFalse(netPermanent)
    }

    @Test
    fun testDetermineErrorClassificationWithGenericException() {
        val generalEx = RuntimeException("API_NOT_AVAILABLE from mock store")
        val (code, isPermanent) = determineErrorClassification(generalEx)
        assertEquals(CustomErrorCodeImmediateFailure, code)
        assertTrue(isPermanent)

        val socketEx = RuntimeException("Connection timed out")
        val (socketCode, socketPermanent) = determineErrorClassification(socketEx)
        assertEquals(CustomErrorCodeImmediateFailure, socketCode)
        assertFalse(socketPermanent)
    }

    @Test
    fun testSuccessfulDownloadFlow() = runBlocking(Dispatchers.Default) {
        val fakeManager = FakeAssetPackManager { packs ->
            val states = packs.associateWith { pack ->
                createFakeAssetPackState(
                    name = pack,
                    status = AssetPackStatus.DOWNLOADING,
                    bytesDownloaded = 10_000_000L,
                    totalBytesToDownload = 100_000_000L,
                )
            }
            FakeTask(createFakeAssetPackStates(states))
        }

        val tracker = EngineDownloadTracker()
        val downloadJob = async {
            fetchAndAwaitAssetPacksWithRetry(
                manager = fakeManager,
                neededPacks = listOf(KatagoModelPackName),
                isMandatory = true,
                tracker = tracker,
            )
        }

        // 약간의 딜레이 후 listener가 등록되었는지 확인
        withTimeout(3000L) {
            while (fakeManager.registeredListeners.isEmpty()) {
                delay(10)
            }
        }

        // 다운로드 진행 업데이트
        fakeManager.emitState(
            createFakeAssetPackState(
                name = KatagoModelPackName,
                status = AssetPackStatus.DOWNLOADING,
                bytesDownloaded = 50_000_000L,
                totalBytesToDownload = 100_000_000L,
            )
        )
        val downloadingStatus = tracker.status.value
        assertTrue(downloadingStatus is EngineDownloadStatus.Downloading)

        // 완료 이벤트 방출
        fakeManager.emitState(
            createFakeAssetPackState(
                name = KatagoModelPackName,
                status = AssetPackStatus.COMPLETED,
                bytesDownloaded = 100_000_000L,
                totalBytesToDownload = 100_000_000L,
            )
        )

        downloadJob.await()
        assertTrue("다운로드 완료 후 상태가 Completed여야 함", tracker.status.value is EngineDownloadStatus.Completed)
        assertTrue("리스너가 정상적으로 unregister되어야 함", fakeManager.registeredListeners.isEmpty())
    }

    @Test
    fun testPermanentFailureStubTransition() = runBlocking(Dispatchers.Default) {
        val fakeManager = FakeAssetPackManager {
            FakeTask(exception = newAssetPackException(AssetPackErrorCode.API_NOT_AVAILABLE))
        }

        val tracker = EngineDownloadTracker()
        fetchAndAwaitAssetPacksWithRetry(
            manager = fakeManager,
            neededPacks = listOf(KatagoModelPackName),
            isMandatory = true,
            tracker = tracker,
        )

        assertTrue("영구 실패 시 재시도 루프를 빠져나와 Failed 상태가 되어야 함", tracker.status.value is EngineDownloadStatus.Failed)
        val failed = tracker.status.value as EngineDownloadStatus.Failed
        assertEquals(AssetPackErrorCode.API_NOT_AVAILABLE, failed.errorCode)
        assertTrue("리스너가 정리되어야 함", fakeManager.registeredListeners.isEmpty())
    }

    @Test
    fun testRecoverableNetworkErrorAndRetryFlow() = runBlocking(Dispatchers.Default) {
        var shouldFail = true
        val fakeManager = FakeAssetPackManager {
            if (shouldFail) {
                FakeTask(exception = newAssetPackException(AssetPackErrorCode.NETWORK_ERROR))
            } else {
                FakeTask(createFakeAssetPackStates(emptyMap()))
            }
        }

        val tracker = EngineDownloadTracker()
        val downloadJob = async {
            fetchAndAwaitAssetPacksWithRetry(
                manager = fakeManager,
                neededPacks = listOf(KatagoModelPackName),
                isMandatory = true,
                tracker = tracker,
            )
        }

        // 첫 번째 시도 실패 대기
        withTimeout(3000L) {
            while (tracker.status.value !is EngineDownloadStatus.Failed) {
                delay(10)
            }
        }

        val failedStatus = tracker.status.value as EngineDownloadStatus.Failed
        assertEquals(AssetPackErrorCode.NETWORK_ERROR, failedStatus.errorCode)
        assertEquals(1, fakeManager.fetchInvocationCount)

        // 네트워크 복구 후 재시도 트리거
        shouldFail = false
        tracker.retry()

        withTimeout(3000L) {
            while (fakeManager.registeredListeners.isEmpty()) {
                delay(10)
            }
        }

        fakeManager.emitState(
            createFakeAssetPackState(
                name = KatagoModelPackName,
                status = AssetPackStatus.COMPLETED,
            )
        )

        downloadJob.await()
        assertTrue(tracker.status.value is EngineDownloadStatus.Completed)
        assertEquals(2, fakeManager.fetchInvocationCount)
    }

    @Test
    fun testSingleTapRetryFromWaitingForWifi() = runBlocking(Dispatchers.Default) {
        val fakeManager = FakeAssetPackManager {
            FakeTask(createFakeAssetPackStates(emptyMap()))
        }

        val tracker = EngineDownloadTracker()
        val downloadJob = async {
            fetchAndAwaitAssetPacksWithRetry(
                manager = fakeManager,
                neededPacks = listOf(KatagoModelPackName),
                isMandatory = true,
                tracker = tracker,
            )
        }

        withTimeout(3000L) {
            while (fakeManager.registeredListeners.isEmpty()) {
                delay(10)
            }
        }

        fakeManager.emitState(
            createFakeAssetPackState(
                name = KatagoModelPackName,
                status = AssetPackStatus.WAITING_FOR_WIFI,
            )
        )
        assertTrue(tracker.status.value is EngineDownloadStatus.WaitingForWifi)
        assertEquals(1, fakeManager.fetchInvocationCount)

        // Wi-Fi 대기 중 단 1회 retry() 호출로 즉시 새 시도가 시작되어야 함 (2회 탭 버그 방지)
        tracker.retry()

        withTimeout(3000L) {
            while (fakeManager.fetchInvocationCount < 2) {
                delay(10)
            }
        }
        assertEquals("Wi-Fi 대기 중 retry() 단 1회 호출로 새 fetch가 실행되어야 함", 2, fakeManager.fetchInvocationCount)

        withTimeout(3000L) {
            while (fakeManager.registeredListeners.isEmpty()) {
                delay(10)
            }
        }

        fakeManager.emitState(
            createFakeAssetPackState(
                name = KatagoModelPackName,
                status = AssetPackStatus.COMPLETED,
            )
        )

        downloadJob.await()
        assertTrue(tracker.status.value is EngineDownloadStatus.Completed)
    }

    @Test
    fun testHumanModelOptionalDownloadDoesNotPermanentlyAbandonOnNetworkError() = runBlocking(Dispatchers.Default) {
        var attempts = 0
        val fakeManager = FakeAssetPackManager {
            attempts++
            if (attempts == 1) {
                // 일시적 네트워크 오류 발생
                FakeTask(exception = newAssetPackException(AssetPackErrorCode.NETWORK_ERROR))
            } else {
                FakeTask(createFakeAssetPackStates(emptyMap()))
            }
        }

        val tracker = EngineDownloadTracker()
        val downloadJob = async {
            fetchAndAwaitAssetPacksWithRetry(
                manager = fakeManager,
                neededPacks = listOf(KatagoHumanPackName),
                isMandatory = false,
                tracker = tracker,
            )
        }

        withTimeout(3000L) {
            while (tracker.status.value !is EngineDownloadStatus.Failed) {
                delay(10)
            }
        }

        val failed = tracker.status.value as EngineDownloadStatus.Failed
        assertEquals(AssetPackErrorCode.NETWORK_ERROR, failed.errorCode)
        assertTrue(failed.isHumanModelOnly)
        assertTrue("사람 모델도 일시적 네트워크 오류 시 루프를 탈출하여 영구 포기하면 안 됨", downloadJob.isActive)

        // 사용자 또는 백그라운드 재시도
        tracker.retry()

        withTimeout(3000L) {
            while (fakeManager.registeredListeners.isEmpty()) {
                delay(10)
            }
        }

        fakeManager.emitState(
            createFakeAssetPackState(
                name = KatagoHumanPackName,
                status = AssetPackStatus.COMPLETED,
            )
        )

        downloadJob.await()
        assertTrue(tracker.status.value is EngineDownloadStatus.Completed)
        val completed = tracker.status.value as EngineDownloadStatus.Completed
        assertTrue(completed.isHumanModelOnly)
    }

    @Test
    fun testCancellationCleansUpListenerWithoutCrash() = runBlocking(Dispatchers.Default) {
        val fakeManager = FakeAssetPackManager {
            FakeTask(createFakeAssetPackStates(emptyMap()))
        }

        val downloadJob = async {
            fetchAndAwaitAssetPacksWithRetry(
                manager = fakeManager,
                neededPacks = listOf(KatagoModelPackName),
                isMandatory = true,
            )
        }

        withTimeout(3000L) {
            while (fakeManager.registeredListeners.isEmpty()) {
                delay(10)
            }
        }

        // 다운로드 도중 코루틴 취소 시 invokeOnCancellation이 단일 등록되어 예외 없이 unregister되는지 검증
        downloadJob.cancel()

        withTimeout(3000L) {
            while (fakeManager.registeredListeners.isNotEmpty()) {
                delay(10)
            }
        }
        assertTrue("코루틴 취소 시 리스너가 안전하게 해제되어야 함", fakeManager.registeredListeners.isEmpty())
    }
}
