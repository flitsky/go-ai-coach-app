package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.application.premium.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.AdRewardOutcome
import kotlinx.coroutines.launch

/**
 * 화면 트리 전역에서 읽는 프리미엄 상태. [LocalUiStrings]와 동일한 방식으로
 * `CompositionLocalProvider`를 통해 공급되며, 개별 화면/버튼은 이 값만 읽으면 된다.
 *
 * [isActive]는 [isPurchased](영구)이거나 만료 전 광고 시청 부여(부여 시점부터 1시간,
 * [activateAdGrant]) 상태일 때 true다 — 실제 게이팅 판정은 항상 이 값을 쓴다. 광고 시청
 * 활성화는 특정 대국(매치)에 묶이지 않는다 — 그 1시간 동안 대국을 몇 판을 새로 시작하든
 * 시간이 남아 있으면 계속 유효하다. [isPurchased]는 그중 "영구 구매" 소스인지만 별도로
 * 구분한다(설정 화면의 개발자 테스트 토글 표시용).
 *
 * [activateAdGrant]는 실제 AdMob 리워드 광고를 로드/노출하고, 시청 완료(보상 획득) 콜백
 * 안에서만 프리미엄을 활성화한다(premium-mode/README.md Step 3) — 로드 실패/중도 이탈 시
 * 상태를 바꾸지 않고 [AdRewardOutcome.NotRewarded]를 반환하므로, 호출부([PremiumUpsellDialogHost])가
 * 그 경우 일반 모드 유지 안내를 띄운다. [setPurchased]는 여전히 실제 결제 없이 영구 활성화
 * 상태를 즉시 켜고 끄는 스텁이다(Step 4 대기 중) — "구매하기" 선택지와 설정 화면의 개발자
 * 테스트 토글이 이 함수 하나를 공유해서, 둘 중 어느 쪽에서 바꾸든 같은 전역 상태에 반영된다.
 *
 * [adGrantExpiresAtMillis]는 광고 시청 기반 활성화(AdGrant)일 때만 만료 시각(부여 시각 +
 * 1시간)을 담고, 영구 구매거나 비활성 상태면 `null`이다 — 화면에서 "남은 시간" 카운트다운을
 * 표시할지, 아니면(영구) 별도 표식을 쓸지 이 값의 null 여부만으로 분기할 수 있게 한다.
 * 실제 카운트다운(초 단위 재계산)은 이 값을 읽는 화면(예: [GameSetupLobby]) 쪽에서
 * 자체적으로 tick을 돌며 계산한다 — `GoCoachApp.kt`는 상태 훅 예산이 빠듯해 여기서는
 * 만료 시각만 그대로 넘겨준다.
 */
internal data class PremiumUiState(
    val isActive: Boolean = false,
    val isPurchased: Boolean = false,
    val adGrantExpiresAtMillis: Long? = null,
    val activateAdGrant: suspend () -> AdRewardOutcome = {
        AdRewardOutcome.NotRewarded(AdRewardFailureReason.Unavailable)
    },
    val setPurchased: (Boolean) -> Unit = {},
)

internal val LocalPremiumUiState = staticCompositionLocalOf { PremiumUiState() }

/**
 * "프리미엄 기능 활성화" 업셀 팝업. 3지선다: 영구 활성화(결제)/광고 시청 1시간/아니오.
 * 홈 화면 대국 시작 시와 인게임 중 잠긴 프리미엄 버튼 탭 시, 두 지점에서 공통으로 재사용한다.
 *
 * [isAdGrantInProgress]가 true인 동안(광고 로드~노출~시청 완료 판정까지)은 세 버튼을 모두
 * 비활성화하고 광고 시청 버튼 자리에 진행 표시를 보여준다 — 로드가 끝나기 전에 다른 선택지로
 * 바꾸거나 중복 탭하는 것을 막는다. 같은 이유로 뒤로가기/바깥 탭으로 닫는 것도 막는다 —
 * 이 시점에 닫으면 [PremiumUpsellDialogHost]의 코루틴 스코프가 취소되어 이미 화면에 떠 있는
 * 실제 광고(별도 Activity)의 시청 결과를 영영 반영하지 못하게 된다.
 */
@Composable
internal fun PremiumUpsellDialog(
    onSelectPurchase: () -> Unit,
    onSelectAdGrant: () -> Unit,
    onDismiss: () -> Unit,
    isAdGrantInProgress: Boolean = false,
) {
    val strings = LocalUiStrings.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isAdGrantInProgress,
            dismissOnClickOutside = !isAdGrantInProgress,
        ),
    ) {
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
                    enabled = !isAdGrantInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isAdGrantInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                    } else {
                        Text(strings.premiumUpsellAdGrantOption)
                    }
                }
                OutlinedButton(
                    onClick = onSelectPurchase,
                    enabled = !isAdGrantInProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(strings.premiumUpsellPurchaseOption)
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isAdGrantInProgress,
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
 * 광고 시청은 실제 AdMob 리워드 광고를 로드/노출하고 시청 완료 시에만 활성화, 아니오는
 * 그냥 닫기)를 한 곳에 모아, 3개 호출부(`GameSetupLobby.kt`/`GamePlaySection.kt`/
 * `KaTrainUxPanels.kt`)가 각자 복붙하지 않게 한다. 각 호출부는 `visible` 표시 여부만 로컬
 * 상태로 들고, 닫힘 처리만 [onDismiss]로 넘기면 된다. [onAnyChoice]는 홈 화면처럼 선택과
 * 무관하게 항상 다음 화면으로 진행해야 하는 경우에만 쓴다.
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
    val scope = rememberCoroutineScope()
    var isAdGrantInProgress by remember { mutableStateOf(false) }

    PremiumUpsellDialog(
        isAdGrantInProgress = isAdGrantInProgress,
        onSelectPurchase = {
            onDismiss()
            // Play Billing 연동은 premium-mode/README.md Step 4 대기 중 — 실제 결제 없이
            // 즉시 영구 활성화로 전환하는 스텁이다. 설정 화면의 개발자 테스트 토글과 같은
            // premium.setPurchased를 공유하므로, 여기서 켠 상태를 그 토글에서도 그대로 보고 끌 수 있다.
            premium.setPurchased(true)
            Toast.makeText(context, strings.premiumPurchaseStubActivatedMessage, Toast.LENGTH_SHORT).show()
            onAnyChoice()
        },
        onSelectAdGrant = {
            // 탭 즉시 활성화하지 않는다 — 광고 시청 완료(보상 획득) 콜백 안에서만
            // premium.activateAdGrant()가 실제로 상태를 바꾼다. 로드 실패/중도 이탈 시에는
            // 일반 모드를 유지한 채 안내만 하고 팝업은 닫지 않아, 바로 재시도하거나 다른
            // 선택지를 고를 수 있게 한다.
            isAdGrantInProgress = true
            scope.launch {
                val outcome = premium.activateAdGrant()
                isAdGrantInProgress = false
                when (outcome) {
                    AdRewardOutcome.RewardEarned -> {
                        onDismiss()
                        onAnyChoice()
                    }
                    is AdRewardOutcome.NotRewarded -> {
                        Toast.makeText(context, strings.premiumAdGrantFailedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onDismiss = {
            onDismiss()
            onAnyChoice()
        },
    )
}
