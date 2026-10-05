package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.endgame.resolveAiEndgame
import com.worksoc.goaicoach.match.MatchReferee
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.match.applyAiTurn
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.describe
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.HumanNetworkJudgeProfile
import com.worksoc.goaicoach.shared.enginecontract.HumanPolicy
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.playstyle.HumanMoveSampler
import com.worksoc.goaicoach.shared.playstyle.HumanPlayStyle
import com.worksoc.goaicoach.shared.playstyle.humanPlayStyle
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.scoring.ScoreTimeline
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal class LocalEngineCoreSessionDelegate(
    private val coreApi: EngineCoreApi,
    private val clock: EngineClock = SystemEngineClock,
    /** 사람 정책에서 수를 뽑는 주사위 — 테스트가 고정한다. */
    private val random: Random = Random.Default,
) {
    private val benchmarkDelegate = LocalEngineBenchmarkDelegate(coreApi)

    /**
     * 사람 모델로 두려다 **진짜로 실패**한 적이 있는가(백로그 #215) — 그 뒤로는 이 엔진에서 사람 모델을 다시 시도하지 않고 지금 방식으로 둔다.
     * 수마다 다시 시도하면 수마다 프로세스를 두 번 갈아 올린다(사람 모델로 → 실패 → 주 모델로). 앱을 다시 켜면 처음부터 다시 본다.
     */
    private var humanStyleUnavailable = false

    suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult {
        // 띄우기 **전에** 판 크기를 알린다(백로그 #215) — 모르고 띄우면 19줄로 떠서 첫 `boardsize`에 약 1.2초를 더 쓴다.
        coreApi.expectBoardSize(state.boardSize)
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
        komi: Double = com.worksoc.goaicoach.shared.domain.DefaultKomi,
    ): EngineStartupResult {
        // A fresh process is intentional here. KataGo's GTP search tree can
        // survive clear_board across repeated games, causing the next game to
        // replay nearly instantly from retained search data.
        coreApi.stop()
        // 새 대국마다 프로세스를 새로 띄우므로, 이 판의 크기로 띄운다(백로그 #215 — S23에서 13줄 새 대국의 준비가 약 1.2초 준다).
        coreApi.expectBoardSize(boardSize)
        coreApi.initialize(profile)
        val status = coreApi.newGame(boardSize, ruleset, handicapCount, komi)
        val estimate = runCatching {
            coreApi.estimateScore(scoreGraphAnalysisLimit(profile))
        }.getOrNull()
        return EngineStartupResult(
            message = status.message,
            scoreSnapshot = estimate?.let { ScoreTimeline.fromEstimate(0, it) },
        )
    }

    /**
     * 사용자가 **물어본** 분석은 주 모델이 답한다(백로그 #215 보강 ②) — 급수 캐릭터와 두는 동안 올라가 있는 사람 모델은
     * 그 급수처럼 두려고 올린 것이지 판을 읽으려고 올린 것이 아니다(형세 오차 0.5 → 2.5집, 실험실 E6).
     * 다음 AI 차례가 제게 필요한 망을 다시 올린다([runAutoAiTurn]).
     *
     * @return 갈아 올렸는가. 그랬다면 **새 프로세스의 판은 비어 있다** — 부른 쪽이 판부터 맞춘다.
     */
    suspend fun bringMainNetwork(): Boolean =
        coreApi.supportsHumanNetwork && coreApi.useNetwork(EngineNetwork.Main)

    /** 올릴 망을 고를 수 있는 엔진인가 — 아니면 [bringMainNetwork]는 늘 아무 일도 하지 않는다. */
    val canSwapNetworks: Boolean
        get() = coreApi.supportsHumanNetwork

    /** 추천 수 분석 — **늘 주 모델로** 한다([bringMainNetwork]). 판은 어차피 여기서 맞추므로 갈아 올린 뒤의 빈 판도 덮인다. */
    suspend fun syncAndAnalyzePosition(
        state: GameState,
        limit: AnalysisLimit,
    ): AnalysisResult {
        bringMainNetwork()
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
        // 급수 캐릭터는 사람 모델로 둔다(백로그 #215) — 그 모델이 기기에 있고, 앞서 실패한 적이 없을 때.
        val humanStyle = playLevel.humanPlayStyle()?.takeIf { coreApi.supportsHumanNetwork && !humanStyleUnavailable }
        if (humanStyle != null) {
            try {
                return runHumanStyleTurn(currentState, aiPlayer, playLevel, turnProfile, humanStyle)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                // 사람 모델을 못 쓴다(파일이 깨졌거나 프로세스가 죽었다) — 이 차례부터 지금 방식으로 둔다. 판은 아래에서 다시 맞춘다.
                currentCoroutineContext().ensureActive()
                humanStyleUnavailable = true
            }
        }
        // 그 밖의 AI 차례는 주 모델로 둔다 — 급수 캐릭터와 두던 엔진이면 여기서 갈아 올린다. 판은 바로 아래에서 맞춘다.
        if (coreApi.supportsHumanNetwork) coreApi.useNetwork(EngineNetwork.Main)
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
            // 진짜 실패로 genMove에 떨어지기 전에 엔진을 다시 맞춘다(refactor backlog #74, 설계 F2) — 실패한
            // GTP 요청은 프로세스를 내린 뒤라, 그대로 genMove하면 새 프로세스가 빈 판의 수를 낸다.
            prepareFallback = {
                coreApi.configure(turnProfile)
                coreApi.syncToGameState(currentState)
            },
        )
        // 형세 추정이 실패·시간 초과해도 AI의 수는 막지 않는다(삼킨다). 단 **차례 자체가 취소됐으면** 올린다
        // — 무르기·나가기로 취소된 차례를 여기서 삼키면 끝난 결과처럼 위로 올라간다(refactor backlog #74).
        val estimate = try {
            coreApi.estimateScore(scoreGraphAnalysisLimit(turnProfile))
        } catch (cancellation: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancellation
            null
        } catch (failure: Throwable) {
            currentCoroutineContext().ensureActive()
            null
        }
        return AutoAiTurnResult(
            turnOutcome = outcome,
            scoreEstimate = estimate,
            profile = turnProfile,
            playLevel = playLevel,
        )
    }

    /**
     * 급수 캐릭터의 한 수 — **사람 모델만 올린 채** 그 급수의 사람이 둘 법한 수를 뽑는다(백로그 #215).
     *
     * 탐색이 없다. 신경망 평가 1회로 정책을 받고([EngineCoreApi.humanPolicy]) 꼬리 누르기로 뽑는다([HumanMoveSampler]) —
     * S23에서 13줄 0.3초(지금 방식 16방문 4.3초). 통과는 그 급수가 아니라 가장 센 프로필이 정한다(약한 프로필은 너무 일찍 통과한다).
     * 둔 뒤의 형세도 사람 모델이 가장 센 프로필로 본 **임시 값**이다(`ScoreEstimate.network`).
     *
     * 갈아 올린 직후의 프로세스는 판이 비어 있다 — 그래서 늘 판부터 맞춘다(주 모델 쪽 AI 차례와 같은 순서).
     */
    private suspend fun runHumanStyleTurn(
        currentState: GameState,
        aiPlayer: StoneColor,
        playLevel: PlayLevelSetting,
        turnProfile: EngineProfile,
        style: HumanPlayStyle,
    ): AutoAiTurnResult {
        coreApi.useNetwork(EngineNetwork.Human)
        coreApi.configure(turnProfile)
        coreApi.syncToGameState(currentState)
        val move = chooseHumanStyleMove(currentState, aiPlayer, coreApi.humanPolicy(style.profile))
        val afterAi = MatchReferee.playOrThrow(currentState, move)
        val status = coreApi.playMove(move)
        val moveText = move.describe(currentState.boardSize)
        // 형세 추정이 실패해도 수는 막지 않는다 — 주 모델 쪽 AI 차례와 같은 규칙(취소만 올린다).
        val estimate = try {
            coreApi.estimateScore(scoreGraphAnalysisLimit(turnProfile))
        } catch (cancellation: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancellation
            null
        } catch (failure: Throwable) {
            currentCoroutineContext().ensureActive()
            null
        }
        return AutoAiTurnResult(
            turnOutcome = TurnOutcome(
                gameState = afterAi,
                engineMessage = "${status.message}\nAI selected $moveText as ${style.profile}.",
                candidateText = "Human-style move ($moveText) sampled from the ${style.profile} policy.",
                lastMoveText = moveText,
            ),
            scoreEstimate = estimate,
            profile = turnProfile,
            playLevel = playLevel,
        )
    }

    /**
     * 사람 정책에서 둘 수 하나를 고른다. 뽑힌 자리가 이 판에서 둘 수 없는 자리면(패 — KataGo의 정책은 대개 걸러 주지만 앱의 규칙이
     * 마지막 판정이다) 그 자리를 빼고 다시 뽑는다. 끝내 둘 자리가 없으면 통과한다.
     */
    private suspend fun chooseHumanStyleMove(
        state: GameState,
        aiPlayer: StoneColor,
        own: HumanPolicy,
    ): Move {
        if (HumanMoveSampler.shouldAskJudgeAboutPass(own) &&
            HumanMoveSampler.shouldPass(coreApi.humanPolicy(HumanNetworkJudgeProfile))
        ) {
            return Move.Pass(aiPlayer)
        }
        val rejected = mutableSetOf<BoardCoordinate>()
        repeat(MaxHumanStyleDraws) {
            val coordinate = HumanMoveSampler.sample(own, state.moves.size, state.boardSize, random, rejected)
                ?: return Move.Pass(aiPlayer)
            val move = Move.Play(aiPlayer, coordinate)
            if (MatchReferee.play(state, move).isSuccess) return move
            rejected += coordinate
        }
        return Move.Pass(aiPlayer)
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

/** 사람 정책에서 뽑은 자리가 둘 수 없는 자리일 때 다시 뽑는 횟수 — 넘으면 통과한다. */
private const val MaxHumanStyleDraws = 8
