package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 형세 보기를 켜 둔 채 급수 캐릭터와 두면, AI가 둘 때마다 화면의 형세가 사람 모델의 임시 값이 된다 —
 * 사람 차례가 오면 주 모델로 한 번 다시 잰다(백로그 #215 보강 ②).
 */
class ProvisionalScoreRefineTest {
    private val provisionalOnMyTurn = ProvisionalScoreRefineInput(
        isScoreViewOn = true,
        shownEstimateNetwork = EngineNetwork.Human,
        isGameEnded = false,
        isEngineReady = true,
        isEngineBusy = false,
        isPendingUndoSync = false,
        isHumanTurn = true,
        attempt = ProvisionalScoreRefineAttempt(sessionGeneration = 1L, moveNumber = 12),
    )

    @Test
    fun aProvisionalValueOnScreenIsRefinedWhenItIsTheUsersTurn() {
        assertTrue(shouldRefineProvisionalScore(provisionalOnMyTurn, lastAttempt = null))
    }

    /** 안 보는 값을 위해 엔진을 갈아 올리지 않는다 — 형세 보기를 끈 대국은 예전처럼 갈아 올리기가 없다. */
    @Test
    fun nothingIsRefinedWhileTheScoreViewIsOff() {
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(isScoreViewOn = false), lastAttempt = null))
    }

    /** 주 모델이 낸 값(방금 누른 형세 보기, 사람이 둔 뒤의 형세)은 이미 정확하다. */
    @Test
    fun aValueFromTheMainNetworkIsLeftAlone() {
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(shownEstimateNetwork = EngineNetwork.Main), lastAttempt = null))
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(shownEstimateNetwork = null), lastAttempt = null))
    }

    /** AI 차례에는 그 차례가 곧 엔진을 쓴다 — 끼어들면 수마다 갈아 올리기만 두 번 는다. AI끼리 두는 판이 그렇다. */
    @Test
    fun theAisTurnIsNotInterrupted() {
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(isHumanTurn = false), lastAttempt = null))
    }

    @Test
    fun itWaitsWhileTheEngineIsBusyOrNotReadyOrAnUndoSyncIsPending() {
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(isEngineBusy = true), lastAttempt = null))
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(isEngineReady = false), lastAttempt = null))
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(isPendingUndoSync = true), lastAttempt = null))
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(isGameEnded = true), lastAttempt = null))
    }

    /**
     * 한 국면에 한 번만 시도한다 — 재측정이 실패하면 형세는 임시 값 그대로이고 엔진은 다시 한가해진다.
     * 그때마다 다시 걸면 실패하는 재측정을 끝없이 되풀이한다.
     */
    @Test
    fun aPositionIsTriedOnlyOnce() {
        assertFalse(shouldRefineProvisionalScore(provisionalOnMyTurn, lastAttempt = provisionalOnMyTurn.attempt))
    }

    /** 다음 수, 그리고 무르기·새 대국 뒤의 같은 수순 번호는 다른 국면이다. */
    @Test
    fun theNextMoveAndTheSameMoveNumberOfAnotherGameAreTriedAgain() {
        val tried = provisionalOnMyTurn.attempt

        assertTrue(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(attempt = tried.copy(moveNumber = 14)), lastAttempt = tried))
        assertTrue(shouldRefineProvisionalScore(provisionalOnMyTurn.copy(attempt = tried.copy(sessionGeneration = 2L)), lastAttempt = tried))
    }
}
