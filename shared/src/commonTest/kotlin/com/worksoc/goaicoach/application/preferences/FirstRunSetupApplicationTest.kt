package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.Ruleset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 백로그 #140 — 첫 실행은 묻지 않고, 첫 판은 첫돌이와 호선·집 계가로 시작한다. */
class FirstRunSetupApplicationTest {

    /**
     * ⚠️ **첫 판의 모양은 신규 설치 기본값이 정한다**(`completeFirstRun`은 설정을 쓰지 않는다).
     * 그래서 사용자 결정(2026-09-11: *"기본은 첫돌이 호선, 계가는 집 계가"*)을 **기본값에** 못박는다 —
     * 누가 기본값을 바꾸면 신규 사용자의 첫 판이 조용히 바뀌므로 여기서 빨개져야 한다.
     */
    @Test
    fun aFreshInstallStartsAgainstFirstDolInAnEvenGameUnderTerritoryScoring() {
        val fresh = completeFirstRun(UserPreferencesSnapshot())

        assertEquals(0, fresh.handicapCount, "호선이어야 한다")
        assertEquals(Ruleset.Japanese, fresh.ruleset, "집 계가여야 한다")
        assertEquals(SeatController.Human, fresh.playerSetup.black.controller, "사람이 흑")
        assertEquals(SeatController.Ai, fresh.playerSetup.white.controller, "첫돌이가 백")
        assertEquals(PlayLevelGroup.FastBeginner, fresh.playerSetup.white.playLevel.group, "첫돌이 = 빠른 초급")
        assertEquals(1, fresh.playerSetup.white.playLevel.safeLevel, "첫돌이 = 1단계")
    }

    /** 첫 실행을 마쳤다는 사실이 남아야 다음 실행에 첫 실행 처리를 다시 하지 않는다. */
    @Test
    fun completingTheFirstRunIsRecorded() {
        assertTrue(completeFirstRun(UserPreferencesSnapshot()).hasSeenOnboarding)
    }

    /**
     * ⚠️ **설정을 가진 채 `hasSeenOnboarding`이 꺼진 사람**(랜딩 이전 빌드에서 곧장 올라온 사용자)의
     * 설정을 덮어쓰면 안 된다 — 옛 랜딩의 *'나중에 할게요'* 가 지키던 성질이다. 기본값으로 새 스냅샷을
     * 만들면 판 크기·접바둑·상대가 통째로 초기화된다(`UserPreferencesAutosaveApplication`의 사고와
     * 같은 모양).
     */
    @Test
    fun anExistingSetupSurvivesTheFirstRun() {
        val current = UserPreferencesSnapshot(
            boardSize = BoardSize.Nineteen,
            ruleset = Ruleset.Chinese,
            handicapCount = 3,
            komi = 0.5,
            showCoordinates = true,
            isPlayHapticEnabled = false,
            appFontScale = 1.3f,
        )

        assertEquals(current.copy(hasSeenOnboarding = true), completeFirstRun(current))
    }
}
