package com.worksoc.goaicoach.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.ui.designsystem.ActionButton
import com.worksoc.goaicoach.ui.designsystem.AppDarkColorScheme
import com.worksoc.goaicoach.ui.designsystem.AppLightColorScheme
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.LocalAppDarkTheme
import com.worksoc.goaicoach.ui.designsystem.PremiumGold
import com.worksoc.goaicoach.ui.designsystem.RankTierBadge
import com.worksoc.goaicoach.ui.designsystem.StonePalette
import com.worksoc.goaicoach.ui.designsystem.ToggleActionButton

/** 카탈로그 「디자인 값」(W2) — 옮긴 색 · 크기 · 급수 배지 · 대국 버튼을 밝은 · 어두운 구성으로 함께 본다. */
@Composable
internal fun CatalogDesignTokens() {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        TokenPanel(dark = false)
        TokenPanel(dark = true)
    }
}

@Composable
private fun TokenPanel(dark: Boolean) {
    CompositionLocalProvider(LocalAppDarkTheme provides dark) {
        MaterialTheme(colorScheme = if (dark) AppDarkColorScheme else AppLightColorScheme) {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AppSpacing.Space16), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    Text(if (dark) "어두운 구성" else "밝은 구성", style = MaterialTheme.typography.titleMedium)
                    val scheme = MaterialTheme.colorScheme
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            scheme.primary, scheme.secondary, scheme.background, scheme.surface,
                            scheme.surfaceVariant, scheme.error, PremiumGold, StonePalette.Black, StonePalette.White,
                        ).forEach { color -> Swatch(color) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(KgsRank.kyu(20), KgsRank.kyu(15), KgsRank.kyu(10), KgsRank.kyu(5), KgsRank.kyu(1), KgsRank.dan(1), KgsRank.dan(5), KgsRank.dan(9))
                            .forEach { rank ->
                                RankTierBadge(rank = rank, label = if (rank.isDan) "${rank.number}단" else "${rank.number}급", modifier = Modifier.size(64.dp))
                            }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(label = "무르기", enabled = true, onClick = {}, mark = "무료 3", modifier = Modifier.weight(1f))
                        ToggleActionButton(isOn = true, label = "형세 보기", onClick = {}, modifier = Modifier.weight(1f))
                        ActionButton(label = "추천 수", enabled = true, onClick = {}, premiumLocked = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        modifier = Modifier.size(28.dp).background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}
