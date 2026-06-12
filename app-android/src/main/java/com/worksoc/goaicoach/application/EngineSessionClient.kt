package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate

internal data class EngineSessionCapabilities(
    val supportsDeviceBenchmark: Boolean,
)

/**
 * Application-facing engine session boundary.
 *
 * UI code depends on this contract instead of the low-level [EngineCoreApi].
 * The current implementation delegates to a stateful local process adapter,
 * but a future remote-server engine can implement this interface without
 * exposing process sync, cache isolation, or transport details to Compose.
 */
internal interface EngineSessionClient {
    val capabilities: EngineSessionCapabilities

    suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult

    suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult

    suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
    ): AnalysisResult

    suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate

    suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate

    suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult

    suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult

    suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate

    suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution

    suspend fun undoMove(): EngineStatus

    suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile
}

internal class AdapterEngineSessionClient(
    private val coreApi: EngineCoreApi,
    override val capabilities: EngineSessionCapabilities = EngineSessionCapabilities(
        supportsDeviceBenchmark = false,
    ),
) : EngineSessionClient {
    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult =
        coreApi.startEngineSession(profile, state)

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult =
        coreApi.startNewEngineGame(profile, boardSize, ruleset)

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
    ): AnalysisResult {
        coreApi.syncToGameState(state)
        return coreApi.analyze(limit)
    }

    override suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        coreApi.syncAndEstimateGraphScore(state, profile)

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        coreApi.configureSyncAndEstimateGraphScore(state, profile)

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult =
        coreApi.runAutoAiTurn(
            currentState = currentState,
            playLevel = playLevel,
            currentProfile = currentProfile,
            searchTimeSettings = searchTimeSettings,
            isolateSearchCache = isolateSearchCache,
        )

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        coreApi.syncAfterHumanMove(
            afterMove = afterMove,
            profile = profile,
            move = move,
            previousReviewCandidates = previousReviewCandidates,
        )

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        coreApi.estimateScoreForState(
            state = state,
            profile = profile,
            syncFirst = syncFirst,
        )

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        coreApi.resolveEndgameForState(
            state = state,
            profile = profile,
            prePassCandidates = prePassCandidates,
        )

    override suspend fun undoMove(): EngineStatus =
        coreApi.undoMove()

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        coreApi.runStartupEngineBenchmark(
            restoreState = restoreState,
            nowMillis = nowMillis,
            onProgress = onProgress,
        )
}
