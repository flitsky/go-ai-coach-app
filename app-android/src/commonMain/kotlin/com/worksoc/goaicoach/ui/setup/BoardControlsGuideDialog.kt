package com.worksoc.goaicoach.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.foundation.fullScreenDialogProperties
import com.worksoc.goaicoach.ui.guide.GuideBlockingOverlays
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide

/**
 * 「바둑판 조작법」을 **전체 화면**으로 연다(백로그 #249 ②) — 설정의 도움말 링크와 대국 설정의 받기 카드가 연다.
 * 내용은 엔진 준비 화면과 같은 [BoardControlsGuide] 하나다(가이드는 한 곳). 받기 진행 카드는 없다.
 */
@Composable
internal fun BoardControlsGuideDialog(onClose: () -> Unit) {
    GuideBlockingOverlays.TrackWhileShown()
    val language = LocalUiStrings.current.language
    Dialog(
        onDismissRequest = onClose,
        properties = fullScreenDialogProperties(),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(AppSpacing.Space20),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = UiStringsDownloadGuide.boardControlsGuide(language),
                        fontSize = AppTextSize.Text18,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text(UiStringsDownloadGuide.close(language)) }
                }
                Spacer(modifier = Modifier.height(AppSpacing.Space12))
                BoardControlsGuide(modifier = Modifier.weight(1f))
            }
        }
    }
}
