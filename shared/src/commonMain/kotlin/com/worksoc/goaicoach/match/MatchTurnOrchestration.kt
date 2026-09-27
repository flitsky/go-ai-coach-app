package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.describe
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.policy.aiMoveSearchMode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

data class TurnOutcome(
    val gameState: GameState,
    val engineMessage: String,
    val candidateText: String,
    val lastMoveText: String,
)

/**
 * AI 착수 탐색이 **시간 초과**로 끝났다(refactor backlog #74). 호출자(AI 차례의 코루틴)는 살아 있고, 안쪽
 * `withTimeout`만 끊겼다 — 사용자의 취소와 다르다.
 *
 * ⚠️ **`CancellationException`의 하위 타입으로 만들지 말 것.** 그러면 launch가 이것을 정상 취소로 보고
 * 조용히 끝나, 위에서 선택 팝업(「한 번 더 기다리기」/「엔진 다시 시작하기」)을 띄울 기회가 없다.
 * ⚠️ 이것을 `genMove`로 덮지 않는다 — 예산을 다 써서 끊긴 요청을 같은 예산으로 또 태우는 것이 이 일감의 본체다.
 */
class AiMoveSearchTimedOut(
    cause: Throwable,
) : Exception("AI move search timed out: ${cause.message}", cause)

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
    prepareFallback: suspend () -> Unit = {},
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
    val selection = engineAdapter.selectAiMoveFromAnalysis(
        currentState = stateAfterHuman,
        aiPlayer = AiPlayer,
        playLevel = playLevel,
        searchTimeSettings = searchTimeSettings,
        searchMode = playLevel.aiMoveSearchMode(),
    )
    val selectedAiMove = selection.selectedMoveOrPrepareFallback(prepareFallback)
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
        engineMessage = "${humanStatus.message}\n${selection.fallbackReasonLine()}${aiResult.status.message}\n${aiResult.summary}",
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
    /**
     * 분석이 **진짜로 실패**해 `genMove`로 떨어지기 직전에 부른다(refactor backlog #74, 설계 F2). 실패한 GTP
     * 요청은 프로세스를 내린 뒤라, 맞추지 않은 `genMove`는 판 크기·덤·수순을 모르는 새 프로세스가 빈 판의
     * 수를 낸다. 호출부(`LocalEngineCoreSessionDelegate`)가 `configure` + `syncToGameState`를 넘긴다.
     * "후보가 없다"·"고른 수가 불법"일 때는 부르지 않는다 — 분석은 성공했고 엔진 판도 맞다.
     */
    prepareFallback: suspend () -> Unit = {},
): TurnOutcome {
    val resolvedSearchMode = searchMode ?: playLevel.aiMoveSearchMode()
    if (isolateSearchCache && resolvedSearchMode == EngineSearchMode.GtpStatefulFast) {
        // AI-vs-AI currently shares one KataGo process. Without this isolation,
        // a lower-budget side can inherit the previous higher-budget side's
        // subtree and hide the intended B16/B32/B64 strength gap.
        engineAdapter.clearSearchCache()
    }
    val selection = engineAdapter.selectAiMoveFromAnalysis(
        currentState = currentState,
        aiPlayer = aiPlayer,
        playLevel = playLevel,
        searchTimeSettings = searchTimeSettings,
        searchMode = resolvedSearchMode,
        analysisProvider = analysisProvider,
    )
    val selectedAiMove = selection.selectedMoveOrPrepareFallback(prepareFallback)
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
        engineMessage = "${selection.fallbackReasonLine()}${aiResult.status.message}\n${aiResult.summary}",
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

/**
 * 분석으로 수를 고른 결과(refactor backlog #74). 예전에는 `runCatching { … }.getOrNull()` 하나가 아래 넷을
 * 전부 `null`(→ `genMove`)로 뭉갰다 — 사용자의 취소도, 시간 초과도, 진짜 실패도.
 */
private sealed class AiMoveSelectionOutcome {
    data class Selected(val move: SelectedAiMove) : AiMoveSelectionOutcome()

    /** 분석은 성공했지만 쓸 후보가 없다 — `genMove` 폴백의 **원래** 목적이다. */
    data object NoUsableCandidate : AiMoveSelectionOutcome()

    /** 안쪽 `withTimeout`이 끊겼다(호출자는 살아 있다) — 위로 [AiMoveSearchTimedOut]을 던진다. */
    data class TimedOut(val cause: CancellationException) : AiMoveSelectionOutcome()

    /** 진짜 실패(분석 프로세스가 죽었다 등) — 엔진을 다시 맞춘 뒤 `genMove`로 떨어진다. */
    data class Failed(val cause: Throwable) : AiMoveSelectionOutcome()
}

/**
 * 고른 수를 돌려준다. `null`이면 `genMove`로 떨어진다는 뜻이고, [AiMoveSelectionOutcome.Failed]면 그 전에
 * [prepareFallback]으로 엔진을 다시 맞춘다. [AiMoveSelectionOutcome.TimedOut]은 [AiMoveSearchTimedOut]으로 올라간다.
 */
private suspend fun AiMoveSelectionOutcome.selectedMoveOrPrepareFallback(
    prepareFallback: suspend () -> Unit,
): SelectedAiMove? =
    when (this) {
        is AiMoveSelectionOutcome.Selected -> move
        AiMoveSelectionOutcome.NoUsableCandidate -> null
        is AiMoveSelectionOutcome.TimedOut -> throw AiMoveSearchTimedOut(cause)
        is AiMoveSelectionOutcome.Failed -> {
            prepareFallback()
            null
        }
    }

/** `genMove` 폴백이 왜 났는지 엔진 메시지 앞에 붙일 한 줄 — 예전에는 진짜 실패의 폴백이 **완전 무음**이었다. */
private fun AiMoveSelectionOutcome.fallbackReasonLine(): String =
    when (this) {
        is AiMoveSelectionOutcome.Failed ->
            "AI move analysis failed (${cause::class.simpleName}: ${cause.message}); engine re-synced, fell back to KataGo genmove.\n"

        else -> ""
    }

private suspend fun AiMoveEngineGateway.selectAiMoveFromAnalysis(
    currentState: GameState,
    aiPlayer: StoneColor,
    playLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings,
    searchMode: EngineSearchMode,
    analysisProvider: (suspend (AnalysisLimit) -> AnalysisResult)? = null,
): AiMoveSelectionOutcome =
    try {
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
        )?.let(AiMoveSelectionOutcome::Selected) ?: AiMoveSelectionOutcome.NoUsableCandidate
    } catch (cancellation: CancellationException) {
        // 호출자가 취소됐으면(무르기·나가기·새 대국·엔진 다시 시작하기) 그대로 올린다. 살아 있는데 취소가
        // 왔다면 그것은 안쪽 `withTimeout`의 시간 초과다(TimeoutCancellationException).
        if (!currentCoroutineContext().isActive) throw cancellation
        AiMoveSelectionOutcome.TimedOut(cancellation)
    } catch (failure: Throwable) {
        // 취소된 뒤 막혔던 읽기가 일반 예외로 풀리는 경우(forceReset으로 파이프가 닫히면 ISE) — 그 예외는
        // 결과가 아니다. RemoteEngineCoreApiAdapter의 같은 자리와 같은 모양이다.
        currentCoroutineContext().ensureActive()
        AiMoveSelectionOutcome.Failed(failure)
    }
