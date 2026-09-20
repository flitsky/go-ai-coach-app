package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.premium.BillingPeriod
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 구독 고지 계약(백로그 #159).
 *
 * ## 왜 계약이 필요한가
 * 이 화면 조각들은 **구글 결제 정책이 요구하는 고지**다 — 가격 · 결제 주기 · 자동 갱신 · 해지 경로.
 * 정책 요구는 코드를 읽어서는 보이지 않고, 문구를 줄이거나 카드를 다듬는 평범한 작업에
 * 조용히 깎여 나간다. #87(앱이 안 하는 것을 문구가 약속했다)·함정 51(등재문이 앱보다 늦게 늙는다)이
 * 같은 경로로 라이브까지 나갔다.
 *
 * ## ⚠️ 여기 있는 문구는 `fun`이라 리플렉션 그물이 못 본다
 * `UiStringsTest`의 그물은 [UiStrings]의 **String 필드**만 훑는다(함정 10). 이 파일의 문구는
 * 언어별 `Map`을 함수가 꺼내 주는 형태라 그 그물 밖이다 — 그래서 **손 그물**을 여기 단다.
 */
class PremiumSubscriptionNoticeContractTest {

    private val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String): String = File(repoRoot, path).readText()

    private val noticeFile = "app-android/src/main/java/com/worksoc/goaicoach/ui/PremiumSubscriptionNotice.kt"
    private val upsellFile = "app-android/src/main/java/com/worksoc/goaicoach/ui/PremiumUiState.kt"
    private val myPageFile = "app-android/src/main/java/com/worksoc/goaicoach/ui/MyPageScreen.kt"

    private val labelGetters: Map<String, (UiLanguage) -> String> = mapOf(
        "premiumSubscriptionActiveLabel" to ::premiumSubscriptionActiveLabelFor,
        "premiumSubscriptionActiveTagline" to ::premiumSubscriptionActiveTaglineFor,
        "premiumSubscriptionInactiveLabel" to ::premiumSubscriptionInactiveLabelFor,
        "premiumSubscriptionInactiveTagline" to ::premiumSubscriptionInactiveTaglineFor,
        "premiumSubscriptionManageAction" to ::premiumSubscriptionManageActionFor,
        "premiumSubscriptionSubscribeAction" to ::premiumSubscriptionSubscribeActionFor,
        "premiumSubscriptionAutoRenewNotice" to ::premiumSubscriptionAutoRenewNoticeFor,
        "premiumSubscriptionCancelNotice" to ::premiumSubscriptionCancelNoticeFor,
        "premiumSubscriptionPriceLoading" to ::premiumSubscriptionPriceLoadingFor,
        "premiumSubscriptionPriceUnavailable" to ::premiumSubscriptionPriceUnavailableFor,
        "premiumSubscriptionBenefitsTitle" to ::premiumSubscriptionBenefitsTitleFor,
        "premiumSubscriptionBenefitRoster" to ::premiumSubscriptionBenefitRosterFor,
        "premiumSubscriptionBenefitFeatures" to ::premiumSubscriptionBenefitFeaturesFor,
        "premiumSubscriptionBenefitFeatureNames" to ::premiumSubscriptionBenefitFeatureNamesFor,
    )

    /**
     * ⚠️ 표(`Map`)에서 키가 하나라도 빠지면 `getValue`가 **던진다** — 그 언어 사용자는
     * 마이페이지를 아예 열 수 없다(마이페이지 인사가 같은 함정을 안고 있어 `UiStringsGuideTest`가
     * 같은 그물을 단다). 컴파일은 통과하므로 그물 없이는 실기 전까지 드러나지 않는다.
     */
    @Test
    fun everySubscriptionLabelExistsInEveryLanguage() {
        labelGetters.forEach { (name, getter) ->
            UiLanguage.entries.forEach { language ->
                val value = getter(language)
                assertTrue(
                    "$name 이 ${language.name}에서 비어 있다 — 표에 키가 없으면 `getValue`가 던진다(#159).",
                    value.isNotBlank(),
                )
            }
        }
    }

    /**
     * 번역 누락 그물(#31·#32와 같은 뜻). `Map`은 `copy()`가 아니라 직접 채우는 형태라 값이
     * **상속되지는 않지만**, 급할 때 한국어를 그대로 붙여 넣는 것은 여전히 쉽다.
     */
    @Test
    fun nonKoreanSubscriptionLabelsNeverKeepKoreanText() {
        val leaks = labelGetters.flatMap { (name, getter) ->
            UiLanguage.entries
                .filter { it != UiLanguage.Korean }
                .mapNotNull { language ->
                    getter(language).takeIf { it.containsHangul() }?.let { "$name(${language.name}) = $it" }
                }
        }
        assertEquals("번역되지 않은 구독 고지 문구가 있다(#159):\n" + leaks.joinToString("\n"), emptyList<String>(), leaks)
    }

    /**
     * ⚠️ **앱이 금액을 만지지 않는다.** `UiStrings`가 *"Play가 지역/통화별로 다른 값을 보여주는데
     * 앱이 하나를 적어 두면 어긋난다"* 며 금액을 문구에 쓰는 것을 금지해 뒀다(#18). 그래서
     * 가격 줄은 Play가 준 문자열을 **그대로** 품고 있어야 한다 — 다시 포맷하거나 자르면 안 된다.
     */
    @Test
    fun thePriceLineCarriesPlaysOwnFormattedPriceVerbatimInEveryLanguage() {
        val playPrice = "₩3,900"
        UiLanguage.entries.forEach { language ->
            BillingPeriod.entries.forEach { period ->
                val line = premiumSubscriptionPriceLineFor(language, playPrice, period)
                assertTrue(
                    "${language.name}/$period 의 가격 줄이 Play가 준 금액을 그대로 담지 않는다: $line",
                    line.contains(playPrice),
                )
            }
        }
    }

    /**
     * ⚠️ **모르는 주기를 "매월"이라고 말하지 않는다** — 거짓 고지다.
     *
     * `billingPeriodFromIso8601`이 [BillingPeriod.Unknown]을 남기는 이유가 여기 있다. 파서만
     * 조심하고 문구가 월로 접으면 아무 소용이 없어, 문구 쪽에도 같은 그물을 단다.
     */
    @Test
    fun anUnknownPeriodNeverClaimsAMonthlyCadence() {
        val monthlyWords = listOf("매월", "month", "毎月", "每月", "か月", "个月", "개월")
        UiLanguage.entries.forEach { language ->
            val line = premiumSubscriptionPriceLineFor(language, "₩3,900", BillingPeriod.Unknown)
            monthlyWords.forEach { word ->
                assertTrue(
                    "${language.name}의 Unknown 주기 문구가 \"$word\"로 주기를 주장한다: $line (#159)",
                    !line.contains(word),
                )
            }
        }
    }

    /**
     * ⚠️ **결제가 시작되는 모든 자리에 고지가 있어야 한다.**
     *
     * 지금 결제 지점은 둘이다 — 대국 흐름의 업셀 팝업(`PremiumUiState.kt`)과 마이페이지
     * 카드가 여는 확인 다이얼로그(`PremiumSubscriptionNotice.kt`). 셋째가 생기는 날에도
     * 이 테스트가 먼저 물어야 한다: *"그 자리에 고지가 있는가."*
     */
    @Test
    fun everyPurchaseEntryPointDrawsTheSharedNoticeBlock() {
        listOf(upsellFile, noticeFile).forEach { path ->
            assertTrue(
                "`$path`가 결제 버튼을 그리면서 `PremiumSubscriptionNoticeBlock`을 그리지 않는다 — " +
                    "구글 정책은 **결제를 시작하는 자리에** 가격·주기·자동갱신 고지를 요구한다(#159).",
                source(path).contains("PremiumSubscriptionNoticeBlock("),
            )
        }
    }

    /**
     * ⚠️ **마이페이지 카드는 결제 지점이 아니라 입구다.**
     *
     * 카드는 1.5줄이라 가격·주기가 들어갈 자리가 없다(2026-09-18 사용자 확정). 그런데 거기서
     * `purchasePremium()`을 직접 부르면 **고지 없는 결제 지점**이 된다 — 카드를 "한 번 탭으로
     * 끝나게" 다듬는 평범한 개선이 정확히 그 형태를 만든다. 그래서 그물을 단다.
     */
    @Test
    fun theMyPageCardNeverStartsAPurchaseWithoutTheNoticeDialog() {
        val myPage = source(myPageFile)
        assertTrue(
            "`MyPageScreen.kt`가 결제를 직접 시작한다 — 카드는 `PremiumSubscribeDialog`를 여는 " +
                "입구여야 한다(#159).",
            !myPage.contains("purchasePremium"),
        )
        assertTrue(
            "`MyPageScreen.kt`가 구독 카드를 그리지 않는다 — 구독자가 앱 안에서 해지 경로에 닿는 " +
                "길은 여기뿐이다(업셀 팝업은 구독 중에는 뜨지 않는다, #159).",
            myPage.contains("PremiumSubscriptionCard()"),
        )
    }

    /**
     * ⚠️ **해지 경로는 구독 중에도 닿을 수 있어야 한다.**
     *
     * 업셀 팝업은 프리미엄이 이미 활성이면 뜨지 않는다 — 그래서 해지 링크를 팝업에만 두면
     * 정작 해지할 사람이 못 본다. 카드의 구독 중 분기가 [openSubscriptionManagement]를
     * 부르는 것이 그 유일한 통로다.
     */
    @Test
    fun theSubscribedCardOffersTheManagementDeepLink() {
        val notice = source(noticeFile)
        assertTrue(
            "구독 중 카드가 `openSubscriptionManagement`를 부르지 않는다(#159).",
            notice.contains("openSubscriptionManagement(context)"),
        )
        assertTrue(
            "구독 관리 딥링크가 `context.packageName`을 쓰지 않는다 — 이 앱은 `applicationId`와 " +
                "`namespace`가 다르고 Play가 아는 것은 전자다(`openStoreListing`과 같은 이유).",
            notice.contains("context.packageName"),
        )
    }

    /**
     * ⚠️ **업셀 팝업의 결제 라벨이 "영구"를 주장하면 안 된다.**
     *
     * #157·#158이 프리미엄을 월간 구독으로 옮긴 뒤로 *"프리미엄 영구 활성화(결제)"* 는 거짓이었고,
     * 2026-09-18까지 네 언어에 그대로 남아 있었다(#159가 고쳤다). 되돌아오기 쉬운 문구라 그물을 단다.
     */
    @Test
    fun theUpsellPurchaseLabelNeverPromisesPermanentAccess() {
        val forbidden = mapOf(
            UiLanguage.Korean to listOf("영구"),
            UiLanguage.English to listOf("permanent", "forever", "lifetime"),
            UiLanguage.Japanese to listOf("永久"),
            UiLanguage.ChineseSimplified to listOf("永久"),
        )
        forbidden.forEach { (language, words) ->
            val label = UiStrings.forLanguage(language).premiumUpsellPurchaseOption
            words.forEach { word ->
                assertTrue(
                    "${language.name}의 결제 선택지가 \"$word\"로 영구 권한을 약속한다: $label — " +
                        "파는 것은 월간 구독이다(#159).",
                    !label.lowercase().contains(word.lowercase()),
                )
            }
        }
    }
}
