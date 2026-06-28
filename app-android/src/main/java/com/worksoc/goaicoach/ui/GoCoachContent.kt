package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.fillSummaryText
import com.worksoc.goaicoach.application.engine.rootSummaryText
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.shouldCollapseMenuAfterEvent
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.describe

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
    val savedSessionToPrompt = if (benchmarkProgress == null && benchmarkResult == null) {
        screenState.resumePrompt?.snapshot
    } else {
        null
    }
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

    if (savedSessionToPrompt != null) {
        ResumeSavedSessionDialog(
            snapshot = savedSessionToPrompt,
            engineName = screenState.engine.name,
            strings = strings,
            onResume = { onEvent(GameUiEvent.ResumeSavedSession(savedSessionToPrompt)) },
            onDismiss = { onEvent(GameUiEvent.DismissResumePrompt) },
        )
    } else if (cacheOptimizationPrompt != null) {
        CacheOptimizationPromptDialog(
            title = cacheOptimizationPrompt.title,
            message = cacheOptimizationPrompt.message,
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
            ExpandedGameMenuSection(
                screenState = screenState,
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                onEvent = onMenuEvent,
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

private fun FinalScoreJudgement.dialogKey(moveCount: Int): String =
    listOf(
        moveCount.toString(),
        resultText,
        blackLine.orEmpty(),
        whiteLine.orEmpty(),
        removedStonesLine,
        scoringRuleLine,
    ).joinToString("|")

@Composable
private fun FinalJudgementDialog(
    judgement: FinalScoreJudgement,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
    onNewGame: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.finalJudgementTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(judgement.resultText)
                Text(judgement.scoringRuleLine)
                Text(judgement.removedStonesLine)
                judgement.blackLine?.let { Text(it) }
                judgement.whiteLine?.let { Text(it) }
                judgement.note?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onReview) {
                Text(strings.reviewJudgement)
            }
        },
        dismissButton = {
            TextButton(onClick = onNewGame) {
                Text(strings.newGameAction)
            }
        },
    )
}

@Composable
private fun CacheOptimizationPromptDialog(
    title: String,
    message: String,
    strings: UiStrings,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(strings.analyze)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.later)
            }
        },
    )
}

@Composable
private fun EngineBenchmarkResultDialog(
    profile: EngineBenchmarkProfile,
    strings: UiStrings,
    onConfirm: () -> Unit,
    onRerun: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(strings.benchmarkDoneTitle) },
        text = {
            Text(profile.toResultDialogText(strings))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onRerun) {
                Text(strings.rerunBenchmark)
            }
        },
    )
}

@Composable
private fun EngineBenchmarkProgressDialog(progress: EngineBenchmarkProgress) {
    val strings = LocalUiStrings.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(strings.benchmarkRunningTitle) },
        text = {
            Column {
                Text(strings.benchmarkRunningBody)
                Spacer(modifier = Modifier.height(12.dp))
                Text(progress.localizedStageText(strings))
                Text("${progress.localizedSampleText(strings)} · ${progress.localizedProgressText(strings)}")
                progress.localizedLastResultText(strings)?.let { text ->
                    Text(text)
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
    )
}

private fun EngineBenchmarkProfile.toResultDialogText(strings: UiStrings): String =
    buildString {
        appendLine("${strings.benchmarkSamples}: B16/B32/B64 x $samplesPerVisit")
        appendLine("${strings.benchmarkTimeCap}: ${timeCapMs}ms")
        appendLine("${strings.benchmarkPosition}: $benchmarkPositionName")
        appendLine("${strings.benchmarkPositionMoves}: ${benchmarkPositionMoves.ifEmpty { listOf(strings.none) }.joinToString(", ")}")
        appendLine()
        metrics.sortedBy { metric -> metric.visits }.forEach { metric ->
            appendLine(
                "B${metric.visits}: min ${metric.minMs}ms / max ${metric.maxMs}ms / avg ${metric.avgMs}ms",
            )
            appendLine("  root ${metric.rootSummaryText()} / fill ${metric.fillSummaryText()}")
        }
        appendLine()
        appendLine(strings.benchmarkDetailsIncluded)
    }.trim()

private fun EngineBenchmarkProgress.localizedStageText(strings: UiStrings): String =
    if (stageOverride != null) {
        strings.benchmarkEngineSettling
    } else {
        "B$currentVisits ${strings.benchmarkSecuringRuntime}"
    }

private fun EngineBenchmarkProgress.localizedSampleText(strings: UiStrings): String =
    if (currentSample <= 0) {
        strings.benchmarkPreparing
    } else {
        "${strings.benchmarkSample} $currentSample / $samplesPerVisit"
    }

private fun EngineBenchmarkProgress.localizedProgressText(strings: UiStrings): String =
    "${strings.benchmarkTotalProgress} $completedCalls / $totalCalls"

private fun EngineBenchmarkProgress.localizedLastResultText(strings: UiStrings): String? =
    lastFillStatus?.let { fill ->
        "${strings.benchmarkLastResult} root=${lastRootVisits ?: strings.none}, elapsed=${lastElapsedMs ?: strings.none}ms, fill=$fill"
    }

@Composable
private fun ResumeSavedSessionDialog(
    snapshot: SavedGameSnapshot,
    engineName: String,
    strings: UiStrings,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.resumeTitle) },
        text = {
            Text(
                text = buildString {
                    appendLine("${strings.resumeMoveCountPrefix} ${snapshot.gameState.moves.size}${strings.resumeMoveCountSuffix}")
                    appendLine(strings.resumeQuestion)
                    appendLine()
                    append("${strings.lastMovePrefix}: ")
                    append(
                        snapshot.gameState.moves
                            .lastOrNull()
                            ?.describe(snapshot.gameState.boardSize)
                            ?: strings.none,
                    )
                    appendLine()
                    append(strings.setupSummary(snapshot.playerSetup, engineName))
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(strings.yes)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.no)
            }
        },
    )
}
