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
import com.worksoc.goaicoach.shared.playstyle.LosingStreak
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
    /** 신경망을 갈아 올릴 때 판의 행방을 적는 쪽 — 세션 클라이언트다([NetworkSwapBoardTracker]). */
    private val swapBoardTracker: NetworkSwapBoardTracker = NetworkSwapBoardTracker.None,
) {
    private val benchmarkDelegate = LocalEngineBenchmarkDelegate(coreApi)

    /**
     * 사람 모델로 두려다 **진짜로 실패**한 적이 있는가(백로그 #215) — 그 뒤로는 이 엔진에서 사람 모델을 다시 시도하지 않고 지금 방식으로 둔다.
     * 수마다 다시 시도하면 수마다 프로세스를 두 번 갈아 올린다(사람 모델로 → 실패 → 주 모델로). 앱을 다시 켜면 처음부터 다시 본다.
     */
    private var humanStyleUnavailable = false

    /**
     * 진영마다 **진 판이 이어진 차례 수**(백로그 #213, [LosingStreak]) — 종국의 통과와 중반의 기권 제안이 이것을 본다.
     * 진영마다 따로 센다: AI끼리 두는 판에서 한 진영의 기록이 다른 진영의 차례에 읽히면 이기는 쪽이 통과한다.
     */
    private val losingStreaks = mutableMapOf<StoneColor, LosingStreak>()

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
        coreApi.supportsHumanNetwork && swapTo(EngineNetwork.Main)

    /**
     * [network]를 올린다. 갈아 올렸으면 참 — **새 프로세스의 판은 비어 있다.**
     *
     * ⚠️ 갈아 올리기는 이 함수로만 한다. **부르기 전에** 판을 모른다고 알려야 하기 때문이다([NetworkSwapBoardTracker]) —
     * 갈아 올리다 끊기면(취소·마감) 옛 프로세스는 이미 내려갔고, 판을 다시 맞출 코드는 돌지 않는다.
     */
    private suspend fun swapTo(network: EngineNetwork): Boolean {
        swapBoardTracker.swapStarting()
        val swapped = coreApi.useNetwork(network)
        if (!swapped) swapBoardTracker.swapNotNeeded()
        return swapped
    }

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
        // 엔진에는 **이 단계의 방문 수**로 건다(초고수는 더 깊이 읽는다 — `PlayLevelGroup.aiMoveVisits`). 세션에 돌려주는 프로필은
        // [turnProfile] 그대로다: 돌려준 것이 공용 프로필이 되어 추천 수·형세가 쓰므로, 상대의 방문 수가 거기 새면 안 된다.
        val aiSearchProfile = playLevel.toAiTurnEngineProfile(currentProfile, searchTimeSettings)
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
        bringMainNetwork()
        coreApi.configure(aiSearchProfile)
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
                coreApi.configure(aiSearchProfile)
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
        swapTo(EngineNetwork.Human)
        coreApi.configure(turnProfile)
        coreApi.syncToGameState(currentState)
        // **통과는 종국에서만** 한다(사용자 2026-10-07) — 진 판이 이어졌고 판의 80%를 뒀으면 상대 집 안에 뜻 없는 수를 잇지 않고 통과한다.
        // 대국 중반에는 통과하지 않는다: 가망이 없으면 통과가 아니라 기권을 제안한다(아래 `offersResignation`).
        val streak = losingStreaks[aiPlayer]?.takeIf { it.continuesAt(currentState) } ?: LosingStreak()
        val move = if (streak.passesAt(currentState)) Move.Pass(aiPlayer) else chooseHumanStyleMove(currentState, aiPlayer, coreApi.humanPolicy(style.profile))
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
        val nextStreak = streak.after(aiPlayer, estimate, afterAi)
        losingStreaks[aiPlayer] = nextStreak
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
            // 가망이 없으면 **기권을 제안한다**(사용자 2026-10-07) — 다음 AI 차례에 세션이 사용자에게 묻는다. 한 판에 한 번만 묻는 것은 세션이 지킨다.
            offersResignation = nextStreak.offersResignation(afterAi),
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
        // **상대가 통과했으면 심판에게 묻는다**(사용자 2026-10-07) — 약한 프로필은 통과를 떠올리지 않아서, 사람이 「끝났다」고 통과해도
        // 묻지도 않고 계속 뒀다(폰: 사용자가 열다섯 번 통과하는 동안 AI는 매번 뒀고, 심판은 첫 통과 직후에 이미 통과를 권했다 — E10).
        // 심판이 아직 둘 곳이 있다고 보면 그대로 둔다 — 이른 통과를 봐주지 않는다.
        val opponentPassed = state.moves.lastOrNull() is Move.Pass
        if ((HumanMoveSampler.shouldAskJudgeAboutPass(own) || opponentPassed) &&
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
        val endsTheGame = MatchReferee.shouldResolveEndgame(afterMove)
        // **계가는 주 모델이 한다**(백로그 #215 보강 ①) — 급수 캐릭터와 두던 판은 사람 모델이 올라간 채로 끝난다.
        // 판을 맞추기 **전에** 올린다: 갈아 올린 프로세스의 판은 비어 있다.
        if (endsTheGame) bringMainNetwork()
        val syncReplayStartMillis = clock.currentTimeMillis()
        coreApi.syncToGameState(afterMove)
        val syncReplayMs = clock.currentTimeMillis() - syncReplayStartMillis
        return if (endsTheGame) {
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

    /** 지나간 국면의 형세를 주 모델로, 매 수 형세 기록과 같은 깊이로 — `EngineScoringClient.remeasureGraphScore`. */
    suspend fun remeasureGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate {
        bringMainNetwork()
        coreApi.syncToGameState(state)
        return coreApi.estimateScore(scoreGraphAnalysisLimit(profile))
    }

    suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
        diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    ): AiEndgameResolution {
        // **계가는 주 모델이 한다**(백로그 #215 보강 ①) — AI가 통과해 끝난 판은 사람 모델이 올라간 채로 온다.
        // 이 함수는 "판이 이미 이 국면"이라고 믿고 계가만 하므로, 갈아 올렸으면(새 프로세스의 판은 비어 있다) 판부터 맞춘다.
        if (bringMainNetwork()) coreApi.syncToGameState(state)
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

/**
 * 신경망을 갈아 올릴 때 **엔진 판의 행방**을 적는 쪽(백로그 #215) — `LocalEngineSessionClient`의 `boardLeftByAnalysis`다.
 *
 * 갈아 올리면 옛 프로세스가 내려가고 새 프로세스의 판은 비어 있다. 그 뒤에 판을 맞추는 코드가 끝까지 돌면 괜찮지만,
 * 그 사이에 끊기면 판은 어느 국면도 아니다 — 형세 추정의 "판이 이미 이 국면"이라는 믿음(`syncFirst = false`)이 빈 판을 읽는다.
 */
interface NetworkSwapBoardTracker {
    /** 갈아 올릴지도 모르는 호출 **직전**. 이 뒤로는 판을 모른다. */
    fun swapStarting()

    /** 갈아 올릴 일이 없었다(이미 그 망이다) — 판은 [swapStarting] 전 그대로다. */
    fun swapNotNeeded()

    data object None : NetworkSwapBoardTracker {
        override fun swapStarting() = Unit

        override fun swapNotNeeded() = Unit
    }
}
