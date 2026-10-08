package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting

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

    /**
     * **기력 측정 대국**(백로그 #219)의 사람 좌석 — 자기 기력과 같은 급수의 AI와 두고, 결과가 기력을 옮긴다.
     * 이 판에서는 형세 보기·추천 수·무르기를 끈다(재는 것은 스스로 둔 수여야 한다).
     * ⚠️ 이름이 곧 저장 형식이다(좌석 설정의 코덱이 enum 이름을 적는다) — 바꾸지 말 것. 이 값을 모르는 옛 빌드는 `Normal`로 읽는다.
     */
    RankMeasure("기력 측정"),
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
    val playLevel: PlayLevelSetting,
) {
    // ⚠️ **엔진 이름은 여기 없다**(백로그 #109). 예전에는 `AiEngineChoice.label`("KataGo")을
    // 앞에 붙였는데, 그 열거형은 **값이 하나뿐**이라 스텁으로 떨어져도 `KataGo`라고 말했다.
    // 이름은 실제로 뜬 엔진을 아는 쪽(`EngineIdentity`)에서 화면이 붙인다.
    val displayLabel: String = playLevel.displayLabel
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
        /**
         * **기본은 지연 없음**(2026-09-10 사용자 결정, 이전 값은 [Normal] 1초).
         *
         * ⚠️ **이미 쓰던 기기는 안 바뀐다** — `UserPreferencesSnapshot.autoPlayDelayMillis`가
         * 저장된 값을 그대로 읽으므로, 이 상수는 **신규 설치와 초기화 이후에만** 효력이 있다.
         * 되돌리려면 이 한 줄만 고치면 된다.
         *
         * ⚠️ 이 값이 보이는 곳은 **AI 대 AI 대국뿐**이었다(`SidePlayerSetup.isAutoPlay()`).
         * 2026-09-10에 그 조작 UI 자체를 개발자 섹션으로 옮겼다 — 일반 사용자에게는
         * *"AI가 자기들끼리 두는 속도"* 가 설정에 있을 이유가 없다는 판단이다.
         */
        val Default: AutoPlayDelaySetting = None

        fun fromMillis(millis: Long): AutoPlayDelaySetting =
            entries.firstOrNull { setting -> setting.millis == millis }
                ?: Default
    }
}

data class SidePlayerSetup(
    val controller: SeatController,
    val humanGameType: HumanGameType = HumanGameType.Normal,
    val playLevel: PlayLevelSetting = PlayLevelSetting(),
)

/**
 * 이 좌석 배치가 **기력 측정 대국**인가(백로그 #219) — 사람 좌석의 대국 종류가 말한다. 대국 중 도움(형세 보기·추천 수·무르기)을
 * 끄는 자리들이 이것 하나를 본다. 급수·기력은 6계층(`application.rankmeasure`)의 일이고, 여기는 "이 판이 그런 판인가"만 안다.
 */
fun PlayerSetup.isRankMeasure(): Boolean =
    listOf(black, white).any { side -> side.controller == SeatController.Human && side.humanGameType == HumanGameType.RankMeasure }

/**
 * 이 좌석 배치에 **그룹 기본보다 더 깊이 읽는 AI**(초고수)가 앉아 있는가(백로그 #215) — 그런 AI는 「최대 탐색 시간 제한」이
 * 길수록 더 세게 둔다. 설정 화면이 이것으로 안내 한 줄을 띄운다.
 */
fun PlayerSetup.hasDeepSearchingAi(): Boolean =
    listOf(black, white).any { side -> side.controller == SeatController.Ai && side.playLevel.searchesDeeperThanItsGroup }

/** 사람이 앉은 좌석이 있는가 — AI의 기권 제안처럼 **사람이 답해야 하는 일**은 사람이 있는 판에서만 묻는다(백로그 #213). */
fun PlayerSetup.hasHumanSeat(): Boolean =
    listOf(black, white).any { side -> side.controller == SeatController.Human }

fun SidePlayerSetup.aiCharacterProfile(): AiCharacterProfile? =
    if (controller == SeatController.Ai) {
        AiCharacterProfile(playLevel = playLevel)
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
                // 무르기 직후 기권·통과가 잠깐 꺼지는 것은 이 조건이다 — 의도된 동작(#104, `EngineOperationKind.isBlocking` 주석).
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
