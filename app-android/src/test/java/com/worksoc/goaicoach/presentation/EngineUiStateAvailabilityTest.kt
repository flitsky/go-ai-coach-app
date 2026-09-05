package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.application.engine.EngineAvailability
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.shared.EngineProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUiStateAvailabilityTest {

    private fun engineState(mode: EngineMode, isReady: Boolean) = EngineUiState(
        name = "engine",
        diagnostic = "",
        profile = EngineProfile(mode = mode),
        isReady = isReady,
        isBusy = false,
        isBlockingBusy = false,
        activityIndicator = null,
        engineTurnWaitCompletionSeq = 0,
        message = "",
    )

    @Test
    fun aStubReportsItselfReadyAndUnavailableAtTheSameTime() {
        // ⚠️ **이 조합이 #101 ④·#105의 존재 이유다.** 스텁도 기동은 성공하므로 `isReady`는 true다 —
        // 그래서 앱이 정상으로 보였고, 사용자는 자기가 가짜와 두는 것을 알 수 없었다.
        val stub = engineState(EngineMode.Stub, isReady = true)

        assertTrue("전제가 깨졌다 — 스텁이 준비됐다고 보고하지 않는다.", stub.isReady)
        assertEquals(EngineAvailability.Unavailable, stub.availability)
    }

    @Test
    fun aBootstrapThatHasNotFinishedIsPreparingNotBroken() {
        // 준비 중에 Unavailable이면 앱을 켤 때마다 배지가 번쩍인다(기조 1ⓒ가 금지하는 것).
        val preparing = engineState(EngineMode.Unknown, isReady = false)

        assertEquals(EngineAvailability.Preparing, preparing.availability)
    }

    @Test
    fun aRealEngineIsReadyAndAvailable() {
        val real = engineState(EngineMode.LocalProcess, isReady = true)

        assertEquals(EngineAvailability.Ready, real.availability)
    }
}
