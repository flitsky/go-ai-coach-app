package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings

data class GameSessionSettingsState(
    val boardSize: BoardSize,
    val playerSetup: PlayerSetup,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val topMovesEnabled: Boolean,
    val handicapCount: Int = 0,
    val komi: Double = com.worksoc.goaicoach.shared.domain.DefaultKomi,
) {
    val matchMode: MatchMode
        get() = playerSetup.matchMode()

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
     * 거쳐 여기에 닿으므로 이 한 곳이 두 화면을 함께 덮는다. 이어지는 `refreshNewGamePreview`가
     * 이 덤으로 미리보기를 다시 그려 드롭다운에 곧바로 보이고, 자동저장은 그 미리보기의 덤을 적는다.
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
