package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameStateReplayer
import com.worksoc.goaicoach.shared.LegalMoveGenerator
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.allCoordinates
import org.json.JSONObject

internal class KataGoJsonPositionAnalysisClient(
    private val sendAnalysisQuery: (JSONObject) -> String,
    private val buildAnalysisQuery: (
        limit: AnalysisLimit,
        refineMove: Move.Play?,
        includePolicyOverride: Boolean?,
    ) -> JSONObject,
    private val contextProvider: () -> KataGoAnalysisContext,
) {
    fun analyze(
        effectiveLimit: AnalysisLimit,
        candidateCount: Int,
    ): AnalysisResult {
        val context = contextProvider()
        val startNanos = System.nanoTime()
        val response = sendAnalysisQuery(buildAnalysisQuery(effectiveLimit, null, null))
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        val rootVisits = KataGoJsonAnalysisParser.parseRootVisits(response)
        val searchedCandidates = KataGoJsonAnalysisParser.parseCandidates(
            response = response,
            player = context.nextPlayer,
            boardSize = context.boardSize,
            maxCandidates = candidateCount,
        )
        val currentState = GameStateReplayer
            .replay(boardSize = context.boardSize, ruleset = context.ruleset, moves = context.playedMoves)
        val legalCoordinates = LegalMoveGenerator
            .legalPlayCoordinates(currentState, context.nextPlayer)
            .toSet()
        val illegalCoordinates = context.boardSize.allCoordinates().toSet() - legalCoordinates
        val searchedCoordinates = searchedCandidates.playCoordinates()
        val policyCandidates = if (effectiveLimit.includePolicy) {
            KataGoJsonAnalysisParser.parsePolicyCandidates(
                response = response,
                player = context.nextPlayer,
                boardSize = context.boardSize,
                maxCandidates = candidateCount,
                excludedCoordinates = searchedCoordinates + illegalCoordinates,
            )
        } else {
            emptyList()
        }
        val referenceScoreLead = KataGoJsonAnalysisParser.parseRootWhiteScoreLead(response)
        val refinedCandidates = if (referenceScoreLead == null || effectiveLimit.refinePolicyMoves <= 0) {
            emptyList()
        } else {
            refineJsonPolicyCandidates(
                policyCandidates = policyCandidates,
                referenceScoreLead = referenceScoreLead,
                maxCandidates = candidateCount,
                refineBudget = effectiveLimit.refinePolicyMoves,
            )
        }
        val refinedCoordinates = refinedCandidates.playCoordinates()
        val candidates = (
            searchedCandidates +
                refinedCandidates +
                policyCandidates.filterNot { candidate ->
                    (candidate.move as? Move.Play)?.coordinate in refinedCoordinates
                }
            )
            .take(candidateCount)
        val scoredCount = candidates.count { it.pointLoss != null }
        val policyOnlyCount = candidates.count { it.pointLoss == null && it.policyPrior != null }
        return AnalysisResult(
            status = EngineStatus.ready(
                "KataGo JSON analysis complete for ${context.nextPlayer.label}: $scoredCount/$candidateCount scored candidate(s)",
            ),
            candidates = candidates,
            summary = buildJsonAnalysisSummary(
                effectiveLimit = effectiveLimit,
                rootVisits = rootVisits,
                elapsedMs = elapsedMs,
                scoredCount = scoredCount,
                policyOnlyCount = policyOnlyCount,
                refinedCount = refinedCandidates.size,
            ),
            rootVisits = rootVisits,
            elapsedMillis = elapsedMs,
        )
    }

    private fun refineJsonPolicyCandidates(
        policyCandidates: List<CandidateMove>,
        referenceScoreLead: Double,
        maxCandidates: Int,
        refineBudget: Int,
    ): List<CandidateMove> {
        val refineCount = (maxCandidates / JsonRefineCandidateDivisor)
            .coerceIn(0, refineBudget)
        if (refineCount <= 0) {
            return emptyList()
        }

        val context = contextProvider()
        return policyCandidates
            .asSequence()
            .mapNotNull { candidate ->
                val move = candidate.move as? Move.Play ?: return@mapNotNull null
                move to candidate.policyPrior
            }
            .take(refineCount)
            .mapNotNull { (move, policyPrior) ->
                runCatching {
                    val response = sendAnalysisQuery(
                        buildAnalysisQuery(JsonRefineLimit, move, false),
                    )
                    KataGoJsonAnalysisParser.parseRefinedCandidate(
                        response = response,
                        player = context.nextPlayer,
                        move = move,
                        referenceScoreLead = referenceScoreLead,
                        policyPrior = policyPrior,
                    )
                }.getOrNull()
            }
            .toList()
    }

    private fun List<CandidateMove>.playCoordinates(): Set<BoardCoordinate> =
        mapNotNull { candidate -> (candidate.move as? Move.Play)?.coordinate }
            .toSet()

    private fun buildJsonAnalysisSummary(
        effectiveLimit: AnalysisLimit,
        rootVisits: Int?,
        elapsedMs: Long,
        scoredCount: Int,
        policyOnlyCount: Int,
        refinedCount: Int,
    ): String {
        val fillStatus = when {
            rootVisits == null -> "UNKNOWN"
            rootVisits < effectiveLimit.visits -> "SHORT"
            else -> "OK"
        }
        return buildString {
            append("KataGo JSON analysis with ${effectiveLimit.visits} visits / ${effectiveLimit.timeMillis ?: 0}ms.")
            append(" Visit diagnostics: request=${effectiveLimit.visits}, root=${rootVisits ?: "none"}, elapsedMs=$elapsedMs, timeCapMs=${effectiveLimit.timeMillis ?: "none"}, fill=$fillStatus.")
            append(" Returned $scoredCount scored, $policyOnlyCount policy-only candidate(s); refined $refinedCount/${effectiveLimit.refinePolicyMoves} policy move(s).")
        }
    }

    private companion object {
        private const val JsonRefineCandidateDivisor = 4
        private val JsonRefineLimit = AnalysisLimit(
            visits = 8,
            includePolicy = false,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )
    }
}
