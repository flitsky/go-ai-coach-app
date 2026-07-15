package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent

@Composable
internal fun GameHeaderSection(
    screenState: GameScreenState,
    isDisplayMenuExpanded: Boolean,
    onDisplayMenuExpandedChange: (Boolean) -> Unit,
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
                maxLines = 1,
            )

            Text(
                text = strings.setupSummary(screenState.playerSetup, screenState.engine.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
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
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    onEvent: (GameUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayerSetupPanel(
            state = screenState.playerSetupUi,
            enabled = true, // engine-busy gate disabled; restore with !screenState.engine.isBusy
            onPlayerSetupChange = { setup -> onEvent(GameUiEvent.ChangePlayerSetup(setup)) },
            onAutoPlayDelayChange = { setting -> onEvent(GameUiEvent.ChangeAutoPlayDelay(setting)) },
        )

        ScoringAndBoardSettingsPanel(
            ruleset = screenState.gameState.ruleset,
            boardSize = screenState.gameState.boardSize,
            handicapCount = screenState.handicapCount,
            canChangeRuleset = true,
            canChangeBoardSize = screenState.isGameEnded,
            canChangeHandicap = screenState.isGameEnded,
            onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
            onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) },
            onHandicapCountChange = { count -> onEvent(GameUiEvent.ChangeHandicapCount(count)) },
        )

        LanguageSettingsPanel(
            selectedLanguage = selectedLanguage,
            onLanguageChange = onLanguageChange,
        )

        KaTrainUxMenuPanel(
            options = screenState.uxOptions,
            onOptionsChange = { nextOptions -> onEvent(GameUiEvent.ChangeUxOptions(nextOptions)) },
        )

        GameMenuActionsPanel(
            onCopyLog = { onEvent(GameUiEvent.CopyDebugReport) },
            onBenchmark = { onEvent(GameUiEvent.ShowEngineBenchmark) },
        )

        SearchTimeSettingsPanel(
            settings = screenState.searchTimeSettings,
            enabled = true, // engine-busy gate disabled; restore with !screenState.engine.isBusy
            onSettingsChange = { settings -> onEvent(GameUiEvent.ChangeSearchTimeSettings(settings)) },
        )
    }
}

@Composable
private fun LanguageSettingsPanel(
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
            SettingDropdownRow(
                label = strings.languageLabel,
                selectedText = selectedLanguage.menuLabel,
                enabled = true,
                options = UiLanguage.entries,
                optionLabel = { language -> language.menuLabel },
                onSelected = onLanguageChange,
            )
        }
    }
}
