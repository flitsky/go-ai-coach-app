package com.worksoc.goaicoach.application.preferences

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [isBoardSetupLockedDuringGame]의 계약(백로그 #75).
 *
 * ⚠️ **이 그물이 지키는 핵심은 "잠근다"가 아니라 "엉뚱한 사람에게 잠기지 않는다"** 이다.
 * 설정 화면과 콤팩트 패널은 단위 테스트가 하나도 참조하지 않는 파일이라(#76에서 확인),
 * 이 판정이 화면 안에 인라인으로 있었으면 **틀려도 아무도 몰랐다.**
 */
class BoardSetupLockPolicyTest {

    /**
     * ⚠️ **가장 중요한 경우.** 대국을 한 번도 하지 않은 사용자는 `isGameEnded`가 `false`다 —
     * 시작한 적이 없으니 끝난 적도 없다. 그 하나만 보고 잠그면 **신규 사용자가 판 크기를 아예
     * 못 고른다.** 이것이 이 함수가 조건 둘을 보는 이유 전부다.
     */
    @Test
    fun aUserWhoHasNeverPlayedIsNotLocked() {
        assertFalse(
            isBoardSetupLockedDuringGame(moveCount = 0, isGameEnded = false),
            "대국을 한 번도 하지 않았는데 잠겼다 — `isGameEnded`만 보면 이렇게 된다(#75).",
        )
    }

    /** 둔 수가 있고 아직 안 끝났다 = 진행 중. 이때만 잠근다. */
    @Test
    fun aGameInProgressIsLocked() {
        assertTrue(isBoardSetupLockedDuringGame(moveCount = 1, isGameEnded = false))
        assertTrue(isBoardSetupLockedDuringGame(moveCount = 137, isGameEnded = false))
    }

    /** 끝난 판은 잠그지 않는다 — 다음 대국을 준비하는 자리가 된다. */
    @Test
    fun aFinishedGameIsNotLocked() {
        assertFalse(isBoardSetupLockedDuringGame(moveCount = 200, isGameEnded = true))
    }

    /**
     * ⚠️ **접바둑 돌은 "둔 수"가 아니다** — 접바둑 5점으로 차려만 놓고 아직 안 둔 판의
     * `moveCount`는 0이다(실기 저장으로 확인). 그 상태는 **다음 대국 준비**이므로 잠기면 안 된다.
     */
    @Test
    fun aHandicapBoardWithNoMovesYetIsNotLocked() {
        assertFalse(
            isBoardSetupLockedDuringGame(moveCount = 0, isGameEnded = false),
            "접바둑만 놓인 채 아직 안 둔 판이 잠겼다 — 그때는 아직 준비 단계다(#75).",
        )
    }

    /**
     * ⚠️ **앱을 껐다 켜면 잠금이 풀리는 구멍을 막는다**(2026-09-05 실기에서 실제로 밟았다).
     *
     * 재시작 직후에는 저장된 대국이 아직 메모리에 없어 `moveCount`가 0이다 — 홈 화면이
     * *"이전 대국 이어하기"* 를 띄우고 있는데도 그렇다. 그때 잠기지 않으면 **껐다 켜는 것만으로
     * 우회되고**, 이어하기를 누른 순간 이 항목이 없애려던 어긋남이 그대로 돌아온다.
     */
    @Test
    fun aSavedButNotYetResumedGameStillLocks() {
        assertTrue(
            isBoardSetupLockedDuringGame(
                moveCount = 0,
                isGameEnded = false,
                hasResumableSavedGame = true,
            ),
            "재시작 직후 저장된 대국이 있는데 잠기지 않았다 — 껐다 켜면 잠금이 우회된다(#75).",
        )
    }

    /** 이어할 대국이 없으면 그 조건은 아무것도 하지 않는다 — 기본값이 잠그는 쪽이면 안 된다. */
    @Test
    fun noSavedGameLeavesTheDecisionToTheLiveState() {
        assertFalse(isBoardSetupLockedDuringGame(moveCount = 0, isGameEnded = false, hasResumableSavedGame = false))
        assertTrue(isBoardSetupLockedDuringGame(moveCount = 3, isGameEnded = false, hasResumableSavedGame = false))
    }

    /** 있을 수 없는 조합(끝났는데 둔 수가 0)에서도 잠그지 않는다 — 잠금은 보수적이어야 한다. */
    @Test
    fun anImpossibleCombinationFailsOpen() {
        assertFalse(isBoardSetupLockedDuringGame(moveCount = 0, isGameEnded = true))
    }
}
