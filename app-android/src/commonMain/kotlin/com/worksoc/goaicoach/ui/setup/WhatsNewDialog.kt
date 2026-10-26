package com.worksoc.goaicoach.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.guide.GuideBlockingOverlays
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.WhatsNewRelease
import com.worksoc.goaicoach.ui.l10n.whatsNewTitleFor

/**
 * 「새로워진 점」 창(백로그 #257) — 홈 한 줄과 설정 › 앱 정보가 함께 연다. 묶음이 여럿이면(여러 버전을 건너뛴 업데이트 · 설정의 기록) 새것부터 버전마다 나눠 보인다.
 * 별도 윈도우(`AlertDialog`)라 화면의 뒤로가기에 지지 않는다.
 */
@Composable
internal fun WhatsNewDialog(releases: List<WhatsNewRelease>, onClose: () -> Unit) {
    val strings = LocalUiStrings.current
    val language = strings.language
    // 홈의 첫돌이 말풍선(③)이 이 창 아래에서 「봤음」으로 소진되지 않게 한다(함정 40 — 팝업 게이트는 GuideBlockingOverlays).
    GuideBlockingOverlays.TrackWhileShown()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(whatsNewTitleFor(language), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
            ) {
                releases.forEach { release ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space6)) {
                        Text(
                            text = release.versionName,
                            fontSize = AppTextSize.Text14,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        release.linesFor(language).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space6)) {
                                Text("•", fontSize = AppTextSize.Text14)
                                Text(line, fontSize = AppTextSize.Text14)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(strings.close) } },
    )
}
