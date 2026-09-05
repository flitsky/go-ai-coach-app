package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.aiMoveSearchMode
import com.worksoc.goaicoach.shared.describe

data class TurnOutcome(
    val gameState: GameState,
    val engineMessage: String,
    val candidateText: String,
    val lastMoveText: String,
)

interface AiMoveEngineGateway {
    suspend fun playMove(move: Move): EngineStatus
    suspend fun genMove(player: StoneColor): MoveResult
    suspend fun clearSearchCache(): EngineStatus
    suspend fun analyze(limit: AnalysisLimit): AnalysisResult
}

suspend fun applyAiResponseAfterHumanTurn(
    engineAdapter: AiMoveEngineGateway,
    stateAfterHuman: GameState,
    humanMove: Move,
    playLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    onHumanMoveAccepted: suspend () -> Unit = {},
): TurnOutcome {
    val humanStatus = engineAdapter.playMove(humanMove)
    val humanText = humanMove.describe(stateAfterHuman.boardSize)
    onHumanMoveAccepted()

    if (MatchReferee.shouldResolveEndgame(stateAfterHuman)) {
        val endgameReason = MatchReferee.endgameReasonText(stateAfterHuman) ?: "Game ended."
        return TurnOutcome(
            gameState = stateAfterHuman,
            engineMessage = "${humanStatus.message}\n$endgameReason",
            candidateText = "Game ended after $humanText.",
            lastMoveText = humanText,
        )
    }

    // Human-vs-AI keeps KataGo's search tree reuse. This is the normal engine
    // continuation case: the same AI benefits from prior reading, and there is
    // no cross-seat leakage between two differently budgeted AI players.
    val selectedAiMove = engineAdapter.selectAiMoveFromAnalysis(
        currentState = stateAfterHuman,
        aiPlayer = AiPlayer,
        playLevel = playLevel,
        searchTimeSettings = searchTimeSettings,
        searchMode = playLevel.aiMoveSearchMode(),
    )
    if (selectedAiMove != null) {
        val afterAi = MatchReferee.play(stateAfterHuman, selectedAiMove.move).getOrNull()
        if (afterAi != null) {
            val syncStatus = engineAdapter.playMove(selectedAiMove.move)
            val aiText = selectedAiMove.move.describe(stateAfterHuman.boardSize)
            return TurnOutcome(
                gameState = afterAi,
                engineMessage = "${humanStatus.message}\n${syncStatus.message}\nAI selected $aiText from ${playLevel.displayLabel}.",
                candidateText = selectedAiMove.summary,
                lastMoveText = aiText,
            )
        }
    }

    val aiResult = engineAdapter.genMove(AiPlayer)
    val afterAi = MatchReferee.playOrThrow(stateAfterHuman, aiResult.move)
    val aiText = aiResult.move.describe(stateAfterHuman.boardSize)
    return TurnOutcome(
        gameState = afterAi,
        engineMessage = "${humanStatus.message}\n${aiResult.status.message}\n${aiResult.summary}",
        candidateText = "AI replied with $aiText.",
        lastMoveText = aiText,
    )
}

suspend fun applyAiTurn(
    engineAdapter: AiMoveEngineGateway,
    currentState: GameState,
    aiPlayer: StoneColor,
    playLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    searchMode: EngineSearchMode? = null,
    isolateSearchCache: Boolean = false,
    analysisProvider: (suspend (AnalysisLimit) -> AnalysisResult)? = null,
): TurnOutcome {
    val resolvedSearchMode = searchMode ?: playLevel.aiMoveSearchMode()
    if (isolateSearchCache && resolvedSearchMode == EngineSearchMode.GtpStatefulFast) {
        // AI-vs-AI currently shares one KataGo process. Without this isolation,
        // a lower-budget side can inherit the previous higher-budget side's
        // subtree and hide the intended B16/B32/B64 strength gap.
        engineAdapter.clearSearchCache()
    }
    val selectedAiMove = engineAdapter.selectAiMoveFromAnalysis(
        currentState = currentState,
        aiPlayer = aiPlayer,
        playLevel = playLevel,
        searchTimeSettings = searchTimeSettings,
        searchMode = resolvedSearchMode,
        analysisProvider = analysisProvider,
    )
    if (selectedAiMove != null) {
        val afterAi = MatchReferee.play(currentState, selectedAiMove.move).getOrNull()
        if (afterAi != null) {
            val syncStatus = engineAdapter.playMove(selectedAiMove.move)
            val aiText = selectedAiMove.move.describe(currentState.boardSize)
            return TurnOutcome(
                gameState = afterAi,
                engineMessage = "${syncStatus.message}\nAI selected $aiText from ${playLevel.displayLabel}.",
                candidateText = selectedAiMove.summary,
                lastMoveText = aiText,
            )
        }
    }

    val aiResult = engineAdapter.genMove(aiPlayer)
    val afterAi = MatchReferee.playOrThrow(currentState, aiResult.move)
    val aiText = aiResult.move.describe(currentState.boardSize)
    return TurnOutcome(
        gameState = afterAi,
        engineMessage = "${aiResult.status.message}\n${aiResult.summary}",
        candidateText = "AI replied with $aiText.",
        lastMoveText = aiText,
    )
}

fun boardInputEnabled(
    playerSetup: PlayerSetup,
    isEngineReady: Boolean,
    isEngineBlockingBusy: Boolean,
    nextPlayer: StoneColor,
): Boolean =
    playerSetup
        .seatSnapshot(
            nextPlayer = nextPlayer,
            isEngineReady = isEngineReady,
            isEngineBlockingBusy = isEngineBlockingBusy,
        )
        .current
        .canAcceptBoardInput


private suspend fun AiMoveEngineGateway.selectAiMoveFromAnalysis(
    currentState: GameState,
    aiPlayer: StoneColor,
    playLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings,
    searchMode: EngineSearchMode,
    analysisProvider: (suspend (AnalysisLimit) -> AnalysisResult)? = null,
): SelectedAiMove? =
    runCatching {
        val analysisLimit = AiMoveSelectionPolicy.analysisLimitFor(
            playLevel = playLevel,
            searchTimeSettings = searchTimeSettings,
            searchMode = searchMode,
        )
        val analysis = if (analysisProvider != null) {
            analysisProvider(analysisLimit)
        } else {
            analyze(analysisLimit)
        }
        AiMoveSelectionPolicy.select(
            currentState = currentState,
            aiPlayer = aiPlayer,
            playLevel = playLevel,
            searchMode = searchMode,
            candidates = analysis.candidates,
            analysisSummary = analysis.summary,
        )
    }.getOrNull()
