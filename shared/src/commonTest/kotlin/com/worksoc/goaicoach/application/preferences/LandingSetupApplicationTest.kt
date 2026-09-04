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

    @Test
    fun weakerSelfRatingTakesMoreStonesAsBlack() {
        assertEquals(LandingSetupPlan(5, humanPlaysBlack = true), landingSetupPlan(SelfRatedSkill.Entry))
        assertEquals(LandingSetupPlan(3, humanPlaysBlack = true), landingSetupPlan(SelfRatedSkill.Beginner))
        assertEquals(LandingSetupPlan(0, humanPlaysBlack = true), landingSetupPlan(SelfRatedSkill.Intermediate))
    }

    /**
     * ⚠️ 상급 이상은 **사람이 백을 잡는다.** 접바둑 돌은 규칙상 항상 흑이 놓으므로, 좌석을
     * 뒤집는 것이 곧 "내가 AI에게 돌을 접어 준다"가 된다 — 이 뒤집힘이 빠지면 고수가 오히려
     * 돌을 받는 정반대 설정이 된다.
     */
    @Test
    fun strongerSelfRatingGivesStonesToTheAiByPlayingWhite() {
        assertEquals(LandingSetupPlan(2, humanPlaysBlack = false), landingSetupPlan(SelfRatedSkill.Advanced))
        assertEquals(LandingSetupPlan(3, humanPlaysBlack = false), landingSetupPlan(SelfRatedSkill.Expert))
    }

    @Test
    fun entryLevelSeatsTheHumanOnBlackWithFiveStones() {
        val applied = applyLandingSetup(UserPreferencesSnapshot(), SelfRatedSkill.Entry, Ruleset.Japanese)

        assertEquals(5, applied.handicapCount)
        assertEquals(SeatController.Human, applied.playerSetup.black.controller)
        assertEquals(SeatController.Ai, applied.playerSetup.white.controller)
        assertEquals(Ruleset.Japanese, applied.ruleset)
    }

    @Test
    fun expertLevelSeatsTheHumanOnWhiteSoTheAiTakesTheStones() {
        val applied = applyLandingSetup(UserPreferencesSnapshot(), SelfRatedSkill.Expert, Ruleset.Chinese)

        assertEquals(3, applied.handicapCount)
        assertEquals(SeatController.Ai, applied.playerSetup.black.controller)
        assertEquals(SeatController.Human, applied.playerSetup.white.controller)
        assertEquals(Ruleset.Chinese, applied.ruleset)
    }

    /** 다섯 답 모두 1단계를 상대로 시작한다 — 신규 설치에 열려 있는 캐릭터가 그것뿐이다. */
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
            appFontScale = 1.5f,
        )

        val applied = applyLandingSetup(current, SelfRatedSkill.Intermediate, Ruleset.Chinese)

        assertEquals(BoardSize.Nineteen, applied.boardSize)
        assertEquals(0.5, applied.komi)
        assertTrue(applied.showCoordinates)
        assertTrue(applied.showMoveNumbers)
        assertTrue(!applied.isPlayHapticEnabled)
        assertEquals(1.5f, applied.appFontScale)
    }

    /**
     * ⚠️ 9x9는 접바둑 상한이 5다. 표의 값이 상한을 넘는 판에서도 잘리는지 본다 — 넘긴 채로
     * 저장되면 `handicapStonePositions`가 `require`에서 터진다.
     */
    @Test
    fun handicapIsClampedToWhatTheBoardAllows() {
        val nine = UserPreferencesSnapshot(boardSize = BoardSize.Nine)

        val applied = applyLandingSetup(nine, SelfRatedSkill.Entry, Ruleset.Japanese)

        assertTrue(applied.handicapCount <= BoardSize.Nine.maxHandicapCount)
        assertEquals(5, applied.handicapCount)
    }
}
