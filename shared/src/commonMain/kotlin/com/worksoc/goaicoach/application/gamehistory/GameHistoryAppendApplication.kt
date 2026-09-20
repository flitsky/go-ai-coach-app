package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.movereview.deriveMoveReviewMarkersFromScoreSwing
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
 * ## ⚠️ 2026-09-20부터 손실집수는 **여기서 직접 계산한다** (백로그 #151 개정, 사용자 결정)
 * 예전에는 매 수 사람 차례가 시작될 때마다 엔진에 후보수 탐색을 걸어 손실집수를 미리
 * 구해 뒀다가([moveEvaluations]로 넘겨받았다) — 그런데 "추천 수 보기"·"착수 평가" 둘 다
 * 꺼 둔 사용자에게도 **그 탐색이 매 턴 돌아** 대국 진행 자체를 방해했다(사용자 제보).
 * 이제는 그 탐색을 하지 않는다. 대신 [deriveMoveReviewMarkersFromScoreSwing]이 이미
 * 공짜로 기록되는 [scoreSnapshots](매 수 엔진 동기화의 부산물)의 **앞뒤 차이**만으로 큰
 * 실수를 골라낸다 — 새 엔진 호출이 전혀 없다. 주 기능(대국 진행)이 보조 기능(다시보기)
 * 때문에 느려지지 않아야 한다는 게 이 개정의 철학이다.
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
        moveEvaluations = deriveMoveReviewMarkersFromScoreSwing(
            moves = gameState.moves,
            scoreSnapshots = scoreSnapshots,
            humanColors = humanControlledColors(playerSetup),
        ),
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
 * 사람이 잡은 진영 전부 — 둘 다일 수도(로컬 2인 대국), 하나도 없을 수도(AI:AI) 있다.
 *
 * ⚠️ **더 이상 이 파일 안에서만 쓰지 않는다**(2026-09-20) — [deriveReplayMoveEvaluations]가
 * 다시보기를 열 때마다 같은 계산을 다시 하려면 이 함수가 필요하다. 대국 저장 시점과
 * 다시보기 조회 시점이 **같은 규칙**으로 사람 진영을 가려야 결과가 어긋나지 않는다.
 */
fun humanControlledColors(playerSetup: PlayerSetup): Set<StoneColor> =
    buildSet {
        if (playerSetup.black.controller == SeatController.Human) add(StoneColor.Black)
        if (playerSetup.white.controller == SeatController.Human) add(StoneColor.White)
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
