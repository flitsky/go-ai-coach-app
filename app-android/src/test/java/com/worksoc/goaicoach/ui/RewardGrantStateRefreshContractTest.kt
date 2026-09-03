package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **저장소에 직접 쓰는 지급 경로는 화면이 들고 있는 상태를 함께 되읽어야 한다** — 이 저장소가
 * 두 번 밟은 함정의 소스 계약(백로그 #65).
 *
 * ⚠️ **일반 단위 테스트로는 안 잡힌다.** 지급은 실제로 성공하고 저장소에도 정확히 들어가므로
 * 도메인 테스트는 전부 초록이다. 틀리는 것은 **그 세션의 화면 판정**뿐이고, 앱을 다시 켜면
 * 정상으로 돌아와 재현조차 까다롭다. 그래서 값 대신 **배선을 소스에 못박는다.**
 *
 * 밟은 두 번:
 * 1. 재고(1회권) — 마이 페이지에서 *"도장은 찍혔는데 0개"* 로 드러났고 `consumables.refresh()`로 고쳤다.
 * 2. 프리미엄 — **3일차 출석 보상이 무르기 영구 해금**인데(`AttendanceRewardPolicy`,
 *    `UndoUnlimitedRewardTier = 3`) `GoCoachApp`의 `premiumState`가 앱 실행당 한 번만 로드돼,
 *    받고도 그 세션 내내 무르기가 `Locked`로 판정됐다. 1번을 고칠 때 프리미엄 쪽이 빠져 있었다.
 *
 * ⚠️ 주석은 걷어내고 본다([codeOnly]) — 이 처방은 그것을 설명하는 KDoc에도 그대로 적혀 있어서,
 * 걷어내지 않으면 **코드를 되돌려도 주석만 보고 통과한다**(#63에서 실제로 거짓 통과가 났다).
 */
class RewardGrantStateRefreshContractTest {

    private val claimDialog = codeOnly(sourceOf("AttendanceRewardClaimDialog.kt"))
    private val shell = codeOnly(sourceOf("GoCoachApp.kt"))

    /**
     * 지급 직후 **두 상태를 모두** 되읽는지. 하나만 되읽으면 그 하나만 고쳐진 채로 다른 쪽이
     * 조용히 남는데, 그것이 정확히 #65의 사고 경위다.
     */
    @Test
    fun grantingAttendanceRewardsRefreshesBothConsumablesAndPremium() {
        assertTrue(
            "지급 직후 재고를 되읽지 않는다 — 마이 페이지가 다시 \"도장은 찍혔는데 0개\"가 된다.",
            claimDialog.contains("consumables.refresh()"),
        )
        assertTrue(
            "지급 직후 프리미엄을 되읽지 않는다(#65) — 3일차 무르기 해금이 그 세션 동안 먹지 않는다.",
            claimDialog.contains("onPremiumChanged(premiumStore.load())"),
        )
    }

    /**
     * ⚠️ **되읽기가 `onClaim` 안에 있어야 한다.** 이 다이얼로그는 `onDismissRequest = onClaim`이라
     * **어떻게 닫아도 지급된다**(`ATTENDANCE_REWARD_POLICY.md` 1장) — 확인 버튼 쪽에만 걸면
     * 뒤로 가기·바깥 탭으로 닫은 사용자에게 조용히 누락된다. 지급 호출과 같은 블록에 있는지를
     * 위치로 확인한다.
     */
    @Test
    fun theRefreshCallsSitInsideTheSameClaimBlockAsTheGrant() {
        val grant = claimDialog.indexOf("runAttendanceRewardGrant(")
        val premium = claimDialog.indexOf("onPremiumChanged(premiumStore.load())")
        val pendingCleared = claimDialog.indexOf("pending = emptyList()")
        assertTrue("지급 호출을 찾지 못했다 — 이 계약의 전제가 무너졌으니 테스트를 다시 쓸 것.", grant >= 0)
        assertTrue("프리미엄 되읽기를 찾지 못했다.", premium >= 0)
        assertTrue("`pending = emptyList()`를 찾지 못했다.", pendingCleared >= 0)
        assertTrue(
            "프리미엄 되읽기가 지급 호출보다 앞에 있다 — 지급 전 값을 읽으면 아무것도 안 고쳐진다.",
            premium > grant,
        )
        assertTrue(
            "프리미엄 되읽기가 `pending = emptyList()` 뒤에 있다 — 그 줄이 팝업을 닫으므로 " +
                "되읽기가 뒤에 오면 컴포지션이 이미 떠난 뒤일 수 있다.",
            premium < pendingCleared,
        )
    }

    /**
     * 셸이 그 콜백을 실제로 자기 상태에 꽂는지. 콜백을 받기만 하고 `{}`로 흘려버리면 계약이
     * 형식적으로만 지켜진다.
     *
     * ⚠️ 그리고 이 배선은 **한 줄이어야 한다** — `GoCoachApp.kt`는 라인·상태훅 예산이 정확히
     * 소진된 상태다(`LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`).
     */
    @Test
    fun theShellFeedsTheCallbackIntoItsOwnPremiumState() {
        assertTrue(
            "셸이 출석 팝업의 프리미엄 콜백을 자기 `premiumState`에 꽂지 않는다(#65) — " +
                "콜백만 받고 버리면 저장소만 바뀌고 화면 판정은 그대로다.",
            shell.contains("AttendanceRewardClaimDialog(context) { next -> premiumState = next }"),
        )
    }

    private fun sourceOf(fileName: String): String =
        File("src/main/java/com/worksoc/goaicoach/ui/$fileName").readText()

    /** 주석을 걷어낸 코드만 남긴다. 여러 줄 KDoc을 반드시 지워야 한다 — 위 KDoc의 경고 참고. */
    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .joinToString("\n") { it.substringBefore("//") }
}
