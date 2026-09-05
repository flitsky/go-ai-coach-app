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

    // ⚠️ **개발자 섹션은 이제 별도 파일이다**(백로그 #102). 두 파일을 **셸 → 섹션 순서로**
    // 이어 읽는다 — 그래야 아래 위치 비교(`indexOf`)가 **원래의 문서 순서**를 그대로 뜻한다.
    // 섹션을 먼저 붙이면 "1차 게이트 안에 2차 진입이 있다"는 비교가 거꾸로 성립해 버린다.
    private val settings =
        codeOnly(sourceOf("SettingsScreen.kt")) + "\n" + codeOnly(sourceOf("DeveloperTestSection.kt"))

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

    /*
     * ⚠️ **여기 있던 계약 둘(`theVersionTextUsesOneGestureForBothTapAndHold`,
     * `theHoldConsumesTheDownSoTheScrollCannotStealIt`)은 #84가 지웠다** — 지킬 제스처가
     * 없어졌기 때문이다. 그중 `down` 소비 계약은 **처방 자체가 불충분했다**: down 소비는 첫
     * 접촉만 막고 이후 MOVE 경쟁은 막지 못해서, **계약이 초록인 채로 실기에서는 계속 샜다.**
     * 아래 두 계약이 그 자리를 대신하며 **홀드로 돌아가는 것 자체를** 막는다 —
     * 잘못된 처방을 지키는 계약보다 그 처방을 금지하는 계약이 낫다는 것이 이 항목의 교훈이다.
     */

    /**
     * ⚠️ **2차 진입은 '빌드 정보' 행의 탭이어야 한다 — 길게 누르기로 돌아가지 말 것**
     * (2026-09-04, #84. 사용자가 **두 번** 제보한 결함이다).
     *
     * #77의 3초 홀드는 **세로 스크롤 안에서 신뢰할 수 없었다.** `waitForUpOrCancellation()`은
     * 다른 핸들러가 제스처를 가져가면 3초 전에 `null`을 돌려주고, 그러면 홀드도 탭도 아니라서
     * **어느 분기에도 걸리지 않고 조용히 끝난다.** 재시작 뒤 그 자리까지 스크롤해 내려간
     * 손가락은 이미 스크롤과 경쟁 중이라, 사용자에게는 *"한 번은 됐는데 다시는 안 된다"* 로
     * 보였다. **탭은 터치 슬롭을 넘기 전에 끝나므로 이 경쟁을 아예 겪지 않는다.**
     *
     * ⚠️ 홀드는 *"더 숨겨져 보인다"* 는 이유로 다시 끌려올 만한 선택지이고, 그 실패는
     * **조용해서** 이 계약이 없으면 다시 새어 나간다.
     */
    @Test
    fun theAdvancedTierIsEnteredByTappingAndNeverByHolding() {
        // ⚠️ **존재만 보면 안 된다** — `onBuildInfoTap(`은 헬퍼 **선언**에도 있어서, 배선을
        // 떼어내도 `contains`는 통과한다(변이로 확인했다). 선언 1 + 호출 1 = 정확히 둘이어야
        // 하고, 그러면 "배선을 뗐다"와 "두 곳에서 부른다"를 함께 잡는다.
        assertEquals(
            "`onBuildInfoTap(` 출현이 둘(선언+호출 한 곳)이 아니다 — 배선이 떨어졌거나 " +
                "진입점이 늘었다(#84).",
            2,
            settings.split("onBuildInfoTap(").size - 1,
        )
        assertTrue(
            "'빌드 정보' 행이 탭을 받지 않는다 — `DeveloperInfoRow`에 `onTap`이 배선돼야 한다(#84).",
            settings.contains("onTap = {"),
        )
        assertTrue(
            "2차 진입에 필요한 탭 수가 상수로 없다(#84).",
            settings.contains("AdvancedDeveloperModeTapsRequired"),
        )
        listOf(
            "waitForUpOrCancellation" to "스크롤이 가져가면 조용히 실패한다",
            "PointerEventTimeoutCancellationException" to "홀드 판정의 잔재다",
            "combinedClickable" to "`onLongClick`은 약 500ms 고정이라 3초를 표현할 수 없다",
            "AdvancedDeveloperModeHoldMillis" to "홀드 시간 상수가 되살아났다",
        ).forEach { (needle, why) ->
            assertFalse(
                "2차 진입이 다시 길게 누르기로 돌아갔다(`$needle`) — $why. #84가 탭으로 옮긴 사유를 볼 것.",
                settings.contains(needle),
            )
        }
    }

    /**
     * ⚠️ **"1차를 먼저 켜야 한다"는 조건은 런타임 검사가 아니라 위치로 성립한다**(#84).
     *
     * 진입점인 '빌드 정보' 행이 **1차 섹션 안에** 있어야 한다 — 1차가 꺼져 있으면 누를 대상
     * 자체가 화면에 없다. 이 행을 섹션 밖으로 옮기면 그 구조적 보장이 사라져 10탭을 거치지
     * 않은 사람에게 2차가 열리고, ⚠️ 반대로 **2차 블록 안으로** 들어가면 *"2차를 켜야 2차를
     * 켤 수 있는"* 닫힌 고리가 된다. 그래서 양쪽 경계를 함께 못박는다.
     */
    @Test
    fun theAdvancedTierEntryPointLivesInsideTheFirstTierSection() {
        val firstTierGate = settings.indexOf("if (isDeveloperModeEnabled) {")
        val entryPoint = settings.indexOf("onBuildInfoTap(")
        val advancedGate = settings.indexOf("BuildConfig.DEBUG && isAdvancedDeveloperModeEnabled")
        assertTrue("1차 섹션 게이트를 찾지 못했다 — 이 계약의 전제가 무너졌다.", firstTierGate >= 0)
        assertTrue("2차 진입점을 찾지 못했다.", entryPoint >= 0)
        assertTrue("2차 게이트를 찾지 못했다.", advancedGate >= 0)
        assertTrue(
            "2차 진입점이 1차 섹션보다 앞에 있다 — 1차를 켜지 않아도 누를 수 있다는 뜻이다(#84).",
            entryPoint > firstTierGate,
        )
        assertTrue(
            "2차 진입점이 2차 블록 안에 있다 — 2차를 켜야 2차를 켤 수 있는 닫힌 고리다(#84).",
            entryPoint < advancedGate,
        )
    }

    /**
     * ⚠️ **2차 진입 탭 카운터도 저장하면 안 된다.** 2차 자체가 세션 한정인데(#77) 카운터가
     * 살아남으면 다음 실행이 9탭에서 시작하는 셈이고, 은닉이 한 번 쓰고 없어진다.
     */
    @Test
    fun theAdvancedTierTapCounterIsNotPersistedEither() {
        assertTrue(
            "2차 진입 탭 카운터가 평범한 `remember`가 아니다(#84).",
            settings.contains("var buildInfoTapCount by remember { mutableStateOf(0) }"),
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
     * ⚠️ **권한 저장소 넷을 지우는 버튼은 2차에 있어야 한다**(백로그 #80). 이 배치에서 가장
     * 파괴적인 컨트롤이다 — 1차는 release 빌드에 실리므로, 여기 새면 **출시된 앱에 "내 출석·캐릭터·
     * 1회권을 통째로 지우기" 버튼이 노출된다.** 잘못 눌러도 되돌릴 길이 없다.
     *
     * ⚠️ **지울 목록을 이 화면이 따로 쓰지 않는 것도 함께 못박는다.** `ReleaseResetCoordinator`의
     * 목록을 재사용해야 하고(함정 6번), 그래서 화면은 `runReleaseResetAgain` **하나만** 부른다.
     * 저장소를 여기서 직접 지우기 시작하면 실제 초기화와 개발자 도구가 갈라져, 이 도구로 한
     * 검증이 아무것도 보장하지 않게 된다.
     */
    @Test
    fun theControlThatWipesEntitlementStoresSitsInTheAdvancedTier() {
        val advancedGate = settings.indexOf("BuildConfig.DEBUG && isAdvancedDeveloperModeEnabled")
        val releaseReset = settings.indexOf("runReleaseResetAgain(context)")
        assertTrue("2차 게이트를 찾지 못했다 — 이 계약의 전제가 무너졌다.", advancedGate >= 0)
        assertTrue("정식 출시 초기화를 다시 돌리는 컨트롤을 찾지 못했다(#80).", releaseReset >= 0)
        assertTrue(
            "권한 저장소를 지우는 버튼이 2차 게이트보다 앞에 있다 — 1차(=release에 실림)에 있다는 " +
                "뜻이고, 출시된 앱에 되돌릴 수 없는 초기화 버튼이 노출된다(#80).",
            releaseReset > advancedGate,
        )
        // 화면이 저장소를 직접 지우면 목록이 두 벌로 갈라진다 — 초기화는 코디네이터의 일이다.
        assertFalse(
            "설정 화면이 권한 저장소를 직접 지우고 있다 — `ReleaseResetCoordinator`의 목록을 " +
                "재사용할 것(함정 6번). 저장소가 늘 때 한쪽만 고쳐진다.",
            settings.contains("Store(context).clear()"),
        )
    }

    /**
     * 초기화 뒤 **화면 사본 셋을 모두 되읽어야 한다.** 세 곳이 각자 저장소를 들고 있어서, 하나라도
     * 빠뜨리면 지워진 값이 화면에 살아 있는 것으로 남는다 — #65가 프리미엄에서 밟은 함정이고,
     * 지우는 쪽이 더 눈에 띈다(마이 페이지에 없는 캐릭터가 보인다).
     */
    @Test
    fun wipingTheStoresRefreshesEveryScreenCopy() {
        val wipe = settings.indexOf("runReleaseResetAgain(context)")
        assertTrue("초기화 호출을 찾지 못했다.", wipe >= 0)
        val after = settings.substring(wipe)
        listOf("consumables.refresh()", "bots.refresh()", "premium.reload()").forEach { call ->
            assertTrue(
                "초기화 뒤 `$call`을 부르지 않는다 — 지워진 값이 화면에 남는다(#65와 같은 함정).",
                after.contains(call),
            )
        }
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
