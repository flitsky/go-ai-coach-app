package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **대국 조건 패널 하나는 출처 하나만 본다**(refactor backlog #94) — 소스 계약.
 *
 * 대국 조건(판 크기·접바둑·덤)은 두 곳에 있다. **다음 대국**의 조건은 설정 상태(`GameSessionSettingsState`)에,
 * **지금 판**의 조건은 `gameState`에. 로비에서 시작한 판이면 두 값이 같아 어느 쪽을 읽어도 같아 보인다.
 * 이어하기·분기 대국·앱 시작 때 되살린 끝난 판은 로비를 거치지 않아 **둘이 다르고**, 그때 한 패널이
 * 두 곳을 섞어 읽으면 *"앞 판의 판 크기·덤 + 설정의 접바둑"* 처럼 어느 판에도 없는 조합이 보인다.
 *
 * - 로비·설정 화면은 **다음 대국**을 고르는 자리다 → 설정 상태만 읽는다.
 * - 대국 화면 메뉴는 **지금 판**을 보여 주는 자리다(늘 잠겨 있다) → `gameState`만 읽는다.
 * - 자동저장은 **설정**을 적는다 → 지금 판의 덤을 적으면 이어한 판의 덤이 사용자의 설정이 된다.
 *
 * ⚠️ 계가 규칙(`ruleset`)은 **설정 상태에 칸이 없어** 아직 `gameState` 하나가 출처다 — 이 계약의 대상이
 * 아니다(#94 계획 §5, #22로 넘김).
 *
 * ⚠️ 주석은 걷어내고 본다 — 처방이 KDoc에도 적혀 있어 걷어내지 않으면 코드를 되돌려도 통과한다(#63).
 */
class MatchSetupSourceContractTest {

    private val shell = codeOnly(RepoPaths.goCoachApp.readContractSource())
    private val lobby = codeOnly(sourceOf("GameSetupLobby.kt"))
    private val settings = codeOnly(sourceOf("SettingsScreen.kt"))
    private val gameMenu = codeOnly(sourceOf("GameMenuSection.kt"))

    /**
     * 자동저장은 **설정 상태의 덤**을 적는다. 지금 판의 덤을 적으면, 이어하기·분기 대국 도중 앱이 죽었을 때
     * 그 판의 덤(6.5)이 설정(3점·0.5)과 함께 저장되고, 다음 실행부터 설정 화면·로비·새 대국이 전부
     * *"3점 + 6.5"* 가 된다. #93의 "이관 없음"이 그것을 사용자가 고른 값으로 존중해 버린다.
     */
    @Test
    fun theAutosaveNeverWritesTheLiveGamesKomi() {
        val call = shell.indexOf("runUserPreferencesAutosave(")
        assertTrue("자동저장 호출을 찾지 못했다 — 이 계약의 전제가 무너졌다.", call >= 0)
        val effect = shell.substring(
            shell.lastIndexOf("LaunchedEffect(", call),
            shell.indexOf("store = preferencesStore", call),
        )
        assertTrue("자동저장 요청이 설정 상태를 싣지 않는다 — 이 계약의 전제가 무너졌다.", effect.contains("settingsState = settingsState"))
        assertFalse(
            "자동저장이 지금 판의 덤(`gameState.komi`)을 읽는다 — 이어하기·분기 대국의 덤이 설정으로 저장된다(#94). " +
                "덤은 설정 상태(`settingsState.komi`)에서 온다.",
            effect.contains("gameState.komi"),
        )
    }

    /** 로비의 패널과 미리보기 판은 **설정 상태**를 그린다 — 끝난 판에서 「대국 설정」으로 와도 앞 판이 섞이지 않는다. */
    @Test
    fun theLobbyShowsTheSettingsNotTheLiveGame() {
        assertFalse(
            "로비가 지금 판의 판 크기를 그린다 — 끝난 이어하기·분기 대국에서 오면 앞 판의 크기가 보인다(#94).",
            lobby.contains("screenState.gameState.boardSize"),
        )
        assertFalse(
            "로비가 지금 판의 덤을 그린다 — 끝난 이어하기·분기 대국에서 오면 앞 판의 덤이 보인다(#94).",
            lobby.contains("screenState.gameState.komi"),
        )
        assertFalse(
            "로비의 미리보기 판이 지금 판을 그대로 그린다 — 끝난 판의 수순이 미리보기로 보인다(#94). " +
                "설정 상태로 빈 판을 만들어 그릴 것.",
            lobby.contains("gameState = screenState.gameState"),
        )
        assertTrue("로비 패널이 설정의 판 크기를 넘기지 않는다(#94).", lobby.contains("boardSize = screenState.setupBoardSize"))
        assertTrue("로비 패널이 설정의 접바둑을 넘기지 않는다.", lobby.contains("handicapCount = screenState.handicapCount"))
        assertTrue("로비 패널이 설정의 덤을 넘기지 않는다(#94).", lobby.contains("komi = screenState.setupKomi"))
    }

    /** 설정 화면도 **다음 대국의 기본값**을 고르는 자리다(#75의 사용자 판단) — 로비와 같은 출처를 그린다. */
    @Test
    fun theSettingsScreenShowsTheSettingsNotTheLiveGame() {
        assertFalse(
            "설정 화면이 지금 판의 판 크기를 그린다 — 새 대국은 설정의 판 크기로 시작한다(#94).",
            settings.contains("boardSize = screenState.gameState.boardSize"),
        )
        assertFalse(
            "설정 화면이 지금 판의 덤을 그린다 — 새 대국은 설정의 덤으로 시작한다(#94).",
            settings.contains("komi = screenState.gameState.komi"),
        )
        assertTrue("설정 화면 패널이 설정의 판 크기를 넘기지 않는다(#94).", settings.contains("boardSize = screenState.setupBoardSize"))
        assertTrue("설정 화면 패널이 설정의 덤을 넘기지 않는다(#94).", settings.contains("komi = screenState.setupKomi"))
    }

    /**
     * 대국 화면 메뉴는 **지금 판**을 보여 준다 — 네 칸이 다 `gameState`에서 와야 한다. 접바둑만 설정에서 읽으면
     * 설정이 3점일 때 이어한 호선 판의 메뉴에 *"3점"* 이 보인다.
     */
    @Test
    fun theInGameMenuShowsTheLiveGameAlone() {
        assertTrue(
            "대국 화면 메뉴가 지금 판의 접바둑을 그리지 않는다 — 이어한 판에 설정의 접바둑이 섞여 보인다(#94).",
            gameMenu.contains("handicapCount = screenState.gameState.handicapCount"),
        )
        assertFalse(
            "대국 화면 메뉴가 설정의 접바둑을 그린다 — 지금 판의 조건과 섞인다(#94).",
            gameMenu.contains("handicapCount = screenState.handicapCount"),
        )
    }

    private fun sourceOf(fileName: String): String = RepoPaths.uiFile(fileName).readContractSource()

    /** 주석을 걷어낸 코드만 남긴다. 여러 줄 KDoc을 반드시 지워야 한다. */
    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .joinToString("\n") { it.substringBefore("//") }
}
