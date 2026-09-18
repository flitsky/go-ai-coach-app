package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 구독 강등의 **배선**(백로그 #158).
 *
 * ## ⚠️ 왜 계약 테스트인가 — 빠뜨려도 전부 초록이다
 * `currentState`는 **기본값이 있는 파라미터**다. 안 넘기면 `PremiumState()`(=`None`)가 들어가고,
 * 도메인은 *"내릴 것이 없다"* 로 정확히 판정한다 — **순수 함수 테스트는 초록**인 채
 * **해지한 구독이 영원히 살아 있는다.** 기본값이 있는 인자는 이런 식으로만 사라진다.
 */
class SubscriptionWiringContractTest {

    private fun source(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private val app = source("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
    private val glue = source("src/main/java/com/worksoc/goaicoach/ui/PremiumPurchaseGlue.kt")
    private val premium = source("src/main/java/com/worksoc/goaicoach/ui/PremiumUiState.kt")

    /** 복원 조회가 현재 상태를 모르면 강등이 **통째로 죽는다**. */
    @Test
    fun theRestoreEffectReceivesTheCurrentPremiumState() {
        assertTrue(
            "복원 효과에 현재 상태를 안 넘긴다 — 해지한 구독이 영원히 살아 있는다(#158).",
            app.contains("PremiumPurchaseRestoreEffect(context, diagnosticEventLog, premiumState)"),
        )
        assertTrue(
            "복원 효과가 받은 상태를 조회에 흘리지 않는다(#158).",
            premium.contains("performPremiumPurchaseRestore(context, diagnosticEventLog, currentState)"),
        )
        assertTrue(
            "조회가 상태를 판정 함수까지 넘기지 않는다(#158).",
            glue.contains("currentState = currentState,"),
        )
    }

    /** 광고 경로가 현재 상태를 모르면 **구독자가 광고를 본 순간 1시간짜리로 강등된다**. */
    @Test
    fun theAdGrantPathReceivesTheCurrentPremiumStateSoItCannotOverwriteASubscription() {
        assertTrue(
            "광고 부여에 현재 상태를 안 넘긴다 — Purchase가 AdGrant로 덮인다(#158).",
            premium.contains("performPremiumAdGrant(context, diagnosticEventLog, premiumState)"),
        )
        // ⚠️ **개발자 시뮬레이션도 같은 루틴을 탄다.** 여기만 빠뜨리면 *"개발자 버튼으로만
        // 구독이 강등되는"* 더 찾기 어려운 어긋남이 된다.
        assertTrue(
            "개발자 광고 시뮬레이션에 현재 상태를 안 넘긴다 — 그 버튼만 구독을 덮는다(#158).",
            premium.contains("simulatePremiumAdGrant(diagnosticEventLog, premiumState)"),
        )
    }

    /**
     * ⚠️ **`PremiumSource.Purchase`를 개명하지 말 것.** `PremiumStateStore`가 모르는 이름을
     * `enumOrDefault(..., PremiumSource.None)`으로 **조용히 `None`에 떨어뜨려**, 저장된 구독이
     * 앱 업데이트 한 번에 통째로 사라진다. 의미만 "영구"→"구독 유효"로 옮겼다(#158).
     */
    @Test
    fun thePersistedSourceNameIsNeverRenamed() {
        val store = source("src/main/java/com/worksoc/goaicoach/persistence/PremiumStateStore.kt")
        assertTrue(
            "저장소가 모르는 `source` 이름을 None으로 떨어뜨리는 구조가 아니다 — 경고의 전제가 바뀌었다.",
            store.contains("enumOrDefault(json.optString(\"source\"), PremiumSource.None)"),
        )
        assertTrue(
            "`PremiumSource.Purchase`가 사라졌다 — 저장된 구독이 업데이트 한 번에 None이 된다(#158).",
            source("../shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/premium/PremiumState.kt")
                .contains("Purchase,"),
        )
    }
}
