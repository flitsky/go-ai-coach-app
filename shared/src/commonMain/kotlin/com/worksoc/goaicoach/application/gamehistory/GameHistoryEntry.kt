package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor

/**
 * 사람 플레이어 기준 결과.
 *
 * ⚠️ **옛 저장분을 읽을 때만 쓴다**(백로그 #151). 2026-09-18부터 사람이 없거나(AI:AI) 둘인
 * (사람:사람) 대국도 기록하므로 *"사람 기준"* 이 성립하지 않는다 — 지금의 정본은
 * [GameHistoryEntry.winner]다. 이 열거형을 화면에 다시 쓰지 말 것.
 */
enum class GameHistoryResult {
    Win,
    Loss,
    Draw,
    Resign,
}

/**
 * 완료된 대국 한 판의 **목록용 메타데이터**. `SavedGameSnapshot`과는 다른 개념이다 — 그건
 * "진행 중인 대국 1개 이어하기" 전용이고, 이건 끝난 대국을 누적해 탐색하기 위한 것이다.
 *
 * ⚠️ **수순·형세·착수 평가는 여기 없다 — [GameReplayData]로 분리했다**(백로그 #151).
 * 목록 화면은 한 번에 수백 줄을 그리는데, 리플레이 본문까지 같이 읽으면 **화면에 쓰지도 않을
 * 수십 MB를 매번 파싱**하게 된다. 본문은 다시보기가 열릴 때 그 한 판만 읽는다.
 *
 * [humanColor]는 **사람이 정확히 한 명일 때만** 값이 있다(사람:사람·AI:AI는 `null`).
 */
data class GameHistoryEntry(
    val id: String,
    val playedAtMillis: Long,
    val boardSize: Int,
    val ruleset: Ruleset,
    val komi: Double,
    val handicapCount: Int,
    val playerSetup: PlayerSetup,
    val moveCount: Int,
    val humanColor: StoneColor?,
    /**
     * 승리한 진영. `null`이면 무승부 **이거나**, 옛 기권 기록이라 승자를 모른다([isResign]로 가른다).
     *
     * ⚠️ 2026-09-18 이전의 기권 기록은 *"어느 쪽이 기권했는지 구분하지 않는다"* 는 옛 결정 때문에
     * **승자를 복원할 수 없다.** 그래서 화면은 `winner == null && isResign`을 "기권"으로만 표시한다.
     */
    val winner: StoneColor?,
    val isResign: Boolean = false,
    val margin: Double? = null,
    /** 이 기록에 딸린 [GameReplayData]가 저장돼 있는가. 목록에서 "다시보기" 가능 여부를 가른다. */
    val hasReplay: Boolean = false,
)

/**
 * 다시보기가 **엔진 재탐색 없이** 한 판을 재생하는 데 필요한 전부(백로그 #151).
 *
 * ⚠️ **새로 계산하는 것이 아니라 옮겨 담는 것이다** — 셋 다 대국 중에 이미 만들어져 세션
 * 상태에 들어 있고, 대국이 끝나는 순간 버려지고 있었다.
 *
 * ⚠️ **저장 가능한 것에 층이 있다.** [moves]는 항상 남지만, [scoreSnapshots]는 무료 대국에서
 * `LocalAreaEstimate`라 거칠고 `whiteWinRate`가 `null`이며 **초반에 거짓말을 한다.**
 * [moveEvaluations]는 사전 분석 캐시가 준비된 수에만 붙는다.
 */
data class GameReplayData(
    val moves: List<Move>,
    val scoreSnapshots: List<ScoreSnapshot> = emptyList(),
    val moveEvaluations: List<MoveReviewMarker> = emptyList(),
) {
    val isEmpty: Boolean = moves.isEmpty()
}
