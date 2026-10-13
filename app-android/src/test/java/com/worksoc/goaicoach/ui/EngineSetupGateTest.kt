package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.ui.setup.engineSetupCanStart
import com.worksoc.goaicoach.ui.setup.engineSetupGateVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 엔진 준비 화면(백로그 #249)의 두 판정 — 언제 덮는가, 언제 「시작하기」를 여는가.
 */
class EngineSetupGateTest {

    @Test
    fun gateShowsOnlyWhileRequiredAndNotClosedAndNotStub() {
        assertTrue(engineSetupGateVisible(required = true, closedByUser = false, engineMode = EngineMode.Unknown))
        assertTrue("받기가 끝나 엔진이 떠도 「시작하기」를 누를 때까지 남는다(U-77)", engineSetupGateVisible(true, false, EngineMode.LocalProcess))
        assertFalse("모델이 다 있으면 띄우지 않는다", engineSetupGateVisible(false, false, EngineMode.Unknown))
        assertFalse("「나중에」·「시작하기」로 닫으면 걷힌다(U-76)", engineSetupGateVisible(true, true, EngineMode.Unknown))
        assertFalse("스텁으로 정해지면(Play 밖 설치·영구 실패) 갇히지 않게 곧바로 걷는다", engineSetupGateVisible(true, false, EngineMode.Stub))
    }

    @Test
    fun startOpensOnlyWhenDownloadCompletedOrEngineReadyWithoutDownload() {
        assertTrue(engineSetupCanStart(EngineDownloadStatus.Completed, EngineMode.Unknown))
        assertTrue("받을 것이 없었는데 엔진이 떴다", engineSetupCanStart(EngineDownloadStatus.Idle, EngineMode.LocalProcess))
        assertFalse(engineSetupCanStart(EngineDownloadStatus.Idle, EngineMode.Unknown))
        assertFalse(
            "받는 중에는 엔진 상태와 무관하게 닫혀 있다(디버그 미리보기에서 진짜 엔진이 먼저 떠도)",
            engineSetupCanStart(EngineDownloadStatus.Downloading(1L, 2L, 50), EngineMode.LocalProcess),
        )
        assertFalse(engineSetupCanStart(EngineDownloadStatus.Failed(-6, "x"), EngineMode.Unknown))
        assertFalse(engineSetupCanStart(EngineDownloadStatus.WaitingForWifi(1L, 2L), EngineMode.Unknown))
        assertFalse(
            "업데이트를 먼저 권하는 동안(#255)은 받기 전이라 닫혀 있다 — 디버그 미리보기에서 진짜 엔진이 떠 있어도",
            engineSetupCanStart(EngineDownloadStatus.UpdateFirst, EngineMode.LocalProcess),
        )
    }
}
