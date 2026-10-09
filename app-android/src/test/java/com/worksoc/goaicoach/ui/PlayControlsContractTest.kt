package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.play.PlayStoneFace
import com.worksoc.goaicoach.ui.play.playStoneFaceOf
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
 * ⓓ 좌석 카드 둘은 폭을 같게 갖는다(가운데 착수 칸이 있으면 셋이 나눈다).
 *
 * ⚠️ **#223(2026-10-08)이 확인 모드를 되살렸다** — 스위치는 켜져 있고, 위의 ⓐ·ⓒ는 "다시 끌 때도 안전하다"를 지키는 그물로 남는다.
 * 판 크기별 권장 팝업만 제 스위치로 갈라 꺼 두었다.
 */
class PlayControlsContractTest {

    private fun code(name: String): String =
        RepoPaths.uiFile(name).readContractSource()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun theConfirmModeIsForcedOffAtExactlyOnePlace() {
        val flags = code("FeatureFlags.kt")
        assertTrue(
            "확인 모드 플래그가 없다(#143).",
            flags.contains("const val isPlayConfirmModeEnabled"),
        )
        assertTrue(
            "플래그가 꺼졌을 때 바로 착수로 돌려놓는 관문이 없다 — 저장값이 `false`인 사용자가 돌을 못 둔다(#143).",
            flags.contains("copy(isDirectPlayEnabled = true)"),
        )

        val shell = code("GoCoachApp.kt")
        assertTrue(
            "셸이 저장값을 옮기는 그 자리에서 관문을 통과시키지 않는다 — 읽는 곳마다 분기하면 반드시 하나를 빠뜨린다(#143).",
            shell.contains("toKaTrainUxOptions().withPlayConfirmModeGate()"),
        )
    }

    /**
     * 판 위의 **바둑판 최대** 토글은 **돌아왔다**(2026-10-09 사용자) — #143이 메뉴로 옮겼던 것을 되살렸다. 오른쪽 끝에 하나만 선다
     * (왼쪽의 착수 돋보기는 #188에서 기능째 사라졌고, 그 자리에 넣어 봤던 「손끝 돌 크게」(그때 이름은 「끌 때 크게」)는 같은 날 뺐다).
     * 글자는 `바둑판 최대`로 고정이다 — 메뉴의 스위치와 같은 말이어야 한다. 메뉴의 스위치도 그대로 있어야 한다(같은 값).
     */
    @Test
    fun theBoardCarriesTheBoardSizeToggleAgainAndItSaysWhatTheMenuSays() {
        val play = code("GamePlaySection.kt")
        val controls = play.substringAfter("private fun BoardTopControls(").substringBefore("private fun BoardTopToggle(")
        assertTrue("폰 배치가 판 위 토글을 그리지 않는다.", play.contains("                BoardTopControls("))
        val menuLabel = "boardSizeToggleLabelFor(strings.language, isMaxSize = true)"
        assertTrue("판 위 칩의 글자가 고정이 아니다 — 메뉴의 스위치와 다른 말을 한다.", controls.contains(menuLabel))
        assertTrue("메뉴의 스위치가 판 위 칩과 다른 글자를 쓴다.", code("KaTrainUxPanels.kt").contains(menuLabel))
        assertTrue("판 위 칩이 오른쪽 끝에 서지 않는다.", controls.contains("horizontalArrangement = Arrangement.End"))
        assertFalse("「손끝 돌 크게」가 판 위에 돌아왔다 — 사용자가 뺐다(2026-10-09).", play.contains("largeHeldStoneLabelFor"))
        assertFalse("착수 돋보기가 판 위에 돌아왔다 — 기능은 #188에서 통째로 걷어냈다.", play.contains("Magnifier"))

        val menu = code("KaTrainUxPanels.kt")
        // ⚠️ **착수 돋보기는 2026-09-22에 기능째 사라졌다**(백로그 #188) — 여기서 "메뉴에 있어야
        // 한다"고 지키던 것을, 이제 **어디에도 없어야 한다**로 뒤집는다. 판에서 뺐는데 끌 방법이
        // 사라지는 것이 #143의 걱정이었는데, 끌 것 자체가 없어졌다.
        assertFalse(
            "착수 돋보기가 메뉴에 돌아왔다 — 기능은 #188에서 통째로 걷어냈다.",
            menu.contains("isPlayMagnifierEnabled"),
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
        val status = code("GameStatusPanel.kt")
        assertTrue("착수 칸이 플래그 뒤에 있지 않다(#143).", status.contains("if ($gate) PlaySlot("))
        assertTrue("착수 칸 코드 자체가 사라졌다 — 플래그를 켜도 되살아나지 않는다(#143).", status.contains("internal fun PlaySlot("))
        assertTrue("착수 칸의 돌 버튼 코드가 사라졌다(#143).", status.contains("private fun PlayStoneButton("))

        val menu = code("KaTrainUxPanels.kt")
        assertTrue("메뉴의 `착수 확인` 스위치가 플래그 뒤에 있지 않다(#143).", menu.contains("if ($gate)"))
        assertTrue("메뉴의 `착수 확인` 스위치 코드가 사라졌다(#143).", menu.contains("strings.confirmPlay"))

        val shell = code("GoCoachApp.kt")
        assertTrue(
            "판 크기별 착수 모드 권장 팝업이 확인 모드와 제 스위치 둘 다의 뒤에 있지 않다 — 확인 모드가 꺼지면 없는 기능을 묻는 팝업이 된다(#143·#223).",
            shell.contains("if ($gate && FeatureFlags.isPlayModeRecommendationEnabled)") && shell.contains("DirectPlayRecommendationDialog("),
        )
    }

    /**
     * 백로그 #223(2026-10-08 사용자) — 확인 모드를 **되살렸다**: 착수 칸이 대국 화면에 돌아오고, 기본값은 바로 착수 그대로이며,
     * 판 크기별 권장 팝업은 되살리지 않는다. 셋 가운데 하나만 뒤집혀도 사용자가 정한 것과 달라진다.
     */
    @Test
    fun theConfirmModeIsBackWithDirectPlayAsTheDefaultAndWithoutTheSizePopup() {
        assertTrue("확인 모드가 꺼져 있다 — #223이 되살렸다.", com.worksoc.goaicoach.ui.foundation.FeatureFlags.isPlayConfirmModeEnabled)
        assertFalse("판 크기별 권장 팝업이 켜져 있다 — 사용자는 되살리지 않기로 했다(#223).", com.worksoc.goaicoach.ui.foundation.FeatureFlags.isPlayModeRecommendationEnabled)
        assertTrue("기본값이 바로 착수가 아니다(#223).", com.worksoc.goaicoach.presentation.KaTrainUxOptions().isDirectPlayEnabled)
        assertTrue(
            "저장된 설정의 기본값이 바로 착수가 아니다(#223).",
            com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot().isDirectPlayEnabled,
        )
    }

    /**
     * 착수 칸은 **돌 버튼 하나**다(2026-10-09 사용자 지시) — 모드를 켜고 끄다가, 착수 확인에서 가착수가 놓이면 그 수를 확정한다.
     * 바로 착수에서는 가착수가 있든 없든 확정 얼굴이 뜨지 않아야 한다(탭이 곧 착수인 모드다).
     */
    @Test
    fun theOnePlayStoneTogglesTheModeUntilAStoneWaitsToBeConfirmed() {
        assertEquals(PlayStoneFace.ConfirmOff, playStoneFaceOf(isDirectPlay = true, hasTentativeMove = false))
        assertEquals(PlayStoneFace.ConfirmOff, playStoneFaceOf(isDirectPlay = true, hasTentativeMove = true))
        assertEquals(PlayStoneFace.ConfirmOn, playStoneFaceOf(isDirectPlay = false, hasTentativeMove = false))
        assertEquals(PlayStoneFace.CommitMove, playStoneFaceOf(isDirectPlay = false, hasTentativeMove = true))

        val status = code("GameStatusPanel.kt")
        assertFalse("착수 모드 스위치가 돌 버튼과 따로 남아 있다 — 버튼은 하나다.", status.contains("fun PlayModeSwitch("))
        assertTrue(
            "돌 버튼이 판의 돌과 다른 그림을 쓴다 — 판의 돌 모양을 고치면 이 버튼만 옛 모양으로 남는다.",
            status.contains("drawStone(") && status.contains("drawGhostStone("),
        )
        assertTrue(
            "돌의 지름이 좌석 카드 높이의 70%가 아니다(2026-10-09 사용자 지시).",
            status.contains("private const val PlayStoneSeatHeightShare = 0.7f") &&
                status.contains(".fillMaxHeight(PlayStoneSeatHeightShare)") &&
                status.contains(".height(IntrinsicSize.Min)"),
        )

        // ⚠️ 글자는 `착수 확인` 하나로 고정이다(같은 날 사용자 지시) — 켜짐·꺼짐은 돌의 진하기가 말하고, **꺼진 쪽이 흐리다**.
        assertFalse("돌 버튼의 글자가 다시 모드마다 바뀐다.", status.contains("playModeDirect"))
        assertTrue(
            "꺼진 얼굴이 흐린 돌이 아니다 — 글자가 고정이라 진하기가 뒤집히면 켜짐·꺼짐이 거꾸로 읽힌다.",
            status.contains("if (face == PlayStoneFace.ConfirmOff) {\n                    drawGhostStone("),
        )
        // ⚠️ 바뀔 때마다 토스트가 뜬다 — 버튼이 아니라 값이 바뀐 자리에서(메뉴의 스위치로 바꿔도 같은 말이 떠야 한다).
        val play = code("GamePlaySection.kt")
        assertTrue(
            "착수 확인을 켜고 끌 때의 토스트가 값이 바뀐 자리에 있지 않다.",
            play.contains("confirmPlayToggledToastFor(confirmPlayToastLanguage, isConfirmOn = !isDirectPlay)"),
        )
        assertFalse("토스트를 돌 버튼이 직접 띄운다 — 메뉴로 바꾸면 말이 없다.", status.contains("Toast"))

        // ⚠️ 메뉴의 스위치는 **착수 확인을 켜는** 쪽이다 — 저장값(`isDirectPlayEnabled`)과 방향이 반대라 양쪽에서 뒤집어야 한다.
        val menu = code("KaTrainUxPanels.kt")
        assertTrue("메뉴 스위치의 켜짐이 착수 확인이 아니다.", menu.contains("checked = !options.isDirectPlayEnabled"))
        assertTrue("메뉴 스위치를 켜도 착수 확인이 되지 않는다.", menu.contains("options.copy(isDirectPlayEnabled = !it)"))
    }

    @Test
    fun theSeatCardsSplitTheRowEvenly() {
        val status = code("GameStatusPanel.kt")
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
