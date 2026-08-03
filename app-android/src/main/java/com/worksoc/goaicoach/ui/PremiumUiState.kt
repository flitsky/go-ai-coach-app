package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

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
 * "프리미엄 기능 활성화" 업셀 팝업. 3지선다: 영구 활성화(결제)/광고 시청 1시간/아니오.
 * 홈 화면 대국 시작 시와 인게임 중 잠긴 프리미엄 버튼 탭 시, 두 지점에서 공통으로 재사용한다.
 */
@Composable
internal fun PremiumUpsellDialog(
    onSelectPurchase: () -> Unit,
    onSelectAdGrant: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = strings.premiumUpsellTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = strings.premiumUpsellMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
                Button(
                    onClick = onSelectAdGrant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.premiumUpsellAdGrantOption)
                }
                OutlinedButton(
                    onClick = onSelectPurchase,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(strings.premiumUpsellPurchaseOption)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(strings.no)
                }
            }
        }
    }
}

/**
 * [PremiumUpsellDialog]의 3가지 선택지가 실제로 무엇을 하는지(결제는 아직 스텁 토스트,
 * 광고 시청은 즉시 활성화, 아니오는 그냥 닫기)를 한 곳에 모아, 3개 호출부
 * (`GoCoachHomeScreen.kt`/`GamePlaySection.kt`/`KaTrainUxPanels.kt`)가 각자 복붙하지 않게 한다.
 * 각 호출부는 `visible` 표시 여부만 로컬 상태로 들고, 닫힘 처리만 [onDismiss]로 넘기면 된다.
 * [onAnyChoice]는 홈 화면처럼 선택과 무관하게 항상 다음 화면으로 진행해야 하는 경우에만 쓴다.
 */
@Composable
internal fun PremiumUpsellDialogHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAnyChoice: () -> Unit = {},
) {
    if (!visible) return
    val premium = LocalPremiumUiState.current
    val context = LocalContext.current
    val strings = LocalUiStrings.current
    PremiumUpsellDialog(
        onSelectPurchase = {
            onDismiss()
            // Play Billing 연동은 premium-mode/README.md Step 4 대기 중 — 로그인 버튼과
            // 동일한 "준비 중" 스텁으로 처리해, 실제 결제 없이 영구 활성화가 부여되지 않게 한다.
            Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show()
            onAnyChoice()
        },
        onSelectAdGrant = {
            onDismiss()
            premium.activateForMatch()
            onAnyChoice()
        },
        onDismiss = {
            onDismiss()
            onAnyChoice()
        },
    )
}
