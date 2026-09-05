package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 개발자 2차의 "광고 본 것으로 프리미엄 1시간"이 **실제 보상 루틴을 탄다는** 소스 계약(백로그 #78).
 *
 * ⚠️ **이 계약이 없으면 이 버튼은 자기 목적을 배반할 수 있다.** 목적이 둘인데 — ⓐ 광고 보상이
 * 잘 들어오는지, ⓑ 프리미엄 1시간 활성화·만료가 맞는지 — `PremiumState.adGranted(...)`를 직접
 * 만들어 저장하면 **ⓐ가 통째로 우회된다.** 그래도 화면은 똑같이 켜지므로 눈으로는 구분되지 않는다.
 *
 * ⚠️ 주석은 걷어내고 본다([codeOnly]).
 */
class DevAdGrantSimulationContractTest {

    private val glue = codeOnly(sourceOf("PremiumPurchaseGlue.kt"))
    private val uiState = codeOnly(sourceOf("PremiumUiState.kt"))
    // ⚠️ **개발자 섹션은 이제 별도 파일이다**(백로그 #102). 두 파일을 **셸 → 섹션 순서로**
    // 이어 읽는다 — 그래야 아래 위치 비교(`indexOf`)가 **원래의 문서 순서**를 그대로 뜻한다.
    // 섹션을 먼저 붙이면 "1차 게이트 안에 2차 진입이 있다"는 비교가 거꾸로 성립해 버린다.
    private val settings =
        codeOnly(sourceOf("SettingsScreen.kt")) + "\n" + codeOnly(sourceOf("DeveloperTestSection.kt"))

    /**
     * 시뮬레이션은 **실제 경로와 같은 5계층 함수**를 지나야 한다 — 다른 것은 광고를 띄우는
     * 한 걸음뿐이어야 한다.
     */
    /**
     * ⚠️ **단언을 시뮬레이션 함수 본문으로 좁힌다.** 파일 전체를 보면 `performPremiumAdGrant`(실제
     * 경로)가 같은 줄을 갖고 있어서, 시뮬레이션에서 그 줄을 지워도 **테스트가 통과한다** —
     * 2026-09-04 변이 검증에서 실제로 그렇게 거짓 통과가 났다.
     */
    @Test
    fun theSimulationGoesThroughTheSameRewardRoutineAsTheRealAd() {
        val body = simulationBody()
        assertTrue(
            "시뮬레이션이 `runPremiumAdGrantApplication`을 지나지 않는다 — 보상 루틴을 우회한다(#78).",
            body.contains("runPremiumAdGrantApplication("),
        )
        assertTrue(
            "`RewardEarned`를 넣지 않는다 — 그것이 '광고를 본 것으로 상정한다'의 전부다.",
            body.contains("outcome = AdRewardOutcome.RewardEarned()"),
        )
        assertTrue(
            "시뮬레이션이 진단 이벤트를 남기지 않는다 — 실제 경로는 남기므로 둘이 갈라지고, " +
                "그러면 '광고 보상이 잘 들어오는지'를 로그로 확인할 수 없다(#78).",
            body.contains("diagnosticEventLog.append(result.diagnosticEvent)"),
        )
    }

    /** ⚠️ 상태를 직접 만들어 우회하지 않는지 — 이것이 이 계약의 본체다. */
    @Test
    fun theSimulationNeverBuildsThePremiumStateItself() {
        val body = simulationBody()
        assertFalse(
            "시뮬레이션이 `PremiumState.adGranted(...)`를 직접 만든다 — 보상 루틴이 우회되고, " +
                "그래도 화면은 똑같이 켜져 눈으로 구분되지 않는다(#78).",
            body.contains("PremiumState.adGranted"),
        )
    }

    /**
     * ⚠️ **영구 활성화 토글은 없어졌어야 한다**(2026-09-03 사용자 결정). #26이 구독으로 옮기면
     * 판정 기준이 "지금 유효한가"가 되므로, 사라질 상태를 계속 테스트하게 두지 않는다.
     * · 다만 **`PremiumSource.Purchase` 상수는 남아야 한다**(함정 1번) — 없앤 것은 토글이다.
     */
    @Test
    fun thePermanentActivationToggleIsGone() {
        assertFalse(
            "`setPurchased` 배선이 아직 남아 있다 — 영구 활성화 토글을 없애기로 했다(#78).",
            uiState.contains("val setPurchased") || settings.contains("premium.setPurchased"),
        )
        assertTrue(
            "부여가 버튼이 아니라 아직 Switch다 — 1시간 부여는 상태가 아니라 사건이다(#78).",
            settings.contains("TextButton(onClick = premium.simulateAdGrant)"),
        )
    }

    /** 남은 시간을 보여 줘야 한다 — 만료 확인이 이 버튼 목적의 절반이다. */
    @Test
    fun theRowShowsHowMuchTimeIsLeft() {
        assertTrue(
            "남은 시간을 계산하지 않는다 — 만료를 확인할 수 없다(#78).",
            settings.contains("premium.adGrantExpiresAtMillis"),
        )
        assertTrue(
            "부제가 남은 시간을 읽지 않는다.",
            settings.contains("strings.settingsDevAdGrantSubtitle(premiumRemainingMinutes)"),
        )
    }

    /**
     * 시뮬레이션 함수의 **본문만** 잘라낸다. 파일 전체로 단언하면 실제 경로
     * ([performPremiumAdGrant])가 같은 줄을 갖고 있어 거짓 통과가 난다 — 위 KDoc 참고.
     */
    private fun simulationBody(): String {
        val start = glue.indexOf("internal fun simulatePremiumAdGrant")
        val end = glue.indexOf("internal suspend fun showRewardedAdOnce")
        assertTrue("시뮬레이션 함수를 찾지 못했다 — 이 계약의 전제가 무너졌으니 다시 쓸 것.", start in 0 until end)
        return glue.substring(start, end)
    }

    private fun sourceOf(fileName: String): String =
        File("src/main/java/com/worksoc/goaicoach/ui/$fileName").readText()

    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .joinToString("\n") { it.substringBefore("//") }
}
