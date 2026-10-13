package com.worksoc.goaicoach.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.collapseLabelFor
import com.worksoc.goaicoach.ui.l10n.expandLabelFor

/**
 * 설정 화면의 절 머리말(#256 — 사용자 2026-10-13 「A 정리형」: 화면 · 대국 · 도움말 · 앱 정보).
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

/**
 * 접어 둔 묶음(#256) — 줄을 누르면 펼친다. 대국 화면 표시 토글 13개가 설정의 세로 절반을 차지해서 접는다.
 *
 * ⚠️ 펼침 상태는 **이 컴포저블이 든다** — `SettingsScreen.kt`의 상태는 원장(`architecture-budgets.json`)으로 묶여 있다.
 * `rememberSaveable`이라 라이선스 하위 화면에 다녀오거나 화면을 돌려도 펼친 채로 남는다. 저장소에는 쓰지 않는다(다음 실행엔 접힌다).
 */
@Composable
internal fun CollapsibleSettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val language = LocalUiStrings.current.language
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.Corner12))
                .clickable { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = AppTextSize.Text14,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) collapseLabelFor(language) else expandLabelFor(language),
                    fontSize = AppTextSize.Text13,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    // 바로 옆 글자(펼치기/접기)가 같은 것을 말한다.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (expanded) content()
    }
}
