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
import com.worksoc.goaicoach.application.engine.operation.EngineOperationBlockReason
import com.worksoc.goaicoach.application.engine.toResultSummary

@Composable
internal fun EngineBenchmarkResultDialog(
    profile: EngineBenchmarkProfile,
    strings: UiStrings,
    onConfirm: () -> Unit,
    onRerun: () -> Unit,
) {
    // ⚠️ 이 팝업이 떠 있는 동안 첫돌이 가이드를 **기록하지 않는다** — 뒤에 깔린 채 "봤음"으로
    //   소진되는 것을 막는다(그 사유는 `GuideBlockingOverlays`의 KDoc).
    GuideBlockingOverlays.TrackWhileShown()
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
    // ⚠️ 이 팝업이 떠 있는 동안 첫돌이 가이드를 **기록하지 않는다** — 뒤에 깔린 채 "봤음"으로
    //   소진되는 것을 막는다(그 사유는 `GuideBlockingOverlays`의 KDoc).
    GuideBlockingOverlays.TrackWhileShown()
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

/**
 * 벤치마크가 사용자에게 말을 거는 **유일한 자리** — 막힘·진행·결과 셋을 한 곳에서 그린다.
 *
 * ## ⚠️ 왜 셸이 이걸 직접 부르는가 (2026-09-10)
 * 예전에는 진행·결과 팝업이 `GoCoachContent` 안, 즉 **`ScreenDestination.InGame` 가지 안에서만**
 * 그려졌다. 그런데 '엔진 성능 측정' 버튼은 **설정 화면의 개발자 섹션**으로 옮겨져 있었다.
 * 그래서 설정에서 누르면 **막혀도, 돌아가도, 끝나도, 실패해도** 화면이 아무 말을 하지 않았다 —
 * 사용자가 *"눌러도 아무 반응 없음"* 이라고 신고한 것이 이것이다.
 *
 * ⚠️ **이 호출을 다시 목적지 분기 안으로 넣지 말 것.** 버튼이 어느 화면에 있든 응답은 보여야
 * 하고, 그 둘을 같은 자리에 묶는 순간 버튼을 옮기는 사람이 이 결함을 다시 만든다.
 *
 * ⚠️ **막힘은 진행보다 뒤에 판정한다** — 게이트가 막았다면 진행이 있을 수 없고, 그 둘이
 * 동시에 참인 상태는 모델이 이미 막아 뒀다(`blockedBy`가 `progress`를 비운다).
 *
 * @return 이 오버레이가 지금 무언가를 띄우고 있으면 `true`. ⚠️ 호출부는 이때 **다른 팝업을
 *   띄우지 말 것** — Compose 다이얼로그는 각자 별도 윈도우라 **선언 순서로는 위아래가 정해지지
 *   않는다**(`EngineUnavailableNoticeDialog`가 실기에서 확인한 것).
 */
@Composable
internal fun EngineBenchmarkOverlays(
    progress: EngineBenchmarkProgress?,
    result: EngineBenchmarkProfile?,
    blockedReason: EngineOperationBlockReason?,
    onResultConfirmed: () -> Unit,
    onRerun: () -> Unit,
    onBlockedDismissed: () -> Unit,
): Boolean {
    val strings = LocalUiStrings.current
    return when {
        progress != null -> {
            EngineBenchmarkProgressDialog(progress = progress)
            true
        }

        result != null -> {
            EngineBenchmarkResultDialog(
                profile = result,
                strings = strings,
                onConfirm = onResultConfirmed,
                onRerun = onRerun,
            )
            true
        }

        blockedReason != null -> {
            EngineBenchmarkBlockedDialog(reason = blockedReason, onDismiss = onBlockedDismissed)
            true
        }

        else -> false
    }
}

/**
 * 벤치마크가 **왜** 지금 돌 수 없는지 알린다.
 *
 * ⚠️ **게이트의 영어 `message`를 그대로 띄우지 않는다** — 그건 진단 로그가 읽는 문장이다.
 * 여기서는 `reason`을 보고 4개 언어에서 고른다. 새 차단 사유를 더하면 `when`이 컴파일 에러로
 * 빠뜨린 번역을 잡아 준다(그래서 `else`를 두지 않는다).
 */
@Composable
private fun EngineBenchmarkBlockedDialog(
    reason: EngineOperationBlockReason,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val message = when (reason) {
        EngineOperationBlockReason.EngineNotReady -> strings.benchmarkBlockedEngineNotReady
        EngineOperationBlockReason.BenchmarkUnsupported -> strings.benchmarkBlockedUnsupported
        EngineOperationBlockReason.EngineBusy -> strings.benchmarkBlockedEngineBusy
    }
    // ⚠️ 이 팝업이 떠 있는 동안 첫돌이 가이드를 **기록하지 않는다** — 뒤에 깔린 채 "봤음"으로
    //   소진되는 것을 막는다(그 사유는 `GuideBlockingOverlays`의 KDoc).
    GuideBlockingOverlays.TrackWhileShown()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.benchmarkBlockedTitle) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        },
    )
}
