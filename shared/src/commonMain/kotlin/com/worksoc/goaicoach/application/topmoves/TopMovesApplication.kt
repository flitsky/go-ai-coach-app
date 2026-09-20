package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.application.analysis.analysisKeyFor
import com.worksoc.goaicoach.application.analysis.deepTopMovesAnalysisLimitFor
import com.worksoc.goaicoach.application.analysis.topMoveCandidateCountFor
import com.worksoc.goaicoach.application.analysis.topMovesAnalysisLimitFor
import com.worksoc.goaicoach.application.autoai.shouldRequestTopMoveAnalysis
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot

fun GameSessionAnalysisState.applyTopMoveAnalysisLaunchPlan(
    launchPlan: TopMoveAnalysisLaunchPlan,
): TopMoveAnalysisLaunchStateUpdate? =
    when (launchPlan) {
        TopMoveAnalysisLaunchPlan.Skip -> null
        is TopMoveAnalysisLaunchPlan.RestoreCurrentSnapshot ->
            TopMoveAnalysisLaunchStateUpdate(
                analysisState = copy(candidateMoves = launchPlan.candidateMoves),
            )
        is TopMoveAnalysisLaunchPlan.UseCached ->
            TopMoveAnalysisLaunchStateUpdate(
                analysisState = applyTopMoveAnalysisUpdate(
                    update = launchPlan.update,
                    analysisKey = launchPlan.analysisKey,
                ),
                engineMessage = launchPlan.update.engineMessage,
            )
        is TopMoveAnalysisLaunchPlan.RunEngine ->
            TopMoveAnalysisLaunchStateUpdate(
                analysisState = copy(lastAnalysisKey = launchPlan.plan.analysisKey),
                effect = GameSessionEffect.RunTopMoveAnalysis(
                    plan = launchPlan.plan,
                    deep = launchPlan.deep,
                    automatic = launchPlan.automatic,
                ),
            )
    }

fun buildTopMoveAnalysisPlan(
    targetState: GameState,
    engineProfile: EngineProfile,
    analysisPreset: AnalysisPreset,
    deep: Boolean,
): TopMoveAnalysisPlan {
    val candidateCount = topMoveCandidateCountFor(targetState, analysisPreset)
    val analysisLimit = if (deep) {
        deepTopMovesAnalysisLimitFor(engineProfile, candidateCount)
    } else {
        topMovesAnalysisLimitFor(engineProfile, analysisPreset, candidateCount)
    }
    return TopMoveAnalysisPlan(
        candidateCount = candidateCount,
        analysisLimit = analysisLimit,
        analysisKey = analysisKeyFor(
            state = targetState,
            preset = analysisPreset,
            limit = analysisLimit,
            deep = deep,
            searchMode = TopMovesSearchMode,
        ),
        searchMode = TopMovesSearchMode,
    )
}

fun buildTopMoveAnalysisLaunchPlan(
    request: TopMoveAnalysisLaunchRequest,
    cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
): TopMoveAnalysisLaunchPlan =
    buildTopMoveAnalysisLaunchPlan(
        targetState = request.targetState,
        engineProfile = request.engineProfile,
        analysisPreset = request.analysisPreset,
        deep = request.deep,
        automatic = request.automatic,
        topMovesEnabled = request.topMovesEnabled,
        currentCandidateMoves = request.currentCandidateMoves,
        reviewAnalysis = request.reviewAnalysis,
        lastAnalysisKey = request.lastAnalysisKey,
        cachedResultFor = cachedResultFor,
    )

fun buildTopMoveAnalysisLaunchPlan(
    targetState: GameState,
    engineProfile: EngineProfile,
    analysisPreset: AnalysisPreset,
    deep: Boolean,
    automatic: Boolean,
    topMovesEnabled: Boolean,
    currentCandidateMoves: List<CandidateMove>,
    reviewAnalysis: MoveAnalysisSnapshot,
    lastAnalysisKey: AnalysisCacheKey?,
    cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
): TopMoveAnalysisLaunchPlan {
    val plan = buildTopMoveAnalysisPlan(
        targetState = targetState,
        engineProfile = engineProfile,
        analysisPreset = analysisPreset,
        deep = deep,
    )

    if (automatic && plan.analysisKey == lastAnalysisKey) {
        return if (topMovesEnabled && currentCandidateMoves.isEmpty() && reviewAnalysis.scoredPlayCount > 0) {
            TopMoveAnalysisLaunchPlan.RestoreCurrentSnapshot(reviewAnalysis.candidatesForDisplay())
        } else {
            TopMoveAnalysisLaunchPlan.Skip
        }
    }

    val cached = cachedResultFor(plan.analysisKey)
    if (cached != null) {
        return TopMoveAnalysisLaunchPlan.UseCached(
            analysisKey = plan.analysisKey,
            update = buildCachedTopMoveAnalysisUpdate(
                targetState = targetState,
                cacheKey = plan.analysisKey,
                cached = cached,
                topMovesEnabled = topMovesEnabled,
            ),
        )
    }

    return TopMoveAnalysisLaunchPlan.RunEngine(
        plan = plan,
        deep = deep,
        automatic = automatic,
    )
}

fun GameSessionControllerState.toTopMoveAnalysisLaunchPlan(
    targetState: GameState,
    deep: Boolean,
    automatic: Boolean,
    cachedResultFor: (AnalysisCacheKey) -> CachedAnalysisResult?,
): TopMoveAnalysisLaunchPlan =
    buildTopMoveAnalysisLaunchPlan(
        request = TopMoveAnalysisLaunchRequest(
            targetState = targetState,
            engineProfile = core.runtimeState.engineProfile,
            analysisPreset = core.runtimeState.analysisPreset,
            deep = deep,
            automatic = automatic,
            topMovesEnabled = settings.topMovesEnabled,
            currentCandidateMoves = core.analysisState.candidateMoves,
            reviewAnalysis = core.analysisState.reviewAnalysis,
            lastAnalysisKey = core.analysisState.lastAnalysisKey,
        ),
        cachedResultFor = cachedResultFor,
    )

fun runTopMoveAnalysisApplication(request: TopMoveAnalysisRunRequest) {
    if (request.automatic && request.pendingPostUndoEngineSync) {
        return
    }
    if (
        !shouldRequestTopMoveAnalysis(
            isGameEnded = request.isGameEnded,
            isEngineReady = request.isEngineReady,
            isEngineBusy = request.isEngineBusy,
            shouldShowResumePrompt = request.shouldShowResumePrompt,
            playerSetup = request.playerSetup,
            targetState = request.targetState,
            // ⚠️ **2026-09-20 개정(백로그 #151, 사용자) — U-35의 강제 상시 탐색을 걷어냈다.**
            // 예전에는 여기서 `topMovesEnabled`가 거짓이어도 리플레이용으로 무조건 탐색을
            // 걸었다("추천 수 보기"를 꺼도 매 턴 돌았다) — 대국 진행 자체를 방해한다는
            // 제보로 걷어냈다. 리플레이의 큰 실수 표시는 이제 이 탐색 없이
            // `deriveMoveReviewMarkersFromScoreSwing`(이미 공짜로 기록되는 형세 스냅샷의
            // 앞뒤 차이)로 대국이 끝나는 시점에 만든다 — `GameHistoryAppendApplication.kt` 참고.
            // ⚠️ **`showMoveReviewEnabled`는 남긴다** — "착수 평가"(대국 중 마지막 수 색 링,
            // 구독자 전용)가 이 탐색 결과(`reviewAnalysis`)를 그대로 쓰기 때문에, 그 토글을
            // 켠 사용자에게는 여전히 매 턴 돌아야 한다. 둘 다 **사용자가 명시적으로 켠 경우만**
            // 돈다는 게 이번 개정의 원칙 — "언젠가 필요할지 모른다"는 이유만으로는 대국 진행을
            // 방해하지 않는다.
            // ⚠️ 이 토글은 **표시**를 끄는 것이지 분석을 끄는 것이 아니다 — 실제로 아래 실행부
            // (`TopMoveAnalysisEngine`)는 `topMovesEnabled`로 **후보 표시만** 가른다.
            topMovesEnabled = request.controllerState.settings.topMovesEnabled ||
                request.showMoveReviewEnabled,
        )
    ) {
        return
    }

    val launchPlan = request.controllerState.toTopMoveAnalysisLaunchPlan(
        targetState = request.targetState,
        deep = request.deep,
        automatic = request.automatic,
        cachedResultFor = request.cachedResultFor,
    )
    val launchUpdate = request.controllerState.core.analysisState
        .applyTopMoveAnalysisLaunchPlan(launchPlan)
        ?: return
    request.applyLaunchUpdate(launchUpdate)
    val effect = launchUpdate.effect ?: return
    val operationToken = topMoveAnalysisOperationToken(
        targetState = request.targetState,
        plan = effect.plan,
        sessionGeneration = request.currentSessionGeneration(),
    )

    request.launchEngineOperation(operationToken.operation) {
        val applyPlan = request.runEngineWork {
            request.engineClient.runTopMoveAnalysisEffectApplyPlan(
                request = TopMoveAnalysisEffectLaunchRequest(
                    effect = effect,
                    context = TopMoveAnalysisExecutionContext(
                        targetState = request.targetState,
                        engineProfile = request.controllerState.core.runtimeState.engineProfile,
                        analysisPreset = request.controllerState.core.runtimeState.analysisPreset,
                        topMovesEnabled = request.controllerState.settings.topMovesEnabled,
                        cacheEnabled = request.analysisCacheEnabled,
                    ),
                    token = operationToken,
                    currentState = request.currentState(),
                    currentAnalysisKey = request.currentAnalysisKey(),
                    currentSessionGeneration = request.currentSessionGeneration(),
                    targetState = request.targetState,
                    topMovesEnabled = request.controllerState.settings.topMovesEnabled,
                ),
            )
        }
        applyTopMoveAnalysisCompletionApplication(
            TopMoveAnalysisCompletionApplyRunRequest(
                applyPlan = applyPlan,
                applyTopMoveAnalysisUpdate = request.applyTopMoveAnalysisUpdate,
                putUndoRestoreCache = request.putUndoRestoreCache,
                putAnalysisCache = request.putAnalysisCache,
                applyFailureDisplay = request.applyFailureDisplay,
                appendEngineOperationDiscardLog = request.appendEngineOperationDiscardLog,
            ),
        )
    }
}
