package com.worksoc.goaicoach.ui

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableSpendDecision
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.premium.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureAccessPolicy
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import com.worksoc.goaicoach.application.premium.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.runPremiumFeatureClaim
import com.worksoc.goaicoach.application.premium.buildPremiumDeactivatedDiagnosticEvent
import com.worksoc.goaicoach.application.premium.saveMergingClaimedFeatures
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
 * 그 경우 일반 모드 유지 안내를 띄운다. [purchasePremium]은 실제 Google Play Billing 구매
 * 플로우를 띄우고, 구매 완료(및 확인/acknowledge) 후에만 프리미엄을 영구 활성화한다
 * (premium-mode/README.md Step 4) — 취소/실패 시 상태를 바꾸지 않고 [PurchaseOutcome.NotPurchased]를
 * 반환한다. [setPurchased]는 실제 결제 없이 영구 활성화 상태를 즉시 켜고 끄는 QA 전용 스텁으로
 * 남겨둔다 — 설정 화면의 "개발자 테스트" 토글(`BuildConfig.DEBUG` 게이팅)이 이 함수를 쓴다.
 *
 * [adGrantExpiresAtMillis]는 광고 시청 기반 활성화(AdGrant)일 때만 만료 시각(부여 시각 +
 * 1시간)을 담고, 영구 구매거나 비활성 상태면 `null`이다 — 화면에서 "남은 시간" 카운트다운을
 * 표시할지, 아니면(영구) 별도 표식을 쓸지 이 값의 null 여부만으로 분기할 수 있게 한다.
 * 실제 카운트다운(초 단위 재계산)은 이 값을 읽는 화면(예: [GameSetupLobby]) 쪽에서
 * 자체적으로 tick을 돌며 계산한다 — `GoCoachApp.kt`는 상태 훅 예산이 빠듯해 여기서는
 * 만료 시각만 그대로 넘겨준다.
 *
 * [resolve]는 기능별 게이팅 판정이다(`application/premium/FeatureAccessPolicy.kt`, 6계층에
 * 위임) — 화면은 더 이상 `isActive`/클레임 여부를 직접 조합해 판정하지 않고, 이 함수 하나가
 * 돌려주는 [FeatureAccess]([FeatureAccess.Allowed]/[FeatureAccess.Locked])만 보고 분기한다.
 * ⚠️ **[claim]을 지금 부르는 UI는 하나도 없다**(백로그 #66, 2026-09-03). 유일한 호출부였던
 * 인게임 무르기 "영구 활성화" 팝업을 제거했기 때문이다 — 무르기의 영구 해금은 이제 **3일차 출석
 * 보상**뿐이고, 그쪽은 이 함수가 아니라 `runAttendanceRewardGrant`를 지나 같은
 * `runPremiumFeatureClaim`에 닿는다. **원장([PremiumState.claimedFeatures])은 그대로 살아 있고**
 * 판정도 그것을 읽으므로, 클레임 축이 필요한 기능이 다시 생기면 이 자리를 쓰면 된다.
 *
 * [claim]은 클레임 가능한 기능([FeatureId], 지금은 무르기뿐)을 영구 클레임 원장에 추가한다 —
 * 초도 발행 "무르기 무료 클레임" 그랜드파더링(launch-plan/README.md 3장)용으로, 한 번
 * 클레임하면 이후 그 기능의 기본 정책이 바뀌어도 계속 무료다.
 */
internal data class PremiumUiState(
    val isActive: Boolean = false,
    val isPurchased: Boolean = false,
    val adGrantExpiresAtMillis: Long? = null,
    val resolve: (FeatureId) -> FeatureAccess = { FeatureAccess.Locked(emptySet()) },
    val activateAdGrant: suspend () -> AdRewardOutcome = {
        AdRewardOutcome.NotRewarded(AdRewardFailureReason.Unavailable)
    },
    val purchasePremium: suspend () -> PurchaseOutcome = {
        PurchaseOutcome.NotPurchased(PurchaseFailureReason.Unavailable)
    },
    val setPurchased: (Boolean) -> Unit = {},
    val claim: (FeatureId) -> Unit = {},
)

internal val LocalPremiumUiState = staticCompositionLocalOf { PremiumUiState() }

/**
 * [PremiumUiState]의 배선 본체. `GoCoachApp.kt`는 라인/상태 훅 예산이 빠듯해
 * ([PremiumPurchaseGlue.kt]와 같은 이유) 이 조립을 여기로 뺐다 — 호출부는 현재
 * [premiumState]와 "바뀌면 이렇게 반영해 달라"는 [onStateChanged]만 넘긴다.
 *
 * 상태를 저장할 때 호출부가 메모리에 들고 있던 [PremiumState.claimedFeatures]를 그대로
 * 이어붙이지 않고 저장소에 남아 있는 집합과 합친다([saveMergingClaimedFeatures]) — **출석 Claim이
 * 이 화면 밖에서 원장에 쓴 것**을 이 화면의 저장이 지워버리지 않게 하기 위함이다.
 * 같은 이유로 [PremiumUiState.claim]도 UI에 규칙을 두지 않고 application 계층의
 * [runPremiumFeatureClaim]에 위임한다 — 출석 지급이 지나는 함수와 동일한 경로다.
 *
 * ⚠️ 예전에는 여기 *"출석 **1일차** 보상처럼 Compose 트리 밖(**Application 코루틴**)에서 지급된"*
 * 이라고 적혀 있었는데 **둘 다 사실이 아니게 됐다**(2026-09-03 정정, 백로그 #67): 회차는
 * **3일차**로 옮겨졌고(#55), 지급은 Application 코루틴이 아니라 **`AttendanceRewardClaimDialog`의
 * Claim**이 한다(#14로 자동 지급이 없어졌다). **합치는 이유 자체는 그대로 유효하다** — 그 다이얼로그도
 * 이 화면의 `premiumState`와는 별개로 저장소에 직접 쓰기 때문이다(백로그 #65가 그 반대 방향을 뚫었다).
 */
internal fun buildPremiumUiState(
    premiumState: PremiumState,
    store: PremiumStateStorePort,
    context: Context,
    diagnosticEventLog: DiagnosticEventLogPort,
    characterPerkActive: Boolean,
    onStateChanged: (PremiumState) -> Unit,
): PremiumUiState = PremiumUiState(
    // 재구성 시점의 현재 시각으로 매번 평가한다(별도 tick 없이, 대국 중 재구성이 충분히 잦아 문제없음).
    isActive = premiumState.isActive(nowMillis = System.currentTimeMillis()),
    isPurchased = premiumState.source == PremiumSource.Purchase,
    adGrantExpiresAtMillis = premiumState.adGrantStartedAtMillis?.plus(PremiumState.AdGrantDurationMillis),
    // 구매 특전(#18)을 **여기 한 곳에서** 접는다. 인게임 게이팅이 전부 이 람다를 지나므로
    // (`GamePlaySection`의 형세 보기·추천 수), 호출부를 하나도 고치지 않고 "지금 상대가 내가 산
    // 캐릭터면 열린다"가 성립한다 — 8.3절 1번이 걱정하던 "호출부 전부가 바뀐다"를 피한 자리다.
    resolve = { featureId ->
        FeatureAccessPolicy.resolve(featureId, premiumState, System.currentTimeMillis(), characterPerkActive)
    },
    setPurchased = { purchased ->
        onStateChanged(store.saveMergingClaimedFeatures(if (purchased) PremiumState.purchased() else PremiumState()))
    },
    purchasePremium = {
        val (outcome, nextState) = performPremiumPurchase(context, diagnosticEventLog)
        nextState?.let { onStateChanged(store.saveMergingClaimedFeatures(it)) }
        outcome
    },
    activateAdGrant = {
        val (outcome, nextState) = performPremiumAdGrant(context, diagnosticEventLog)
        nextState?.let { onStateChanged(store.saveMergingClaimedFeatures(it)) }
        outcome
    },
    claim = { featureId -> runPremiumFeatureClaim(featureId, store)?.let(onStateChanged) },
)

/**
 * "프리미엄 기능 활성화" 업셀 팝업. [FeatureFlags.isPurchaseEnabled]가 켜져 있으면 3지선다
 * (영구 활성화(결제)/광고 시청 1시간/아니오), 꺼져 있으면(2026-08-09 결정, 결제 없이 간결하게
 * 출시) 결제 버튼이 숨겨져 광고 시청 1시간/아니오 2지선다가 된다.
 * 홈 화면 대국 시작 시와 인게임 중 잠긴 프리미엄 버튼 탭 시, 두 지점에서 공통으로 재사용한다.
 *
 * [isAdGrantInProgress]/[isPurchaseInProgress] 중 하나라도 true인 동안(광고 로드~노출~시청
 * 완료 판정까지, 또는 구매 플로우 진행 중)은 세 버튼을 모두 비활성화하고 진행 중인 선택지
 * 자리에만 진행 표시를 보여준다 — 끝나기 전에 다른 선택지로 바꾸거나 중복 탭하는 것을 막는다.
 * 같은 이유로 뒤로가기/바깥 탭으로 닫는 것도 막는다 — 이 시점에 닫으면
 * [PremiumUpsellDialogHost]의 코루틴 스코프가 취소되어 이미 화면에 떠 있는 실제 광고/결제
 * 플로우(별도 Activity)의 결과를 영영 반영하지 못하게 된다.
 */
@Composable
internal fun PremiumUpsellDialog(
    onSelectPurchase: () -> Unit,
    onSelectAdGrant: () -> Unit,
    onDismiss: () -> Unit,
    isAdGrantInProgress: Boolean = false,
    isPurchaseInProgress: Boolean = false,
    errorMessage: String? = null,
    // '광고 스킵권'을 보유했을 때만 채워지는 네 번째 선택지 — 광고를 보지 않고 같은 1시간
    // 프리미엄을 켠다(킥오프 플랜 4.5절). 보유량이 0이면 null이라 버튼 자체가 뜨지 않는다.
    adSkipTicketCount: Int = 0,
    onSelectAdSkipTicket: (() -> Unit)? = null,
) {
    val strings = LocalUiStrings.current
    val isAnyInProgress = isAdGrantInProgress || isPurchaseInProgress
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isAnyInProgress,
            dismissOnClickOutside = !isAnyInProgress,
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
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (onSelectAdSkipTicket != null && adSkipTicketCount > 0) {
                    Button(
                        onClick = onSelectAdSkipTicket,
                        enabled = !isAnyInProgress,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Text(strings.premiumUpsellUseTicketLabel(adSkipTicketCount))
                    }
                }
                Button(
                    onClick = onSelectAdGrant,
                    enabled = !isAnyInProgress,
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
                if (FeatureFlags.isPurchaseEnabled) {
                    OutlinedButton(
                        onClick = onSelectPurchase,
                        enabled = !isAnyInProgress,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        if (isPurchaseInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        } else {
                            Text(strings.premiumUpsellPurchaseOption)
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isAnyInProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(strings.no)
                }
            }
        }
    }
}

/**
 * [PremiumUpsellDialog]의 3가지 선택지가 실제로 무엇을 하는지(결제는 실제 Google Play Billing
 * 구매 플로우를 띄우고 완료 시에만 활성화, 광고 시청은 실제 AdMob 리워드 광고를 로드/노출하고
 * 시청 완료 시에만 활성화, 아니오는 그냥 닫기)를 한 곳에 모아, 3개 호출부(`GameSetupLobby.kt`/
 * `GamePlaySection.kt`/`KaTrainUxPanels.kt`)가 각자 복붙하지 않게 한다. 각 호출부는 `visible`
 * 표시 여부만 로컬 상태로 들고, 닫힘 처리만 [onDismiss]로 넘기면 된다. [onAnyChoice]는 홈
 * 화면처럼 선택과 무관하게 항상 다음 화면으로 진행해야 하는 경우에만 쓴다.
 */
@Composable
internal fun PremiumUpsellDialogHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAnyChoice: () -> Unit = {},
) {
    if (!visible) return
    val premium = LocalPremiumUiState.current
    val consumables = LocalConsumableUiState.current
    val strings = LocalUiStrings.current
    val scope = rememberCoroutineScope()
    var isAdGrantInProgress by remember { mutableStateOf(false) }
    var isPurchaseInProgress by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    PremiumUpsellDialog(
        adSkipTicketCount = consumables.countOf(ConsumableCatalog.PremiumOnce),
        onSelectAdSkipTicket = {
            // 광고를 보지 않고 같은 1시간 프리미엄을 켠다 — 차감/활성화 판정은 6계층
            // (decideConsumableSpend)이 하고, 여기서는 결과에 따라 팝업만 닫는다.
            errorMessage = null
            if (consumables.spend(ConsumableCatalog.PremiumOnce) is ConsumableSpendDecision.Spent) {
                onDismiss()
                onAnyChoice()
            }
        },
        isAdGrantInProgress = isAdGrantInProgress,
        isPurchaseInProgress = isPurchaseInProgress,
        errorMessage = errorMessage,
        onSelectPurchase = {
            // 탭 즉시 활성화하지 않는다 — 실제 구매 플로우 완료(및 확인/acknowledge) 후에만
            // premium.purchasePremium()이 실제로 상태를 바꾼다(premium-mode/README.md Step 4).
            // 취소/실패 시에는 일반 모드를 유지한 채 다이얼로그 안에 인라인으로 안내하고
            // 팝업은 닫지 않아, 바로 재시도하거나 다른 선택지를 고를 수 있게 한다 — 토스트는
            // 다이얼로그가 떠 있는 동안 가려지거나 놓치기 쉬워 인라인 메시지로 대체했다
            // (activateAdGrant와 동일한 패턴).
            errorMessage = null
            isPurchaseInProgress = true
            scope.launch {
                val outcome = premium.purchasePremium()
                isPurchaseInProgress = false
                when (outcome) {
                    PurchaseOutcome.Purchased -> {
                        onDismiss()
                        onAnyChoice()
                    }
                    is PurchaseOutcome.NotPurchased -> {
                        errorMessage = strings.premiumPurchaseFailedMessage
                    }
                }
            }
        },
        onSelectAdGrant = {
            // 탭 즉시 활성화하지 않는다 — 광고 시청 완료(보상 획득) 콜백 안에서만
            // premium.activateAdGrant()가 실제로 상태를 바꾼다. 로드 실패/중도 이탈 시에는
            // 일반 모드를 유지한 채 다이얼로그 안에 인라인으로 안내하고 팝업은 닫지 않아,
            // 바로 재시도하거나 다른 선택지를 고를 수 있게 한다.
            errorMessage = null
            isAdGrantInProgress = true
            scope.launch {
                val outcome = premium.activateAdGrant()
                isAdGrantInProgress = false
                when (outcome) {
                    is AdRewardOutcome.RewardEarned -> {
                        onDismiss()
                        onAnyChoice()
                    }
                    is AdRewardOutcome.NotRewarded -> {
                        errorMessage = strings.premiumAdGrantFailedMessage
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

/**
 * 앱 시작 시 1회, 이미 소유 중인 구매가 있는지 Play에 조회해 로컬 상태를 복원한다(재설치 등
 * 이유로 로컬 저장소가 비어 있는 경우 대비 — premium-mode/README.md Step 4). `GoCoachApp.kt`가
 * 이 컴포저블을 호출하는 형태로 감싸는 이유는 [LaunchedEffect]를 이 파일에 남겨 `GoCoachApp.kt`의
 * 상태 훅 예산(47/47, 여유 없음)에 영향을 주지 않기 위함이다 — 위 [PremiumUpsellDialogHost]가
 * `isAdGrantInProgress`를 자체 `remember`로 소유하는 것과 같은 이유다. 소유 중인 구매가 없으면
 * (복원할 [PremiumState]가 없으면) [onRestored]는 호출되지 않는다 — 대부분의 사용자에게
 * 정상적인 기본 상태이고, 로컬에 이미 있던 영구 구매 상태를 이 조회 실패로 되돌리지도 않는다
 * (`runPremiumPurchaseApplication`의 보수적 설계).
 */
@Composable
internal fun PremiumPurchaseRestoreEffect(
    context: Context,
    diagnosticEventLog: DiagnosticEventLogPort,
    onRestored: (PremiumState) -> Unit,
) {
    if (!FeatureFlags.isPurchaseEnabled) return
    LaunchedEffect(Unit) {
        val (_, nextState) = performPremiumPurchaseRestore(context, diagnosticEventLog)
        nextState?.let(onRestored)
    }
}

/**
 * 프리미엄이 비활성이 되는 순간 형세 보기/추천 수의 "켜짐" 상태값 자체를 끈다 — 버튼만 잠기고
 * 기능은 이전 값대로 계속 동작하는 것을 막기 위함이다. 진단 로그 내용 자체는 순수 함수
 * ([buildPremiumDeactivatedDiagnosticEvent])가 판정한다.
 *
 * `GoCoachApp.kt`가 아니라 여기 사는 이유는 [buildPremiumUiState]·[PremiumPurchaseRestoreEffect]와
 * 같다 — 그 파일의 라인/상태 훅 예산이 빠듯하다.
 *
 * **키를 `isActive` 하나로 두지 않는 이유** (원래 `fix/premium-expiry-toggle-off` 브랜치의 진단):
 * 1. `isActive`는 컴포저블이 재구성될 때만 새로 평가되는데, 대국이 끝났거나 로비에 머무는 동안은
 *    만료 시각이 지나도 재구성을 유발하는 게 없어 감지가 임의로 늦어진다 — 그래서 만료 시각까지
 *    정확히 [delay]로 기다려 재구성 여부와 무관하게 정시에 감지한다.
 * 2. 만료 후 앱을 재시작해 저장된 대국을 복원하면 두 토글이 true로 되돌아올 수 있는데, 그 시점에
 *    `isActive`는 이미 false→false라 값이 안 바뀌어 재실행되지 않는 구멍이 있었다 — 두 토글값도
 *    키에 묶어 복원 순간에도 즉시 재교정한다.
 *
 * ⚠️ **[consumables]로 1회권을 반드시 걸러야 한다.** 위 2번 때문에 이 효과는 토글이 켜지는 순간에도
 * 돌고, 1회권 사용자는 정의상 프리미엄이 비활성이다 — 거르지 않으면 티켓을 차감하고 켠 표시를
 * 곧바로 되끄게 된다(재고만 닳고 아무것도 안 보임). 1회권으로 켠 표시를 끄는 책임은 이 효과가
 * 아니라 [OneShotAnalysisAutoClear](다음 수가 놓이면 해제)에 있다.
 */
@Composable
internal fun PremiumExpiryAutoDisableEffect(
    premiumState: PremiumState,
    isTopMovesEnabled: Boolean,
    isEvalEnabled: Boolean,
    consumables: ConsumableUiState,
    diagnosticEventLog: DiagnosticEventLogPort,
    characterPerkActive: Boolean,
    hideTopMoves: () -> Unit,
    hideEval: () -> Unit,
) {
    LaunchedEffect(premiumState, isTopMovesEnabled, isEvalEnabled, characterPerkActive) {
        // 구매 특전으로 켜져 있는 동안은 끄지 않는다(#18). 특전은 프리미엄과 별개 축이라
        // `premiumState.isActive`가 거짓이어도 성립하는데, 그걸 모르면 산 캐릭터와 두는 내내
        // 토글이 저 혼자 꺼진다. 상대가 바뀌어 특전이 사라지면 이 이펙트의 키가 바뀌면서
        // 다시 돌고, 그때 비로소 꺼진다 — 그것이 이 항목이 원하는 동작이다.
        if (characterPerkActive) return@LaunchedEffect
        val remainingMillis = premiumState.adGrantStartedAtMillis
            ?.takeIf { premiumState.source == PremiumSource.AdGrant }
            ?.plus(PremiumState.AdGrantDurationMillis)
            ?.minus(System.currentTimeMillis())
        if (remainingMillis != null && remainingMillis > 0) delay(remainingMillis)
        if (premiumState.isActive(System.currentTimeMillis())) return@LaunchedEffect

        var disabledAny = false
        if (isEvalEnabled && !consumables.isOneShotActive(FeatureId.Eval)) {
            hideEval()
            disabledAny = true
        }
        if (isTopMovesEnabled && !consumables.isOneShotActive(FeatureId.TopMoves)) {
            hideTopMoves()
            disabledAny = true
        }
        // 실제로 무언가를 껐을 때만 남긴다 — 위 2번 때문에 이 효과는 토글이 바뀔 때마다 도는데,
        // 만료된 상태로 계속 머무는 사용자에게 같은 로그를 반복해서 쌓지 않기 위함이다.
        if (disabledAny) {
            buildPremiumDeactivatedDiagnosticEvent(premiumState, System.currentTimeMillis())
                ?.let(diagnosticEventLog::append)
        }
    }
}
