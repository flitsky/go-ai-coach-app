package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 진행 중 대국의 판 크기·접바둑 잠금이 **어디에 걸려 있는지** 못박는 소스 계약(백로그 #75).
 *
 * ⚠️ **이 파일들에는 단위 테스트가 하나도 없다**(#76에서 확인했다) — 배선이 끊겨도 1000건이 전부
 * 초록이다. 그래서 소스로 못박는다.
 *
 * ⚠️ 주석은 걷어내고 본다([codeOnly]) — 처방이 KDoc에도 그대로 적혀 있어 걷어내지 않으면
 * **코드를 되돌려도 주석만 보고 통과한다**(#63에서 실제로 거짓 통과가 났다).
 */
class BoardSetupLockContractTest {

    private val settings = codeOnly(sourceOf("SettingsScreen.kt"))
    private val lobby = codeOnly(sourceOf("GameSetupLobby.kt"))
    private val panel = codeOnly(sourceOf("CompactScoringAndBoardSettingsPanel.kt"))
    private val gameMenu = codeOnly(sourceOf("GameMenuSection.kt"))

    /**
     * ⚠️ **대국 화면 메뉴는 조건 없이 잠근다**(2026-09-10 사용자 결정). 그 화면에 있다는 것
     * 자체가 *"대국 중"* 이므로 정책 판정을 쓸 자리가 아니다.
     *
     * 여기에 `isBoardSetupLockedDuringGame`을 되돌려 놓으면 **0수(막 시작해 아직 안 둔 판)에서
     * 넷이 열리고 한 수 두는 순간 잠기는** 경계가 되살아난다 — 사용자가 실제로 제보한 그
     * 증상이다(*"시작 직후엔 열리는데 한 수 두면 계가·덤만 열린다"*).
     */
    @Test
    fun theGameMenuLocksMatchSetupUnconditionally() {
        assertTrue(
            "대국 화면 메뉴가 `canChangeMatchSetup = false`를 넘기지 않는다 — 0수에서 다시 열린다" +
                "(2026-09-10 사용자 결정).",
            gameMenu.contains("canChangeMatchSetup = false"),
        )
        assertTrue(
            "대국 화면 메뉴가 잠금 정책 판정을 다시 쓰고 있다 — 그 함수의 `moveCount > 0` 조건이 " +
                "0수를 열어 버린다. 이 화면에서는 조건 없이 잠근다.",
            !gameMenu.contains("isBoardSetupLockedDuringGame"),
        )
    }

    /**
     * ⚠️ **설정 화면은 잠금 판정을 넘겨야 한다.** 넘기지 않으면 패널 기본값이 `true`라
     * **조용히 예전 동작(항상 열림)으로 돌아간다** — 기본값이 있는 파라미터라 컴파일도 통과한다.
     */
    @Test
    fun theSettingsScreenPassesTheLockDecision() {
        assertTrue(
            "설정 화면이 `canChangeMatchSetup`을 넘기지 않는다 — 패널 기본값이 true라 " +
                "잠금이 조용히 사라진다(#75).",
            settings.contains("canChangeMatchSetup = !isBoardSetupLockedDuringGame("),
        )
        assertTrue(
            "잠금 판정에 둔 수를 넘기지 않는다(#75).",
            settings.contains("moveCount = screenState.gameState.moves.size"),
        )
        assertTrue(
            "잠금 판정에 종국 여부를 넘기지 않는다(#75).",
            settings.contains("isGameEnded = screenState.isGameEnded"),
        )
        // ⚠️ **저장된 대국도 넘겨야 한다** — 안 넘기면 앱을 껐다 켜는 것만으로 잠금이 우회된다
        // (2026-09-05 실기에서 실제로 밟았다). 기본값이 `false`라 **컴파일은 통과한다.**
        assertTrue(
            "저장된 대국 여부를 넘기지 않는다 — 재시작만으로 잠금이 우회된다(#75).",
            settings.contains("hasResumableSavedGame = resumableSavedGame"),
        )
        assertTrue(
            "저장된 대국 판정을 `isResumable`로 하지 않는다 — 홈 화면의 '이어하기'와 같은 " +
                "판정이어야 한다(#75).",
            settings.contains("GameSessionStore(context).load()?.isResumable == true"),
        )
    }

    /**
     * ⚠️ **판정을 화면에서 인라인으로 다시 쓰면 안 된다.** `isGameEnded` 하나로 판단하는 것이
     * 이 항목에서 가장 밟기 쉬운 실수이고(대국을 한 번도 안 한 사용자에게도 잠긴다),
     * 순수 함수로 빼 둔 이유가 **그 실수에 테스트를 붙이기 위해서**다.
     */
    @Test
    fun theLockDecisionIsNotReimplementedInTheScreen() {
        assertFalse(
            "설정 화면이 `isGameEnded`만 보고 직접 잠금을 판단한다 — 대국을 한 번도 하지 않은 " +
                "사용자에게도 잠긴다. `isBoardSetupLockedDuringGame`을 쓸 것(#75).",
            settings.contains("canChangeMatchSetup = screenState.isGameEnded") ||
                settings.contains("canChangeMatchSetup = !screenState.isGameEnded"),
        )
    }

    /**
     * ⚠️ **로비는 잠그지 않는다.** 사용자 결정의 근거가 *"대국 시작할 때 하면 될 일"* 이었으므로,
     * **로비가 바로 그 자리**다. 여기까지 잠그면 설정을 바꿀 곳이 아예 없어진다.
     */
    @Test
    fun theLobbyIsNeverLocked() {
        assertTrue("로비가 콤팩트 패널을 그리지 않는다 — 이 계약의 전제가 무너졌다.",
            lobby.contains("CompactScoringAndBoardSettingsPanel("))
        assertFalse(
            "로비까지 판 크기·접바둑을 잠갔다 — 그러면 바꿀 수 있는 자리가 사라진다(#75).",
            lobby.contains("canChangeMatchSetup"),
        )
    }

    /**
     * ⚠️ **2026-09-10에 뒤집혔다 — 이제 네 칸 전부를 잠근다.**
     *
     * 그전에는 *"판의 모양을 바꾸는 것과 셈법을 바꾸는 것은 뜻이 다르다"* 는 이유로 계가·덤을
     * 열어 두고 이 테스트가 **둘**을 못박고 있었다. 실사용이 그 구분을 무너뜨렸다(사용자 제보):
     * 넷이 같은 베이지 칸이라 활성/비활성이 글자 농도로만 갈려 **넷 다 잠긴 것처럼 보이는데
     * 둘만 열렸고**, 덤을 바꾸면 이미 둔 판의 점수가 소급해 바뀐다.
     *
     * ⚠️ 되돌리려는 사람에게: 숫자만 2로 낮추지 말 것. 그러면 그 시절의 **혼동이 그대로**
     * 돌아온다 — 되살리려면 네 칸의 활성/비활성이 **한눈에 구분되는 시각**부터 마련할 것.
     */
    @Test
    fun allFourMatchSetupCellsAreGated() {
        assertEquals(
            "게이팅된 셀이 넷이 아니다 — 계가·덤을 다시 열어 뒀는지 볼 것(2026-09-10 사용자 결정).",
            4,
            panel.split("enabled = canChangeMatchSetup").size - 1,
        )
    }

    /**
     * ⚠️ **잠근 이유를 화면에 말해야 한다.** 눌러도 안 열리는 칸을 이유 없이 두면 고장으로 읽히고,
     * 이 항목을 만든 계기 자체가 *"바꿨는데 왜 그대로지"* 라는 어긋남이었다.
     */
    @Test
    fun theLockExplainsItself() {
        assertTrue(
            "잠겼을 때 사유 문구를 띄우지 않는다 — 이유 없는 잠금은 고장으로 읽힌다(#75).",
            panel.contains("strings.matchSetupLockedDuringGame"),
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
