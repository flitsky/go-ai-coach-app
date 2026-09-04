package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 캐릭터 획득 축전 팝업(백로그 #69)의 소스 계약.
 *
 * ⚠️ **여기 걸린 것들은 전부 "잘못돼도 테스트가 초록인" 종류다** — 다이얼로그는 별도 윈도우라
 * 겹쳐도 예외가 없고, 토스트가 하나 더 뜨는 것도 실패가 아니며, 고정 높이 잘림은 측정 결과라
 * 값 검사로는 안 잡힌다(`FontScaleLayoutContractTest`가 같은 이유로 있다). 그래서 **처방을 소스에
 * 못박는다.**
 *
 * ⚠️ 주석은 걷어내고 본다([codeOnly]). 이 처방들은 그것을 설명하는 KDoc에도 그대로 적혀 있어,
 * 걷어내지 않으면 **코드를 되돌려도 주석만 보고 통과한다**(#63에서 실제로 거짓 통과가 났다).
 */
class BotCharacterAcquiredDialogContractTest {

    private val dialog = codeOnly(sourceOf("BotCharacterAcquiredDialog.kt"))
    private val uiState = codeOnly(sourceOf("BotCharacterUiState.kt"))
    private val setupPanel = codeOnly(sourceOf("PlayerSetupPanel.kt"))
    private val claimDialog = codeOnly(sourceOf("AttendanceRewardClaimDialog.kt"))

    /**
     * **닫는 길 셋이 모두 같은 곳으로 간다.** 버튼이 없으므로 전면 탭·바깥 탭·뒤로 가기가 전부
     * 닫기여야 하고, `AlertDialog`로는 "표면 아무 곳이나"를 만들 수 없다.
     */
    @Test
    fun everyDismissPathGoesThroughTheSameCallback() {
        assertTrue(
            "`Dialog`가 아니다 — `AlertDialog`로는 표면 전체를 탭 대상으로 만들 수 없다.",
            dialog.contains("Dialog(") && dialog.contains("onDismissRequest = onDismiss"),
        )
        assertTrue("바깥 탭으로 닫히지 않는다.", dialog.contains("dismissOnClickOutside = true"))
        assertTrue("뒤로 가기로 닫히지 않는다.", dialog.contains("dismissOnBackPress = true"))
        assertTrue(
            "표면 전체가 탭 대상이 아니다 — 버튼이 없으므로 이것이 유일한 닫기 수단이다.",
            dialog.contains("onClick = onDismiss"),
        )
    }

    /**
     * ⚠️ **캐릭터가 여럿이면 순차로 하나씩**(2026-09-03 사용자 결정). 밀린 출석 회차는 7·28일차를
     * 한 번에 줄 수 있다. 대기열에서 **맨 앞 하나만** 그리고, 닫을 때 **하나만** 버려야 한다 —
     * 통째로 비우면 둘째 캐릭터의 축전이 조용히 사라진다.
     */
    @Test
    fun acquisitionsAreCelebratedOneAtATimeInGrantOrder() {
        assertTrue(
            "대기열 맨 앞 하나만 띄우지 않는다 — 여럿을 동시에 그리면 별도 윈도우가 겹친다.",
            uiState.contains("pendingAcquired.firstOrNull()"),
        )
        assertTrue(
            "닫을 때 하나씩 버리지 않는다 — 통째로 비우면 둘째 캐릭터의 축전이 사라진다(#69).",
            uiState.contains("pendingAcquired.drop(1)"),
        )
        assertTrue(
            "대기열에 **뒤로** 붙이지 않는다 — 앞에 넣으면 지급 순서가 뒤집힌다.",
            uiState.contains("pendingAcquired = pendingAcquired + characters"),
        )
    }

    /**
     * ⚠️ **획득 순간에는 토스트를 띄우지 않는다**(2026-09-03 사용자 확정). 축전 팝업이 같은 사실을
     * 말하므로 둘 다 띄우면 같은 말을 두 번 한다. 진행도 토스트(`botShardEarnedToast`)는 남는다.
     */
    @Test
    fun unlockingViaAnAdRaisesThePopupInsteadOfTheOldToast() {
        assertTrue(
            "광고 획득 경로가 축전 대기열에 넣지 않는다.",
            uiState.contains("bots.enqueueAcquired(listOf(character))"),
        )
        assertFalse(
            "획득 순간에 `botUnlockedToast`가 아직 뜬다 — 팝업과 같은 말을 두 번 한다(#69).",
            uiState.contains("botUnlockedToast"),
        )
        assertTrue(
            "조각 적립 중의 진행도 토스트까지 사라졌다 — 그건 남겨야 한다.",
            uiState.contains("botShardEarnedToast"),
        )
    }

    /**
     * ⚠️ **z축은 선언 순서로 정해지지 않는다**(함정 7번) — 겹칠 수 있는 쌍은 **명시적으로** 갈라야
     * 한다. 이 두 줄이 그 게이트다.
     */
    @Test
    fun theTwoWindowsThatCanCollideAreGatedExplicitly() {
        assertTrue(
            "광고 후 픽커를 무조건 되살린다 — 획득했다면 축전 팝업과 두 윈도우가 겹친다(#69).",
            setupPanel.contains("showPicker = !acquired"),
        )
        assertTrue(
            "출석 Claim이 축전을 **대기열에 넣지 않고** 바로 띄우려 한다 — 그 순간 Claim 팝업이 " +
                "아직 떠 있어 두 윈도우가 같은 프레임에 공존한다(#69).",
            claimDialog.contains("bots.enqueueAcquired(result.acquiredCharacters)"),
        )
    }

    /**
     * ⚠️ **글자가 든 상자에 고정 `dp` 높이를 주지 말 것**(함정 9번). 이 팝업은 캐러셀이 아니므로
     * `heightIn(min = …)` 처방이 그대로 통한다 — 배율 1.3배부터 아랫줄이 잘리는 것을 막는다.
     */
    @Test
    fun theDialogSizesTextBoxesWithAFloorRatherThanAFixedHeight() {
        assertTrue(
            "설명 줄에 바닥값이 없다 — 캐릭터마다 길이가 달라 팝업이 연달아 뜰 때 출렁인다.",
            dialog.contains("heightIn(min = DescriptionMinHeight)"),
        )
        assertFalse(
            "글자 상자에 고정 높이(`.height(`)를 줬다 — 배율 1.3배에서 아랫줄이 잘린다(함정 9번).",
            dialog.contains(".height("),
        )
    }

    /**
     * 셸은 예산이 정확히 소진돼 있다(라인 880/880, 상태훅 46/46 — 함정 3번). 이 팝업은 셸에
     * **한 줄도** 들어가면 안 된다.
     */
    @Test
    fun theShellCarriesNoneOfThisDialog() {
        val shell = codeOnly(sourceOf("GoCoachApp.kt"))
        assertFalse(
            "셸이 축전 팝업을 직접 배선했다 — `LayeringContractTest`의 라인/상태훅 예산이 깨진다. " +
                "호스팅은 `buildBotCharacterUiState` 안이다(#69).",
            shell.contains("BotCharacterAcquiredDialog"),
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
