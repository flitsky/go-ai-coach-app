package com.worksoc.goaicoach.ui

import android.content.Context
import com.worksoc.goaicoach.application.gamehistory.runGameHistoryAppendIfCompleted
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.shared.GameState

/**
 * 홈으로 나가기 **직전에** 끝난 대국을 기록한다(백로그 #96).
 *
 * ## ⚠️ 왜 나가는 길목에서 한 번 더 기록하는가 — 반응형 경로만으로는 놓친다
 * 평소의 기록은 `GoCoachApp`의 `LaunchedEffect`가 한다(세션 스냅샷이 바뀌면 돈다). 그런데
 * **뒤로가기 기권**은 한 핸들러 안에서 이렇게 흘렀다:
 *
 * ```
 * dispatch(ResignCurrentGame)   // gameState.moves 에 Move.Resign 이 붙는다
 * exitToHome()                  // refreshNewGamePreview() 가 판을 새 미리보기로 갈아엎는다
 * ```
 *
 * 둘 다 **재구성 전에** 동기로 끝나므로, 효과가 다시 돌 때 `gameState.moves`에는 **`Move.Resign`이
 * 이미 없다.** 그래서 `resigned=false` + `finalScoreJudgement==null` → 기록이 조용히 건너뛰어졌다
 * (2026-09-05 사용자 제보, 차분 실험으로 확인: 인게임 기권 버튼은 기록되고 뒤로가기 기권만 안 됐다).
 *
 * ⚠️ **단위 테스트가 이 결함을 못 잡았다는 점이 중요하다.** `runGameHistoryAppendIfCompleted`의
 * 기권 테스트는 **이미 있었고 통과했다** — 순수 함수는 옳았고 깨진 것은 **호출 순서**다.
 * 순수 함수에 그물을 아무리 촘촘히 쳐도 이런 결함은 잡히지 않는다.
 *
 * ## ⚠️ 두 번 기록되지 않는다
 * `runGameHistoryAppendIfCompleted`가 **저장소를 근거로 멱등성을 확인**한다(가장 최근 기록이 수순
 * 개수·결과·사람 진영으로 일치하면 건너뛴다). 그래서 정상 종국처럼 효과가 이미 기록한 경우에는
 * 여기서 다시 불러도 아무 일도 일어나지 않는다.
 *
 * ⚠️ **`refreshNewGamePreview()`보다 먼저 불러야 한다** — 그것이 이 함수의 존재 이유 전부다.
 * 순서를 바꾸면 버그가 그대로 돌아오고, **테스트는 여전히 초록이다**(`GameExitRecordingContractTest`가
 * 그 순서를 소스에서 못박는 이유).
 */
internal fun recordFinishedGameOnExit(
    context: Context,
    isGameEnded: Boolean,
    finalScoreJudgement: FinalScoreJudgement?,
    gameState: GameState,
    playerSetup: PlayerSetup,
) {
    runGameHistoryAppendIfCompleted(
        isGameEnded = isGameEnded,
        finalScoreJudgement = finalScoreJudgement,
        gameState = gameState,
        playerSetup = playerSetup,
        nowMillis = System.currentTimeMillis(),
        store = GameHistoryStore(context),
    )
}
