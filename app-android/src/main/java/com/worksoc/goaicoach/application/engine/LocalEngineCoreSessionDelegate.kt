package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.endgame.resolveAiEndgame
import com.worksoc.goaicoach.match.MatchReferee
import com.worksoc.goaicoach.match.applyAiTurn
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog

internal class LocalEngineCoreSessionDelegate(
    private val coreApi: EngineCoreApi,
    private val clock: EngineClock = SystemEngineClock,
) {
    private val benchmarkDelegate = LocalEngineBenchmarkDelegate(coreApi)

    suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult {
        val init = coreApi.initialize(profile)
        return EngineStartupResult(
            // 앱 시작은 엔진 준비만 수행한다. 실제 대국 보드 초기화는 사용자가
            // "새 게임"을 누른 뒤 startNewGame에서 실행한다.
            message = "Engine initialized. Select settings, then start a new game.\n${init.message}",
            scoreSnapshot = null,
        )
    }

    suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int = 0,
    ): EngineStartupResult {
        // A fresh process is intentional here. KataGo's GTP search tree can
        // survive clear_board across repeated games, causing the next game to
        // replay nearly instantly from retained search data.
        coreApi.stop()
        coreApi.initialize(profile)
        val status = coreApi.newGame(boardSize, ruleset, handicapCount)
        val estimate = runCatching {
            coreApi.estimateScore(scoreGraphAnalysisLimit(profile))
        }.getOrNull()
        return EngineStartupResult(
            message = status.message,
            scoreSnapshot = estimate?.let { ScoreTimeline.fromEstimate(0, it) },
        )
    }

    suspend fun syncAndAnalyzePosition(
        state: GameState,
        limit: AnalysisLimit,
    ): AnalysisResult {
        coreApi.syncToGameState(state)
        return coreApi.analyze(limit)
    }

    suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate {
        coreApi.syncToGameState(state)
        return coreApi.estimateScore(scoreGraphAnalysisLimit(profile))
    }

    suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate {
        coreApi.configure(profile)
        return syncAndEstimateGraphScore(state, profile)
    }

    suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
        analysisProvider: suspend (AnalysisLimit) -> AnalysisResult,
    ): AutoAiTurnResult {
        val aiPlayer = currentState.nextPlayer
        val turnProfile = playLevel.toEngineProfile(currentProfile, searchTimeSettings)
        coreApi.configure(turnProfile)
        coreApi.syncToGameState(currentState)
        val aiMoveGateway = LocalAiMoveEngineGateway(coreApi)
        val outcome = applyAiTurn(
            engineAdapter = aiMoveGateway,
            currentState = currentState,
            aiPlayer = aiPlayer,
            playLevel = playLevel,
            searchTimeSettings = searchTimeSettings,
            searchMode = searchMode,
            isolateSearchCache = isolateSearchCache,
            analysisProvider = analysisProvider,
        )
        val estimate = runCatching {
            coreApi.estimateScore(scoreGraphAnalysisLimit(turnProfile))
        }.getOrNull()
        return AutoAiTurnResult(
            turnOutcome = outcome,
            scoreEstimate = estimate,
            profile = turnProfile,
            playLevel = playLevel,
        )
    }

    suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
        diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    ): LocalEngineMoveResult {
        val syncReplayStartMillis = clock.currentTimeMillis()
        coreApi.syncToGameState(afterMove)
        val syncReplayMs = clock.currentTimeMillis() - syncReplayStartMillis
        return if (MatchReferee.shouldResolveEndgame(afterMove)) {
            val deadStonesProfile = profile.withAssistantJudgeDeadStonesTimeCap()
            val finalScoreProfile = profile.withAssistantJudgeFinalScoreTimeCap()
            LocalEngineMoveResult(
                endgame = resolveAiEndgame(
                    judgeGateway = LocalEndgameJudgeGateway(coreApi),
                    originalState = afterMove,
                    estimateLimit = scoreGraphAnalysisLimit(deadStonesProfile),
                    prePassCandidates = if (move is Move.Pass) {
                        previousReviewCandidates
                    } else {
                        emptyList()
                    },
                    syncReplayMs = syncReplayMs,
                    assistantJudgeDeadStonesProfile = deadStonesProfile,
                    assistantJudgeFinalScoreProfile = finalScoreProfile,
                    diagnosticEventLog = diagnosticEventLog,
                ),
            )
        } else {
            LocalEngineMoveResult(
                estimate = coreApi.estimateScore(scoreGraphAnalysisLimit(profile)),
            )
        }
    }

    suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate {
        if (syncFirst) {
            coreApi.syncToGameState(state)
        }
        return coreApi.estimateScore(profile.analysisLimit)
    }

    suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
        diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    ): AiEndgameResolution {
        val deadStonesProfile = profile.withAssistantJudgeDeadStonesTimeCap()
        val finalScoreProfile = profile.withAssistantJudgeFinalScoreTimeCap()
        return resolveAiEndgame(
            judgeGateway = LocalEndgameJudgeGateway(coreApi),
            originalState = state,
            estimateLimit = scoreGraphAnalysisLimit(deadStonesProfile),
            prePassCandidates = prePassCandidates,
            assistantJudgeDeadStonesProfile = deadStonesProfile,
            assistantJudgeFinalScoreProfile = finalScoreProfile,
            diagnosticEventLog = diagnosticEventLog,
        )
    }

    suspend fun undoMove(): EngineStatus =
        coreApi.undoMove()

    suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        benchmarkDelegate.runStartupBenchmark(
            restoreState = restoreState,
            nowMillis = nowMillis,
            onProgress = onProgress,
        )
}
