package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 좌석이 **선택된 캐릭터의 이름**을 말하는지, 그리고 **엔진 이름을 더는 섞지 않는지**
 * (2026-09-20 사용자 지시).
 *
 * ## 이 파일의 이전 삶
 * 여기는 원래 `SeatLabelEngineNameTest`였다 — 백로그 #109(스텁 엔진이 실제 KataGo인 척하는
 * 결함)를 잡으려고 좌석 라벨이 **엔진 이름**을 말하는지 검사했다. 그런데 도장 서열 개편으로
 * 좌석 라벨이 `문하생 판다` 같은 **캐릭터 이름 전용**이 되면서, "엔진 이름이 보여야 한다"는
 * 그 시절 불변식은 더 이상 참이 아니다.
 *
 * ⚠️ **#109가 잡던 결함 자체는 여전히 막아야 한다** — 그 그물은 진단 리포트가 읽는
 * `SidePlayerSetup.summary`(`shared`의 `SidePlayerSetupSummaryTest`)로 옮겼다. 여기서는
 * 화면이 실제로 보여주는 것 — 캐릭터 이름 — 만 검사한다.
 */
class SeatLabelCharacterNameTest {

    private val aiSeat = SidePlayerSetup(controller = SeatController.Ai)

    @Test
    fun theSeatSaysTheCharacterNameNotTheEngineName() {
        val character = requireNotNull(BotCharacterCatalog.forPlayLevel(aiSeat.playLevel))

        UiLanguage.entries.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            val summary = strings.sideSummary(aiSeat, "stub AI")
            val characterName = strings.botCharacterName(character)

            assertTrue("$language 좌석이 캐릭터 이름을 말하지 않는다: $summary", characterName in summary)
            assertFalse("$language 좌석이 여전히 엔진 이름을 말한다: $summary", "stub AI" in summary)
        }
    }

    @Test
    fun theMatchHeaderSaysItTooBecauseThatIsWhereItWasSeen() {
        // 실기에서 실제로 본 자리는 대국 헤더의 `흑: … / 백: …`(`GameMenuSection.kt`)이다.
        val setup = PlayerSetup(black = aiSeat, white = aiSeat)
        val strings = UiStrings.forLanguage(UiLanguage.Korean)
        val character = requireNotNull(BotCharacterCatalog.forPlayLevel(aiSeat.playLevel))
        val header = strings.setupSummary(setup, "stub AI")

        assertTrue("헤더가 캐릭터 이름을 말하지 않는다: $header", strings.botCharacterName(character) in header)
        assertFalse("헤더가 여전히 엔진 이름을 말한다: $header", "stub AI" in header)
    }
}
