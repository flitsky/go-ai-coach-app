package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.contract.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.contract.selectRuntimePlayLevel
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings

sealed class PlayerSetupChangePlan {
    data class ShowMessage(val message: String) : PlayerSetupChangePlan()
    data class Apply(
        val playerSetup: PlayerSetup,
        val runtime: RuntimePlayLevelSelection,
        val reviewAnalysis: MoveAnalysisSnapshot,
        val topMoveClearMessage: String,
    ) : PlayerSetupChangePlan()
}

/**
 * 플레이어 설정(좌석·AI 캐릭터) 바꾸기. **엔진이 바빠도 받는다**(2026-09-30 사용자 요청).
 *
 * 예전에는 엔진이 바쁘면 막았는데, **AI 대 AI 대국에서는 엔진이 사실상 늘 바빠** 좌석을 「유저」로 되돌릴 수 없었다
 * (최대 탐색 시간이 2026-08-30에 같은 이유로 풀렸다 — `GameSettingsController.changeSearchTimeSettings`).
 * 바꾼 값은 **다음 수부터** 적용된다 — 이미 탐색 중인 AI 차례는 자기 프로필로 끝까지 두고, 그다음 차례를 트리거가
 * 새 좌석으로 고른다. 사용자 판단: *"변경되면 다음 수부터 적용되는 형태라면 문제없다."*
 */
fun buildPlayerSetupChangePlan(
    nextSetup: PlayerSetup,
    currentState: GameState,
    currentProfile: EngineProfile,
    defaultPlayLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
): PlayerSetupChangePlan {
    return PlayerSetupChangePlan.Apply(
        playerSetup = nextSetup,
        runtime = selectRuntimePlayLevel(
            setup = nextSetup,
            nextPlayer = currentState.nextPlayer,
            currentProfile = currentProfile,
            defaultPlayLevel = defaultPlayLevel,
            searchTimeSettings = searchTimeSettings,
        ),
        reviewAnalysis = MoveAnalysisSnapshot.empty(currentState),
        topMoveClearMessage = "Player Setup changed. Press New to restart with this setup, or continue from the current position.",
    )
}
