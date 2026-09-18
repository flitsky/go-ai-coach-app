package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.random.Random

/**
 * 5계층(App Service) — 대국이 끝났고 아직 기록되지 않았다면 [store]에 추가한다.
 *
 * ## ⚠️ 2026-09-18부터 **모든 대국 방식**을 기록한다 (백로그 #151, 사용자 결정)
 * 예전에는 *"정확히 한쪽만 사람"* 인 대국만 남기고 사람:사람과 AI:AI는 **즉시 빠져나갔다.**
 * 이제 셋 다 남긴다 — 다시보기(#156)의 대상이 대국 방식에 따라 달라질 이유가 없다.
 * 그래서 [GameHistoryEntry.humanColor]가 **nullable**이 됐고, 승패의 정본이 *"사람이 이겼는가"*
 * 에서 **[GameHistoryEntry.winner] "어느 진영이 이겼는가"** 로 옮겨갔다.
 *
 * ## 멱등성 — 저장소 자체가 근거다
 * `ui/GoCoachApp.kt`가 "대국 이어하기" 저장과 같은 `LaunchedEffect`에서 호출한다. 그 효과가
 * 관련 없는 이유로 여러 번 재실행돼도 중복 기록되지 않도록, 새 `LaunchedEffect`나 Compose
 * 상태를 더하지 않고 **가장 최근 기록과 (수순 개수·승자·기권 여부)를 견줘** 건너뛴다.
 *
 * ## 기권 처리 — 이제 **누가 이겼는지 안다**
 * 기권은 `finalScoreJudgement`를 남기지 않으므로(`resignCurrentGameIfAllowed`가 `isGameEnded`만
 * 표시하고 계가 파이프라인은 타지 않는다) `gameState.moves`의 마지막 수로 판정한다.
 * ⭐ `Move.Resign`은 `player`를 들고 있으므로 **승자는 그 반대편**이다 — 옛 결정(*"어느 쪽이
 * 기권했는지 구분하지 않는다"*)은 표시 문구의 문제였지 데이터의 한계가 아니었다.
 * ⚠️ 그래서 **2026-09-18 이전 기록만** 승자를 모른다.
 *
 * @return 실제로 새로 기록했다면 그 항목, 아니라면 `null`(아직 안 끝났거나 이미 기록됨).
 */
fun runGameHistoryAppendIfCompleted(
    isGameEnded: Boolean,
    finalScoreJudgement: FinalScoreJudgement?,
    gameState: GameState,
    playerSetup: PlayerSetup,
    nowMillis: Long,
    store: GameHistoryStorePort,
    scoreSnapshots: List<ScoreSnapshot> = emptyList(),
    moveEvaluations: List<MoveReviewMarker> = emptyList(),
): GameHistoryEntry? {
    if (!isGameEnded) return null

    val resignMove = gameState.moves.lastOrNull() as? Move.Resign
    val resigned = resignMove != null
    // 기권도 계가도 아니면 아직 결과가 없다 — 판이 끝났다고 표시만 된 중간 상태다.
    if (!resigned && finalScoreJudgement == null) return null

    val winner = if (resigned) resignMove.player.opponent else finalScoreJudgement?.winner
    val margin = if (resigned) null else finalScoreJudgement?.margin
    val moveCount = gameState.moves.size

    val lastEntry = store.loadAll().lastOrNull()
    val alreadyRecorded = lastEntry != null &&
        lastEntry.moveCount == moveCount &&
        lastEntry.winner == winner &&
        lastEntry.isResign == resigned
    if (alreadyRecorded) return null

    val replay = GameReplayData(
        moves = gameState.moves,
        scoreSnapshots = scoreSnapshots,
        // 무르기 뒤에도 남아 있는 옛 마커가 섞이지 않도록 지금 수순 길이로 자른다.
        moveEvaluations = moveEvaluations.filter { marker -> marker.moveNumber <= moveCount },
    )
    val entry = GameHistoryEntry(
        id = "$nowMillis-${Random.nextInt(0, 1_000_000)}",
        playedAtMillis = nowMillis,
        boardSize = gameState.boardSize.value,
        ruleset = finalScoreJudgement?.ruleset ?: gameState.ruleset,
        komi = gameState.komi,
        handicapCount = gameState.handicapCount,
        playerSetup = playerSetup,
        moveCount = moveCount,
        humanColor = singleHumanColorOrNull(playerSetup),
        winner = winner,
        isResign = resigned,
        margin = margin,
        hasReplay = !replay.isEmpty,
    )
    store.appendCompletedGame(entry, replay.takeIf { !it.isEmpty })
    return entry
}

/**
 * 정확히 한쪽만 [SeatController.Human]일 때만 그 색을 돌려준다 — 그 외(둘 다 사람/둘 다 AI)는 `null`.
 *
 * ⚠️ **더 이상 기록 여부를 가르지 않는다**(2026-09-18). `null`은 *"기록하지 않는다"* 가 아니라
 * *"이 판에는 '사람의 진영'이라는 개념이 없다"* 는 뜻이다.
 */
private fun singleHumanColorOrNull(playerSetup: PlayerSetup): StoneColor? {
    val blackIsHuman = playerSetup.black.controller == SeatController.Human
    val whiteIsHuman = playerSetup.white.controller == SeatController.Human
    return when {
        blackIsHuman && !whiteIsHuman -> StoneColor.Black
        whiteIsHuman && !blackIsHuman -> StoneColor.White
        else -> null
    }
}
