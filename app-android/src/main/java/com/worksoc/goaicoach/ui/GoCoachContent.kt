package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.analysis.JsonPositionAnalysisCacheOpeningInitialMoveCount
import com.worksoc.goaicoach.application.analysis.JsonPositionAnalysisCacheOpeningMaxMoveCount
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.shouldCollapseMenuAfterEvent

@Composable
internal fun GoCoachContent(
    screenState: GameScreenState,
    benchmarkProgress: EngineBenchmarkProgress?,
    benchmarkResult: EngineBenchmarkProfile?,
    onBenchmarkResultConfirmed: () -> Unit,
    onBenchmarkRerun: () -> Unit,
    isDisplayMenuExpanded: Boolean,
    onDisplayMenuExpandedChange: (Boolean) -> Unit,
    onScoreGraphExpandedChange: (Boolean) -> Unit,
    onFinalJudgementReview: () -> Unit,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    turnTimeState: GameSessionTurnTimeState,
    onEvent: (GameUiEvent) -> Unit,
) {
    val strings = LocalUiStrings.current
    val cacheOptimizationPrompt = if (benchmarkProgress == null && benchmarkResult == null) {
        screenState.cacheOptimizationPrompt
    } else {
        null
    }
    val onMenuEvent: (GameUiEvent) -> Unit = { event ->
        onEvent(event)
        if (shouldCollapseMenuAfterEvent(event)) {
            onDisplayMenuExpandedChange(false)
        }
    }
    var dismissedFinalJudgementKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(screenState.isGameEnded) {
        if (!screenState.isGameEnded) {
            dismissedFinalJudgementKey = null
        }
    }
    val finalJudgementKey = screenState.finalScoreJudgement?.dialogKey(screenState.gameState.moves.size)
    val finalJudgementToShow = screenState.finalScoreJudgement
        ?.takeIf { finalJudgementKey != null && dismissedFinalJudgementKey != finalJudgementKey }
    val dismissFinalJudgement = { dismissedFinalJudgementKey = finalJudgementKey }

    if (benchmarkProgress != null) {
        EngineBenchmarkProgressDialog(progress = benchmarkProgress)
    } else if (benchmarkResult != null) {
        EngineBenchmarkResultDialog(
            profile = benchmarkResult,
            strings = strings,
            onConfirm = onBenchmarkResultConfirmed,
            onRerun = onBenchmarkRerun,
        )
    }

    if (cacheOptimizationPrompt != null) {
        CacheOptimizationPromptDialog(
            title = strings.cacheOptTitle,
            message = strings.cacheOptBody(
                initialCount = JsonPositionAnalysisCacheOpeningInitialMoveCount,
                maxCount = JsonPositionAnalysisCacheOpeningMaxMoveCount,
                moveCount = cacheOptimizationPrompt.moveCount,
                targetCount = cacheOptimizationPrompt.targetCount,
            ),
            strings = strings,
            onAccept = { onEvent(GameUiEvent.AcceptCacheOptimizationPrompt) },
            onDismiss = { onEvent(GameUiEvent.DismissCacheOptimizationPrompt) },
        )
    }

    if (finalJudgementToShow != null && benchmarkProgress == null && benchmarkResult == null) {
        FinalJudgementDialog(
            judgement = finalJudgementToShow,
            strings = strings,
            onDismiss = dismissFinalJudgement,
            onReview = {
                onFinalJudgementReview()
                dismissFinalJudgement()
            },
            onNewGame = {
                dismissFinalJudgement()
                onEvent(GameUiEvent.StartConfiguredGame)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameHeaderSection(
            screenState = screenState,
            isDisplayMenuExpanded = isDisplayMenuExpanded,
            onDisplayMenuExpandedChange = onDisplayMenuExpandedChange,
        )

        if (isDisplayMenuExpanded) {
            AlertDialog(
                onDismissRequest = { onDisplayMenuExpandedChange(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.9f),
                title = {
                    Text(
                        text = strings.matchSetup,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpandedGameMenuSection(
                            screenState = screenState,
                            selectedLanguage = selectedLanguage,
                            onLanguageChange = onLanguageChange,
                            onEvent = onMenuEvent,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onDisplayMenuExpandedChange(false) }) {
                        Text(strings.close)
                    }
                }
            )
        }

        GamePlaySection(
            screenState = screenState,
            onScoreGraphExpandedChange = onScoreGraphExpandedChange,
            turnTimeState = turnTimeState,
            onEvent = onEvent,
        )
    }
}
