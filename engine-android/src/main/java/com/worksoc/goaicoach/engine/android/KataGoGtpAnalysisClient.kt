package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.CandidateMoveSource
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.LegalMoveGenerator
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.allCoordinates

internal class KataGoGtpAnalysisClient(
    private val sendCommand: (String) -> String,
    private val applySearchLimit: (AnalysisLimit) -> Unit,
    private val restoreSearchLimit: () -> AnalysisLimit,
    private val contextProvider: () -> KataGoAnalysisContext,
) {
    fun analyze(
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

    private fun analyzeWithGtp(
        effectiveLimit: AnalysisLimit,
        requestedLimit: AnalysisLimit,
    ): GtpAnalysisResult =
        try {
            applySearchLimit(effectiveLimit)
            val context = contextProvider()
            val startNanos = System.nanoTime()
            val response = sendCommand(KataGoProtocolCommands.searchAnalyze(context.nextPlayer, effectiveLimit))
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            val candidates = KataGoAnalysisParser.attachPointLoss(
                candidates = KataGoAnalysisParser.parseCandidates(
                    response = response,
                    player = context.nextPlayer,
                    boardSize = context.boardSize,
                    maxCandidates = requestedLimit.candidateCount,
                ),
            ).fillFromPolicyIfNeeded(requestedLimit)
            GtpAnalysisResult(
                candidates = candidates,
                rootVisits = KataGoAnalysisParser.parseRootVisitsEstimate(response),
                elapsedMs = elapsedMs,
            )
        } finally {
            applySearchLimit(restoreSearchLimit())
        }

    private fun List<CandidateMove>.fillFromPolicyIfNeeded(
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
            val policyResponse = sendCommand(KataGoProtocolCommands.rawNn())
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
