package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import kotlin.random.Random

/**
 * 5계층(App Service) — 대국이 끝났고 아직 히스토리에 기록되지 않았다면 [store]에 추가한다.
 * [gameState]/[playerSetup]을 그대로 받아 호출부(`ui/GoCoachApp.kt`)가 라인 예산이 빠듯한
 * 셸에서 별도 request 객체를 조립하지 않고 한 번의 함수 호출로 끝내게 한다
 * (`LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`).
 *
 * `ui/GoCoachApp.kt`가 이미 갖고 있는, "대국 이어하기" 저장을 트리거하는 것과 같은
 * `LaunchedEffect`(`isGameEnded` 등을 키로 재구성마다 재실행)에서 같이 호출되도록 설계됐다 —
 * 그 효과가 관련 없는 이유로 여러 번 재실행돼도 중복 기록되지 않도록, 새 `LaunchedEffect`나
 * Compose 상태를 추가하지 않고 **저장소 자체를 근거로 멱등성을 확인**한다
 * (`runPremiumFeatureClaim`/`runAttendanceRewardGrant`와 같은 패턴): 가장 최근 기록이 이번
 * 판정과 (수순 개수, 승자, 집수차)로 일치하면 이미 기록된 것으로 보고 건너뛴다.
 *
 * @return 실제로 새로 기록했다면 그 항목, 아니라면 `null`(대국이 아직 안 끝났거나 이미 기록됨).
 */
fun runGameHistoryAppendIfCompleted(
    isGameEnded: Boolean,
    finalScoreJudgement: FinalScoreJudgement?,
    gameState: GameState,
    playerSetup: PlayerSetup,
    nowMillis: Long,
    store: GameHistoryStorePort,
): GameHistoryEntry? {
    val judgement = finalScoreJudgement
    if (!isGameEnded || judgement == null) return null

    val moveCount = gameState.moves.size
    val lastEntry = store.loadAll().lastOrNull()
    val alreadyRecorded = lastEntry != null &&
        lastEntry.moveCount == moveCount &&
        lastEntry.winner == judgement.winner &&
        lastEntry.margin == judgement.margin
    if (alreadyRecorded) return null

    val entry = GameHistoryEntry(
        id = "$nowMillis-${Random.nextInt(0, 1_000_000)}",
        playedAtMillis = nowMillis,
        boardSize = gameState.boardSize.value,
        ruleset = judgement.ruleset,
        komi = gameState.komi,
        handicapCount = gameState.handicapCount,
        playerSetup = playerSetup,
        moveCount = moveCount,
        winner = judgement.winner,
        margin = judgement.margin,
    )
    store.appendCompletedGame(entry)
    return entry
}
