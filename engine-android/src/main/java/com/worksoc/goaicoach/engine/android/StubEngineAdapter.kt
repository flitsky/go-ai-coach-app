package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.CandidateMoveSource
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameStateReplayer
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.OwnershipEstimate
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.allCoordinates
import com.worksoc.goaicoach.shared.describe

class StubEngineAdapter : EngineCoreApi {
    private var boardSize: BoardSize = BoardSize.Nine
    private var ruleset: Ruleset = Ruleset.Japanese
    private var initialized: Boolean = false
    private var nextPlayer: StoneColor = StoneColor.Black
    private var profile: EngineProfile = EngineProfile()
    private val occupied = linkedSetOf<BoardCoordinate>()
    private val playedMoves = mutableListOf<Move>()

    override suspend fun initialize(profile: EngineProfile): EngineStatus {
        initialized = true
        this.profile = profile
        return EngineStatus.ready(
            "Stub engine ready: ${profile.describe()}",
        )
    }

    override suspend fun configure(profile: EngineProfile): EngineStatus {
        ensureInitialized()
        this.profile = profile
        return EngineStatus.ready("Stub engine configured: ${profile.describe()}")
    }

    override suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset): EngineStatus {
        ensureInitialized()
        this.boardSize = boardSize
        this.ruleset = ruleset
        nextPlayer = StoneColor.Black
        occupied.clear()
        playedMoves.clear()
        return EngineStatus.ready("New ${boardSize.value}x${boardSize.value} ${ruleset.scoringLabel} game")
    }

    override suspend fun playMove(move: Move): EngineStatus {
        ensureInitialized()
        if (move is Move.Play) {
            occupied += move.coordinate
        }
        playedMoves += move
        if (move is Move.Play || move is Move.Pass) {
            nextPlayer = move.player.opponent
        }
        return EngineStatus.ready("Accepted ${move.describe(boardSize)}")
    }

    override suspend fun genMove(player: StoneColor): MoveResult {
        ensureInitialized()
        check(player == nextPlayer) { "Expected ${nextPlayer.label}, got ${player.label}" }
        val coordinate = chooseCoordinate()
        occupied += coordinate
        val move = Move.Play(player, coordinate)
        playedMoves += move
        nextPlayer = player.opponent
        return MoveResult(
            status = EngineStatus.ready("Generated ${move.describe(boardSize)}"),
            move = move,
            summary = "Deterministic stub move using ${profile.difficulty.label}; no KataGo analysis has run yet.",
        )
    }

    override suspend fun undoMove(): EngineStatus {
        ensureInitialized()
        val removed = playedMoves.removeLastOrNull()
            ?: return EngineStatus.ready("Stub engine has no move to undo.")
        rebuildOccupiedFromHistory()
        nextPlayer = removed.player
        return EngineStatus.ready("Stub engine undid ${removed.describe(boardSize)}")
    }

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        ensureInitialized()
        val candidates = priorityCoordinates()
            .asSequence()
            .filter { it !in occupied }
            .take(limit.candidateCount)
            .mapIndexed { index, coordinate ->
                CandidateMove(
                    move = Move.Play(nextPlayer, coordinate),
                    winRate = 0.51 - index * 0.02,
                    scoreLead = 0.8 - index * 0.3,
                    pointLoss = index * 0.3,
                    visits = (limit.visits / (index + 1)).coerceAtLeast(1),
                    engineOrder = index,
                    source = CandidateMoveSource.Stub,
                    note = if (index == 0) "Stub best candidate" else "Stub candidate ${index + 1}",
                )
            }
            .toList()

        return AnalysisResult(
            status = EngineStatus.ready("Analysis stub complete with ${limit.visits} visits"),
            candidates = candidates,
            summary = "Stub analysis response shaped like a future KataGo result. Profile=${profile.difficulty.label}, candidates=${candidates.size}.",
        )
    }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate {
        ensureInitialized()
        val score = localScore()
        val blackArea = score.blackArea ?: 0.0
        val whiteArea = (score.whiteAreaWithKomi ?: 0.0) - (score.komi ?: 0.0)
        val whiteLead = (score.whiteAreaWithKomi ?: 0.0) - blackArea
        val boardPoints = boardSize.value * boardSize.value
        val unclear = (boardPoints - blackArea.toInt() - whiteArea.toInt()).coerceAtLeast(0)
        return ScoreEstimate(
            status = EngineStatus.ready("Stub local score estimate complete."),
            whiteWinRate = if (whiteLead >= 0.0) 0.55 else 0.45,
            whiteScoreLead = whiteLead,
            ownership = OwnershipEstimate(
                blackLikelyPoints = blackArea.toInt(),
                whiteLikelyPoints = whiteArea.toInt(),
                neutralOrUnclearPoints = unclear,
                threshold = 1.0,
            ),
            summary = "Stub estimate uses local area projection; it is not engine analysis.",
        )
    }

    override suspend fun scoreFinal(): FinalScoreResult {
        ensureInitialized()
        return localScore()
    }

    override suspend fun deadStones(): DeadStonesResult {
        ensureInitialized()
        return DeadStonesResult(
            status = EngineStatus.ready("Stub engine has no dead-stone status."),
            coordinates = emptyList(),
            summary = "Stub engine does not infer dead stones.",
        )
    }

    override suspend fun stop(): EngineStatus {
        initialized = false
        occupied.clear()
        playedMoves.clear()
        return EngineStatus.stopped("Stub engine stopped")
    }

    private fun ensureInitialized() {
        check(initialized) { "EngineCoreApi must be initialized before use" }
    }

    private fun chooseCoordinate(): BoardCoordinate {
        return priorityCoordinates().firstOrNull { it !in occupied }
            ?: error("No open coordinates remain on ${boardSize.value}x${boardSize.value}")
    }

    private fun rebuildOccupiedFromHistory() {
        occupied.clear()
        for (move in playedMoves) {
            if (move is Move.Play) {
                occupied += move.coordinate
            }
        }
    }

    private fun localScore(): FinalScoreResult =
        BoardScorer.score(
            GameStateReplayer.replay(
                boardSize = boardSize,
                ruleset = ruleset,
                moves = playedMoves,
            ),
        )

    private fun priorityCoordinates(): List<BoardCoordinate> {
        val last = boardSize.value - 1
        val center = boardSize.value / 2
        val star = when (boardSize.value) {
            9 -> listOf(
                BoardCoordinate(4, 4),
                BoardCoordinate(2, 2),
                BoardCoordinate(2, 6),
                BoardCoordinate(6, 2),
                BoardCoordinate(6, 6),
            )

            13 -> listOf(
                BoardCoordinate(6, 6),
                BoardCoordinate(3, 3),
                BoardCoordinate(3, 9),
                BoardCoordinate(9, 3),
                BoardCoordinate(9, 9),
            )

            19 -> listOf(
                BoardCoordinate(9, 9),
                BoardCoordinate(3, 3),
                BoardCoordinate(3, 9),
                BoardCoordinate(3, 15),
                BoardCoordinate(9, 3),
                BoardCoordinate(9, 15),
                BoardCoordinate(15, 3),
                BoardCoordinate(15, 9),
                BoardCoordinate(15, 15),
            )

            else -> listOf(BoardCoordinate(center, center))
        }
        val corners = listOf(
            BoardCoordinate(0, 0),
            BoardCoordinate(0, last),
            BoardCoordinate(last, 0),
            BoardCoordinate(last, last),
        )
        val sideCenters = listOf(
            BoardCoordinate(0, center),
            BoardCoordinate(center, 0),
            BoardCoordinate(center, last),
            BoardCoordinate(last, center),
        )
        val remaining = boardSize.allCoordinates().toList()
        val profileOrdered = when (profile.difficulty) {
            com.worksoc.goaicoach.shared.DifficultyProfile.Beginner -> corners + sideCenters + star + remaining
            com.worksoc.goaicoach.shared.DifficultyProfile.Casual -> sideCenters + star + corners + remaining
            com.worksoc.goaicoach.shared.DifficultyProfile.Intermediate -> star + sideCenters + corners + remaining
            com.worksoc.goaicoach.shared.DifficultyProfile.Strong -> star + corners + sideCenters + remaining
            com.worksoc.goaicoach.shared.DifficultyProfile.FullAnalysis -> star + corners + sideCenters + remaining
        }
        return profileOrdered.distinct()
    }

    private fun EngineProfile.describe(): String =
        "${difficulty.label}, visits=${analysisLimit.visits}, time=${analysisLimit.timeMillis ?: "none"}ms, mode=$mode"
}
