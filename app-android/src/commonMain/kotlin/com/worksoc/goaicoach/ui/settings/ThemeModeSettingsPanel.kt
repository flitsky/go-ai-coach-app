package com.worksoc.goaicoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.worksoc.goaicoach.shared.policy.ThemeMode
import com.worksoc.goaicoach.ui.designsystem.AppElevation
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.themeModeLabelFor
import com.worksoc.goaicoach.ui.l10n.themeModeNightHoursHintFor
import com.worksoc.goaicoach.ui.l10n.themeModeTitleFor

/**
 * 설정 › 첫 절의 「화면 모드」(백로그 #272, 사용자 2026-10-20: 설정에서 고르기 — 시스템 따라가기 · 밝게 · 어둡게 · 밤 시간 자동, 기본은 시스템).
 * 글자 크기 바로 아래 — 같은 「보이는 모습」 성격이다. 저장소는 이 패널이 스스로 갖는다(설정 화면의 상태는 원장으로 묶여 있다, #102).
 * 선택지 넷은 [ThemeMode.entries] 그대로 — 갈래를 더하면 여기에 저절로 생긴다. 좁은 화면에서는 두 줄로 접힌다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeModeSettingsPanel(modifier: Modifier = Modifier) {
    val language = LocalUiStrings.current.language
    val platform = LocalAppPlatform.current
    val store = remember(platform) { platform.stores.themeMode }
    val selected by remember(store) { store.modeChanges() }.collectAsState(initial = remember(store) { store.load() })

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Corner8),
        tonalElevation = AppElevation.Level1,
        shadowElevation = AppElevation.Level0,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.Space12),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
        ) {
            Text(
                text = themeModeTitleFor(language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { store.save(mode) },
                        label = { Text(themeModeLabelFor(language, mode)) },
                    )
                }
            }
            if (selected == ThemeMode.NightHours) {
                Text(
                    text = themeModeNightHoursHintFor(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
