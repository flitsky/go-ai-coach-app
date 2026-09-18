package com.worksoc.goaicoach.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.application.premium.PremiumProductInfo
import com.worksoc.goaicoach.application.premium.PurchaseOutcome
import kotlinx.coroutines.launch

/**
 * 구독 고지(가격·주기·자동갱신·해지 경로)를 **한 벌만 만들어 두 곳이 쓰게 하는** 파일(백로그 #159).
 *
 * ## 왜 한 벌인가
 * 고지가 필요한 자리가 둘이다 — **마이페이지의 구독 카드**(본거지)와 **대국 흐름의 업셀 팝업**(창구).
 * 두 곳에 각각 문장을 쓰면 언젠가 한쪽만 고쳐져 어긋나는데, **어긋난 고지는 고지가 아니라 위반**이다.
 * 그래서 [PremiumSubscriptionNoticeBlock] 하나를 두 곳이 그린다.
 *
 * ## ⚠️ 카드의 `구독하기`가 결제 시트로 **바로 가지 않는** 이유
 * 마이페이지 카드는 1.5줄이라 가격·주기가 들어갈 자리가 없다. 그런데 구글 정책은
 * **결제를 시작하는 그 자리에** 고지를 요구한다. 그래서 카드는 *결제 지점이 아니라 입구*가 되고,
 * 실제 결제는 고지를 갖춘 [PremiumSubscribeDialog]에서만 시작된다.
 * ⚠️ **카드에서 `purchasePremium()`을 직접 부르지 말 것** — 그 순간 고지 없는 결제 지점이 된다.
 *
 * ## ⚠️ `GoCoachApp.kt`에 상태를 두지 않는다
 * 그 파일은 상태 훅 예산이 42/42로 **여유가 없다**(`LayeringContractTest`). 상품 조회 상태는
 * [rememberPremiumProductInfo]가 자기 자리에서 든다 — `PremiumUpsellDialogHost`가
 * `isAdGrantInProgress`를 자체 `remember`로 소유하는 것과 같은 이유다.
 */
internal sealed interface PremiumProductInfoState {
    /** 아직 Play에 물어보는 중. 가격 자리에 *"불러오는 중"* 을 적는다. */
    data object Loading : PremiumProductInfoState

    data class Loaded(val info: PremiumProductInfo) : PremiumProductInfoState

    /**
     * 못 읽었다. ⚠️ **"상품이 없다"가 아니다** — 네트워크·Play 연결 문제도 여기로 온다.
     * 그래서 구독 버튼을 **잠그지 않는다**(실제 금액은 Play 결제 시트가 반드시 보여준다).
     */
    data object Unavailable : PremiumProductInfoState
}

/**
 * 구독 상품의 가격·주기를 Play에서 한 번 읽는다.
 *
 * ⚠️ **고지가 실제로 필요한 순간에만 부를 것**(다이얼로그가 떠 있을 때). 화면을 여는 것만으로
 * billing 연결을 맺으면 마이페이지를 열 때마다 값을 치른다 — 카드는 가격을 적지 않으므로
 * 조회할 이유가 없다.
 */
@Composable
internal fun rememberPremiumProductInfo(): PremiumProductInfoState {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PremiumProductInfoState>(PremiumProductInfoState.Loading) }
    LaunchedEffect(Unit) {
        val info = queryPremiumProductInfo(context)
        state = if (info != null) {
            PremiumProductInfoState.Loaded(info)
        } else {
            PremiumProductInfoState.Unavailable
        }
    }
    return state
}

/**
 * **구글 정책이 요구하는 네 가지**를 그린다 — 가격 · 결제 주기 · 자동 갱신 · 해지 경로.
 *
 * ⚠️ 네 가지 중 하나를 빼려면 정책부터 확인할 것. 특히 **가격과 주기는 한 줄로 함께** 간다
 * (`premiumSubscriptionPriceLineFor`) — 금액만 적고 주기를 빼면 위반이다.
 *
 * ⚠️ **고정 높이를 쓰지 않는다**(함정 9). 네 언어 중 영어가 가장 길고, 글꼴 배율 1.3에서
 * 세 줄이 네 줄이 된다 — 줄이 늘어도 잘리지 않아야 한다.
 */
@Composable
internal fun PremiumSubscriptionNoticeBlock(
    productInfo: PremiumProductInfoState,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = when (productInfo) {
                PremiumProductInfoState.Loading -> premiumSubscriptionPriceLoadingFor(strings.language)
                PremiumProductInfoState.Unavailable ->
                    premiumSubscriptionPriceUnavailableFor(strings.language)
                is PremiumProductInfoState.Loaded -> premiumSubscriptionPriceLineFor(
                    language = strings.language,
                    formattedPrice = productInfo.info.formattedPrice,
                    period = productInfo.info.period,
                )
            },
            fontWeight = FontWeight.Bold,
            color = PremiumGoldDeep,
        )
        Text(
            text = premiumSubscriptionAutoRenewNoticeFor(strings.language),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // ⚠️ **문자열을 이어 붙이지 않고 `Text`를 하나 더 둔다** — 마이페이지의 소실 고지가
        // 같은 이유로 문장을 끊었다(붙이면 한국어·영어는 공백 하나, 일본어·중국어는 공백 없이
        // 이어야 하는 함정이 생긴다).
        Text(
            text = premiumSubscriptionCancelNoticeFor(strings.language),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 마이페이지 최상단의 **1.5줄 구독 카드**(2026-09-18 사용자 확정).
 *
 * ## 왜 마이페이지가 본거지인가
 * **구독자에게는 업셀 팝업이 뜨지 않는다** — 그래서 해지 경로를 팝업에만 두면 정작 해지할
 * 사람이 못 본다. 여기가 구독의 본거지이고, 기능 게이팅 지점의 팝업은 창구다.
 *
 * ## ⚠️ 왜 최상단인가
 * 이 화면이 나열하는 것들(출석 도장판·1회권) 중 **유일하게 매달 돈이 나가는 항목**이고,
 * 해지 경로는 **찾기 어려우면 그 자체가 정책 문제**가 된다.
 * ⚠️ 그럼에도 **판매처가 되지 않게** 1.5줄로 묶었다 — 이 화면의 성격은 *"내가 모은 것"* 이다.
 *
 * ## ⚠️ 구독 중이면 플래그와 무관하게 보인다
 * 미구독 카드는 [FeatureFlags.isPurchaseEnabled]가 꺼져 있으면 팔 것이 없으니 숨기지만,
 * **이미 구독 중인 사람은 언제나 해지 경로에 닿아야 한다.** 플래그를 껐다 켜는 사이에
 * 구독자가 갇히지 않게 하는 것이 이 분기다.
 */
@Composable
internal fun PremiumSubscriptionCard(modifier: Modifier = Modifier) {
    val premium = LocalPremiumUiState.current
    val context = LocalContext.current
    val strings = LocalUiStrings.current
    var showSubscribeDialog by remember { mutableStateOf(false) }

    // `isPurchased`가 곧 "구독이 살아 있다"이다(#157·#158이 `PremiumSource.Purchase`의 뜻을
    // "영구 구매"에서 "구독 유효"로 옮겼다) — 광고 1시간(AdGrant)은 구독이 아니므로 여기 안 온다.
    val subscribed = premium.isPurchased
    if (!subscribed && !FeatureFlags.isPurchaseEnabled) return

    PremiumSubscriptionCardFrame(
        modifier = modifier,
        // 구독 중 카드는 카드 전체가 눌리지 않는다 — 해지는 실수로 눌릴 자리가 아니다.
        onCardClick = if (subscribed) null else ({ showSubscribeDialog = true }),
        label = if (subscribed) {
            premiumSubscriptionActiveLabelFor(strings.language)
        } else {
            premiumSubscriptionInactiveLabelFor(strings.language)
        },
        tagline = if (subscribed) {
            premiumSubscriptionActiveTaglineFor(strings.language)
        } else {
            premiumSubscriptionInactiveTaglineFor(strings.language)
        },
    ) {
        if (subscribed) {
            TextButton(
                onClick = { openSubscriptionManagement(context) },
                // 기본 여백(가로 24dp)이 좁은 폭에서 라벨 몫을 가져간다 — 해지 경로 문구를
                // 줄이지 않기 위해 여백 쪽을 줄인다.
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = premiumSubscriptionManageActionFor(strings.language),
                    color = PremiumGoldDeep,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        } else {
            Button(
                onClick = { showSubscribeDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                // 해지 버튼과 같은 이유로 기본 여백을 줄인다 — 영어 `Subscribe`는 한국어
                // `구독하기`보다 픽셀 폭이 넓어, 기본 여백이면 왼쪽 문구 몫을 그만큼 빼앗는다.
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = premiumSubscriptionSubscribeActionFor(strings.language),
                    fontSize = 13.sp,
                )
            }
        }
    }

    if (showSubscribeDialog) {
        PremiumSubscribeDialog(onDismiss = { showSubscribeDialog = false })
    }
}

/**
 * 카드의 껍데기 — `GameSetupLobby`의 프리미엄 카드와 **같은 금색 처리**를 쓴다
 * (배경 18% + 그라디언트 테두리 1.5dp). 톤이 갈리지 않도록 `PremiumTheme.kt`만 참조한다.
 *
 * ⚠️ **`maxLines = 1`은 1.5줄을 지키기 위한 것이다** — 네 언어 중 영어 부제가 가장 길어
 * 그대로 두면 좁은 폭에서 두 줄로 접히고 카드가 3줄이 된다. 문구를 늘릴 때 이 제약을 볼 것.
 */
@Composable
private fun PremiumSubscriptionCardFrame(
    label: String,
    tagline: String,
    onCardClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PremiumGoldLight.copy(alpha = 0.18f), PremiumCardShape)
            .border(1.5.dp, PremiumGoldGradient, PremiumCardShape)
            .then(if (onCardClick != null) Modifier.clickable(onClick = onCardClick) else Modifier)
            .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 👑은 로비의 구독자 표식과 같은 글리프다(`PremiumTheme.kt`의 금색 규칙 참고).
        Text(text = "👑", fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // ⚠️ **라벨은 잘리는 대신 접힌다**(함정 9). 2026-09-18 실기에서 배율 1.3의
            // *"프리미엄 구독 이용 중"* 이 말줄임으로 사라졌다 — 상태를 말하는 문구가 잘리면
            // 카드가 무엇을 말하는지 자체가 안 읽힌다. 문구를 짧게 고쳐 배율 1.0에서는 한 줄로
            // 들어가고(그래야 1.5줄이다), 큰 배율에서는 **카드가 자라는 쪽**을 택했다.
            Text(
                text = label,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = PremiumGoldDeep,
                maxLines = 2,
            )
            Text(
                text = tagline,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}

/**
 * 이 앱의 **구독 관리 화면**을 연다 — 해지가 실제로 일어나는 자리다.
 *
 * ⚠️ **두 겹으로 시도한다**(`openStoreListing`과 같은 형태). 첫 주소는 이 상품을 바로 펴고,
 * 실패하면 구독 목록으로 떨어진다. 둘 다 실패하는 기기(브라우저조차 없음)에서는
 * **조용히 아무 일도 없다** — 그래도 고지 문구가 경로를 말해 두었다.
 *
 * ⚠️ 패키지 이름은 `BuildConfig`가 아니라 [Context.getPackageName]에서 읽는다 — 이 앱은
 * `applicationId`와 `namespace`가 다르고, Play가 아는 것은 전자다.
 */
internal fun openSubscriptionManagement(context: Context) {
    val packageName = context.packageName
    val sku = BuildConfig.PREMIUM_PRODUCT_ID
    val attempts = listOf(
        "https://play.google.com/store/account/subscriptions?sku=$sku&package=$packageName",
        "https://play.google.com/store/account/subscriptions",
    )
    attempts.firstOrNull { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    }
}

/** 고지 블록 아래에 버튼 줄을 두는 공통 틀 — 다이얼로그 본문이 쓰는 최소 조각. */
@Composable
internal fun PremiumSubscriptionNoticeSection(
    productInfo: PremiumProductInfoState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumSubscriptionNoticeBlock(productInfo)
        content()
    }
}

/**
 * **고지를 갖춘 결제 지점**(백로그 #159). 마이페이지 카드의 `구독하기`가 여는 유일한 문이다.
 *
 * ## ⚠️ 이 한 단계를 없애지 말 것
 * 카드에서 곧바로 `purchasePremium()`을 부르면 **가격·주기·자동갱신을 보지 않은 채 결제가
 * 시작된다** — 구글 정책이 막는 바로 그 형태다. 카드가 1.5줄이기로 한 이상 고지는 여기 산다.
 *
 * ## 실패를 토스트로 알리지 않는 이유
 * 업셀 팝업과 같은 판단이다 — 다이얼로그가 떠 있는 동안 토스트는 가려지거나 놓치기 쉬워,
 * 인라인으로 적고 팝업을 **닫지 않아** 바로 재시도할 수 있게 한다.
 *
 * ⚠️ 진행 중에는 뒤로가기·바깥 탭으로 닫는 것도 막는다 — 여기서 닫으면 이 컴포저블의 코루틴
 * 스코프가 취소되어, 이미 화면에 떠 있는 **Play 결제 시트(별도 Activity)의 결과를 영영 반영하지
 * 못한다**(`PremiumUpsellDialogHost`가 같은 이유로 같은 것을 한다).
 */
@Composable
internal fun PremiumSubscribeDialog(onDismiss: () -> Unit) {
    val premium = LocalPremiumUiState.current
    val strings = LocalUiStrings.current
    val scope = rememberCoroutineScope()
    val productInfo = rememberPremiumProductInfo()
    var isPurchaseInProgress by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isPurchaseInProgress) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isPurchaseInProgress,
            dismissOnClickOutside = !isPurchaseInProgress,
        ),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = premiumSubscriptionInactiveLabelFor(strings.language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                PremiumSubscriptionNoticeBlock(productInfo)
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = {
                        errorMessage = null
                        isPurchaseInProgress = true
                        scope.launch {
                            val outcome = premium.purchasePremium()
                            isPurchaseInProgress = false
                            when (outcome) {
                                PurchaseOutcome.Purchased -> onDismiss()
                                // ⚠️ 위 업셀 팝업과 **같은 규칙**을 쓴다(#178) — 한쪽만 고치면
                                //   같은 사유에 다른 말을 하는 앱이 된다.
                                is PurchaseOutcome.NotPurchased ->
                                    errorMessage = purchaseFailureMessageFor(outcome, strings)
                            }
                        }
                    },
                    // ⚠️ **조회 실패로 버튼을 잠그지 않는다** — 가격을 못 읽은 것과 팔 수 없는 것은
                    // 다르고, 실제 금액은 Play 결제 시트가 반드시 보여준다.
                    enabled = !isPurchaseInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isPurchaseInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                    } else {
                        Text(premiumSubscriptionSubscribeActionFor(strings.language))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isPurchaseInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.cancel)
                }
            }
        }
    }
}
