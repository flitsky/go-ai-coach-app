package com.worksoc.goaicoach.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize

/**
 * 설정 화면의 절 머리말(#256 「A 정리형」 → #261: 언어 및 글자 크기 · 대국 바둑판 / 착수 / AI 코치 설정 · 도움말 · 앱 정보, 사용자 2026-10-13).
 * 예전에는 「대국 설정」 하나만 머리말이 있고 나머지는 제목 없이 이어져, 성격이 다른 셋(앱 환경 · 대국 · 도움말과 앱 정보)이 한 덩어리로 읽혔다.
 */
@Composable
internal fun SettingsSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = AppTextSize.Text14,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier.padding(top = AppSpacing.Space8),
    )
}
