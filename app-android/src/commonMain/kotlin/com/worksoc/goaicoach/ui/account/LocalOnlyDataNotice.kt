package com.worksoc.goaicoach.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.localOnlyDataNoticePointerFor

/**
 * **「내 기록은 어떻게 보관되나요」** — 기록이 기기에 쌓인다는 고지(백로그 #74 ⓒ)의 본문. 창 하나에 **세 조각이 함께** 있다.
 *
 * ## 어디서 여는가(백로그 #241, 2026-10-09 사용자)
 * 본문은 **설정 하단**이 정본 자리다(`SettingsHelpLinks`) — 데이터·백업 안내를 찾는 곳이 거기다. 전에는 이 본문이
 * 마이 페이지 아래쪽에 통째로 펼쳐져 있었다. 그 자리를 고른 사유(*"잃게 되는 것들을 보는 바로 그 화면에서 읽어야 연결된다"*)는
 * 여전히 옳아서, 마이 페이지에는 **한 줄**([LocalOnlyDataNoticePointer])을 남겼다 — 눌러서 같은 창을 연다.
 *
 * ⚠️ **세 조각을 떼지 말 것** — 본문만 남거나 구독 복원 문장만 남으면 안내가 한쪽으로 기운다(`LocalOnlyDataNoticeContractTest`).
 * ⚠️ **문자열을 이어 붙이지 않고 `Text`를 하나 더 둔다.** 붙이면 한국어·영어는 앞에 공백 하나, 일본어·중국어는 공백 없이
 *   이어야 하는 함정이 생긴다. 조각별로 번역·검증이 독립되는 이점도 있다.
 * ⚠️ **구독 복원 문장은 모두에게 보인다** — 구독자에게는 안심이고, 아직 아닌 사람에게는 「구독하면 이것도 해결된다」는 정보다.
 *   상태로 가리면 후자가 영영 못 본다. 그 문장이 참인 것은 구독을 `SUBS`로 조회하기 때문이다(같은 계약 테스트가 지킨다).
 */
@Composable
internal fun LocalOnlyDataNoticeDialog(onClose: () -> Unit) {
    val strings = LocalUiStrings.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(text = strings.localOnlyDataNoticeTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                Text(text = strings.localOnlyDataNoticeBody)
                Text(text = strings.localOnlyDataNoticePaidRestoreLine)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(strings.close) } },
    )
}

/**
 * 마이 페이지의 **한 줄** — 위에 늘어선 것들(출석·캐릭터·1회권)이 이 기기에 쌓인다는 것만 말하고, 누르면 본문이 열린다.
 * 상태를 스스로 든다 — 창이 별도 윈도우(`AlertDialog`)라 화면의 배치에 참여하지 않는다.
 */
@Composable
internal fun LocalOnlyDataNoticePointer(modifier: Modifier = Modifier) {
    val strings = LocalUiStrings.current
    var showNotice by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showNotice = true }
            .padding(horizontal = AppSpacing.Space4, vertical = AppSpacing.Space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4),
    ) {
        Text(
            text = localOnlyDataNoticePointerFor(strings.language),
            fontSize = AppTextSize.Text13,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "›", fontSize = AppTextSize.Text16, color = MaterialTheme.colorScheme.primary)
    }
    if (showNotice) LocalOnlyDataNoticeDialog(onClose = { showNotice = false })
}
