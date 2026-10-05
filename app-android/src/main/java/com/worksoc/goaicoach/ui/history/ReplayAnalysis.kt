package com.worksoc.goaicoach.ui.history

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableSpendDecision
import com.worksoc.goaicoach.application.engine.EngineAnalysisClient
import com.worksoc.goaicoach.application.engine.EngineOperationBusy
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.application.engine.operation.EngineActivityIndicator
import com.worksoc.goaicoach.application.premium.state.FeatureAccess
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.application.topmoves.buildTopMoveAnalysisPlan
import com.worksoc.goaicoach.application.topmoves.runTopMoveAnalysis
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.OwnershipEstimate
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.ToggleActionButton
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.gameReplayAnalysisBusyFor
import com.worksoc.goaicoach.ui.l10n.gameReplayAnalysisFailedFor
import com.worksoc.goaicoach.ui.l10n.gameReplayNoTopMovesFor
import com.worksoc.goaicoach.ui.monetization.LocalConsumableUiState
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import com.worksoc.goaicoach.ui.monetization.PremiumUpsellDialogHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 다시보기가 **지나간 국면**을 엔진에 묻는 창구(backlog #218). 대국 기록 화면이 셸에서 받은 엔진의 역할 둘을 묶어 내린다.
 *
 * ⚠️ **엔진은 하나다 — 여기서 물으면 대국 엔진의 판이 그 국면으로 바뀐다.** 돌려놓지 않는다: 돌아간 대국은 스스로 판을 다시
 * 맞추고(새 대국·이어하기·AI 차례·사람 착수), 맞추지 않는 하나(대국 화면의 형세 보기)는 엔진 클라이언트가 지킨다
 * (`LocalEngineSessionClient`의 `boardLeftByAnalysis`).
 * ⚠️ 둘 다 **기다리지 않고 포기한다**([EngineOperationBusy]) — 엔진이 기동·설치 중이거나 앞선 분석을 정리하는 중이면 그렇다.
 * 실패가 아니다. 받는 쪽이 잠깐 미뤘다가 다시 묻는다([rememberReplayAnalysis]).
 */
internal class ReplayAnalysisEngine(
    private val scoring: EngineScoringClient,
    private val analysis: EngineAnalysisClient,
) {
    /** 형세 — 대국 화면의 형세 보기와 같은 신경망 평가 1회. 영역([ScoreEstimate.ownership])이 함께 온다. */
    suspend fun estimate(position: GameState): ScoreEstimate =
        withContext(Dispatchers.IO) {
            scoring.estimateScoreForState(state = position, profile = ReplayEngineProfile, syncFirst = true)
        }

    /**
     * 추천 수 — **대국 화면과 같은 계획·같은 가공**을 탄다([buildTopMoveAnalysisPlan]·[runTopMoveAnalysis]). 방문 수·탐색 방식·
     * 판 위에 올릴 후보 고르기를 여기서 다시 정하면, 같은 버튼이 화면마다 다른 수를 추천한다.
     */
    suspend fun topMoves(position: GameState): List<CandidateMove> {
        val plan = buildTopMoveAnalysisPlan(
            targetState = position,
            engineProfile = ReplayEngineProfile,
            analysisPreset = ReplayAnalysisPreset,
            deep = false,
        )
        return withContext(Dispatchers.IO) {
            analysis.runTopMoveAnalysis(
                targetState = position,
                engineProfile = ReplayEngineProfile,
                analysisPreset = ReplayAnalysisPreset,
                plan = plan,
                deep = false,
                topMovesEnabled = true,
                // 대국 세션의 분석 캐시(무르기 복원용)는 여기 없다. 같은 국면을 다시 보는 것은 이 화면의 결과 맵이 맡는다.
                cacheEnabled = false,
            )
        }.candidateMoves
    }
}

/**
 * 다시보기는 대국 세션 밖이라 그 판의 엔진 프로필·설정을 모른다 — 새로 설치한 사용자의 기본값으로 묻는다
 * (최대 탐색 시간 5초, 기본 대국 단계의 분석 프리셋). 추천 수의 방문 수는 계획이 정한다.
 */
private val ReplayEngineProfile: EngineProfile =
    EngineProfile().let { profile -> profile.copy(analysisLimit = SearchTimeSettings().applyTo(profile.analysisLimit)) }
private val ReplayAnalysisPreset = PlayLevelSetting().analysisPreset

/**
 * 수순을 옮긴 뒤 엔진에 묻기까지 기다리는 시간. 켜 둔 채 ◀▶를 빠르게 누르면 지나가는 국면마다 묻지 않고 **멈춘 자리만** 묻는다 —
 * 한 번 묻는 값이 `newGame` + 전 수순 재생이라, 넘길 때마다 물으면 엔진이 지나간 국면을 쫓느라 멈춘 자리가 늦는다.
 */
private const val ReplayStepSettleMillis = 300L

/** 엔진이 바쁠 때 미뤘다가 다시 묻는 횟수와 간격. 수순을 넘기며 취소한 앞선 분석의 뒷정리는 대개 이 안에 끝난다. */
private const val ReplayBusyDeferrals = 6
private const val ReplayBusyDeferralMillis = 500L

/** 버튼 하나가 그릴 것. */
internal class ReplayAnalysisButton(
    val label: String,
    /** 잔량·무제한·시간제 표기(`UiStrings.featureButtonMark`). 라벨이 점수로 바뀌어 있으면 `null`. */
    val mark: String?,
    val isOn: Boolean,
    /** 이 수순에서 누르면 값이 든다(1회권 차감 또는 업셀) — 금색 잠금 테두리. */
    val premiumLocked: Boolean,
    val onClick: () -> Unit,
)

/** [rememberReplayAnalysis]가 다시보기 화면에 건네는 것 — 판에 올릴 결과와 버튼 둘. */
internal class ReplayAnalysisUi(
    /** 형세 보기가 켜져 있고 이 수순의 결과가 있으면 그 영역. 판이 그대로 그린다. */
    val ownership: OwnershipEstimate?,
    /** 추천 수가 켜져 있고 이 수순의 결과가 있으면 그 후보들. */
    val candidateMoves: List<CandidateMove>,
    /** 엔진에 묻는 중이면 판 위에 띄울 표시. */
    val activity: EngineActivityIndicator?,
    val eval: ReplayAnalysisButton,
    val topMoves: ReplayAnalysisButton,
    /** 열 길이 없어 업셀 팝업(광고 1시간·구독)을 띄울 차례인가. 팝업은 버튼 줄([ReplayAnalysisButtons])이 그린다. */
    val isUpsellVisible: Boolean,
    val onUpsellDismiss: () -> Unit,
)

/**
 * 다시보기의 「형세 보기」·「추천 수」(backlog #218) — 게이트, 엔진에 묻기, 결과 보관을 한곳에서 한다.
 *
 * ## 누가 무엇으로 여는가 (`FEATURE_ACCESS_PRINCIPLES.md` 8.1 ⓒ)
 * - **구독·광고 1시간**: 차감 없이. 켜 두면 수순을 옮겨도 **켜진 채 따라가며** 그 국면을 다시 묻는다(U-60).
 * - **1회권**: 한 장이 국면 하나(U-61). 결과가 나온 뒤에 차감하고 잔량을 토스트로 알린다(대국 화면과 같은 문구).
 * - **둘 다 없으면** 업셀 팝업(광고 1시간·구독) — 대국 화면이 띄우는 그 팝업이다.
 *
 * ## ⚠️ 판정과 원장은 이 파일에만 둔다
 * `GameReplayScreen.kt`는 프리미엄 판정을 직접 보지 않는다(`GameReplayContractTest` — 판 위 착수 평가 색을 뒷문으로 내주지
 * 않으려는 그물, 함정 14). 여기서 보는 것은 [FeatureId.Eval]·[FeatureId.TopMoves] 둘뿐이다.
 * 대국 화면의 1회권 원장은 쓰지 않는다 — 사유는 [ReplayFeatureLedger].
 *
 * ## 결과는 수순 번호로 든다
 * 한 번 받은 결과는 이 화면이 열려 있는 동안 남는다. 값을 치른 국면으로 돌아오면 엔진에 다시 묻지 않고 바로 보인다.
 *
 * @param replayKey 판이 바뀌면 원장·결과를 모두 버린다(기록 id).
 * @param positionAt 수순 번호 → 그 국면(`GameReplayTimeline::stateAt`).
 */
@Composable
internal fun rememberReplayAnalysis(
    engine: ReplayAnalysisEngine,
    replayKey: String,
    moveNumber: Int,
    positionAt: (Int) -> GameState,
): ReplayAnalysisUi {
    val strings = LocalUiStrings.current
    val premium = LocalPremiumUiState.current
    val context = LocalContext.current
    // 효과 안에서는 **지금의** 값으로 불러야 한다 — 효과가 떠 있는 동안 재구성이 지나가면 붙잡아 둔 것이 낡는다.
    val consumables by rememberUpdatedState(LocalConsumableUiState.current)
    val currentStrings by rememberUpdatedState(strings)
    val currentPositionAt by rememberUpdatedState(positionAt)

    var evalLedger by remember(replayKey) { mutableStateOf(ReplayFeatureLedger()) }
    var topMovesLedger by remember(replayKey) { mutableStateOf(ReplayFeatureLedger()) }
    var estimates by remember(replayKey) { mutableStateOf(emptyMap<Int, ScoreEstimate>()) }
    var candidates by remember(replayKey) { mutableStateOf(emptyMap<Int, List<CandidateMove>>()) }
    var inFlight by remember(replayKey) { mutableStateOf<FeatureId?>(null) }
    // 누를 때마다 올린다 — 같은 수순에서 다시 켠 것도 아래 효과를 다시 돌려야 한다.
    var requestEpoch by remember(replayKey) { mutableIntStateOf(0) }
    var showUpsell by remember { mutableStateOf(false) }
    // 방금 **눌러서** 물은 기능들. 보여 줄 것이 없는 결과는 누른 사람에게만 알린다 — 켜 둔 채 넘기는 동안에는 조용히 지나간다.
    val justTapped = remember(replayKey) { mutableSetOf<FeatureId>() }

    val evalAccess = premium.resolve(FeatureId.Eval)
    val topMovesAccess = premium.resolve(FeatureId.TopMoves)
    val evalUnlimited = evalAccess is FeatureAccess.Allowed
    val topMovesUnlimited = topMovesAccess is FeatureAccess.Allowed

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // 걸어 둔 1회권을 정산한다 — 결과가 손에 들어온 뒤에만 부른다(`ReplayFeatureLedger`의 "차감은 결과가 나온 뒤다").
    fun settleTicket(featureId: FeatureId, ledger: ReplayFeatureLedger, apply: (ReplayFeatureLedger) -> Unit) {
        if (ledger.awaitingTicketAt != moveNumber) return
        val ticket = ConsumableCatalog.forFeature(featureId)
        if (ticket == null) {
            apply(ledger.onTicketReleased())
            return
        }
        // 차감 여부는 6계층이 정한다 — 그 사이 프리미엄이 켜졌으면 재고를 건드리지 않고 통과시킨다(`decideConsumableSpend`).
        when (val decision = consumables.spend(ticket)) {
            is ConsumableSpendDecision.Spent -> {
                apply(ledger.onTicketCharged(moveNumber))
                // ⚠️ 잔량은 판정 결과의 `remaining`(차감 후)이다 — 재구성 전의 `countOf`는 한 장 많다.
                toast(currentStrings.consumableSpentToast(ticket, decision.remaining))
            }
            is ConsumableSpendDecision.AllowedWithoutSpending -> apply(ledger.onTicketReleased())
            ConsumableSpendDecision.OutOfStock -> {
                apply(ledger.onTicketReleased())
                showUpsell = true
            }
        }
    }

    // 걸어 둔 1회권을 **차감하지 않고** 거두며 사유를 알린다. 표를 걸었던 사람에게는 한 장도 안 나갔다는 문장으로 말한다.
    fun releaseAndTell(
        reason: (ticketKept: Boolean) -> String,
        ledger: ReplayFeatureLedger,
        apply: (ReplayFeatureLedger) -> Unit,
        tell: Boolean,
    ) {
        val ticketKept = ledger.awaitingTicketAt == moveNumber
        apply(ledger.onTicketReleased())
        if (tell) toast(reason(ticketKept))
    }

    // 답을 못 받았다. 켜 둔 것은 그대로 둔다 — 다음 수순에서 다시 묻는다.
    fun reportNoResult(featureId: FeatureId, error: Throwable, ledger: ReplayFeatureLedger, apply: (ReplayFeatureLedger) -> Unit) {
        justTapped.remove(featureId)
        val language = currentStrings.language
        val reason: (Boolean) -> String = { ticketKept ->
            if (error is EngineOperationBusy) gameReplayAnalysisBusyFor(language, ticketKept) else gameReplayAnalysisFailedFor(language, ticketKept)
        }
        releaseAndTell(reason, ledger, apply, tell = true)
    }

    // 이 수순의 결과가 손에 있다. **판에 보여 줄 것이 있을 때만** 걸어 둔 1회권을 정산한다.
    // ⚠️ 끝난 국면에서는 엔진의 추천이 통과뿐이라 판에 올릴 후보가 없다 — 다시보기는 바로 그 마지막 수에서 열리므로
    //    가장 먼저 눌리는 자리다. 거기서 차감하면 아무것도 못 보고 한 장이 나간다(2026-10-05 에뮬레이터에서 실제로 나갔다).
    fun conclude(featureId: FeatureId, ledger: ReplayFeatureLedger, apply: (ReplayFeatureLedger) -> Unit) {
        val tapped = justTapped.remove(featureId)
        val language = currentStrings.language
        val nothingToShow = when (featureId) {
            FeatureId.TopMoves -> candidates[moveNumber].isNullOrEmpty()
            else -> estimates[moveNumber]?.hasSomethingToShow() != true
        }
        if (!nothingToShow) {
            settleTicket(featureId, ledger, apply)
            return
        }
        releaseAndTell(
            reason = { ticketKept ->
                if (featureId == FeatureId.TopMoves) {
                    gameReplayNoTopMovesFor(language, ticketKept)
                } else {
                    gameReplayAnalysisFailedFor(language, ticketKept)
                }
            },
            ledger = ledger,
            apply = apply,
            tell = tapped,
        )
    }

    LaunchedEffect(replayKey, moveNumber, requestEpoch, evalUnlimited, topMovesUnlimited) {
        evalLedger = evalLedger.onMoved(moveNumber)
        topMovesLedger = topMovesLedger.onMoved(moveNumber)
        inFlight = null
        val wantsEstimate = evalLedger.wantsResultAt(moveNumber, evalUnlimited)
        val wantsCandidates = topMovesLedger.wantsResultAt(moveNumber, topMovesUnlimited)
        val needsEstimate = wantsEstimate && moveNumber !in estimates
        val needsCandidates = wantsCandidates && moveNumber !in candidates
        // 결과가 이미 있는 국면이다(값을 치른 수순으로 돌아왔거나, 자격이 사라진 뒤에 다시 눌렀다) — 엔진에 묻지 않고 바로 맺는다.
        if (wantsEstimate && !needsEstimate) conclude(FeatureId.Eval, evalLedger) { evalLedger = it }
        if (wantsCandidates && !needsCandidates) conclude(FeatureId.TopMoves, topMovesLedger) { topMovesLedger = it }
        if (!needsEstimate && !needsCandidates) return@LaunchedEffect

        delay(ReplayStepSettleMillis)
        val position = analysablePosition(currentPositionAt, moveNumber)
        try {
            // ⚠️ **차례로 묻는다** — 둘을 한꺼번에 띄우면 뒤의 것이 앞의 것이 쥔 엔진에 막혀 포기한다.
            if (needsEstimate) {
                inFlight = FeatureId.Eval
                runCatchingEngine { deferWhileBusy { engine.estimate(position) } }
                    .onSuccess { estimate ->
                        estimates = estimates + (moveNumber to estimate)
                        conclude(FeatureId.Eval, evalLedger) { evalLedger = it }
                    }
                    .onFailure { error -> reportNoResult(FeatureId.Eval, error, evalLedger) { evalLedger = it } }
            }
            if (needsCandidates) {
                inFlight = FeatureId.TopMoves
                runCatchingEngine { deferWhileBusy { engine.topMoves(position) } }
                    .onSuccess { moves ->
                        candidates = candidates + (moveNumber to moves)
                        conclude(FeatureId.TopMoves, topMovesLedger) { topMovesLedger = it }
                    }
                    .onFailure { error -> reportNoResult(FeatureId.TopMoves, error, topMovesLedger) { topMovesLedger = it } }
            }
        } finally {
            inFlight = null
        }
    }

    fun tap(
        featureId: FeatureId,
        ledger: ReplayFeatureLedger,
        unlimited: Boolean,
        apply: (ReplayFeatureLedger) -> Unit,
    ) {
        val (next, outcome) = ledger.tap(
            moveNumber = moveNumber,
            unlimited = unlimited,
            hasTicket = consumables.ticketFor(featureId) != null,
        )
        apply(next)
        when (outcome) {
            ReplayAnalysisTap.TurnedOff -> justTapped.remove(featureId)
            ReplayAnalysisTap.TurnedOn, ReplayAnalysisTap.AwaitingTicket -> {
                justTapped.add(featureId)
                requestEpoch += 1
            }
            ReplayAnalysisTap.NeedsUpsell -> showUpsell = true
        }
    }

    val shownEstimate = estimates[moveNumber].takeIf { evalLedger.isShownAt(moveNumber, evalUnlimited) }
    val shownCandidates = candidates[moveNumber].takeIf { topMovesLedger.isShownAt(moveNumber, topMovesUnlimited) }
    // 켜져 있는 동안은 라벨 자체를 점수차로 바꾼다 — 대국 화면의 버튼과 같다(잔량 표기는 그때 지운다).
    val scoreLabel = shownEstimate?.whiteScoreLead?.let(strings::scoreLeadButtonLabel)
    return ReplayAnalysisUi(
        ownership = shownEstimate?.ownership,
        candidateMoves = shownCandidates.orEmpty(),
        activity = when (inFlight) {
            FeatureId.TopMoves -> EngineActivityIndicator.Recommending
            null -> null
            else -> EngineActivityIndicator.Thinking
        },
        eval = ReplayAnalysisButton(
            label = scoreLabel ?: strings.featureShortName(FeatureId.Eval),
            mark = if (scoreLabel == null) {
                strings.featureButtonMark(evalAccess, consumables.countOf(ConsumableCatalog.EvalOnce))
            } else {
                null
            },
            isOn = evalLedger.wantsResultAt(moveNumber, evalUnlimited),
            premiumLocked = !evalLedger.isEntitledAt(moveNumber, evalUnlimited),
            onClick = { tap(FeatureId.Eval, evalLedger, evalUnlimited) { evalLedger = it } },
        ),
        topMoves = ReplayAnalysisButton(
            label = strings.featureShortName(FeatureId.TopMoves),
            mark = strings.featureButtonMark(topMovesAccess, consumables.countOf(ConsumableCatalog.TopMovesOnce)),
            isOn = topMovesLedger.wantsResultAt(moveNumber, topMovesUnlimited),
            premiumLocked = !topMovesLedger.isEntitledAt(moveNumber, topMovesUnlimited),
            onClick = { tap(FeatureId.TopMoves, topMovesLedger, topMovesUnlimited) { topMovesLedger = it } },
        ),
        isUpsellVisible = showUpsell,
        onUpsellDismiss = { showUpsell = false },
    )
}

/** 다시보기 조작부의 분석 버튼 줄 — 대국 화면과 **같은 버튼**을 쓴다(금색 테두리 = 프리미엄 축). */
@Composable
internal fun ReplayAnalysisButtons(
    analysis: ReplayAnalysisUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space6),
    ) {
        listOf(analysis.eval, analysis.topMoves).forEach { button ->
            ToggleActionButton(
                isOn = button.isOn,
                label = button.label,
                mark = button.mark,
                onClick = button.onClick,
                modifier = Modifier.weight(1f),
                premiumLocked = button.premiumLocked,
                premiumFeature = true,
            )
        }
    }
    // 대국 화면이 띄우는 그 팝업이다 — 광고 시청 1시간·구독·(있으면) 광고 스킵권.
    PremiumUpsellDialogHost(visible = analysis.isUpsellVisible, onDismiss = analysis.onUpsellDismiss)
}

/** 형세 결과에 판이나 버튼에 올릴 것이 하나라도 있는가 — 영역도 점수차도 없으면 보여 줄 것이 없다. */
internal fun ScoreEstimate.hasSomethingToShow(): Boolean = ownership != null || whiteScoreLead != null

/**
 * 엔진에 물을 국면. **기권 수는 판을 바꾸지 않으므로 그 앞 국면을 묻는다** — 기권으로 끝난 판의 다시보기는 마지막 수(기권)에서
 * 열리는데, 그 국면을 그대로 보내면 엔진에 `resign`을 두게 된다. 판의 돌은 그 앞 국면과 같다.
 */
internal fun analysablePosition(positionAt: (Int) -> GameState, moveNumber: Int): GameState {
    var number = moveNumber
    while (number > 0 && positionAt(number).moves.lastOrNull() is Move.Resign) number -= 1
    return positionAt(number)
}

/**
 * 엔진이 바쁘면([EngineOperationBusy]) 잠깐 미뤘다가 다시 묻는다. 끝내 바쁘면 그 포기를 그대로 올린다.
 * 미루는 것은 받는 쪽의 일이다 — 엔진 클라이언트는 기다리지 않는다(그 타입의 KDoc).
 */
private suspend fun <T> deferWhileBusy(ask: suspend () -> T): T {
    repeat(ReplayBusyDeferrals) {
        try {
            return ask()
        } catch (busy: EngineOperationBusy) {
            delay(ReplayBusyDeferralMillis)
        }
    }
    return ask()
}

/** `runCatching`이되 **취소는 삼키지 않는다** — 수순을 넘겨 취소된 분석이 "답을 못 받았다"로 읽히면 넘길 때마다 토스트가 뜬다. */
private suspend fun <T> runCatchingEngine(ask: suspend () -> T): Result<T> =
    try {
        Result.success(ask())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
