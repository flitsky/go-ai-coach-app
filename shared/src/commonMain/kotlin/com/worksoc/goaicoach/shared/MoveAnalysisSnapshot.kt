package com.worksoc.goaicoach.shared

data class MoveAnalysisSnapshot(
    val boardSize: BoardSize,
    val player: StoneColor,
    val playMoves: List<AnalyzedPlayMove>,
    val passCandidate: CandidateMove?,
    val sourceCandidateCount: Int,
) {
    val legalPlayCount: Int
        get() = playMoves.size

    val scoredPlayCount: Int
        get() = playMoves.count { it.coverage == MoveEvaluationCoverage.Scored }

    val policyOnlyPlayCount: Int
        get() = playMoves.count { it.coverage == MoveEvaluationCoverage.PolicyOnly }

    val legalOnlyPlayCount: Int
        get() = playMoves.count { it.coverage == MoveEvaluationCoverage.LegalOnly }

    val hasEngineCandidates: Boolean
        get() = sourceCandidateCount > 0

    fun candidateAt(coordinate: BoardCoordinate): CandidateMove? =
        playMoves
            .firstOrNull { it.coordinate == coordinate }
            ?.candidate

    fun candidatesForDisplay(
        policy: MoveAnalysisDisplayPolicy = MoveAnalysisDisplayPolicy.ScoredOnly,
    ): List<CandidateMove> =
        playMoves
            .asSequence()
            .filter { analyzed -> policy.includes(analyzed.coverage) }
            .sortedWith(
                compareBy<AnalyzedPlayMove> { it.displayOrder ?: Int.MAX_VALUE }
                    .thenBy { it.coordinate.row }
                    .thenBy { it.coordinate.column },
            )
            .map { it.candidate }
            .let { sequence ->
                policy.maxMoves?.let { maxMoves -> sequence.take(maxMoves) } ?: sequence
            }
            .toList()

    fun candidatesForReview(): List<CandidateMove> =
        candidatesForDisplay(MoveAnalysisDisplayPolicy.AllKnown) +
            playMoves
                .asSequence()
                .filter { it.coverage == MoveEvaluationCoverage.LegalOnly }
                .sortedWith(compareBy({ it.coordinate.row }, { it.coordinate.column }))
                .map { it.candidate }
                .toList() +
            listOfNotNull(passCandidate)

    fun coverageSummary(): String =
        "Analysis coverage: legal $legalPlayCount, scored $scoredPlayCount, policy-only $policyOnlyPlayCount, pending $legalOnlyPlayCount."

    companion object {
        fun empty(state: GameState): MoveAnalysisSnapshot =
            from(state = state, candidates = emptyList())

        fun from(
            state: GameState,
            candidates: List<CandidateMove>,
        ): MoveAnalysisSnapshot {
            val player = state.nextPlayer
            val legalCoordinates = LegalMoveGenerator.legalPlayCoordinates(state, player)
            val indexedCandidates = candidates
                .mapIndexedNotNull { index, candidate ->
                    val play = candidate.move as? Move.Play ?: return@mapIndexedNotNull null
                    if (play.player != player || play.coordinate !in legalCoordinates) {
                        return@mapIndexedNotNull null
                    }
                    IndexedCandidate(index, candidate)
                }
                .groupBy { indexed -> (indexed.candidate.move as Move.Play).coordinate }
                .mapValues { (_, values) -> values.minBy { it.candidate.engineOrder ?: it.index } }

            val playMoves = legalCoordinates.map { coordinate ->
                val indexed = indexedCandidates[coordinate]
                val candidate = indexed?.candidate
                    ?: CandidateMove(
                        move = Move.Play(player, coordinate),
                        note = "Legal move pending engine evaluation",
                        source = CandidateMoveSource.LegalFallback,
                    )
                AnalyzedPlayMove(
                    coordinate = coordinate,
                    candidate = candidate,
                    coverage = candidate.coverage(),
                    displayOrder = indexed?.candidate?.engineOrder ?: indexed?.index,
                )
            }

            return MoveAnalysisSnapshot(
                boardSize = state.boardSize,
                player = player,
                playMoves = playMoves,
                passCandidate = candidates.firstOrNull { candidate ->
                    candidate.move is Move.Pass && candidate.move.player == player
                },
                sourceCandidateCount = candidates.size,
            )
        }
    }

    private data class IndexedCandidate(
        val index: Int,
        val candidate: CandidateMove,
    )
}

data class AnalyzedPlayMove(
    val coordinate: BoardCoordinate,
    val candidate: CandidateMove,
    val coverage: MoveEvaluationCoverage,
    val displayOrder: Int?,
)

enum class MoveEvaluationCoverage {
    Scored,
    PolicyOnly,
    LegalOnly,
}

data class MoveAnalysisDisplayPolicy(
    val includeScored: Boolean,
    val includePolicyOnly: Boolean,
    val includeLegalOnly: Boolean,
    val maxMoves: Int? = null,
) {
    fun includes(coverage: MoveEvaluationCoverage): Boolean =
        when (coverage) {
            MoveEvaluationCoverage.Scored -> includeScored
            MoveEvaluationCoverage.PolicyOnly -> includePolicyOnly
            MoveEvaluationCoverage.LegalOnly -> includeLegalOnly
        }

    companion object {
        val ScoredOnly = MoveAnalysisDisplayPolicy(
            includeScored = true,
            includePolicyOnly = false,
            includeLegalOnly = false,
        )

        val AllKnown = MoveAnalysisDisplayPolicy(
            includeScored = true,
            includePolicyOnly = true,
            includeLegalOnly = false,
        )
    }
}

private fun CandidateMove.coverage(): MoveEvaluationCoverage =
    when {
        pointLoss != null -> MoveEvaluationCoverage.Scored
        policyPrior != null -> MoveEvaluationCoverage.PolicyOnly
        else -> MoveEvaluationCoverage.LegalOnly
    }
