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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.fillSummaryText
import com.worksoc.goaicoach.application.engine.rootSummaryText
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.shouldCollapseMenuAfterEvent
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
    onEvent: (GameUiEvent) -> Unit,
) {
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

    if (benchmarkProgress != null) {
        EngineBenchmarkProgressDialog(progress = benchmarkProgress)
    } else if (benchmarkResult != null) {
        EngineBenchmarkResultDialog(
            profile = benchmarkResult,
            onConfirm = onBenchmarkResultConfirmed,
            onRerun = onBenchmarkRerun,
        )
    }

    if (savedSessionToPrompt != null) {
        ResumeSavedSessionDialog(
            snapshot = savedSessionToPrompt,
            engineName = screenState.engine.name,
            onResume = { onEvent(GameUiEvent.ResumeSavedSession(savedSessionToPrompt)) },
            onDismiss = { onEvent(GameUiEvent.DismissResumePrompt) },
        )
    } else if (cacheOptimizationPrompt != null) {
        CacheOptimizationPromptDialog(
            title = cacheOptimizationPrompt.title,
            message = cacheOptimizationPrompt.message,
            onAccept = { onEvent(GameUiEvent.AcceptCacheOptimizationPrompt) },
            onDismiss = { onEvent(GameUiEvent.DismissCacheOptimizationPrompt) },
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
                onEvent = onMenuEvent,
            )
        }

        GamePlaySection(
            screenState = screenState,
            onScoreGraphExpandedChange = onScoreGraphExpandedChange,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun CacheOptimizationPromptDialog(
    title: String,
    message: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("분석하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        },
    )
}

@Composable
private fun EngineBenchmarkResultDialog(
    profile: EngineBenchmarkProfile,
    onConfirm: () -> Unit,
    onRerun: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("엔진 벤치마크 완료") },
        text = {
            Text(profile.toResultDialogText())
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onRerun) {
                Text("다시 체크해보기")
            }
        },
    )
}

@Composable
private fun EngineBenchmarkProgressDialog(progress: EngineBenchmarkProgress) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("엔진 벤치마크 진행 중") },
        text = {
            Column {
                Text("사용자 개입 없이 진행됩니다. 느린 기기에서는 1~3분 정도 소요될 수 있습니다.")
                Spacer(modifier = Modifier.height(12.dp))
                Text(progress.stageText)
                Text("${progress.sampleText} · ${progress.progressText}")
                progress.lastResultText?.let { text ->
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

private fun EngineBenchmarkProfile.toResultDialogText(): String =
    buildString {
        appendLine("측정 샘플: B16/B32/B64 각각 ${samplesPerVisit}회")
        appendLine("측정 상한: ${timeCapMs}ms")
        appendLine("측정 포지션: $benchmarkPositionName")
        appendLine("포지션 수순: ${benchmarkPositionMoves.ifEmpty { listOf("none") }.joinToString(", ")}")
        appendLine()
        metrics.sortedBy { metric -> metric.visits }.forEach { metric ->
            appendLine(
                "B${metric.visits}: min ${metric.minMs}ms / max ${metric.maxMs}ms / avg ${metric.avgMs}ms",
            )
            appendLine("  root ${metric.rootSummaryText()} / fill ${metric.fillSummaryText()}")
        }
        appendLine()
        appendLine("상세 sampleDetails는 메뉴의 Copy Log에 포함됩니다.")
    }.trim()

@Composable
private fun ResumeSavedSessionDialog(
    snapshot: SavedGameSnapshot,
    engineName: String,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이전 대국 이어하기") },
        text = {
            Text(
                text = buildString {
                    appendLine("진행 중이던 ${snapshot.gameState.moves.size}수 대국이 있습니다.")
                    appendLine("이어 진행하시겠습니까?")
                    appendLine()
                    append("마지막 수: ")
                    append(
                        snapshot.gameState.moves
                            .lastOrNull()
                            ?.describe(snapshot.gameState.boardSize)
                            ?: "None",
                    )
                    appendLine()
                    append(snapshot.playerSetup.summary(engineName))
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("예")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("아니오")
            }
        },
    )
}
