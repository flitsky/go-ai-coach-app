package com.worksoc.goaicoach.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.application.premium.state.PremiumSource
import com.worksoc.goaicoach.application.premium.state.SubscriptionFreshnessPolicy
import com.worksoc.goaicoach.persistence.PremiumStateStore
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDeveloperStaleSubscription
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState

/**
 * 개발자 테스트 2차의 **「72시간 지난 구독 만들기」**(백로그 #209 확인용 — 카드: *"시계를 당긴 디버그 확인으로 검증한다"*).
 *
 * 결제 경로는 에뮬레이터로 못 밟는다(함정 56) — 그래서 저장된 상태를 구독으로 두고 마지막 확인 시각만 72시간보다 앞으로 옮긴다.
 * 판정은 다음 포그라운드 복귀 때 `PremiumPurchaseRestoreEffect`가 한다(에뮬레이터는 Play 조회가 아무것도 정하지 못해 신선도 경로로 간다).
 * 대국 중에 누르고 앱을 배경에 보냈다 돌아오면 **그 판은 프리미엄 그대로**, 판을 끝내고 대국 화면을 떠나면 안내와 함께 내려야 한다.
 *
 * ⚠️ **권한을 무료로 만드는 컨트롤이라 2차(`BuildConfig.DEBUG`)에만 둔다** — 저장소에 구독을 쓴다(그 섹션의 규칙: *"무엇을 저장하는가로 정한다"*).
 * ⚠️ 상태를 갖지 않는다 — 그 섹션의 훅 예산은 여유 0이다(옆의 `DeveloperFreshInstallControl`과 같은 이유로 파일을 뗐다).
 */
@Composable
internal fun DeveloperStaleSubscriptionControl() {
    val context = LocalContext.current
    val language = LocalUiStrings.current.language
    val premium = LocalPremiumUiState.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = UiStringsDeveloperStaleSubscription.title(language), fontSize = AppTextSize.Text14, fontWeight = FontWeight.SemiBold)
            Text(
                text = UiStringsDeveloperStaleSubscription.subtitle(language),
                fontSize = AppTextSize.Text12,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        TextButton(
            onClick = {
                val store = PremiumStateStore(context)
                val staleAt = System.currentTimeMillis() - SubscriptionFreshnessPolicy.ExpireAfterMillis - StaleMarginMillis
                store.save(store.load().copy(source = PremiumSource.Purchase, lastSubscriptionVerifiedAtMillis = staleAt))
                // 화면의 사본을 되읽는다 — 안 그러면 셸이 든 옛 상태가 남는다(#65와 같은 함정).
                premium.reload()
                Toast.makeText(context, UiStringsDeveloperStaleSubscription.done(language), Toast.LENGTH_LONG).show()
            },
        ) { Text(UiStringsDeveloperStaleSubscription.action(language)) }
    }
}

/** 72시간 경계에 딱 걸치지 않게 한 시간 더 앞으로. */
private const val StaleMarginMillis: Long = 60L * 60L * 1000L
