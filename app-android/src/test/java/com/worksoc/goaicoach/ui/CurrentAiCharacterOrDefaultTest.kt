package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 백로그 #181 — 홈 "대국 하기" 카드 아이콘이 어떤 캐릭터를 보여줄지 고르는
 * [currentAiCharacterOrDefault]를 고정한다.
 *
 * ⚠️ 이 산수는 `PlayerSetupPanel.kt`의 캐릭터 픽커가 "지금 고른 캐릭터"를 표시할 때 쓰는 것과
 * **같은 것을 의도적으로 복제**했다(호출부가 둘로 갈라져 있어 함수를 공유하지 않는다) — 한쪽만
 * 바뀌면 픽커와 홈 카드가 서로 다른 캐릭터를 보여주는데도 컴파일은 조용히 통과한다. 이 테스트는
 * 적어도 [currentAiCharacterOrDefault] 쪽의 규칙을 문서화해 실수로 바뀌면 바로 드러나게 한다.
 */
class CurrentAiCharacterOrDefaultTest {

    @Test
    fun defaultPlayerSetupResolvesToTheLevelThreeCharacter() {
        // 2026-09-21 사용자 요청: PlayerSetup() 기본 상태의 홈 아이콘은 문하생 판다가 아니라 수제자 반상(3단계).
        val character = currentAiCharacterOrDefault(PlayerSetup())
        assertEquals("fast_beginner_3", character.id.raw)
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
        assertEquals("fast_beginner_5", currentAiCharacterOrDefault(playerSetup).id.raw)
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
        assertEquals("fast_beginner_4", currentAiCharacterOrDefault(playerSetup).id.raw)
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
        assertEquals("fast_beginner_5", currentAiCharacterOrDefault(playerSetup).id.raw)
    }

    /**
     * 로컬 2인 대국처럼 AI 좌석이 아예 없으면 기본값(레벨3)으로 떨어진다 — `null`을 던지거나
     * 예외를 내지 않는다는 것이 핵심이다.
     */
    @Test
    fun fallsBackToLevelThreeWhenNeitherSeatIsAi() {
        val playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        assertEquals("fast_beginner_3", currentAiCharacterOrDefault(playerSetup).id.raw)
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
        assertEquals("fast_beginner_3", currentAiCharacterOrDefault(playerSetup).id.raw)
    }
}
