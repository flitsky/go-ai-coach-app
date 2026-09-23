package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.readContractSource
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **스토어 등재문의 결제 서술을 코드의 플래그와 묶는다**(백로그 #162, 함정 51).
 *
 * ## 왜 이 그물이 필요한가 — 이미 두 건이 라이브로 나갔다
 * 함정 51이 적은 그대로다: `AppNameContractTest`가 `store_listing.txt`를 읽지만 보는 것은
 * **앱 이름뿐**이고, **기능 문구를 보는 계약은 0건**이었다. 그래서 #143이 UX에서 지운 기능과
 * 실제와 다른 광고 서술이 등재문에 남은 채 출시됐다.
 *
 * ## 왜 하필 결제인가
 * 결제 서술은 **틀리면 정책 위반**이다(2026-09-16 거부가 가르친 것 — 함정 52). 그리고 이 축은
 * `FeatureFlags.isPurchaseEnabled` **한 값**으로 뒤집히므로, 그 값과 등재문을 묶으면 기계적으로 지켜진다.
 *
 * ⚠️ **줄 번호로 찾지 않는다** — 릴리스마다 밀린다(함정 51). 문장으로 찾는다.
 */
class StoreListingPaymentContractTest {

    private val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    /**
     * ⚠️ **주석·메모 영역을 뺀 「사용자에게 보이는 부분」만 본다.** 파일 아래쪽 「주의」 절은
     * 우리끼리 읽는 기록이라 옛 문장을 **인용**한다 — 그것까지 검사하면 기록을 남길 수 없다.
     * 경계는 `[출시 노트]`다(그 위까지가 등재정보 본문).
     */
    private val listingBody: String = File(repoRoot, "work/play-store-assets/store_listing.txt")
        .readContractSource()
        .substringBefore("[출시 노트]")

    /**
     * ⚠️ **결제를 켠 채 *"앱 내 결제는 없습니다"* 를 남겨 두면 그 자체가 허위 고지다.**
     * 반대로 결제를 끈 채 구독 문구를 남기면 **살 수 없는 것을 파는 것으로 보인다.**
     * 두 방향을 모두 막는다.
     */
    @Test
    fun theListingsPaymentClaimMatchesWhetherPurchaseIsActuallyEnabled() {
        val noPaymentClaims = listOf("앱 내 결제는 없습니다", "앱 내 결제 없음")
        val subscriptionWords = listOf("구독", "자동으로 갱신", "해지")

        if (FeatureFlags.isPurchaseEnabled) {
            noPaymentClaims.forEach { claim ->
                assertTrue(
                    "결제가 켜져 있는데 등재문이 아직 \"$claim\"이라고 말한다 — 허위 고지다(#162 · 함정 51).",
                    !listingBody.contains(claim),
                )
            }
            subscriptionWords.forEach { word ->
                assertTrue(
                    "결제가 켜져 있는데 등재문에 \"$word\"이(가) 없다 — 구글은 구독 상품에 " +
                        "**가격·주기·자동갱신·해지 경로**의 고지를 요구한다(#159가 앱 안에 넣은 것과 같은 축).",
                    listingBody.contains(word),
                )
            }
        } else {
            assertTrue(
                "결제가 꺼져 있는데 등재문이 구독을 판다고 말한다 — 살 수 없는 것을 파는 것으로 보인다.",
                noPaymentClaims.any { listingBody.contains(it) },
            )
        }
    }

    /**
     * ⚠️ **구독 가격은 등재문과 콘솔이 같아야 한다.**
     *
     * 앱 화면은 `ProductDetails` 런타임 조회라 콘솔과 어긋날 수 없지만(#159), **등재문은 손으로 쓴
     * 글자**라 얼마든지 어긋난다. 콘솔 정본은 활성 백로그 #150이고 값은 **3,900원**이다.
     * ⚠️ 가격을 바꾸는 날 이 테스트가 먼저 깨지게 두는 것이 목적이다 — 등재문을 잊지 않도록.
     */
    @Test
    fun theListingQuotesTheSamePriceAsTheConsoleProduct() {
        if (!FeatureFlags.isPurchaseEnabled) return
        assertTrue(
            "등재문의 구독 가격이 콘솔 상품(3,900원/월)과 다르거나 빠져 있다(#162).",
            listingBody.contains("3,900"),
        )
    }

    /**
     * ⚠️ **캐릭터 판매는 폐기됐다**(`FEATURE_ACCESS_PRINCIPLES.md` 8.2). 등재문이 캐릭터 가격을
     * 말하기 시작하면 그것은 폐기 결정이 조용히 뒤집혔다는 뜻이다 — 사유부터 뒤집어야 한다.
     */
    @Test
    fun theListingNeverSellsIndividualCharactersWhileThatModelIsDiscarded() {
        if (FeatureFlags.isBotCharacterPurchaseEnabled) return
        listOf("1,900", "2,900", "3,400", "4,900").forEach { price ->
            assertTrue(
                "등재문에 폐기된 캐릭터 판매 가격 \"$price\"이 적혀 있다 — 8.2의 사유부터 뒤집을 것.",
                !listingBody.contains(price),
            )
        }
    }
    /**
     * ⚠️ **「로그인 없이」를 판매 문구로 되돌리지 말 것**(백로그 #169, 2026-09-18 사용자 결정).
     *
     * 걷어낸 사유는 **문구가 거짓이어서가 아니다** — 로그인은 여전히 비활성이고 그 말은 지금도
     * 참이다. *"로그인 없이 즐길 수 있다"* 를 **장점으로 파는 것을 그만둔** 것이고, #170이
     * 로그인을 켜는 날 그 세 문장이 **정반대로 뒤집히는 사고**를 미리 막은 것이다.
     *
     * ⚠️ 그래서 이 그물은 *"지금 거짓인 말"* 이 아니라 **"켜는 날 거짓이 될 말"** 을 지킨다 —
     * 되돌리기 쉽고(문장 하나다), 되돌려도 **아무 테스트도 빨개지지 않던** 자리였다.
     * ⚠️ 검사 대상은 `[출시 노트]` 위의 **본문뿐**이다(위 `listingBody` 주석) — 그 아래 기록은
     * 옛 문장을 일부러 인용한다.
     */
    @Test
    fun theListingNoLongerSellsTheAbsenceOfSignIn() {
        listOf("로그인 없이", "로그인·회원가입 없이", "회원가입 없이").forEach { phrase ->
            assertTrue(
                "등재정보 본문에 \"$phrase\"가 판매 문구로 돌아왔다 — #170이 로그인을 켜는 날 " +
                    "정반대가 된다(백로그 #169). 로그인을 약속하는 문구는 **켠 뒤에** 넣을 것(함정 51).",
                !listingBody.contains(phrase),
            )
        }
    }

}
