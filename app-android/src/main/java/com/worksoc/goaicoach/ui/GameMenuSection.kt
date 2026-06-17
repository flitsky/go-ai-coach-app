package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.modeSummary
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent

@Composable
internal fun GameHeaderSection(
    screenState: GameScreenState,
    isDisplayMenuExpanded: Boolean,
    onDisplayMenuExpandedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Go AI Coach POC",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = modeSummary(screenState.playerSetup, screenState.engine.name),
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
        canStartNew = screenState.matchMode == MatchMode.LocalTwoPlayer || screenState.engine.isReady, // engine-busy gate disabled; restore with !screenState.engine.isBusy &&
        canChangeRuleset = true, // engine-busy gate disabled; restore with !screenState.engine.isBusy
        onNewGame = { onEvent(GameUiEvent.StartConfiguredGame) },
        onCopyLog = { onEvent(GameUiEvent.CopyDebugReport) },
        onBenchmark = { onEvent(GameUiEvent.ShowEngineBenchmark) },
        onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
    )

    KaTrainUxMenuPanel(
        options = screenState.uxOptions,
        onOptionsChange = { nextOptions -> onEvent(GameUiEvent.ChangeUxOptions(nextOptions)) },
    )
}
