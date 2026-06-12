package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineCoreApi
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

internal data class PlayerSetup(
    val black: SidePlayerSetup = SidePlayerSetup(controller = SeatController.Human),
    val white: SidePlayerSetup = SidePlayerSetup(controller = SeatController.Ai),
) {
    fun sideFor(player: StoneColor): SidePlayerSetup =
        when (player) {
            StoneColor.Black -> black
            StoneColor.White -> white
        }

    fun updateSide(
        player: StoneColor,
        side: SidePlayerSetup,
    ): PlayerSetup =
        when (player) {
            StoneColor.Black -> copy(black = side)
            StoneColor.White -> copy(white = side)
        }

    fun matchMode(): MatchMode =
        when {
            black.controller == SeatController.Human && white.controller == SeatController.Ai -> MatchMode.HumanVsAi
            black.controller == SeatController.Ai && white.controller == SeatController.Human -> MatchMode.AiVsHuman
            black.controller == SeatController.Ai && white.controller == SeatController.Ai -> MatchMode.AiVsAi
            else -> MatchMode.LocalTwoPlayer
        }

    fun humanSeatCount(): Int =
        listOf(black, white).count { it.controller == SeatController.Human }

    fun isAutoPlay(): Boolean =
        matchMode() == MatchMode.AiVsAi

    fun summary(engineName: String): String =
        "Black: ${black.summary(engineName)} / White: ${white.summary(engineName)}"
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

    if (stateAfterHuman.isBoardFull()) {
        return TurnOutcome(
            gameState = stateAfterHuman,
            engineMessage = "${humanStatus.message}\nBoard is full.",
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
    )
    if (selectedAiMove != null) {
        val afterAi = runCatching { stateAfterHuman.play(selectedAiMove.move) }.getOrNull()
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
    val afterAi = stateAfterHuman.play(aiResult.move)
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
    isolateSearchCache: Boolean = false,
): TurnOutcome {
    if (isolateSearchCache) {
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
    )
    if (selectedAiMove != null) {
        val afterAi = runCatching { currentState.play(selectedAiMove.move) }.getOrNull()
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
    val afterAi = currentState.play(aiResult.move)
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
        playerSetup.sideFor(nextPlayer).controller == SeatController.Human &&
        (isEngineReady || playerSetup.matchMode() == MatchMode.LocalTwoPlayer)

internal fun turnStatus(
    nextPlayer: StoneColor,
    isEngineBusy: Boolean,
    playerSetup: PlayerSetup,
): String =
    when {
        isEngineBusy -> "AI thinking"
        playerSetup.sideFor(nextPlayer).controller == SeatController.Human -> "Your turn: ${nextPlayer.label}"
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
