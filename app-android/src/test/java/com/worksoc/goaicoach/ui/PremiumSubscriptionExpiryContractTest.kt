package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.readContractSource
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 구독 권한이 **살아 있는지 다시 묻는 계기**에 대한 계약(백로그 #174).
 *
 * ## 왜 계약인가
 * #173 실기가 *"강등 계기가 「앱 완전 재시작」 하나뿐"* 임을 드러냈다. 원인은 코드를 읽어서는
 * 눈에 띄지 않는다 — `LaunchedEffect(Unit)`은 **틀린 곳이 없어 보이는데** 결과적으로
 * "컴포지션당 한 번"이라는 정책을 몰래 정하고 있었다. 되돌아가기도 쉽다(`collect` 한 줄을
 * 지우면 끝이고 테스트는 전부 초록일 것이다). 그래서 그물을 단다.
 */
class PremiumSubscriptionExpiryContractTest {

    private val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val premiumUiState = File(
        repoRoot,
        "app-android/src/main/java/com/worksoc/goaicoach/ui/PremiumUiState.kt",
    ).readContractSource()

    /**
     * ⚠️ **재시작만이 계기이면 해지한 사용자가 앱을 계속 켜 두는 동안 권한이 산다**(#173 실기).
     */
    @Test
    fun ownershipIsRecheckedOnEveryReturnToForegroundNotJustOnColdStart() {
        assertTrue(
            "`PremiumPurchaseRestoreEffect`가 `AppForegroundEvents`를 구독하지 않는다 — " +
                "그러면 소유 재조회 계기가 **앱 완전 재시작 하나뿐**이 되고, 해지한 사용자가 " +
                "앱을 계속 켜 두는 동안 권한이 살아 있는다(#174).",
            premiumUiState.contains("AppForegroundEvents.events.collect"),
        )
    }

    /**
     * ⚠️ **함정 46** — 이 효과는 이제 앱이 사는 내내 돌아 있다. `LaunchedEffect(Unit)`의 람다가
     * 첫 컴포지션의 [PremiumState]를 붙잡으면, 그 뒤에 구독을 산 사람이 **다음 포그라운드 복귀에서
     * 도로 강등된다** — 낡은 상태로 강등 판정을 하기 때문이다. 컴파일도 되고 화면도 멀쩡해 보인다.
     */
    @Test
    fun theLongLivedEffectReadsTheLatestStateInsteadOfTheFirstCompositionsCopy() {
        assertTrue(
            "`PremiumPurchaseRestoreEffect`가 `rememberUpdatedState`를 쓰지 않는다 — " +
                "`LaunchedEffect(Unit)`이 첫 컴포지션의 상태를 붙잡아 강등 판정이 낡은 값으로 " +
                "굳는다(함정 46 · #174).",
            premiumUiState.contains("rememberUpdatedState(currentState)"),
        )
    }

    /**
     * ⚠️ **#158의 관문은 이 변경의 안전장치다.** 조회가 잦아진 만큼 실패도 잦아지는데, 강등이
     * `isAuthoritativeNotOwned` 하나로만 일어나야 *"지하철에서 앱을 연 유료 구독자"* 가 안 내려간다.
     * 관문이 application 계층에 살아 있는지 확인한다 — 여기가 뚫리면 #174가 결함으로 바뀐다.
     */
    @Test
    fun downgradeStillHappensOnlyThroughTheAuthoritativeNotOwnedGate() {
        val application = File(
            repoRoot,
            "shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/premium",
        ).walkTopDown().filter { it.extension == "kt" }.map { it.readContractSource() }.joinToString("\n")
        assertTrue(
            "`isAuthoritativeNotOwned` 관문이 사라졌다 — 조회 실패 한 번이 유료 구독자를 내리게 " +
                "된다(#158이 막은 결함, #174가 조회를 잦게 만들어 위험이 커졌다).",
            application.contains("isAuthoritativeNotOwned"),
        )
    }
}
