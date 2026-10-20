package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnExecutionContext
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.hasHumanSeat
import com.worksoc.goaicoach.shared.playstyle.MoveTempo
import com.worksoc.goaicoach.shared.playstyle.TempoBand
import com.worksoc.goaicoach.shared.playstyle.moveTempo
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

/**
 * 이번 AI 차례의 착수 템포(백로그 #212) — 어느 구간이고, 두기까지 얼마이며, 엔진이 이미 얼마를 썼는가. 진단 로그에 그대로 적는다.
 *
 * @property waitMillis 앱이 더 기다리는 몫 — 모자란 만큼만이다. 엔진이 [targetMillis]보다 오래 걸렸으면 0이다.
 */
internal data class AutoAiTurnTempo(
    val band: TempoBand,
    val targetMillis: Long,
    val engineMillis: Long,
) {
    val waitMillis: Long get() = (targetMillis - engineMillis).coerceAtLeast(0L)
}

/**
 * 이번 차례에 템포가 있으면 그것, 없으면 `null`(엔진이 답하는 대로 곧바로 둔다).
 *
 * 사람이 앉은 판에서 캐릭터가 둘 때만 있다([moveTempo]). AI끼리 두는 판(개발자 메뉴)은 그 판의 지연 설정(`AutoPlayDelaySetting`)을 따른다.
 * 형세는 차례를 요청할 때의 형세 기록([snapshots])에서 읽는다.
 */
internal fun autoAiTurnTempo(
    context: AutoAiTurnExecutionContext,
    playerSetup: PlayerSetup,
    snapshots: List<ScoreSnapshot>,
    searchTimeSettings: SearchTimeSettings,
    engineMillis: Long,
): AutoAiTurnTempo? {
    if (!playerSetup.hasHumanSeat()) return null
    val tempo = context.playLevel.moveTempo() ?: return null
    val state = context.turnState
    val band = tempo.bandAt(
        moveNumber = state.moves.size + 1,
        boardSize = state.boardSize,
        ownScoreLead = MoveTempo.ownScoreLead(context.aiPlayer, snapshots),
    )
    return AutoAiTurnTempo(
        band = band,
        targetMillis = tempo.targetMillis(band, capMillis = searchTimeSettings.limit.maximumMillis),
        engineMillis = engineMillis,
    )
}
