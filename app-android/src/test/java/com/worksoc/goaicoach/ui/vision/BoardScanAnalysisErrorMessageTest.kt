package com.worksoc.goaicoach.ui.vision

import com.worksoc.goaicoach.application.engine.EngineOperationBusy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * refactor backlog #15 — 보드 스캔의 분석은 형세 추정·분석 두 호출이고, 엔진이 다른 오퍼레이션을 하고 있으면 둘 다
 * 기다리지 않고 [EngineOperationBusy]로 포기한다. 그것은 실패가 아니다 — "실패했습니다"와 영어 예외 원문 대신 잠시 뒤
 * 다시 누르라는 문구를 보인다. 진짜 실패는 지금처럼 원인을 붙여 보인다.
 */
class BoardScanAnalysisErrorMessageTest {
    @Test
    fun anEngineThatIsBusyWithAnotherOperationIsNotReportedAsAFailure() {
        val message = boardScanAnalysisErrorMessage(EngineOperationBusy("estimateScoreForState"))

        assertEquals("엔진이 다른 작업을 하고 있습니다. 잠시 뒤 다시 분석해 주세요.", message)
        assertFalse("giving up is not a failure: $message", message.contains("실패"))
        assertFalse("the exception's English text must not leak: $message", message.contains("estimateScoreForState"))
    }

    @Test
    fun aRealFailureStillShowsItsCause() {
        val message = boardScanAnalysisErrorMessage(IllegalStateException("KataGo gtp gen=1 died"))

        assertTrue(message, message.startsWith("AI 분석에 실패했습니다: "))
        assertTrue(message, message.endsWith("KataGo gtp gen=1 died"))
    }
}
