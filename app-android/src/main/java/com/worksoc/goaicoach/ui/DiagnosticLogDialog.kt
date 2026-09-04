package com.worksoc.goaicoach.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import java.io.File

/**
 * 진단 이벤트 로그를 앱 안에서 **읽고 복사**하는 개발자 1차 도구(백로그 #79).
 *
 * ## 왜 필요한가
 * `DiagnosticEventLog`는 광고 실패 사유·엔진 판정·프리미엄 전이 같은 것을 계속 쌓고 있는데
 * **앱에서 볼 길이 없었다** — 실기에서 무슨 일이 있었는지 확인하려면 `adb logcat`이나 앱 저장소를
 * 직접 뒤져야 했다. 폰만 손에 있는 상황에서는 사실상 확인이 불가능하다.
 *
 * ## 왜 1차(release 포함)에 둬도 되는가
 * **아무것도 쓰지 않는다.** 권한을 만들지 않고 상태를 바꾸지 않으므로, 2단 분리의 기준
 * (*"그것이 무엇을 저장하는가"*, #77)에서 1차가 맞다.
 *
 * ⚠️ **개인정보가 실리지 않는 것을 확인하고 넣었다**(2026-09-04). 이벤트 `context`의 키는 전부
 * 기술적 값이고(`elapsedMillis`·`rootVisits`·`positionFingerprint`·`reason` 등) 기기 식별자나
 * 계정은 들어가지 않는다 — `DeviceIdentityStore`는 이 로그와 배선돼 있지 않다.
 * · ⚠️ 다만 `detail`·`error` 값은 **외부 SDK가 준 문장**을 그대로 싣는다. 공유하기 전에 눈으로
 *   훑는 것을 전제로 하고, **새 이벤트를 추가할 때 식별자를 `context`에 담지 말 것.**
 *
 * ## ⚠️ 화면을 새로 만들지 않았다
 * 목적지를 추가하면 `GoCoachApp.kt`의 `when` 분기가 늘고, 그 파일은 라인 예산 **880/880**으로
 * 여유가 정확히 0이다(함정 3번). 설정 화면 안의 다이얼로그로 두면 셸에 **0줄**이 든다.
 */
@Composable
internal fun DiagnosticLogDialog(
    context: Context,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    // ⚠️ 파일 읽기는 **한 번만** 한다 — 컴포지션마다 읽으면 스크롤할 때마다 디스크를 때린다.
    val tail = remember(context) { readDiagnosticLogTail(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        // 로그 한 줄이 길어 기본 다이얼로그 폭으로는 거의 읽히지 않는다.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        title = { Text(strings.settingsDevDiagnosticLogTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // ⚠️ 고정 높이가 아니라 **상한**이다. 스크롤 컨테이너는 높이가 묶여야 하지만,
                    // `heightIn(max = …)`은 글자를 자르지 않는다 — 넘치면 스크롤된다(함정 9번과
                    // 어긋나지 않는 이유).
                    .heightIn(max = LogViewMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = tail,
                    // ⚠️ 로그는 JSON 한 줄이 하나의 이벤트다 — 줄바꿈하면 경계가 사라져 읽기
                    // 어려워지므로 가로 스크롤로 두고 고정폭 글꼴을 쓴다.
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copyToClipboard(context, tail)
                    Toast.makeText(context, strings.settingsDevDiagnosticLogCopied, Toast.LENGTH_SHORT).show()
                },
            ) {
                Text(strings.settingsDevDiagnosticLogCopyAction)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

/**
 * 로그의 **뒤쪽 [MaxTailLines]줄만** 읽어 온다.
 *
 * ⚠️ **전부 싣지 않는 이유가 둘이다.** 이 로그는 1MB까지 자라고(`TrimmedAppendOnlyLog`),
 * ⓐ 그만한 문자열을 Compose `Text` 하나에 넣으면 측정에서 화면이 멈추며,
 * ⓑ **클립보드/Intent에 그 크기를 실으면 `TransactionTooLargeException`으로 조용히 실패한다**
 * (바인더 트랜잭션 한도가 1MB 근처다). 진단에 필요한 것은 언제나 **최근**이라 뒤쪽만 남긴다.
 */
private fun readDiagnosticLogTail(context: Context): String {
    val log = DiagnosticEventLog(File(context.applicationContext.filesDir, DiagnosticEventLog.FileName))
    val lines = log.readText().trim().lines()
    val tail = lines.takeLast(MaxTailLines)
    val omitted = lines.size - tail.size
    val header = if (omitted > 0) "… (앞부분 ${omitted}줄 생략)\n" else ""
    return header + tail.joinToString("\n")
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("go-ai-coach diagnostic log", text))
}

/** 뒤쪽 몇 줄까지 보여줄지. 위 KDoc의 두 이유로 상한이 필요하다. */
private const val MaxTailLines = 200

private val LogViewMaxHeight = 420.dp
