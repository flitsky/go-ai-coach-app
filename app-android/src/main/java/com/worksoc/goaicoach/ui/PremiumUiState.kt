package com.worksoc.goaicoach.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight

/**
 * 화면 트리 전역에서 읽는 프리미엄 상태. [LocalUiStrings]와 동일한 방식으로
 * `CompositionLocalProvider`를 통해 공급되며, 개별 화면/버튼은 이 값만 읽으면 된다.
 *
 * [activateForMatch]는 현재는 실제 광고 시청 없이 즉시 활성화하는 스텁이다
 * (premium-mode/README.md Step 3에서 실제 리워드 광고로 교체 예정).
 */
internal data class PremiumUiState(
    val isActive: Boolean = false,
    val activateForMatch: () -> Unit = {},
)

internal val LocalPremiumUiState = staticCompositionLocalOf { PremiumUiState() }

/**
 * "프리미엄 기능 활성화(광고 시청)" 업셀 팝업. 홈 화면 대국 시작 시(Step 2)와
 * 인게임 중 잠긴 프리미엄 버튼 탭 시, 두 지점에서 공통으로 재사용한다.
 */
@Composable
internal fun PremiumUpsellDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.premiumUpsellTitle, fontWeight = FontWeight.Bold) },
        text = { Text(strings.premiumUpsellMessage) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.yes)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.no)
            }
        },
    )
}
