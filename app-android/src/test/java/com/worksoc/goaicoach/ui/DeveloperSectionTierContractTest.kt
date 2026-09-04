package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 개발자 테스트 섹션의 **2단 구조** 소스 계약(백로그 #77).
 *
 * ⚠️ **이 계약이 지키는 것은 "무엇이 어느 단에 있는가"이고, 그 기준은 라벨이 아니라 저장이다.**
 * 1차는 **release 빌드에도 실리므로**(10탭 게이트는 런타임 플래그다) 거기에 권한을 만드는
 * 컨트롤이 하나라도 섞이면 **무료 획득 경로가 그대로 출시된다.** 2차의 유일한 실제 경계는
 * `BuildConfig.DEBUG`이고, 길게 누르기는 오조작만 막는 은닉이다.
 *
 * ⚠️ 주석은 걷어내고 본다([codeOnly]) — 이 처방들은 KDoc에도 그대로 적혀 있어서, 걷어내지 않으면
 * **코드를 되돌려도 주석만 보고 통과한다**(#63에서 실제로 거짓 통과가 났다).
 */
class DeveloperSectionTierContractTest {

    private val settings = codeOnly(sourceOf("SettingsScreen.kt"))

    /**
     * ⚠️ **2차는 `BuildConfig.DEBUG` 안에 있어야 한다.** 길게 누르기만으로 게이트하면 제스처를
     * 아는 사람이 release에서 프리미엄을 무료로 켠다. 진입점 둘 다 막혀 있어야 한다 — 화면을
     * 그리는 쪽과, 상태를 켜는 쪽.
     */
    @Test
    fun theAdvancedTierIsGatedByTheBuildTypeAndNotOnlyByTheGesture() {
        assertTrue(
            "2차 블록이 `BuildConfig.DEBUG`로 감싸여 있지 않다 — release에 그대로 실린다(#77).",
            settings.contains("BuildConfig.DEBUG && isAdvancedDeveloperModeEnabled"),
        )
        assertTrue(
            "길게 누르기 처리가 `BuildConfig.DEBUG`를 먼저 보지 않는다 — release에서 2차가 켜진다.",
            settings.contains("if (!BuildConfig.DEBUG) return"),
        )
    }

    /**
     * ⚠️ **2차 활성 상태를 저장하면 안 된다.** 저장하는 순간 한 번 켠 기기가 영구히 열린 상태로
     * 남는다. 평범한 `remember`여야 하고, `rememberSaveable`도 저장소도 아니어야 한다.
     */
    @Test
    fun theAdvancedTierStateIsNeverPersisted() {
        assertTrue(
            "2차 상태가 평범한 `remember`가 아니다(#77).",
            settings.contains("var isAdvancedDeveloperModeEnabled by remember { mutableStateOf(false) }"),
        )
        assertFalse(
            "2차 상태에 `rememberSaveable`을 썼다 — 회전만 해도 살아남아 '세션 한정'이 깨진다.",
            settings.contains("rememberSaveable") && settings.contains("isAdvancedDeveloperModeEnabled"),
        )
        // 저장 경로는 1차 하나뿐이어야 한다 — 호출이 늘었다면 2차까지 남기고 있다는 뜻이다.
        assertEquals(
            "`setEnabled(...)` 호출이 1차의 한 곳이 아니다 — 2차도 저장하고 있지 않은지 볼 것(#77).",
            1,
            settings.split(".setEnabled(").size - 1,
        )
    }

    /**
     * ⚠️ **탭과 3초 홀드는 제스처 **하나**로 판정해야 한다.** `clickable`은 누른 시간과 무관하게
     * 릴리즈에서 onClick을 부르므로 홀드가 탭으로도 세어지고, `combinedClickable`의 `onLongClick`은
     * 약 500ms에 하드와이어돼 3초를 표현할 수 없다. 감지기를 둘 겹치면 한쪽이 굶는다
     * (`GoBoard.kt`가 같은 이유로 단일 `pointerInput`을 쓴다).
     */
    @Test
    fun theVersionTextUsesOneGestureForBothTapAndHold() {
        assertTrue("단일 제스처(`pointerInput`)를 쓰지 않는다(#77).", settings.contains("Modifier.pointerInput(isDeveloperModeEnabled)"))
        assertTrue("홀드 시간을 명시적 타임아웃으로 재지 않는다.", settings.contains("withTimeout(AdvancedDeveloperModeHoldMillis)"))
        assertFalse(
            "`combinedClickable`을 썼다 — `onLongClick`은 약 500ms 고정이라 3초를 표현할 수 없다(#77).",
            settings.contains("combinedClickable"),
        )
        assertFalse(
            "버전 텍스트가 아직 `Modifier.clickable`로 탭을 센다 — 홀드가 탭으로도 세어진다(#77).",
            settings.contains("if (isDeveloperModeEnabled) return@clickable"),
        )
    }

    /**
     * ⚠️ **1차에는 권한을 만드는 컨트롤이 없어야 한다.** 프리미엄 부여는 저장소에 프리미엄
     * 소스를 기록하고 `FeatureAccessPolicy.resolve`가 소스에서 곧바로 통과시켜 **모든 유료 기능이
     * 한꺼번에 열린다.** 그래서 2차다. 위치를 소스 오프셋으로 확인한다.
     *
     * ⚠️ **이 테스트는 #78로 한 번 갱신됐다.** 원래는 `premium.setPurchased(checked)`(영구 활성화
     * **토글**)를 찾았는데, #78이 그것을 **"광고 본 것으로 1시간" 버튼**으로 바꿨다 — #26이 프리미엄을
     * 월간 구독으로 옮기면서 판정이 *"영구히 샀는가"* 에서 **"지금 유효한가"** 로 바뀌기 때문이다.
     * **지켜야 할 것은 컨트롤의 종류가 아니라 "권한을 만드는 것은 2차에 있다"** 이므로, 무엇이
     * 그 자리에 오든 이 단언은 그대로 유효해야 한다.
     */
    @Test
    fun theControlThatGrantsPremiumSitsInTheAdvancedTier() {
        val advancedGate = settings.indexOf("BuildConfig.DEBUG && isAdvancedDeveloperModeEnabled")
        val premiumGrant = settings.indexOf("premium.simulateAdGrant")
        assertTrue("2차 게이트를 찾지 못했다 — 이 계약의 전제가 무너졌다.", advancedGate >= 0)
        assertTrue("프리미엄을 부여하는 컨트롤을 찾지 못했다.", premiumGrant >= 0)
        assertTrue(
            "프리미엄 부여가 2차 게이트보다 앞에 있다 — 1차(=release에 실림)에 있다는 뜻이다(#77).",
            premiumGrant > advancedGate,
        )
        assertFalse(
            "영구 활성화 토글이 되살아났다 — #78이 없앤 것이고, #26의 구독 전환과 어긋난다.",
            settings.contains("premium.setPurchased"),
        )
    }

    /**
     * 1회권 지급은 1차에 있어도 되지만(출석 1일차가 30장을 준다) ⚠️ **`refresh()`를 함께 불러야
     * 한다** — `runConsumableGrant`는 저장소에 직접 쓰고 화면 사본은 나가는 것만 알기 때문이다.
     * 백로그 #65가 프리미엄에서 같은 함정을 밟았다.
     */
    @Test
    fun grantingATicketRefreshesTheScreenCopy() {
        val grant = settings.indexOf("runConsumableGrant(item, amount = 1, consumableStore = store)")
        val refresh = settings.indexOf("consumables.refresh()")
        assertTrue("1회권 지급 호출을 찾지 못했다.", grant >= 0)
        assertTrue(
            "지급 뒤 재고를 되읽지 않는다 — 마이 페이지가 옛 재고를 계속 보여준다(#65와 같은 함정).",
            refresh > grant,
        )
    }

    private fun sourceOf(fileName: String): String =
        File("src/main/java/com/worksoc/goaicoach/ui/$fileName").readText()

    /** 주석을 걷어낸 코드만 남긴다. 여러 줄 KDoc을 반드시 지워야 한다. */
    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .joinToString("\n") { it.substringBefore("//") }
}
