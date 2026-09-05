package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.EngineAvailability
import com.worksoc.goaicoach.application.engine.engineAvailabilityFor
import com.worksoc.goaicoach.shared.EngineMode
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineAvailabilityPolicyTest {

    @Test
    fun notKnowingYetMeansPreparingRatherThanBroken() {
        // ⚠️ 여기가 Unavailable이면 앱을 켤 때마다 실패 팝업이 뜬다 — 부트스트랩이 끝나기 전
        // 몇 초 동안은 **언제나** Unknown이기 때문이다(기조 1ⓒ).
        assertEquals(EngineAvailability.Preparing, engineAvailabilityFor(EngineMode.Unknown))
    }

    @Test
    fun theStubFallbackIsAFailureNotAnEngine() {
        // 스텁도 수는 둔다. 그래서 `isEngineReady`가 true가 되고 앱이 정상으로 보였다 —
        // 사용자는 자기가 가짜와 두고 있다는 것을 어디서도 알 수 없었다.
        assertEquals(EngineAvailability.Unavailable, engineAvailabilityFor(EngineMode.Stub))
    }

    @Test
    fun everyRealBackendIsUsable() {
        listOf(EngineMode.LocalProcess, EngineMode.JniNative, EngineMode.RemoteServer)
            .forEach { mode ->
                assertEquals(EngineAvailability.Ready, engineAvailabilityFor(mode), "mode=$mode")
            }
    }

    @Test
    fun everyModeIsClassified() {
        // `when`이 exhaustive라 이 테스트는 지금 통과한다. 남겨두는 이유는 **누군가 `else ->`를
        // 넣었을 때** 새 백엔드가 조용히 Ready로 흘러가는 것을 막기 위해서다.
        EngineMode.entries.forEach { mode -> engineAvailabilityFor(mode) }
        assertEquals(5, EngineMode.entries.size)
    }
}
