package com.worksoc.goaicoach.shared

/**
 * Core boundary for all Go engine implementations.
 *
 * The app owns canonical game state and replays it into this adapter. Engine
 * implementations may keep internal search trees, NN caches, or transport
 * sessions, so callers must not infer that `newGame()` or `playMove()` also
 * isolates search history. Reusing prior tree work is valuable in human-vs-AI
 * play because the same AI is continuing its reading. It can contaminate
 * visit-calibrated play levels only when multiple AI seats share one process.
 * Use `clearSearchCache()` before AI-vs-AI move selection when fair per-seat
 * search is more important than reuse.
 *
 * Keep this interface as the 1:1 layer for raw engine capabilities. Higher
 * middleware APIs may compose these calls, but should not hide or drop engine
 * primitives that a local process, JNI runtime, or remote server can provide.
 */
interface EngineCoreApi {
    suspend fun initialize(profile: EngineProfile): EngineStatus
    suspend fun configure(profile: EngineProfile): EngineStatus
    suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset): EngineStatus
    suspend fun playMove(move: Move): EngineStatus
    suspend fun genMove(player: StoneColor): MoveResult
    suspend fun undoMove(): EngineStatus

    /**
     * Clears engine-side search state without changing the board.
     *
     * This is intentionally separate from app-level analysis caching. KataGo
     * can count reusable tree visits from previous turns and can also retain
     * enough state across repeated games to replay moves nearly instantly.
     * Random seeds may diversify search, but they are not a correctness
     * substitute for cache isolation. The current app calls this for AI-vs-AI
     * shared-process play only; human-vs-AI keeps reuse for strength and speed.
     * A future performance-oriented policy may choose tree reuse with a
     * new-playout budget instead.
     */
    suspend fun clearSearchCache(): EngineStatus =
        EngineStatus.ready("Engine search cache unchanged.")
    suspend fun analyze(limit: AnalysisLimit): AnalysisResult
    suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate
    suspend fun deadStones(): DeadStonesResult
    suspend fun scoreFinal(): FinalScoreResult
    suspend fun stop(): EngineStatus
}

/**
 * Compatibility name for current local engine implementations.
 *
 * New middleware code should depend on [EngineCoreApi] when it needs raw engine
 * primitives. Existing external code may still use this alias, but concrete
 * local process/JNI/remote implementations should implement [EngineCoreApi]
 * directly.
 */
interface EngineAdapter : EngineCoreApi

data class EngineProfile(
    val mode: EngineMode = EngineMode.Stub,
    val difficulty: DifficultyProfile = DifficultyProfile.Beginner,
    val analysisLimit: AnalysisLimit = difficulty.defaultAnalysisLimit(),
    val name: String = "stub",
)

enum class EngineMode {
    Stub,
    LocalProcess,
    JniNative,
    RemoteServer,
}

enum class DifficultyProfile(
    val label: String,
    val defaultVisits: Int,
    val defaultTimeMillis: Long,
) {
    Beginner("Beginner", defaultVisits = 16, defaultTimeMillis = 250),
    Casual("Casual", defaultVisits = 64, defaultTimeMillis = 500),
    Intermediate("Intermediate", defaultVisits = 160, defaultTimeMillis = 1_000),
    Strong("Strong", defaultVisits = 400, defaultTimeMillis = 2_000),
    FullAnalysis("Full Analysis", defaultVisits = 1_000, defaultTimeMillis = 5_000),
    ;

    fun defaultAnalysisLimit(): AnalysisLimit =
        AnalysisLimit(
            visits = defaultVisits,
            timeMillis = defaultTimeMillis,
        )

    fun previous(): DifficultyProfile {
        val all = entries
        return all[(ordinal - 1).coerceAtLeast(0)]
    }

    fun next(): DifficultyProfile {
        val all = entries
        return all[(ordinal + 1).coerceAtMost(all.lastIndex)]
    }
}

data class EngineStatus(
    val state: EngineState,
    val message: String,
) {
    companion object {
        fun idle(message: String): EngineStatus = EngineStatus(EngineState.Idle, message)
        fun ready(message: String): EngineStatus = EngineStatus(EngineState.Ready, message)
        fun busy(message: String): EngineStatus = EngineStatus(EngineState.Busy, message)
        fun error(message: String): EngineStatus = EngineStatus(EngineState.Error, message)
        fun stopped(message: String): EngineStatus = EngineStatus(EngineState.Stopped, message)
    }
}

enum class EngineState {
    Idle,
    Ready,
    Busy,
    Error,
    Stopped,
}

data class AnalysisLimit(
    val visits: Int = 64,
    val timeMillis: Long? = null,
    val candidateCount: Int = 5,
    val includePolicy: Boolean = true,
    val refinePolicyMoves: Int = 12,
    val minVisitsPerCandidate: Int = 20,
    val minTimeMillis: Long? = 2_000L,
) {
    init {
        require(visits > 0) { "visits must be positive" }
        require(timeMillis == null || timeMillis > 0) { "timeMillis must be positive when set" }
        require(candidateCount > 0) { "candidateCount must be positive" }
        require(refinePolicyMoves >= 0) { "refinePolicyMoves must be non-negative" }
        require(minVisitsPerCandidate >= 0) { "minVisitsPerCandidate must be non-negative" }
        require(minTimeMillis == null || minTimeMillis > 0) { "minTimeMillis must be positive when set" }
    }
}

enum class AnalysisPreset(
    val label: String,
    val description: String,
    val candidateCap: Int,
    val promoteTopMovesDifficulty: Boolean,
    val includePolicy: Boolean,
    val refinePolicyMoves: Int,
    val minVisitsPerCandidate: Int,
    val minTimeMillis: Long?,
    val allowManualDeepFallback: Boolean,
) {
    Lite(
        label = "Lite",
        description = "Fast play on slower devices",
        candidateCap = 8,
        promoteTopMovesDifficulty = false,
        includePolicy = false,
        refinePolicyMoves = 0,
        minVisitsPerCandidate = 0,
        minTimeMillis = null,
        allowManualDeepFallback = false,
    ),
    Learning(
        label = "Learning",
        description = "Beginner candidate coverage with low latency",
        candidateCap = 16,
        promoteTopMovesDifficulty = false,
        includePolicy = true,
        refinePolicyMoves = 0,
        minVisitsPerCandidate = 0,
        minTimeMillis = null,
        allowManualDeepFallback = false,
    ),
    Balanced(
        label = "Balanced",
        description = "Moderate hints with limited policy refinement",
        candidateCap = 20,
        promoteTopMovesDifficulty = true,
        includePolicy = true,
        refinePolicyMoves = 4,
        minVisitsPerCandidate = 4,
        minTimeMillis = 800L,
        allowManualDeepFallback = false,
    ),
    Deep(
        label = "Deep",
        description = "Study mode with broad legal-move coverage",
        candidateCap = 81,
        promoteTopMovesDifficulty = true,
        includePolicy = true,
        refinePolicyMoves = 12,
        minVisitsPerCandidate = 20,
        minTimeMillis = 2_000L,
        allowManualDeepFallback = true,
    ),
}

data class CandidateMove(
    val move: Move,
    val winRate: Double? = null,
    val scoreLead: Double? = null,
    val pointLoss: Double? = null,
    val visits: Int? = null,
    val policyPrior: Double? = null,
    val engineOrder: Int? = null,
    val source: CandidateMoveSource = CandidateMoveSource.Unknown,
    val note: String? = null,
)

enum class CandidateMoveSource {
    EngineSearch,
    PolicyRefine,
    PolicyOnly,
    LegalFallback,
    Stub,
    Unknown,
}

data class AnalysisResult(
    val status: EngineStatus,
    val candidates: List<CandidateMove>,
    val summary: String,
    val rootVisits: Int? = null,
    val elapsedMillis: Long? = null,
)

data class OwnershipEstimate(
    val blackLikelyPoints: Int,
    val whiteLikelyPoints: Int,
    val neutralOrUnclearPoints: Int,
    val threshold: Double,
    val points: List<OwnershipPoint> = emptyList(),
)

data class OwnershipPoint(
    val coordinate: BoardCoordinate,
    val value: Double,
)

data class ScoreEstimate(
    val status: EngineStatus,
    val whiteWinRate: Double? = null,
    val whiteScoreLead: Double? = null,
    val ownership: OwnershipEstimate? = null,
    val summary: String,
)

data class FinalScoreResult(
    val status: EngineStatus,
    val rawScore: String,
    val winner: StoneColor? = null,
    val margin: Double? = null,
    val blackArea: Double? = null,
    val whiteAreaWithKomi: Double? = null,
    val komi: Double? = null,
    val summary: String,
)

data class DeadStonesResult(
    val status: EngineStatus,
    val coordinates: List<BoardCoordinate>,
    val summary: String,
)

data class MoveResult(
    val status: EngineStatus,
    val move: Move,
    val summary: String,
)
