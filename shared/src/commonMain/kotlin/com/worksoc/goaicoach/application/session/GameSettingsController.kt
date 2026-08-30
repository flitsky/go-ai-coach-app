package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.engine.operation.EngineOperationGate
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeAutoPlayDelayChangeLog
import com.worksoc.goaicoach.application.analysis.toDisplayText
import com.worksoc.goaicoach.application.topmoves.SearchTimeTopMovesResetRunRequest
import com.worksoc.goaicoach.application.topmoves.runSearchTimeTopMovesResetApplication
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.EngineProfile

/**
 * Owns three related settings-change workflows that share no engine I/O
 * and are gated only on engine-busy state or validation rules:
 *
 * - [changePlayerSetup]: validates engine-busy gate, builds plan, applies it.
 * - [changeSearchTimeSettings]: validates gate, normalises settings, resets top-move
 *   analysis cache, updates runtime play-level selection.
 * - [changeAutoPlayDelay]: logs and applies delay setting.
 * - [changeBoardSize]/[changeHandicapCount]: only apply while the game has ended (mid-game
 *   resizing would invalidate the board already in play), then refresh the new-game preview.
 * - [changeKomi]: no such gate — komi can change mid-game, updating the live [GameState]
 *   and its score display in place instead of only the preview.
 *
 * GoCoachApp delegates all handlers here. The composable supplies
 * provider lambdas for current state values and callbacks for state writes.
 * This controller owns no Compose state itself.
 */
class GameSettingsController(
    private val currentGameState: () -> GameState,
    private val currentPlayerSetup: () -> PlayerSetup,
    private val currentEngineProfile: () -> EngineProfile,
    private val currentSearchTimeSettings: () -> SearchTimeSettings,
    private val currentAnalysisState: () -> GameSessionAnalysisState,
    private val currentAutoPlayDelaySetting: () -> AutoPlayDelaySetting,
    private val currentSettingsState: () -> GameSessionSettingsState,
    private val isGameEnded: () -> Boolean,
    private val defaultPlayLevel: PlayLevelSetting,
    private val isEngineBusy: () -> Boolean,
    private val runtimeEventLog: RuntimeEventLogPort,
    private val currentRuntimeLogContext: () -> RuntimeLogContext,
    private val onEngineMessage: (String) -> Unit,
    private val applyPlayerSetup: (PlayerSetup) -> Unit,
    private val applyCoreSessionState: (GameSessionCoreState) -> Unit,
    private val currentCoreSessionState: () -> GameSessionCoreState,
    private val applyRuntimePlayLevelSelection: (RuntimePlayLevelSelection) -> Unit,
    private val applyAnalysisState: (GameSessionAnalysisState) -> Unit,
    private val applySettingsAutoPlayDelay: (AutoPlayDelaySetting) -> Unit,
    private val applySettingsSearchTimeSettings: (SearchTimeSettings) -> Unit,
    private val applySettingsBoardSize: (BoardSize) -> Unit,
    private val applySettingsHandicapCount: (Int) -> Unit,
    private val applySettingsKomi: (Double) -> Unit,
    private val clearUndoEngineInterventionQuietWindow: () -> Unit,
) {
    /**
     * Validates engine-busy gate, then builds and applies a [PlayerSetupChangePlan].
     * No engine I/O — only local state mutation.
     */
    fun changePlayerSetup(nextSetup: PlayerSetup) {
        val gameState = currentGameState()
        when (
            val plan = buildPlayerSetupChangePlan(
                nextSetup = nextSetup,
                currentState = gameState,
                currentProfile = currentEngineProfile(),
                defaultPlayLevel = defaultPlayLevel,
                isEngineBusy = isEngineBusy(),
                searchTimeSettings = currentSearchTimeSettings(),
            )
        ) {
            is PlayerSetupChangePlan.ShowMessage -> {
                onEngineMessage(plan.message)
            }
            is PlayerSetupChangePlan.Apply -> {
                clearUndoEngineInterventionQuietWindow()
                applyPlayerSetup(plan.playerSetup)
                applyCoreSessionState(currentCoreSessionState().applyPlayerSetupChangePlan(plan))
            }
        }
    }

    /**
     * Validates engine-busy gate, normalises [SearchTimeSettings], resets the
     * top-moves analysis cache, and updates runtime play-level selection.
     */
    /**
     * 최대 탐색 시간 제한을 바꾼다. **엔진이 바쁠 때도 받는다**(2026-08-30).
     *
     * 예전에는 `evaluateSearchTimeChangeGate`가 엔진이 바쁘면 막았는데, 그 게이트가 **AI 대 AI
     * 대국에서 이 설정을 영영 못 만지게 했다** — 그 모드에서는 엔진이 사실상 항상 바쁘다.
     *
     * 막을 이유도 없었다. 이 값은 **다음 엔진 호출부터** 적용된다: 여기서 바꾸는 것은
     * `settingsState.searchTimeSettings`와 그로부터 파생되는 런타임 플레이 레벨뿐이고,
     * 진행 중인 작업은 시작할 때 이미 자기 `analysisLimit`을 확보했다. 즉 날아가는 탐색을
     * 중간에 흔들지 않는다.
     */
    fun changeSearchTimeSettings(nextSettings: SearchTimeSettings) {
        val normalized = nextSettings.normalized()
        clearUndoEngineInterventionQuietWindow()
        applySettingsSearchTimeSettings(normalized)
        applyRuntimePlayLevelSelection(
            selectRuntimePlayLevel(
                setup = currentPlayerSetup(),
                nextPlayer = currentGameState().nextPlayer,
                currentProfile = currentEngineProfile(),
                defaultPlayLevel = defaultPlayLevel,
                searchTimeSettings = normalized,
            ),
        )
        runSearchTimeTopMovesResetApplication(
            SearchTimeTopMovesResetRunRequest(
                analysisState = currentAnalysisState(),
                state = currentGameState(),
                applyAnalysisState = applyAnalysisState,
            ),
        )
    }

    /**
     * Logs the delay change and applies the new [AutoPlayDelaySetting].
     * No gate check required — purely a UI preference update.
     */
    fun changeAutoPlayDelay(setting: AutoPlayDelaySetting) {
        runtimeEventLog.append(
            runtimeAutoPlayDelayChangeLog(
                context = currentRuntimeLogContext(),
                from = currentAutoPlayDelaySetting(),
                to = setting,
            ),
        )
        applySettingsAutoPlayDelay(setting)
    }

    /** Board size only changes between games — mid-game resizing would invalidate the board already in play. */
    fun changeBoardSize(size: BoardSize) {
        if (!isGameEnded()) return
        applySettingsBoardSize(size)
        refreshNewGamePreview()
    }

    /** Same isGameEnded gate as [changeBoardSize] — handicap only changes between games. */
    fun changeHandicapCount(count: Int) {
        if (!isGameEnded()) return
        applySettingsHandicapCount(count)
        refreshNewGamePreview()
    }

    /**
     * Unlike board size/handicap, komi doesn't invalidate an in-progress board, so it can
     * change mid-game. While a game is running, the live [GameState] and its score display
     * update in place instead of only the new-game preview.
     */
    fun changeKomi(komi: Double) {
        applySettingsKomi(komi)
        if (isGameEnded()) {
            refreshNewGamePreview()
            return
        }
        val updatedState = currentGameState().copy(komi = komi)
        val core = currentCoreSessionState()
        applyCoreSessionState(
            core.copy(
                gameState = updatedState,
                scoreState = core.scoreState.copy(scoreText = BoardScorer.score(updatedState).toDisplayText()),
            ),
        )
    }

    /**
     * Re-derives the not-yet-started game's preview board from the current settings —
     * called whenever board size/handicap/komi changes between games, and also by
     * GoCoachApp when leaving an ended game back to Home.
     */
    fun refreshNewGamePreview() {
        val settings = currentSettingsState()
        applyCoreSessionState(
            currentCoreSessionState().applyGameSetupPreview(
                ruleset = currentGameState().ruleset,
                boardSize = settings.boardSize,
                handicapCount = settings.handicapCount,
                komi = settings.komi,
            ),
        )
    }
}
