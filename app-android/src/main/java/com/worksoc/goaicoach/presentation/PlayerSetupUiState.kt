package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.shared.StoneColor

internal data class PlayerSetupUiState(
    val setup: PlayerSetup,
    val black: PlayerSetupSideUiState,
    val white: PlayerSetupSideUiState,
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

/**
 * ⚠️ **`autoPlayDelaySetting`/`showAutoPlayDelay`는 2026-09-10에 지웠다.** 'AI 착수 지연'이
 * 개발자 섹션(`DeveloperAutoPlayDelayControl`)으로 옮겨 가면서 이 상태를 읽는 곳이 하나도
 * 남지 않았다 — 값과 그 표시 조건을 여기 남겨 두면 **아무도 그리지 않는 상태**가 된다.
 * 개발자 섹션은 세션 설정(`GameScreenState.autoPlayDelaySetting`)에서 직접 읽는다.
 */
internal fun buildPlayerSetupUiState(
    setup: PlayerSetup,
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
