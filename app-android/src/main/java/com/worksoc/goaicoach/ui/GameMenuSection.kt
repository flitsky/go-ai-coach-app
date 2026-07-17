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
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent

private fun formatBuildTime(rawBuildTime: String): String {
    return try {
        val parts = rawBuildTime.split(" ")
        if (parts.size == 2) {
            val dateParts = parts[0].split("-")
            if (dateParts.size == 3) {
                val yy = dateParts[0].takeLast(2)
                val mm = dateParts[1]
                val dd = dateParts[2]
                val time = parts[1].replace(":", "")
                "v$yy$mm$dd.$time"
            } else {
                "v$rawBuildTime"
            }
        } else {
            "v$rawBuildTime"
        }
    } catch (e: Exception) {
        "v$rawBuildTime"
    }
}

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
        // [1] 좌측 끝: 빌드타임 [260717 15:33]
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = formatBuildTime(BuildConfig.BUILD_TIME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1
            )
        }

        // [2] 가운데 정렬: [흑 백 플레이어 정보]
        Box(
            modifier = Modifier.weight(2f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.setupSummary(screenState.playerSetup, screenState.engine.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
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
            enabled = true, // engine-busy gate disabled; restore with !screenState.engine.isBusy
            onSettingsChange = { settings -> onEvent(GameUiEvent.ChangeSearchTimeSettings(settings)) },
        )

        GameMenuActionsPanel(
            onCopyLog = { onEvent(GameUiEvent.CopyDebugReport) },
            onBenchmark = { onEvent(GameUiEvent.ShowEngineBenchmark) },
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
