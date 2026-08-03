package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.toResultSummary

@Composable
internal fun EngineBenchmarkResultDialog(
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
internal fun EngineBenchmarkProgressDialog(progress: EngineBenchmarkProgress) {
    val strings = LocalUiStrings.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(strings.benchmarkRunningTitle) },
        text = {
            Column {
                Text(strings.benchmarkRunningBody)
                Spacer(modifier = Modifier.height(12.dp))
                Text("${strings.benchmarkProgress}: ${progress.completedCalls} / ${progress.totalCalls}")
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
    toResultSummary().let { summary ->
        buildList {
            add(strings.benchmarkReadyMessage)
            add("${strings.recommendedMaximumSearchTime}: ${strings.searchTimeLimitLabel(summary.recommendedSearchTimeLimit)}")
            if (summary.isCautious) {
                add(strings.benchmarkCautiousMessage)
            }
        }.joinToString(separator = "\n")
    }
