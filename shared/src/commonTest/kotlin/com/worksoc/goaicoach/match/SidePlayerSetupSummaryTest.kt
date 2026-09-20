package com.worksoc.goaicoach.match

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 진단 리포트가 **실제로 뜬 엔진의 이름**을 말하는지(백로그 #109).
 *
 * ## 왜 여기로 옮겼는가
 * 이 그물은 원래 대국 화면 헤더(`UiStrings.sideSummary`, `SeatLabelEngineNameTest`)를 봤다.
 * 그런데 2026-09-20 도장 서열 개편으로 그 헤더가 **캐릭터 이름 전용**이 되면서 엔진 이름을
 * 더는 말하지 않는다 — 그 화면에서는 이 검사가 더 이상 성립하지 않는다.
 *
 * 실제로 뜬 엔진 이름이 여전히 흘러나가는 자리는 진단 리포트(`DebugReportSections`가 읽는
 * [SidePlayerSetup.summary])뿐이다. 스텁 엔진이 실제 KataGo인 척하는 결함(2026-09-05 스텁
 * 빌드 실기에서 발견)을 잡으려던 것이 이 그물의 원래 목적이므로, 목적이 남아 있는 자리로
 * 그대로 옮긴다.
 */
class SidePlayerSetupSummaryTest {

    private val aiSeat = SidePlayerSetup(controller = SeatController.Ai)

    @Test
    fun theDiagnosticSummarySaysWhicheverEngineActuallyCameUp() {
        val summary = aiSeat.summary("stub AI")

        assertTrue("stub AI" in summary, "진단 리포트가 엔진 이름을 말하지 않는다(#109): $summary")
        // ⚠️ 하드코딩된 이름이 돌아오면 여기서 걸린다.
        assertFalse("KataGo" in summary, "진단 리포트가 아직 KataGo라고 말한다(#109): $summary")
    }

    @Test
    fun theFullMatchSummarySaysItTooBecauseThatIsWhatTheDebugReportReads() {
        // 진단 리포트가 실제로 읽는 자리는 `PlayerSetup.summary`다(`DebugReportSections.kt`).
        val setup = PlayerSetup(black = aiSeat, white = aiSeat)
        val summary = setup.summary("stub AI")

        assertTrue("stub AI" in summary, "리포트가 엔진 이름을 말하지 않는다(#109): $summary")
        assertFalse("KataGo" in summary, "리포트가 아직 KataGo라고 말한다(#109): $summary")
    }
}
