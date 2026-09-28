package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameSetup
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings

/**
 * **다음 대국**의 조건. 판 크기·계가 규칙·접바둑·덤은 여기가 유일한 출처다(refactor backlog #94, 룰은 #22) — 로비·설정
 * 화면이 그리고, 새 대국(`NewGameController.startConfiguredGame`)이 [nextGameSetup] 하나로 시작하고, 자동저장이 적는다.
 * **지금 판**의 조건은 `GameState`에 있고, 이어하기·기록 분기·되살린 끝난 판이면 둘이 다르다
 * ([applySavedGameRestore]는 이쪽의 판 조건을 건드리지 않는다).
 */
data class GameSessionSettingsState(
    val boardSize: BoardSize,
    val playerSetup: PlayerSetup,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val topMovesEnabled: Boolean,
    val handicapCount: Int = 0,
    val komi: Double = com.worksoc.goaicoach.shared.domain.DefaultKomi,
    /**
     * 다음 대국의 계가 규칙(refactor backlog #22). 저장된 설정(`UserPreferencesSnapshot.ruleset`)으로 채워진다.
     * 예전에는 이 칸이 없어 로비·새 대국·자동저장이 지금 판의 룰을 읽었고, 이어한 판의 룰이 다음 대국과 설정으로 샜다.
     */
    val ruleset: Ruleset = Ruleset.Japanese,
) {
    val matchMode: MatchMode
        get() = playerSetup.matchMode()

    /** 다음 대국의 정체성 네 값 — 새 대국과 로비 미리보기가 이것 **하나**로 판을 만든다(refactor backlog #22). */
    val nextGameSetup: GameSetup
        get() = GameSetup(boardSize = boardSize, ruleset = ruleset, handicapCount = handicapCount, komi = komi)

    fun applyPlayerSetup(nextSetup: PlayerSetup): GameSessionSettingsState =
        copy(playerSetup = nextSetup)

    fun applyBoardSize(size: BoardSize): GameSessionSettingsState =
        copy(
            boardSize = size,
            // 바둑판 크기 변경 시 접바둑 수가 새 바둑판의 최대값을 초과하면 클램프
            handicapCount = handicapCount.coerceAtMost(size.maxHandicapCount),
        )

    /**
     * 접바둑을 바꾸면 **덤도 함께** 정한다 — 규칙은 [komiAfterHandicapChange]가 갖는다
     * (refactor backlog #93). 로비와 설정 화면이 모두 `GameSettingsController.changeHandicapCount`를
     * 거쳐 여기에 닿으므로 이 한 곳이 두 화면을 함께 덮는다. 두 화면의 드롭다운·새 대국·자동저장이
     * 모두 **이 덤**을 읽는다(refactor backlog #94) — 이어지는 `refreshNewGamePreview`는 미리보기 판만 다시 그린다.
     */
    fun applyHandicap(count: Int): GameSessionSettingsState {
        val nextCount = count.coerceIn(0, boardSize.maxHandicapCount)
        return copy(
            handicapCount = nextCount,
            komi = komiAfterHandicapChange(
                previousHandicap = handicapCount,
                newHandicap = nextCount,
                currentKomi = komi,
            ),
        )
    }

    fun applyKomi(nextKomi: Double): GameSessionSettingsState =
        copy(komi = nextKomi)

    fun applyRuleset(nextRuleset: Ruleset): GameSessionSettingsState =
        copy(ruleset = nextRuleset)

    fun applySavedGameRestore(
        restoredSetup: PlayerSetup,
        restoredTopMovesEnabled: Boolean,
    ): GameSessionSettingsState =
        copy(
            playerSetup = restoredSetup,
            topMovesEnabled = restoredTopMovesEnabled,
        )

    fun applyAutoPlayDelay(setting: AutoPlayDelaySetting): GameSessionSettingsState =
        copy(autoPlayDelaySetting = setting)

    fun applySearchTimeSettings(settings: SearchTimeSettings): GameSessionSettingsState =
        copy(searchTimeSettings = settings.normalized())

    fun showTopMoves(): GameSessionSettingsState =
        copy(topMovesEnabled = true)

    fun hideTopMoves(): GameSessionSettingsState =
        copy(topMovesEnabled = false)
}
