package com.worksoc.goaicoach

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 개발자 모드 주기 초기화의 **범위와 게이트**를 못박는 소스 계약(백로그 #99).
 *
 * ⚠️ **이 항목은 사용자 데이터를 지운다 — 잘못되면 되돌릴 방법이 없다.** 순수 정책은
 * `DeveloperModeResetPolicyTest`가 보고, 여기서는 *"무엇을 지우는가"* 와 *"언제 부르는가"* 를 본다.
 * 둘 다 단위 테스트로는 닿지 않는 배선이다.
 */
class DeveloperModeResetContractTest {

    private val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun codeOnly(path: String): String = File(repoRoot, path).readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines().joinToString("\n") { it.substringBefore("//") }

    private val coordinator = codeOnly("app-android/src/main/java/com/worksoc/goaicoach/DeveloperModeResetCoordinator.kt")
    private val application = codeOnly("app-android/src/main/java/com/worksoc/goaicoach/GoAiCoachApplication.kt")
    private val settings = codeOnly("app-android/src/main/java/com/worksoc/goaicoach/ui/SettingsScreen.kt")

    /**
     * ⚠️ **debug 빌드에서는 절대 돌면 안 된다**(2026-09-05 사용자 결정) — 개발자 본인의 실기
     * 테스트가 3시간마다 날아간다. 게이트가 사라져도 **테스트는 전부 초록**이므로 여기서 못박는다.
     */
    @Test
    fun thePeriodicResetNeverRunsInDebugBuilds() {
        val gate = application.indexOf("if (!BuildConfig.DEBUG)")
        val call = application.indexOf("DeveloperModeResetCoordinator(this).applyIfNeeded(")
        assertTrue("`BuildConfig.DEBUG` 게이트를 찾지 못했다(#99).", gate >= 0)
        assertTrue("주기 초기화 호출을 찾지 못했다(#99).", call >= 0)
        assertTrue(
            "주기 초기화가 `BuildConfig.DEBUG` 게이트 **밖**에 있다 — debug에서도 3시간마다 " +
                "개발자의 데이터가 지워진다(#99).",
            call > gate,
        )
    }

    /**
     * ⚠️ **릴리즈 초기화보다 먼저 돌아야 한다.** 지우는 것 안에 **릴리즈 초기화 마커**가 들어 있어서,
     * 순서가 뒤집히면 릴리즈 초기화가 방금 지워진 마커를 보고 한 번 더 돈다.
     */
    @Test
    fun itRunsBeforeTheReleaseReset() {
        val developer = application.indexOf("DeveloperModeResetCoordinator(this)")
        val release = application.indexOf("ReleaseResetCoordinator(this)")
        assertTrue("개발자 초기화가 릴리즈 초기화보다 뒤에 있다(#99).", developer in 0 until release)
    }

    /**
     * ⚠️ **기기 식별자는 남겨야 한다.** 지우면 **한 기기가 하루 8개의 새 기기로 보여** 기기 기준
     * 지표가 오염된다. #63(`ReleaseResetCoordinator`)도 같은 이유로 남긴다.
     */
    @Test
    fun theDeviceIdentityIsSpared() {
        assertTrue(
            "기기 식별자를 예외로 두지 않는다 — 한 기기가 하루 8개로 보인다(#99).",
            coordinator.contains("DeviceIdentityStore.PrefsName"),
        )
        assertTrue(
            "예외가 '지우지 않는다'는 형태가 아니다 — 조건을 눈으로 확인할 것(#99).",
            coordinator.contains("!= DeviceIdentityStore.PrefsName"),
        )
    }

    /**
     * ⚠️ **지울 목록을 손으로 적지 않는다**(함정 6번). 접두사로 골라야 저장소가 늘어도 자동으로
     * 포함되고 목록이 두 벌로 갈라지지 않는다. ⚠️ 동시에 **접두사 밖(SDK 상태)은 건드리지 않아야**
     * 한다 — 실행 중인 AdMob/WebView 상태까지 지우면 그쪽이 흔들린다.
     */
    @Test
    fun theWipeIsPrefixDrivenAndStaysInsideOurNamespace() {
        assertTrue(
            "접두사 기반으로 고르지 않는다 — 저장소가 늘면 목록이 갈라진다(#99, 함정 6번).",
            coordinator.contains("startsWith(AppPrefsPrefix)"),
        )
        assertTrue(
            "앱 접두사가 `go_ai_coach_`가 아니다 — SDK 상태까지 지울 위험이 있다(#99).",
            coordinator.contains("\"go_ai_coach_\""),
        )
    }

    /**
     * ⚠️ **끄기 버튼과 주기 초기화가 같은 함수로 수렴해야 한다**(사용자 확정: *"끄면 최초 설치
     * 상태로 전환"*). 나눠 쓰면 한쪽만 고쳐진다.
     */
    @Test
    fun theOffButtonAndThePeriodicResetShareOneImplementation() {
        assertTrue("끄기 버튼이 공용 초기화를 부르지 않는다(#99).", settings.contains("wipeToFreshInstall(context)"))
        assertTrue("코디네이터가 공용 초기화를 부르지 않는다(#99).", coordinator.contains("wipeToFreshInstall(context)"))
        assertEquals(
            "`wipeToFreshInstall` 정의가 하나가 아니다 — 구현이 갈라졌다(#99).",
            1,
            coordinator.split("internal fun wipeToFreshInstall(").size - 1,
        )
    }

    /**
     * ⚠️ **켤 때 기준 시각을 심어야 한다.** 없으면 정책이 `null`을 보고 **절대 초기화하지 않는다** —
     * 즉 이 기능 전체가 **조용히 동작하지 않는다.** 가장 놓치기 쉬운 배선이다.
     */
    @Test
    fun enablingDeveloperModePlantsTheBaseline() {
        val enable = settings.indexOf("developerModeStore.setEnabled(true)")
        val baseline = settings.indexOf("developerModeStore.markResetBaseline(")
        assertTrue("개발자 모드를 켜는 곳을 찾지 못했다.", enable >= 0)
        assertTrue(
            "켤 때 기준 시각을 심지 않는다 — 주기 초기화가 조용히 동작하지 않는다(#99).",
            baseline > enable,
        )
    }

    /** ⚠️ 10탭이 곧바로 켜면 안 된다 — 경고 팝업을 거쳐야 한다(#99 ⓐ). */
    @Test
    fun tenTapsAsksBeforeTurningItOn() {
        assertTrue(
            "10탭이 확인 팝업을 띄우지 않는다(#99 ⓐ).",
            settings.contains("showDeveloperModeOptIn = true"),
        )
        assertFalse(
            "10탭 직후에 곧바로 켜고 있다 — 경고를 보여 준 뒤에 켜야 한다(#99 ⓐ).",
            settings.contains("remainingTaps <= 0) {\n                                isDeveloperModeEnabled = true"),
        )
    }
}
