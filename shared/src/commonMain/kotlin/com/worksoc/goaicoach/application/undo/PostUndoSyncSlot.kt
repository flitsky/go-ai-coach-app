package com.worksoc.goaicoach.application.undo

import com.worksoc.goaicoach.shared.domain.GameState
import kotlinx.coroutines.Job

internal data class PendingPostUndoEngineSync(
    val targetState: GameState,
    val quietUntilMillis: Long,
)

/**
 * 무르기 뒤 대기 중인 엔진 재동기화 **한 건**의 자리 — 예약한 목표 국면과 그 잡(refactor backlog #107).
 *
 * ⚠️ **[UndoController]의 필드로 두지 않는 이유**: 그 컨트롤러는 `GoCoachApp`의 `remember(wiringContext)`로
 * 무르기 **바로 다음 프레임에** 새로 만들어진다 — 무르기가 세션 스냅샷·`undoEngineInterventionQuietUntil`·
 * `isPendingUndoSync`를 바꾸고, 그것들이 `wiringContext`의 remember 키이기 때문이다(함정 67 — 키는 뺄 수 없다).
 * 필드에 두면 예약한 잡은 버려진 옛 인스턴스에 남고, 새 인스턴스의 [UndoController.cancelPendingSync]는 빈 필드를
 * 보고 아무것도 안 한다. 그러면 `isPendingUndoSync`가 옛 잡이 깰 때까지 true로 남아 무르기 1초 안에 둔 수의 자동
 * 추천 수·착수 평가 요청이 버려지고, 그 사이의 설정 변경·두 번째 무르기도 그 잡을 멈추지 못한다.
 *
 * `GoCoachApp`이 키 없는 `remember`로 **한 번** 만들어 배선 컨텍스트로 넘기므로, 몇 번을 다시 배선해도 모든 세대의
 * [UndoController]가 같은 한 건을 보고 취소한다 — `TopMoveAnalysisDeferral`(추천 수 유예)·AI 차례 Job(#74, 수명
 * 컨트롤러가 쥔다)과 같은 모양이다. 읽고 쓰는 것은 [UndoController]뿐이고, 전부 UI 스코프(메인 스레드)에서다.
 */
class PostUndoSyncSlot {
    internal var pending: PendingPostUndoEngineSync? = null
    internal var job: Job? = null
}
