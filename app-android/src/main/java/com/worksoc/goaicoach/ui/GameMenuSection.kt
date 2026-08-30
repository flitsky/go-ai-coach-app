package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.presentation.GameActionButtonRole
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.formatBuildTime



@Composable
internal fun GameHeaderSection(
    screenState: GameScreenState,
    isDisplayMenuExpanded: Boolean,
    onDisplayMenuExpandedChange: (Boolean) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // [1] 좌측 끝: 빌드타임 [260717 15:33] — 개발/QA용 정보라 시각적 비중은 낮춘다.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = formatBuildTime(BuildConfig.BUILD_TIME),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                maxLines = 1
            )
        }

        // [2] 가운데 정렬: [흑 백 플레이어 정보] — 엔진이 실제로 연산 중일 때 primary 색으로 강조한다.
        Box(
            modifier = Modifier.weight(2f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.setupSummary(screenState.playerSetup, screenState.engine.name),
                style = MaterialTheme.typography.bodyMedium,
                color = if (screenState.engine.isBusy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                // 색만으로 구분하면 색맹 사용자에게 안 보인다 — 굵기도 함께 바꿔 이중으로 신호한다.
                fontWeight = if (screenState.engine.isBusy) FontWeight.Bold else FontWeight.Normal,
                // 2줄이 아니라 4줄인 이유(#30): 이 칸은 화면 폭의 절반뿐인데(가중치 1:2:1),
                // `흑: 유저 / 백: KataGo 초고수`는 배율 2.0배에서 약 380dp라 3줄이 필요하다.
                // 2줄 상한이면 **상대가 누구인지가 통째로 사라진다** — 실기에서 일본어가
                // `黒: プレイヤー / 白:`에서 끊겨 AI 이름이 없어졌다. 엔진 이름은 길이 상한이
                // 없으므로 무제한으로 두지 않고 4줄에서 끊되, 잘렸다는 사실은 보이게 한다.
                //
                // 헤더가 커져도 안전하다 — 대국 화면 루트 Column에 `verticalScroll`이 있다
                // (`GoCoachContent.kt`).
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // [3] 우측 끝: [메뉴 버튼]
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            KaTrainUxMenuButton(
                menuExpanded = isDisplayMenuExpanded,
                onMenuExpandedChange = onDisplayMenuExpandedChange,
            )
        }
    }
}

@Composable
internal fun ExpandedGameMenuSection(
    screenState: GameScreenState,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    onEvent: (GameUiEvent) -> Unit,
    showSettings: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSettings) {
            PlayerSetupPanel(
                state = screenState.playerSetupUi,
                enabled = !screenState.engine.isBusy,
                onPlayerSetupChange = { setup -> onEvent(GameUiEvent.ChangePlayerSetup(setup)) },
                onAutoPlayDelayChange = { setting -> onEvent(GameUiEvent.ChangeAutoPlayDelay(setting)) },
            )

            ScoringAndBoardSettingsPanel(
                ruleset = screenState.gameState.ruleset,
                boardSize = screenState.gameState.boardSize,
                handicapCount = screenState.handicapCount,
                komi = screenState.gameState.komi,
                canChangeRuleset = true,
                canChangeBoardSize = screenState.isGameEnded,
                canChangeHandicap = screenState.isGameEnded,
                canChangeKomi = true,
                onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
                onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) },
                onHandicapCountChange = { count -> onEvent(GameUiEvent.ChangeHandicapCount(count)) },
                onKomiChange = { komi -> onEvent(GameUiEvent.ChangeKomi(komi)) },
            )
        }

        LanguageSettingsPanel(
            selectedLanguage = selectedLanguage,
            onLanguageChange = onLanguageChange,
        )

        KaTrainUxMenuPanel(
            options = screenState.uxOptions,
            onOptionsChange = { nextOptions -> onEvent(GameUiEvent.ChangeUxOptions(nextOptions)) },
            isTopMovesEveryMove = screenState.actionButtons
                .firstOrNull { it.role == GameActionButtonRole.TopMoves }?.isFilled == true,
            // 추천 수는 uxOptions가 아니라 세션 설정에 있어 전용 이벤트로 뒤집는다.
            onTopMovesEveryMoveChange = { onEvent(GameUiEvent.ToggleTopMoves) },
        )

        SearchTimeSettingsPanel(
            settings = screenState.searchTimeSettings,
            enabled = !screenState.engine.isBusy,
            onSettingsChange = { settings -> onEvent(GameUiEvent.ChangeSearchTimeSettings(settings)) },
        )

        GameMenuActionsPanel(
            onCopyLog = { onEvent(GameUiEvent.CopyDebugReport) },
            onBenchmark = { onEvent(GameUiEvent.ShowEngineBenchmark) },
        )
    }
}

@Composable
internal fun LanguageSettingsPanel(
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
) {
    val strings = LocalUiStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 나열형(`SettingChoiceRow`)이 아니라 드롭다운이다(#34) — 언어가 늘어도 한 줄이
            // 무너지지 않는다. 라벨과 컨트롤을 한 행에 놓아 다른 설정 항목과 결을 맞춘다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.languageLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LanguageDropdownChip(
                    selectedLanguage = selectedLanguage,
                    onLanguageChange = onLanguageChange,
                )
            }
        }
    }
}

/**
 * 언어 선택 드롭다운. **원래 홈 우상단 칩이었는데 설정 화면 안으로 들어왔다**(#34,
 * 2026-08-30 사용자 지시).
 *
 * 설정에 이미 언어 절이 있었지만 [SettingChoiceRow]로 **지원 언어를 전부 나열**하는 방식이라,
 * 언어가 늘수록 한 줄이 감당이 안 된다. 드롭다운은 항목이 몇 개든 칩 하나로 접힌다 —
 * 그래서 나열형을 버리고 이쪽을 남겼다.
 */
@Composable
internal fun LanguageDropdownChip(
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "🌐 ${selectedLanguage.menuLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            UiLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = lang.menuLabel,
                            fontWeight = if (lang == selectedLanguage) FontWeight.Bold else FontWeight.Normal,
                            color = if (lang == selectedLanguage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onLanguageChange(lang)
                    },
                )
            }
        }
    }
}
