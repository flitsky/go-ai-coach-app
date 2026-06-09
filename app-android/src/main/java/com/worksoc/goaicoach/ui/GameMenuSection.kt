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

        KaTrainUxMenuButton(
            menuExpanded = isDisplayMenuExpanded,
            onMenuExpandedChange = onDisplayMenuExpandedChange,
        )
    }
}

@Composable
internal fun ExpandedGameMenuSection(
    screenState: GameScreenState,
    onEvent: (GameUiEvent) -> Unit,
) {
    PlayerSetupPanel(
        playerSetup = screenState.playerSetup,
        engineName = screenState.engine.name,
        enabled = !screenState.engine.isBusy,
        onPlayerSetupChange = { setup -> onEvent(GameUiEvent.ChangePlayerSetup(setup)) },
    )

    GameMenuActionsPanel(
        mode = screenState.matchMode,
        ruleset = screenState.gameState.ruleset,
        canStartNew = !screenState.engine.isBusy &&
            (screenState.matchMode == MatchMode.LocalTwoPlayer || screenState.engine.isReady),
        canChangeRuleset = !screenState.engine.isBusy,
        onNewGame = { onEvent(GameUiEvent.StartConfiguredGame) },
        onCopyLog = { onEvent(GameUiEvent.CopyDebugReport) },
        onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
    )

    KaTrainUxMenuPanel(
        options = screenState.uxOptions,
        onOptionsChange = { nextOptions -> onEvent(GameUiEvent.ChangeUxOptions(nextOptions)) },
    )
}
