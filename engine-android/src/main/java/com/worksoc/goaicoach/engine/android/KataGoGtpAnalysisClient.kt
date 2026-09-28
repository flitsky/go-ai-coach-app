package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.LegalMoveGenerator
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.allCoordinates
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.CandidateMoveSource
import com.worksoc.goaicoach.shared.enginecontract.DefaultCommandTimeoutMillis
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.searchTimeoutMillisFor

internal class KataGoGtpAnalysisClient(
    private val sendCommand: suspend (command: String, timeoutMillis: Long) -> String,
    private val applySearchLimit: suspend (AnalysisLimit) -> Unit,
    private val restoreSearchLimit: () -> AnalysisLimit,
    private val contextProvider: () -> KataGoAnalysisContext,
) {
    suspend fun analyze(
        effectiveLimit: AnalysisLimit,
        requestedLimit: AnalysisLimit,
    ): AnalysisResult {
        val gtpResult = analyzeWithGtp(effectiveLimit, requestedLimit)
        val candidates = gtpResult.candidates
        val policyFallbackCount = candidates.count { it.visits == null && it.policyPrior != null }
        val legalFallbackCount = candidates.count { it.visits == null && it.policyPrior == null }
        val scoredCount = candidates.count { it.pointLoss != null }
        val context = contextProvider()
        return AnalysisResult(
            status = EngineStatus.ready(
                "KataGo analysis complete for ${context.nextPlayer.label}: $scoredCount scored / ${requestedLimit.candidateCount} requested candidate(s)",
            ),
            candidates = candidates,
            summary = buildAnalysisSummary(
                requestedLimit = requestedLimit,
                effectiveLimit = effectiveLimit,
                candidateCount = candidates.size,
                scoredCount = scoredCount,
                policyFallbackCount = policyFallbackCount,
                legalFallbackCount = legalFallbackCount,
                rootVisits = gtpResult.rootVisits,
                elapsedMs = gtpResult.elapsedMs,
            ),
            rootVisits = gtpResult.rootVisits,
            elapsedMillis = gtpResult.elapsedMs,
        )
    }

    /**
     * ⚠️ **`finally`의 되돌리기가 원래 예외를 가리면 안 된다**(refactor backlog #74, 설계 F1). 탐색이 시간 초과로
     * 끝나면 `sendCommand`가 프로세스를 내린 뒤(`input = null`) 그 시간 초과를 던진다. 그 직후 되돌리기
     * (`kata-set-param`)는 내려간 프로세스에 가서 `IllegalArgumentException`을 내는데, 평범한 `finally`에서는
     * 그것이 시간 초과를 **대신했다** — 5계층은 GTP 경로의 시간 초과를 실패로 보고 같은 예산으로 `genMove`를
     * 또 태웠다. 그래서 원래 예외가 있으면 되돌리기 실패는 그 예외의 suppressed로만 남긴다. 탐색이 성공한
     * 뒤의 되돌리기 실패는 지금처럼 그대로 올라간다.
     */
    private suspend fun analyzeWithGtp(
        effectiveLimit: AnalysisLimit,
        requestedLimit: AnalysisLimit,
    ): GtpAnalysisResult {
        var primary: Throwable? = null
        try {
            return searchWithGtp(effectiveLimit, requestedLimit)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            if (primary == null) {
                applySearchLimit(restoreSearchLimit())
            } else {
                try {
                    applySearchLimit(restoreSearchLimit())
                } catch (restoreFailure: Throwable) {
                    // 취소된 Job에서는 되돌리기가 **같은** 취소 예외를 다시 던질 수 있다 — 자기 자신을 suppressed로
                    // 넣으면 addSuppressed가 IllegalArgumentException을 던져 원래 예외를 또 가린다.
                    if (restoreFailure !== primary) primary.addSuppressed(restoreFailure)
                }
            }
        }
    }

    private suspend fun searchWithGtp(
        effectiveLimit: AnalysisLimit,
        requestedLimit: AnalysisLimit,
    ): GtpAnalysisResult {
        applySearchLimit(effectiveLimit)
        val context = contextProvider()
        val startNanos = System.nanoTime()
        val response = sendCommand(
            KataGoProtocolCommands.searchAnalyze(context.nextPlayer, effectiveLimit),
            searchTimeoutMillisFor(effectiveLimit.timeMillis),
        )
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        val candidates = KataGoAnalysisParser.attachPointLoss(
            candidates = KataGoAnalysisParser.parseCandidates(
                response = response,
                player = context.nextPlayer,
                boardSize = context.boardSize,
                maxCandidates = requestedLimit.candidateCount,
            ),
        ).fillFromPolicyIfNeeded(requestedLimit)
        return GtpAnalysisResult(
            candidates = candidates,
            rootVisits = KataGoAnalysisParser.parseRootVisitsEstimate(response),
            elapsedMs = elapsedMs,
        )
    }

    private suspend fun List<CandidateMove>.fillFromPolicyIfNeeded(
        limit: AnalysisLimit,
    ): List<CandidateMove> {
        val remaining = limit.candidateCount - size
        if (remaining <= 0) {
            return take(limit.candidateCount)
        }

        val context = contextProvider()
        val currentState = context.replayState()
        val legalCoordinates = LegalMoveGenerator
            .legalPlayCoordinates(currentState, context.nextPlayer)
            .toSet()
        val illegalCoordinates = context.boardSize.allCoordinates().toSet() - legalCoordinates
        val occupiedCoordinates = currentState
            .stones
            .keys
        val searchCoordinates = mapNotNull { candidate ->
            (candidate.move as? Move.Play)?.coordinate
        }
        val policyCandidates = if (limit.includePolicy) {
            val policyResponse = sendCommand(KataGoProtocolCommands.rawNn(), DefaultCommandTimeoutMillis)
            KataGoAnalysisParser.parsePolicyCandidates(
                response = policyResponse,
                player = context.nextPlayer,
                boardSize = context.boardSize,
                maxCandidates = remaining,
                excludedCoordinates = occupiedCoordinates + illegalCoordinates + searchCoordinates,
            )
        } else {
            emptyList()
        }
        val usedCoordinates = occupiedCoordinates + illegalCoordinates + searchCoordinates +
            policyCandidates.mapNotNull { candidate -> (candidate.move as? Move.Play)?.coordinate }
        val legalFallbackCandidates = context.boardSize.allCoordinates()
            .filter { coordinate -> coordinate in legalCoordinates && coordinate !in usedCoordinates }
            .take(remaining - policyCandidates.size)
            .mapIndexed { index, coordinate ->
                CandidateMove(
                    move = Move.Play(context.nextPlayer, coordinate),
                    source = CandidateMoveSource.LegalFallback,
                    note = "Legal fallback ${index + 1}",
                )
            }
            .toList()

        return (this + policyCandidates + legalFallbackCandidates).take(limit.candidateCount)
    }

    private fun buildAnalysisSummary(
        requestedLimit: AnalysisLimit,
        effectiveLimit: AnalysisLimit,
        candidateCount: Int,
        scoredCount: Int,
        policyFallbackCount: Int,
        legalFallbackCount: Int,
        rootVisits: Int?,
        elapsedMs: Long,
    ): String {
        val searchText = if (
            effectiveLimit.visits != requestedLimit.visits ||
            effectiveLimit.timeMillis != requestedLimit.timeMillis
        ) {
            "KataGo search analysis raised search to ${effectiveLimit.visits} visits / ${effectiveLimit.timeMillis ?: 0}ms for ${requestedLimit.candidateCount} candidate(s)."
        } else {
            "KataGo search analysis with ${effectiveLimit.visits} visits / ${effectiveLimit.timeMillis ?: 0}ms."
        }
        val searchedCount = candidateCount - policyFallbackCount - legalFallbackCount
        val fillStatus = when {
            rootVisits == null -> "UNKNOWN"
            rootVisits < effectiveLimit.visits -> "SHORT"
            else -> "OK"
        }
        val diagnosticsText =
            " Visit diagnostics: request=${effectiveLimit.visits}, root=${rootVisits ?: "none"}, elapsedMs=$elapsedMs, timeCapMs=${effectiveLimit.timeMillis ?: "none"}, fill=$fillStatus."
        return if (policyFallbackCount > 0 || legalFallbackCount > 0) {
            buildString {
                append(searchText)
                append(diagnosticsText)
                append(" Returned $searchedCount searched candidate(s)")
                if (policyFallbackCount > 0) {
                    append("; kept $policyFallbackCount raw NN policy fallback candidate(s) for logs only")
                }
                if (legalFallbackCount > 0) {
                    append("; kept $legalFallbackCount legal fallback candidate(s) for logs only")
                }
                append(". ")
                append("Showing $scoredCount scored spot(s) on the board")
                append(".")
            }
        } else {
            "$searchText$diagnosticsText Showing $scoredCount/${requestedLimit.candidateCount} scored spot(s)."
        }
    }

    private data class GtpAnalysisResult(
        val candidates: List<CandidateMove>,
        val rootVisits: Int?,
        val elapsedMs: Long,
    )
}
