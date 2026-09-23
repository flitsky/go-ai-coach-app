package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

/**
 * 다시보기의 수순 N에서 갈라 나오는 **새 대국**의 시작점(백로그 #172).
 *
 * ## ⚠️ 원본은 한 글자도 건드리지 않는다
 * 여기 들어오는 [GameHistoryEntry]·[GameReplayData]는 **읽기 전용**이다(2026-09-19 사용자, 1번).
 * 이 파일은 그 둘에서 값을 **베껴** 새 스냅샷을 만들 뿐이고, 결과물이 끝나면 평소의
 * `GameHistoryStore.appendCompletedGame(...)` 경로로 **별개의 새 기록**이 쌓인다 —
 * 덮어쓰기 경로 자체가 없다.
 *
 * ## ⭐ 새로 짤 것이 거의 없었던 이유 — 「이어하기」와 같은 모양이다
 * 분기 대국이 필요로 하는 것(수순이 이미 실린 `GameState`에서 대국을 시작하고, 엔진을 그
 * 국면에 맞추고, 플레이어 설정을 그 판의 것으로 되돌리는 것)은 **이어하기가 이미 하는 일
 * 전부**다. 그래서 새 진입점을 파지 않고 [SavedGameSnapshot]을 하나 지어
 * `SavedSessionController.restore(...)`에 태운다 — 엔진 동기화(`runRestoredGameSyncApplication`),
 * 세션·매치 제너레이션, 턴 시계 리셋이 전부 검증된 그 길에 이미 있다.
 *
 * ⚠️ **저장 슬롯에 직접 쓰지 않는다.** 이 스냅샷은 메모리에서 복원 경로로 바로 건네고,
 * 저장은 평소의 자동저장(`runSavedGamePersistenceApplication`)이 맡는다. 진행 중이던
 * **다른** 대국이 슬롯에 있으면 그것이 밀려나므로, 부르는 쪽이 먼저 경고를 띄운다.
 */

/**
 * 이 국면에서 새 대국을 시작할 수 있는가.
 *
 * ⚠️ **끝난 국면에서는 분기할 수 없다** — 기권이 들어 있거나 연속 패스로 종국한 판을 그대로
 * 새 대국의 전반부로 삼으면, **시작하자마자 끝나 있는** 대국이 만들어진다. 마지막 수에서 여는
 * 다시보기의 기본 위치가 정확히 그 자리라(#156) 가장 먼저 눌리는 자리이기도 하다.
 *
 * 수순 0(시작 국면)은 **막지 않는다** — 그 판의 설정 그대로 처음부터 다시 두는 것도 뜻이 있다.
 */
fun canStartBranchedGameAt(state: GameState): Boolean =
    state.moves.none { move -> move is Move.Resign } &&
        !state.hasConsecutivePasses() &&
        !state.isBoardFull()

/**
 * [branchState]까지 둔 판을 전반부로 물려받는 새 대국의 스냅샷.
 *
 * @param branchState 다시보기가 보고 있던 그 국면 — `GameReplayTimeline.stateAt(N)`이 그대로 온다.
 *   `moves`가 이미 원본의 앞 N수라 **국면을 새로 계산하지 않는다**(`BoardRules.playStone`이
 *   `moves = state.moves + move`로 누적하기 때문).
 * @param playerSetup 원 대국의 좌석 설정 — 흑/백의 사람·AI와 **AI 기력까지** 여기 실려 있다
 *   (`PlayerSetupJsonCodec`가 `playLevel`을 기록에 남긴다). 대국 설정 화면을 다시 거치지
 *   않는다는 결정(2026-09-19 사용자, 구 U-39)이 이 한 줄로 지켜진다.
 * @param scoreSnapshots 원본 리플레이의 형세 기록 **전부** — 분기점까지만 잘라 물려준다.
 *   ⚠️ 자르지 않으면 새 대국의 그래프가 **아직 두지 않은 수의 형세**를 미리 그린다.
 * @param topMovesEnabled 지금 설정값 — 복원 경로가 이 값을 설정에 되쓰기 때문에, 원 대국의
 *   것이 아니라 **현재 값**을 그대로 통과시켜야 사용자의 설정이 조용히 꺼지지 않는다.
 */
fun buildBranchedGameSnapshot(
    branchState: GameState,
    playerSetup: PlayerSetup,
    scoreSnapshots: List<ScoreSnapshot>,
    topMovesEnabled: Boolean,
    nowMillis: Long,
): SavedGameSnapshot {
    val branchAtMoveNumber = branchState.moves.size
    return SavedGameSnapshot(
        gameState = branchState,
        playerSetup = playerSetup,
        playLevel = branchedGamePlayLevel(playerSetup),
        topMovesEnabled = topMovesEnabled,
        savedAtMillis = nowMillis,
        scoreSnapshots = scoreSnapshots.filter { snapshot -> snapshot.moveNumber <= branchAtMoveNumber },
        // ⚠️ **반드시 null이다.** 값이 있으면 `buildEndedGameRestoreDisplayPlan`이 이것을
        // "끝난 대국의 결과 팝업을 되살려라"로 읽는다 — 분기는 **이어서 둘** 판이다.
        finalScoreJudgement = null,
    )
}

/**
 * 스냅샷의 [SavedGameSnapshot.playLevel]은 이어하기 팝업이 "AI 기력"을 적을 때 쓰는 값이다.
 * 분기 대국은 그 팝업을 거치지 않지만, 비워 두면 기본값(입문)이 들어가 디버그 리포트가
 * 실제와 다른 기력을 말한다 — 좌석에서 그대로 집어 온다. 사람:사람이면 기본값이 맞다.
 */
private fun branchedGamePlayLevel(playerSetup: PlayerSetup): PlayLevelSetting =
    playerSetup.seats().firstOrNull { seat -> seat.isAi }?.setup?.playLevel ?: PlayLevelSetting()
