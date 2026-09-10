package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.Ruleset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 백로그 #51 — 랜딩의 답이 초기 설정으로 옮겨지는 순수 로직. */
class LandingSetupApplicationTest {

    /** 보기는 2026-09-09에 셋으로 줄었다 — 입문자가 5점이 아니라 **3점**을 받는다. */
    @Test
    fun weakerSelfRatingTakesMoreStonesAsBlack() {
        assertEquals(LandingSetupPlan(3, humanPlaysBlack = true), landingSetupPlan(SelfRatedSkill.Entry))
        assertEquals(LandingSetupPlan(0, humanPlaysBlack = true), landingSetupPlan(SelfRatedSkill.Intermediate))
    }

    /**
     * ⚠️ 상급자는 **사람이 백을 잡는다.** 접바둑 돌은 규칙상 항상 흑이 놓으므로, 좌석을
     * 뒤집는 것이 곧 "내가 AI에게 돌을 접어 준다"가 된다 — 이 뒤집힘이 빠지면 상급자가 오히려
     * 돌을 받는 정반대 설정이 된다.
     */
    @Test
    fun strongerSelfRatingGivesStonesToTheAiByPlayingWhite() {
        assertEquals(LandingSetupPlan(2, humanPlaysBlack = false), landingSetupPlan(SelfRatedSkill.Advanced))
    }

    @Test
    fun entryLevelSeatsTheHumanOnBlackWithThreeStones() {
        val applied = applyLandingSetup(UserPreferencesSnapshot(), SelfRatedSkill.Entry, Ruleset.Japanese)

        assertEquals(3, applied.handicapCount)
        assertEquals(SeatController.Human, applied.playerSetup.black.controller)
        assertEquals(SeatController.Ai, applied.playerSetup.white.controller)
        assertEquals(Ruleset.Japanese, applied.ruleset)
    }

    @Test
    fun advancedLevelSeatsTheHumanOnWhiteSoTheAiTakesTheStones() {
        val applied = applyLandingSetup(UserPreferencesSnapshot(), SelfRatedSkill.Advanced, Ruleset.Chinese)

        assertEquals(2, applied.handicapCount)
        assertEquals(SeatController.Ai, applied.playerSetup.black.controller)
        assertEquals(SeatController.Human, applied.playerSetup.white.controller)
        assertEquals(Ruleset.Chinese, applied.ruleset)
    }

    /**
     * 보기가 셋뿐이라는 것 자체를 못박는다 — 사용자 지시가 *"간결하게 3가지로만"* 이었고,
     * 넷째가 조용히 붙으면 랜딩의 그 간결함이 되돌려진다(2026-09-09).
     */
    @Test
    fun theLandingAsksWithExactlyThreeChoices() {
        assertEquals(3, SelfRatedSkill.entries.size, "${SelfRatedSkill.entries}")
    }

    /** 세 답 모두 1단계를 상대로 시작한다 — 신규 설치에 열려 있는 캐릭터가 그것뿐이다. */
    @Test
    fun everyAnswerStartsAgainstTheFirstTierOpponent() {
        SelfRatedSkill.entries.forEach { skill ->
            val applied = applyLandingSetup(UserPreferencesSnapshot(), skill, Ruleset.Japanese)
            listOf(applied.playerSetup.black, applied.playerSetup.white).forEach { side ->
                assertEquals(PlayLevelGroup.FastBeginner, side.playLevel.group, "$skill")
                assertEquals(1, side.playLevel.safeLevel, "$skill")
            }
        }
    }

    /** 랜딩을 마쳤다는 사실이 남아야 다음 실행에 다시 뜨지 않는다. */
    @Test
    fun completingTheLandingIsRecorded() {
        assertTrue(applyLandingSetup(UserPreferencesSnapshot(), SelfRatedSkill.Entry, Ruleset.Japanese).hasSeenOnboarding)
    }

    /**
     * ⚠️ 이 항목에서 가장 조용히 깨질 수 있는 곳이다. 기본값으로 새 스냅샷을 만들면 판 크기·덤·
     * 표시 옵션이 통째로 초기화된다 — `UserPreferencesAutosaveApplication`이 실제로 겪었던 사고와
     * 같은 모양이라, 여기서 미리 막는다.
     */
    @Test
    fun untouchedPreferencesSurviveTheLanding() {
        val current = UserPreferencesSnapshot(
            boardSize = BoardSize.Nineteen,
            komi = 0.5,
            showCoordinates = true,
            showMoveNumbers = true,
            isPlayHapticEnabled = false,
            appFontScale = 1.3f,
        )

        val applied = applyLandingSetup(current, SelfRatedSkill.Intermediate, Ruleset.Chinese)

        assertEquals(BoardSize.Nineteen, applied.boardSize)
        assertEquals(0.5, applied.komi)
        assertTrue(applied.showCoordinates)
        assertTrue(applied.showMoveNumbers)
        assertTrue(!applied.isPlayHapticEnabled)
        assertEquals(1.3f, applied.appFontScale)
    }

    /**
     * ⚠️ 9x9는 접바둑 상한이 5다. 상한을 넘긴 채로 저장되면 `handicapStonePositions`가
     * `require`에서 터지므로, 작은 판에서도 값이 안전한지 본다.
     *
     * ⚠️ **2026-09-09부터 이 검사는 잠든 그물이다 — "자르기가 동작한다"의 증거로 읽지 말 것.**
     * 보기가 셋으로 줄면서 표의 최대가 3점(입문자)이 됐고, 9x9 상한이 5라 **자를 일이 없다.**
     * 그래도 남겨 두는 이유는 표가 다시 커지는 날(예: 입문자를 5점으로 되돌리는 변경)
     * 이 자리가 곧바로 실검사로 되살아나기 때문이다. 그때 아래 단언의 기대값도 함께 바뀐다.
     */
    @Test
    fun handicapIsClampedToWhatTheBoardAllows() {
        val nine = UserPreferencesSnapshot(boardSize = BoardSize.Nine)

        val applied = applyLandingSetup(nine, SelfRatedSkill.Entry, Ruleset.Japanese)

        assertTrue(applied.handicapCount <= BoardSize.Nine.maxHandicapCount)
        assertEquals(3, applied.handicapCount)
    }
}
