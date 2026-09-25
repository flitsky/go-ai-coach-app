package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.profile.UserNicknamePolicy

/**
 * 닉네임을 짓는 팝업(백로그 #165).
 *
 * ## ⚠️ 왜 화면 안 인라인이 아니라 [AlertDialog]인가
 * 이 저장소에 **`imePadding()` 사용이 0건이다.** 화면 안에 입력칸을 두면 키보드가 올라올 때
 * 그 칸을 가리는데, 지금 어느 화면도 그것을 다루고 있지 않다 — 팝업은 시스템이 알아서
 * 키보드 위로 띄워 준다. 이 선택은 게으름이 아니라 **없는 관용구를 이 조각에서 새로 만들지
 * 않겠다**는 것이다.
 *
 * ## ⚠️ 왜 `*Dialog.kt` 별도 파일인가
 * 화면 파일 안에 인라인으로 두면 `FirstDolGuideContractTest`의 **자동 그물 밖**이 된다 —
 * 팝업이 떠 있는 동안 첫돌이 가이드가 뒤에 깔린 채 "봤음"으로 **조용히 소진**되는 사고가
 * 그 그물이 막는 것이다(`GuideBlockingOverlays`).
 */
@Composable
internal fun UserNicknameDialog(
    initialNickname: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val strings = LocalUiStrings.current
    // ⚠️ 팝업이 떠 있는 동안 첫돌이 가이드를 **기록하지 않는다**(`FinalJudgementDialog`와 같은 이유).
    GuideBlockingOverlays.TrackWhileShown()

    var text by remember { mutableStateOf(initialNickname.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(nicknameDialogTitleFor(strings.language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    // ⚠️ **자르는 규칙을 화면이 따로 만들지 않는다** — 저장소가 부르는 것과 같은 규칙
                    // (`shared`의 `UserNicknamePolicy`, refactor backlog #85)을 쓴다. 둘이 다르면 13번째
                    // 글자가 보이다가 저장하면 사라지는 모양이 된다. 입력 중에는 걷지 않는다 — 그 이유는
                    // `UserNicknamePolicy` 머리말에 있다.
                    onValueChange = { next -> text = UserNicknamePolicy.capInput(next) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                )
                Text(
                    text = nicknameDialogHintFor(strings.language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}
