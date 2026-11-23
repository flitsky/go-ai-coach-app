package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration

/**
 * 앱의 **밑줄 글자 링크**는 전부 이것으로 그린다(QA-18, 2026-11-23) — 앱 정보 카드 · 라이선스 주소 · App Store 결제 줄.
 *
 * 링크마다 누르는 자리가 48dp다. 그래서 **링크 사이에 Spacer를 두지 않는다** — 글자 사이는 24dp가 된다(스레드가 정함, 48dp 규칙에서 나온 값).
 * 전에는 글자 한 줄(24dp) + Spacer 8dp라 링크 간격이 32dp였다.
 *
 * ⚠️ `clickable`이 바깥, 최소 크기가 안쪽이다 — 그래야 의미 노드 자체가 48dp가 되고, 접근성 검사기와 uiautomator가 그것을 잰다.
 * 한 링크만 키우면 이웃 링크의 넓힌 범위와 겹친다(앞선 시도 36dp).
 */
@Composable
internal fun AppTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        fontSize = AppTextSize.Text12,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = TextDecoration.Underline,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick).minimumInteractiveComponentSize(),
    )
}
