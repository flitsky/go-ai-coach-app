package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import com.worksoc.goaicoach.shared.fastCandidateAnalysis
import kotlin.random.Random

internal val HumanPlayer = StoneColor.Black
internal val AiPlayer = StoneColor.White

internal enum class MatchMode(val label: String) {
    HumanVsAi("AI 대국"),
    AiVsHuman("AI 선공"),
    AiVsAi("AI 자동 대국"),
    LocalTwoPlayer("2P 테스트"),
}

internal enum class SeatController(val label: String) {
    Human("플레이어"),
    Ai("AI"),
}

internal enum class HumanGameType(val label: String) {
    Normal("일반"),
    Teaching("티칭 모드"),
}

internal enum class AiEngineChoice(val label: String) {
    KataGo("KataGo"),
}

internal enum class SeatId(
    val player: StoneColor,
    val label: String,
    val debugLabel: String,
) {
    Black(StoneColor.Black, "흑", "Black"),
    White(StoneColor.White, "백", "White"),
    ;

    companion object {
        fun fromPlayer(player: StoneColor): SeatId =
            when (player) {
                StoneColor.Black -> Black
                StoneColor.White -> White
            }
    }
}

internal data class AiCharacterProfile(
    val engine: AiEngineChoice,
    val playLevel: PlayLevelSetting,
) {
    val displayLabel: String = "${engine.label} ${playLevel.displayLabel}"
    val selectionDescription: String = playLevel.selectionPolicy.description
}

internal data class SeatAssignment(
    val id: SeatId,
    val setup: SidePlayerSetup,
) {
    val player: StoneColor = id.player
    val controller: SeatController = setup.controller
    val isHuman: Boolean = controller == SeatController.Human
    val isAi: Boolean = controller == SeatController.Ai
    val aiCharacter: AiCharacterProfile? = setup.aiCharacterProfile()

    fun summary(engineName: String): String =
        "${id.debugLabel}: ${setup.summary(engineName)}"
}

internal enum class AutoPlayDelaySetting(
    val millis: Long,
    val label: String,
) {
    None(0L, "즉시"),
    Short(500L, "0.5초"),
    Normal(1_000L, "1초"),
    Slow(2_000L, "2초"),
    Study(3_000L, "3초");

    companion object {
        val Default: AutoPlayDelaySetting = Normal

        fun fromMillis(millis: Long): AutoPlayDelaySetting =
            entries.firstOrNull { setting -> setting.millis == millis }
                ?: Default
    }
}

internal data class SidePlayerSetup(
    val controller: SeatController,
    val humanGameType: HumanGameType = HumanGameType.Normal,
    val aiEngine: AiEngineChoice = AiEngineChoice.KataGo,
    val playLevel: PlayLevelSetting = PlayLevelSetting(),
)

internal fun SidePlayerSetup.aiCharacterProfile(): AiCharacterProfile? =
    if (controller == SeatController.Ai) {
        AiCharacterProfile(
            engine = aiEngine,
            playLevel = playLevel,
        )
    } else {
        null
    }

internal data class PlayerSetup(
    val black: SidePlayerSetup = SidePlayerSetup(controller = SeatController.Human),
    val white: SidePlayerSetup = SidePlayerSetup(controller = SeatController.Ai),
) {
    fun seat(id: SeatId): SeatAssignment =
        SeatAssignment(
            id = id,
            setup = when (id) {
                SeatId.Black -> black
                SeatId.White -> white
            },
        )

    fun seatFor(player: StoneColor): SeatAssignment =
        seat(SeatId.fromPlayer(player))

    fun seats(): List<SeatAssignment> =
        listOf(seat(SeatId.Black), seat(SeatId.White))

    fun sideFor(player: StoneColor): SidePlayerSetup =
        seatFor(player).setup

    fun updateSeat(
        id: SeatId,
        side: SidePlayerSetup,
    ): PlayerSetup =
        when (id) {
            SeatId.Black -> copy(black = side)
            SeatId.White -> copy(white = side)
        }

    fun updateSide(
        player: StoneColor,
        side: SidePlayerSetup,
    ): PlayerSetup =
        updateSeat(SeatId.fromPlayer(player), side)

    fun matchMode(): MatchMode {
        val blackSeat = seat(SeatId.Black)
        val whiteSeat = seat(SeatId.White)
        return when {
            blackSeat.isHuman && whiteSeat.isAi -> MatchMode.HumanVsAi
            blackSeat.isAi && whiteSeat.isHuman -> MatchMode.AiVsHuman
            blackSeat.isAi && whiteSeat.isAi -> MatchMode.AiVsAi
            else -> MatchMode.LocalTwoPlayer
        }
    }

    fun humanSeatCount(): Int =
        seats().count { seat -> seat.isHuman }

    fun isAutoPlay(): Boolean =
        seats().all { seat -> seat.isAi }

    fun summary(engineName: String): String =
        seats().joinToString(" / ") { seat -> seat.summary(engineName) }
}

internal fun SidePlayerSetup.summary(engineName: String): String =
    when (controller) {
        SeatController.Human -> "${controller.label} ${humanGameType.label}"
        SeatController.Ai -> "$engineName ${playLevel.displayLabel}"
    }

internal data class TurnOutcome(
    val gameState: GameState,
    val engineMessage: String,
    val candidateText: String,
    val lastMoveText: String,
)

internal suspend fun applyAiResponseAfterHumanTurn(
    engineAdapter: EngineCoreApi,
    stateAfterHuman: GameState,
    humanMove: Move,
    playLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    onHumanMoveAccepted: suspend () -> Unit = {},
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
    val selectedAiMove = engineAdapter.selectAiMoveFromAnalysis(
        currentState = stateAfterHuman,
        aiPlayer = AiPlayer,
        playLevel = playLevel,
        searchTimeSettings = searchTimeSettings,
        searchMode = EngineSearchMode.GtpStatefulFast,
    )
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
        engineMessage = "${humanStatus.message}\n${aiResult.status.message}\n${aiResult.summary}",
        candidateText = "AI replied with $aiText.",
        lastMoveText = aiText,
    )
}

internal suspend fun applyAiTurn(
    engineAdapter: EngineCoreApi,
    currentState: GameState,
    aiPlayer: StoneColor,
    playLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    searchMode: EngineSearchMode = EngineSearchMode.GtpStatefulFast,
    isolateSearchCache: Boolean = false,
): TurnOutcome {
    if (isolateSearchCache && searchMode == EngineSearchMode.GtpStatefulFast) {
        // AI-vs-AI currently shares one KataGo process. Without this isolation,
        // a lower-budget side can inherit the previous higher-budget side's
        // subtree and hide the intended B16/B32/B64 strength gap.
        engineAdapter.clearSearchCache()
    }
    val selectedAiMove = engineAdapter.selectAiMoveFromAnalysis(
        currentState = currentState,
        aiPlayer = aiPlayer,
        playLevel = playLevel,
        searchTimeSettings = searchTimeSettings,
        searchMode = searchMode,
    )
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
        engineMessage = "${aiResult.status.message}\n${aiResult.summary}",
        candidateText = "AI replied with $aiText.",
        lastMoveText = aiText,
    )
}

internal fun activePlayer(
    mode: MatchMode,
    gameState: GameState,
): StoneColor =
    when (mode) {
        MatchMode.HumanVsAi -> HumanPlayer
        MatchMode.AiVsHuman -> AiPlayer
        MatchMode.AiVsAi -> gameState.nextPlayer
        MatchMode.LocalTwoPlayer -> gameState.nextPlayer
    }

internal fun boardInputEnabled(
    playerSetup: PlayerSetup,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    nextPlayer: StoneColor,
): Boolean =
    !isEngineBusy &&
        playerSetup.seatFor(nextPlayer).isHuman &&
        (isEngineReady || playerSetup.matchMode() == MatchMode.LocalTwoPlayer)

internal fun turnStatus(
    nextPlayer: StoneColor,
    isEngineBusy: Boolean,
    playerSetup: PlayerSetup,
): String =
    when {
        isEngineBusy -> "AI thinking"
        playerSetup.seatFor(nextPlayer).isHuman -> "Your turn: ${nextPlayer.label}"
        else -> "AI turn: ${nextPlayer.label}"
    }

internal fun modeSummary(
    playerSetup: PlayerSetup,
    engineName: String,
): String =
    when (playerSetup.matchMode()) {
        MatchMode.HumanVsAi,
        MatchMode.AiVsHuman,
        MatchMode.AiVsAi,
        -> "9x9 ${playerSetup.summary(engineName)}"

        MatchMode.LocalTwoPlayer -> "9x9 local two-player rules test"
    }

private data class SelectedAiMove(
    val move: Move,
    val summary: String,
)

private suspend fun EngineCoreApi.selectAiMoveFromAnalysis(
    currentState: GameState,
    aiPlayer: StoneColor,
    playLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings,
    searchMode: EngineSearchMode,
): SelectedAiMove? =
    runCatching {
        val baseLimit = playLevel.analysisLimitWith(searchTimeSettings)
        val analysis = analyze(baseLimit.fastCandidateAnalysis(baseLimit.candidateCount))
        val scoredCandidates = analysis.candidates
            .filter { candidate ->
                candidate.move.player == aiPlayer && candidate.pointLoss != null
            }

        val bestCandidate = scoredCandidates.firstOrNull()
        if (bestCandidate?.move is Move.Pass) {
            return@runCatching SelectedAiMove(
                move = bestCandidate.move,
                summary = buildString {
                    appendLine("AI level: ${playLevel.displayLabel}, endgame pass override.")
                    appendLine("AI selected pass because KataGo ranked pass as the best scored candidate.")
                    appendLine(analysis.summary)
                }.trim(),
            )
        }

        val scoredPlayCandidates = scoredCandidates
            .filter { candidate -> candidate.move is Move.Play }
        val range = playLevel.selectionPolicy.candidateIndexRange(scoredPlayCandidates.size)
            ?: return@runCatching null
        val pool = scoredPlayCandidates.slice(range)
        if (pool.isEmpty()) {
            return@runCatching null
        }

        val selected = pool[Random.nextInt(pool.size)]
        val selectedRank = scoredPlayCandidates.indexOf(selected) + 1
        SelectedAiMove(
            move = selected.move,
            summary = buildString {
                appendLine("AI level: ${playLevel.displayLabel}, ${playLevel.selectionPolicy.description}.")
                appendLine("Search mode: ${searchMode.label}.")
                appendLine("AI selected rank $selectedRank/${scoredPlayCandidates.size}: ${selected.describeForSelection(currentState)}.")
                appendLine(analysis.summary)
            }.trim(),
        )
    }.getOrNull()

private fun CandidateMove.describeForSelection(stateAfterHuman: GameState): String =
    buildString {
        append(move.describe(stateAfterHuman.boardSize))
        pointLoss?.let { append(" loss=${it.formatOneDecimal()}") }
        winRate?.let { append(" winRate=${(it * 100).toInt()}%") }
        scoreLead?.let { append(" scoreLead=${it.formatOneDecimal()}") }
        visits?.let { append(" visits=$it") }
    }

private fun Double.formatOneDecimal(): String =
    (kotlin.math.round(this * 10.0) / 10.0)
        .let { if (kotlin.math.abs(it) < 0.05) 0.0 else it }
        .toString()
