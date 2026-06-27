package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent

@Composable
internal fun GameHeaderSection(
    screenState: GameScreenState,
    isDisplayMenuExpanded: Boolean,
    onDisplayMenuExpandedChange: (Boolean) -> Unit,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.appTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = strings.setupSummary(screenState.playerSetup, screenState.engine.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = BuildConfig.BUILD_TIME,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            SetupDropdown(
                selectedText = selectedLanguage.menuLabel,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                options = UiLanguage.entries,
                optionLabel = { language -> language.menuLabel },
                onSelected = onLanguageChange,
            )

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
    onEvent: (GameUiEvent) -> Unit,
) {
    PlayerSetupPanel(
        state = screenState.playerSetupUi,
        enabled = true, // engine-busy gate disabled; restore with !screenState.engine.isBusy
        onPlayerSetupChange = { setup -> onEvent(GameUiEvent.ChangePlayerSetup(setup)) },
        onAutoPlayDelayChange = { setting -> onEvent(GameUiEvent.ChangeAutoPlayDelay(setting)) },
    )

    SearchTimeSettingsPanel(
        settings = screenState.searchTimeSettings,
        benchmarkAverages = screenState.searchTimeBenchmarkAverages,
        enabled = true, // engine-busy gate disabled; restore with !screenState.engine.isBusy
        onSettingsChange = { settings -> onEvent(GameUiEvent.ChangeSearchTimeSettings(settings)) },
    )

    GameMenuActionsPanel(
        mode = screenState.matchMode,
        ruleset = screenState.gameState.ruleset,
        boardSize = screenState.gameState.boardSize,
        canStartNew = screenState.matchMode == MatchMode.LocalTwoPlayer || screenState.engine.isReady, // engine-busy gate disabled; restore with !screenState.engine.isBusy &&
        canChangeRuleset = true, // engine-busy gate disabled; restore with !screenState.engine.isBusy
        onNewGame = { onEvent(GameUiEvent.StartConfiguredGame) },
        onCopyLog = { onEvent(GameUiEvent.CopyDebugReport) },
        onBenchmark = { onEvent(GameUiEvent.ShowEngineBenchmark) },
        onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
        onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) },
    )

    KaTrainUxMenuPanel(
        options = screenState.uxOptions,
        onOptionsChange = { nextOptions -> onEvent(GameUiEvent.ChangeUxOptions(nextOptions)) },
    )
}
