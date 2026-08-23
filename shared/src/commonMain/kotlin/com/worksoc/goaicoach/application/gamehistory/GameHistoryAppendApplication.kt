package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.random.Random

/**
 * 5계층(App Service) — 대국이 끝났고, **정확히 한쪽만 사람인 대국**이며, 아직 히스토리에
 * 기록되지 않았다면 [store]에 추가한다. 사람끼리 또는 AI끼리 대국은 지금은 기록하지 않는다
 * (사용자 결정, 백로그 #7 디버깅 — "우선 대국 기록에 저장을 고려하지 않겠습니다").
 *
 * [gameState]/[playerSetup]을 그대로 받아 호출부(`ui/GoCoachApp.kt`)가 라인 예산이 빠듯한
 * 셸에서 별도 request 객체를 조립하지 않고 한 번의 함수 호출로 끝내게 한다
 * (`LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`).
 *
 * `ui/GoCoachApp.kt`가 이미 갖고 있는, "대국 이어하기" 저장을 트리거하는 것과 같은
 * `LaunchedEffect`(`isGameEnded` 등을 키로 재구성마다 재실행)에서 같이 호출되도록 설계됐다 —
 * 그 효과가 관련 없는 이유로 여러 번 재실행돼도 중복 기록되지 않도록, 새 `LaunchedEffect`나
 * Compose 상태를 추가하지 않고 **저장소 자체를 근거로 멱등성을 확인**한다
 * (`runPremiumFeatureClaim`/`runAttendanceRewardGrant`와 같은 패턴): 가장 최근 기록이 이번
 * 판정과 (수순 개수, 결과, 사람 진영)로 일치하면 이미 기록된 것으로 보고 건너뛴다.
 *
 * **기권 처리**: 기권은 `finalScoreJudgement`를 남기지 않는다(`resignCurrentGameIfAllowed`가
 * `isGameEnded = true`만 표시하고 계가 파이프라인은 타지 않음) — 대신 `gameState.moves`의
 * 마지막 수가 `Move.Resign`인지로 판정한다. 어느 쪽이 기권했는지는 구분하지 않고 항상
 * [GameHistoryResult.Resign]으로 기록한다(사용자 요청).
 *
 * @return 실제로 새로 기록했다면 그 항목, 아니라면 `null`(대국이 아직 안 끝났거나, 사람 대 AI
 *   대국이 아니거나, 이미 기록됨).
 */
fun runGameHistoryAppendIfCompleted(
    isGameEnded: Boolean,
    finalScoreJudgement: FinalScoreJudgement?,
    gameState: GameState,
    playerSetup: PlayerSetup,
    nowMillis: Long,
    store: GameHistoryStorePort,
): GameHistoryEntry? {
    if (!isGameEnded) return null
    val humanColor = singleHumanColorOrNull(playerSetup) ?: return null

    val resigned = gameState.moves.lastOrNull() is Move.Resign
    val result = when {
        resigned -> GameHistoryResult.Resign
        finalScoreJudgement == null -> return null
        finalScoreJudgement.winner == null -> GameHistoryResult.Draw
        finalScoreJudgement.winner == humanColor -> GameHistoryResult.Win
        else -> GameHistoryResult.Loss
    }
    val margin = if (resigned) null else finalScoreJudgement?.margin

    val moveCount = gameState.moves.size
    val lastEntry = store.loadAll().lastOrNull()
    val alreadyRecorded = lastEntry != null &&
        lastEntry.moveCount == moveCount &&
        lastEntry.result == result &&
        lastEntry.humanColor == humanColor
    if (alreadyRecorded) return null

    val entry = GameHistoryEntry(
        id = "$nowMillis-${Random.nextInt(0, 1_000_000)}",
        playedAtMillis = nowMillis,
        boardSize = gameState.boardSize.value,
        ruleset = finalScoreJudgement?.ruleset ?: gameState.ruleset,
        komi = gameState.komi,
        handicapCount = gameState.handicapCount,
        playerSetup = playerSetup,
        moveCount = moveCount,
        humanColor = humanColor,
        result = result,
        margin = margin,
    )
    store.appendCompletedGame(entry)
    return entry
}

/** 정확히 한쪽만 [SeatController.Human]일 때만 그 색을 돌려준다 — 그 외(둘 다 사람/둘 다 AI)는 `null`. */
private fun singleHumanColorOrNull(playerSetup: PlayerSetup): StoneColor? {
    val blackIsHuman = playerSetup.black.controller == SeatController.Human
    val whiteIsHuman = playerSetup.white.controller == SeatController.Human
    return when {
        blackIsHuman && !whiteIsHuman -> StoneColor.Black
        whiteIsHuman && !blackIsHuman -> StoneColor.White
        else -> null
    }
}
