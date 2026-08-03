package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.StoneColor

val HumanPlayer = StoneColor.Black
val AiPlayer = StoneColor.White

enum class MatchMode(val label: String) {
    HumanVsAi("AI 대국"),
    AiVsHuman("AI 선공"),
    AiVsAi("AI 자동 대국"),
    LocalTwoPlayer("2인 대국"),
}

enum class SeatController(val label: String) {
    Human("Player"),
    Ai("AI"),
}

enum class HumanGameType(val label: String) {
    Normal("일반"),
    Teaching("티칭 모드"),
}

enum class AiEngineChoice(val label: String) {
    KataGo("KataGo"),
}

enum class SeatId(
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

data class AiCharacterProfile(
    val engine: AiEngineChoice,
    val playLevel: PlayLevelSetting,
) {
    val displayLabel: String = "${engine.label} ${playLevel.displayLabel}"
    val selectionDescription: String = playLevel.selectionPolicy.description
}

data class SeatAssignment(
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

data class MatchSeatRuntimeState(
    val assignment: SeatAssignment,
    val isCurrentTurn: Boolean,
    val canAcceptBoardInput: Boolean,
) {
    val id: SeatId = assignment.id
    val player: StoneColor = assignment.player
    val isHuman: Boolean = assignment.isHuman
    val isAi: Boolean = assignment.isAi
    val aiCharacter: AiCharacterProfile? = assignment.aiCharacter
}

data class MatchSeatSnapshot(
    val mode: MatchMode,
    val black: MatchSeatRuntimeState,
    val white: MatchSeatRuntimeState,
) {
    val current: MatchSeatRuntimeState =
        if (black.isCurrentTurn) black else white

    val isAutoPlay: Boolean =
        black.isAi && white.isAi

    fun seat(id: SeatId): MatchSeatRuntimeState =
        when (id) {
            SeatId.Black -> black
            SeatId.White -> white
        }
}

fun MatchSeatSnapshot.turnStatusText(isEngineBlockingBusy: Boolean): String =
    when {
        isEngineBlockingBusy -> "AI thinking"
        current.isHuman -> "Your turn: ${current.player.label}"
        else -> "AI turn: ${current.player.label}"
    }

enum class AutoPlayDelaySetting(
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

data class SidePlayerSetup(
    val controller: SeatController,
    val humanGameType: HumanGameType = HumanGameType.Normal,
    val aiEngine: AiEngineChoice = AiEngineChoice.KataGo,
    val playLevel: PlayLevelSetting = PlayLevelSetting(),
)

fun SidePlayerSetup.aiCharacterProfile(): AiCharacterProfile? =
    if (controller == SeatController.Ai) {
        AiCharacterProfile(
            engine = aiEngine,
            playLevel = playLevel,
        )
    } else {
        null
    }

data class PlayerSetup(
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

    fun seatSnapshot(
        nextPlayer: StoneColor,
        isEngineReady: Boolean,
        isEngineBlockingBusy: Boolean,
    ): MatchSeatSnapshot {
        val mode = matchMode()
        fun runtimeState(id: SeatId): MatchSeatRuntimeState {
            val assignment = seat(id)
            val isCurrentTurn = assignment.player == nextPlayer
            return MatchSeatRuntimeState(
                assignment = assignment,
                isCurrentTurn = isCurrentTurn,
                canAcceptBoardInput = !isEngineBlockingBusy &&
                    isCurrentTurn &&
                    assignment.isHuman &&
                    (isEngineReady || mode == MatchMode.LocalTwoPlayer),
            )
        }

        return MatchSeatSnapshot(
            mode = mode,
            black = runtimeState(SeatId.Black),
            white = runtimeState(SeatId.White),
        )
    }
}

fun SidePlayerSetup.summary(engineName: String): String =
    when (controller) {
        SeatController.Human -> "${controller.label} ${humanGameType.label}"
        SeatController.Ai -> "$engineName ${playLevel.displayLabel}"
    }
