package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.ui.home.aiSeatCharacterOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 백로그 #181 → #288 — 홈 「일반 대국」 카드 그림이 좌석 배치에서 어떤 캐릭터를 읽는지 고르는
 * [aiSeatCharacterOrNull]을 고정한다(옛 `currentAiCharacterOrDefault`).
 *
 * ⚠️ 이 산수는 `PlayerSetupPanel.kt`의 캐릭터 픽커가 "지금 고른 캐릭터"를 표시할 때 쓰는 것과
 * **같은 것을 의도적으로 복제**했다(호출부가 둘로 갈라져 있어 함수를 공유하지 않는다) — 한쪽만
 * 바뀌면 픽커와 홈 카드가 서로 다른 캐릭터를 보여주는데도 컴파일은 조용히 통과한다. 이 테스트는
 * 적어도 [aiSeatCharacterOrNull] 쪽의 규칙을 문서화해 실수로 바뀌면 바로 드러나게 한다.
 */
class AiSeatCharacterTest {

    /**
     * 첫 설정(흑 사람 · 백 AI 1단계)의 상대는 문하생 판다다 — #181의 「기본 설정이면 수제자 반상」은 #288에서 흑백 돌 그림이 대신하고,
     * 이 함수는 좌석에 앉은 그대로를 읽는다(기본 판다와 둔 사람에게는 판다가 보여야 한다).
     */
    @Test
    fun theFirstSetupSeatsThePanda() {
        assertEquals("fast_beginner_1", aiSeatCharacterOrNull(PlayerSetup())?.id?.raw)
    }

    @Test
    fun picksTheAiSeatsFastBeginnerLevel() {
        val playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 5),
            ),
        )
        assertEquals("fast_beginner_5", aiSeatCharacterOrNull(playerSetup)?.id?.raw)
    }

    /**
     * AI가 백이 아니라 흑에 있어도(예: "AI 선공") 찾아낸다 — 백 좌석만 본다는 실수를 잡는다.
     */
    @Test
    fun findsTheAiSeatEvenWhenItIsBlack() {
        val playerSetup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 4),
            ),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        assertEquals("fast_beginner_4", aiSeatCharacterOrNull(playerSetup)?.id?.raw)
    }

    /**
     * 두 좌석 다 AI(자동 대국)면 백을 먼저 본다 — 구현이 `listOf(white, black)` 순서로 찾는
     * 구체적인 동작이라, 순서가 바뀌면 이 테스트가 먼저 잡는다.
     */
    @Test
    fun whenBothSeatsAreAiTheWhiteSeatWins() {
        val playerSetup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 3),
            ),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 5),
            ),
        )
        assertEquals("fast_beginner_5", aiSeatCharacterOrNull(playerSetup)?.id?.raw)
    }

    /** 로컬 2인 대국처럼 AI 좌석이 아예 없으면 캐릭터가 없다 — 카드는 흑백 돌을 그린다(#288). */
    @Test
    fun noAiSeatMeansNoCharacter() {
        val playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        assertNull(aiSeatCharacterOrNull(playerSetup))
    }

    /**
     * `FastBeginner`가 아닌 그룹(초급/중급/고급 — 대국 설정 UI에서는 숨겨져 있지만 저장값으로는
     * 남아 있을 수 있다)이 저장돼 있으면 기본값(레벨3)으로 떨어진다 — 존재하지 않는 캐릭터를 가리키다
     * `forPlayLevel`이 `null`을 돌려주는 경로를 막는다.
     */
    @Test
    fun fallsBackToLevelThreeWhenTheAiSeatIsNotInTheFastBeginnerGroup() {
        val playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(group = PlayLevelGroup.Intermediate, level = 3),
            ),
        )
        assertEquals("fast_beginner_3", aiSeatCharacterOrNull(playerSetup)?.id?.raw)
    }
}
