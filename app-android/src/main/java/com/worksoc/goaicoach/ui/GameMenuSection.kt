package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.BuildConfig
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
                maxLines = 2,
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
            SettingChoiceRow(
                label = strings.languageLabel,
                options = UiLanguage.entries,
                selected = selectedLanguage,
                enabled = true,
                optionLabel = { language -> language.menuLabel },
                onSelected = onLanguageChange,
            )
        }
    }
}
