package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #143 — 대국 화면 조작부 정리의 **소스 계약**. 넷 다 깨져도 컴파일은 멀쩡하고, 화면을 열어
 * 봐야만 드러나는 것들이라 소스로 잡는다.
 *
 * ⓐ **확인 모드가 꺼져 있으면 바로 착수로 강제한다** — 이것이 이 항목에서 가장 위험한 한 줄이다.
 *   저장값이 `false`인 기존 사용자는 강제가 없으면 확인 모드에 갇힌 채 `착수` 버튼도 없어
 *   **돌을 아예 못 둔다.** 강제하는 자리는 `uxOptions`를 만드는 **한 곳**이어야 한다.
 * ⓑ 판 위 토글 둘은 대국 화면에서 **사라지고** 메뉴에 있다.
 * ⓒ 지운 셋(착수 칸 · 메뉴 스위치 · 판 크기별 권장 팝업)은 **플래그 뒤에 살아 있다** — 사용자 지시가
 *   *"UX에서 지우되 피처로 코드는 남겨두기"* 였다. 지워 버리면 되살릴 길이 사라진다.
 * ⓓ 가운데 칸이 빠졌으니 좌석 카드가 **폭을 반씩** 갖는다.
 */
class PlayControlsContractTest {

    private fun code(path: String): String =
        File("src/main/java/com/worksoc/goaicoach/$path").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun theConfirmModeIsForcedOffAtExactlyOnePlace() {
        val flags = code("ui/FeatureFlags.kt")
        assertTrue(
            "확인 모드 플래그가 없다(#143).",
            flags.contains("const val isPlayConfirmModeEnabled"),
        )
        assertTrue(
            "플래그가 꺼졌을 때 바로 착수로 돌려놓는 관문이 없다 — 저장값이 `false`인 사용자가 돌을 못 둔다(#143).",
            flags.contains("copy(isDirectPlayEnabled = true)"),
        )

        val shell = code("ui/GoCoachApp.kt")
        assertTrue(
            "셸이 저장값을 옮기는 그 자리에서 관문을 통과시키지 않는다 — 읽는 곳마다 분기하면 반드시 하나를 빠뜨린다(#143).",
            shell.contains("toKaTrainUxOptions().withPlayConfirmModeGate()"),
        )
    }

    @Test
    fun theBoardNoLongerCarriesTheTwoToggles() {
        val play = code("ui/GamePlaySection.kt")
        listOf("BoardTopControls(", "BoardTopToggle(").forEach { call ->
            assertFalse(
                "대국 화면이 아직 `$call`을 그린다 — #143은 그 둘을 메뉴로 옮겼다.",
                play.contains(call),
            )
        }

        val menu = code("ui/KaTrainUxPanels.kt")
        assertTrue(
            "메뉴에 착수 돋보기 스위치가 없다 — 판에서 뺐는데 메뉴에도 없으면 **끌 방법이 사라진다**(#143).",
            menu.contains("options.isPlayMagnifierEnabled"),
        )
        assertTrue(
            "메뉴에 바둑판 크기 스위치가 없다 — 판에서 뺐는데 메뉴에도 없으면 끌 방법이 사라진다(#143).",
            menu.contains("options.isBoardMaxSize"),
        )
    }

    /** ⚠️ 지운 셋이 **플래그 뒤에 살아 있어야** 한다 — 사용자 지시는 "지우되 코드는 남겨두기"였다. */
    @Test
    fun whatWasRemovedFromTheUxStillLivesBehindTheFlag() {
        val gate = "FeatureFlags.isPlayConfirmModeEnabled"
        val status = code("ui/GameStatusPanel.kt")
        assertTrue("착수 칸이 플래그 뒤에 있지 않다(#143).", status.contains("if ($gate) PlaySlot("))
        assertTrue("착수 칸 코드 자체가 사라졌다 — 플래그를 켜도 되살아나지 않는다(#143).", status.contains("internal fun PlaySlot("))
        assertTrue("착수 모드 스위치 코드가 사라졌다(#143).", status.contains("private fun PlayModeSwitch("))

        val menu = code("ui/KaTrainUxPanels.kt")
        assertTrue("메뉴의 `바로 착수` 스위치가 플래그 뒤에 있지 않다(#143).", menu.contains("if ($gate)"))
        assertTrue("메뉴의 `바로 착수` 스위치 코드가 사라졌다(#143).", menu.contains("strings.directPlay"))

        val shell = code("ui/GoCoachApp.kt")
        assertTrue(
            "판 크기별 착수 모드 권장 팝업이 플래그 뒤에 있지 않다 — 없는 기능을 묻는 팝업이 된다(#143).",
            shell.contains("if ($gate)") && shell.contains("DirectPlayRecommendationDialog("),
        )
    }

    @Test
    fun theSeatCardsSplitTheRowEvenly() {
        val status = code("ui/GameStatusPanel.kt")
        assertFalse(
            "좌석 카드가 아직 가운데 칸에 폭을 떼어 주고 있다(`weight(1.3f)`) — #143이 그 칸을 뺐다.",
            status.contains("Modifier.weight(1.3f)"),
        )
        // ⚠️ **호출부만 센다** — `PlayerSeatCard(`는 선언까지 셋이 잡히고, 선언의 매개변수 목록에는
        //   당연히 `weight`가 없다(이 그물이 처음에 그래서 빨갛게 떴다).
        val seatCalls = status.split("PlayerSeatCard(")
            .drop(1)
            .count { it.trimStart().startsWith("modifier = Modifier.weight(1f)") }
        assertEquals("좌석 카드 둘이 폭을 반씩 갖지 않는다(#143).", 2, seatCalls)
    }
}
