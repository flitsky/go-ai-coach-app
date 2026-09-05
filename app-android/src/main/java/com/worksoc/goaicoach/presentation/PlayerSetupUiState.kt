package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.shared.StoneColor

internal data class PlayerSetupUiState(
    val setup: PlayerSetup,
    val black: PlayerSetupSideUiState,
    val white: PlayerSetupSideUiState,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val showAutoPlayDelay: Boolean,
    val summaryText: String,
)

internal data class PlayerSetupSideUiState(
    val color: StoneColor,
    val seatLabel: String,
    val side: SidePlayerSetup,
    val controllerLabel: String,
    val humanGameTypeLabel: String,
    val aiLevelGroupLabel: String,
    val aiLevelLabel: String,
    val aiEngineLabel: String,
    val aiDetailText: String,
)

internal fun buildPlayerSetupUiState(
    setup: PlayerSetup,
    autoPlayDelaySetting: AutoPlayDelaySetting,
    engineName: String,
): PlayerSetupUiState =
    PlayerSetupUiState(
        setup = setup,
        black = buildPlayerSetupSideUiState(
            color = StoneColor.Black,
            side = setup.black,
            engineName = engineName,
        ),
        white = buildPlayerSetupSideUiState(
            color = StoneColor.White,
            side = setup.white,
            engineName = engineName,
        ),
        autoPlayDelaySetting = autoPlayDelaySetting,
        showAutoPlayDelay = setup.isAutoPlay(),
        summaryText = setup.summary(engineName),
    )

private fun buildPlayerSetupSideUiState(
    color: StoneColor,
    side: SidePlayerSetup,
    engineName: String,
): PlayerSetupSideUiState =
    PlayerSetupSideUiState(
        color = color,
        seatLabel = if (color == StoneColor.Black) "흑" else "백",
        side = side,
        controllerLabel = side.controller.label,
        humanGameTypeLabel = side.humanGameType.label,
        aiLevelGroupLabel = side.playLevel.group.label,
        aiLevelLabel = "${side.playLevel.safeLevel}단계",
        // ⚠️ 예전에는 `side.aiEngine.label.ifBlank { engineName }`이었는데, 그 라벨이 **절대
        // 비지 않아** 폴백이 죽은 코드였다(백로그 #109) — 스텁으로 떨어져도 `KataGo`라고 말했다.
        aiEngineLabel = engineName,
        aiDetailText = "${side.playLevel.group.difficulty.label} / ${side.playLevel.group.visits} visits",
    )
