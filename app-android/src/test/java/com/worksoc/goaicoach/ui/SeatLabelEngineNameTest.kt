package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 좌석이 **실제로 뜬 엔진의 이름**을 말하는지(백로그 #109).
 *
 * ## ⚠️ 무엇이 잘못돼 있었나
 * 좌석 라벨은 `setup.aiEngine.label.ifBlank { engineName }`이었고, `AiEngineChoice`는 값이
 * **하나뿐**(`KataGo("KataGo")`)이라 그 라벨이 **절대 비지 않았다.** 그래서 `engineName`으로
 * 가는 폴백이 **죽은 코드**였고, 엔진이 스텁으로 떨어져도 화면은 `백: KataGo 초보`라고 말했다 —
 * **가짜와 두면서 진짜 엔진의 이름을 말한 것이다**(2026-09-05 스텁 빌드 실기에서 발견).
 *
 * ⚠️ 이 결함은 **정상 기기에서 절대 드러나지 않는다** — 스텁일 때만 이름이 갈린다.
 * 그래서 화면을 보는 대신 **이름을 주입해** 재현한다.
 */
class SeatLabelEngineNameTest {

    private val aiSeat = SidePlayerSetup(controller = SeatController.Ai)

    @Test
    fun theSeatSaysWhicheverEngineActuallyCameUp() {
        UiLanguage.entries.forEach { language ->
            val summary = UiStrings.forLanguage(language).sideSummary(aiSeat, "stub AI")

            assertTrue("$language 좌석이 엔진 이름을 말하지 않는다(#109): $summary", "stub AI" in summary)
            // ⚠️ 하드코딩된 이름이 돌아오면 여기서 걸린다.
            assertFalse("$language 좌석이 아직 KataGo라고 말한다(#109): $summary", "KataGo" in summary)
        }
    }

    @Test
    fun theMatchHeaderSaysItTooBecauseThatIsWhereItWasSeen() {
        // 실기에서 실제로 본 자리는 대국 헤더의 `흑: … / 백: …`(`GameMenuSection.kt`)이다.
        val setup = com.worksoc.goaicoach.match.PlayerSetup(black = aiSeat, white = aiSeat)
        val header = UiStrings.forLanguage(UiLanguage.Korean).setupSummary(setup, "stub AI")

        assertTrue("헤더가 엔진 이름을 말하지 않는다(#109): $header", "stub AI" in header)
        assertFalse("헤더가 아직 KataGo라고 말한다(#109): $header", "KataGo" in header)
    }
}
