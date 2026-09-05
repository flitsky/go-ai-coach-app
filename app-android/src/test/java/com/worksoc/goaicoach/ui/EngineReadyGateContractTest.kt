package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 엔진이 준비되기 전에 대국을 시작할 수 없게 하는 게이트(백로그 #101 0단계).
 *
 * ⚠️ **게이트를 빼도 다른 테스트는 전부 초록이다.** 강등 자체는 `runStartConfiguredGame`의
 * 정상 동작이고(`!isEngineReady`면 로컬 2인으로), 그 결과가 나쁜 것은 **화면 쪽 사정**이다 —
 * `playerSetup`이 HumanVsAi 그대로라 `canAcceptBoardInput`이 false가 되어 **터치가 죽은 판**이
 * 된다. 순수 함수는 옳고 조합이 나쁜, #96과 같은 모양이다.
 */
class EngineReadyGateContractTest {

    private val lobby = File("src/main/java/com/worksoc/goaicoach/ui/GameSetupLobby.kt").readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun theStartButtonIsDisabledUntilTheEngineIsReady() {
        assertTrue(
            "대국 시작 버튼이 엔진 준비 여부를 보지 않는다 — 준비 전에 누르면 AI 대국이 조용히 " +
                "로컬 2인으로 강등되고 사용자는 터치가 죽은 판 앞에 앉는다(#101).",
            lobby.contains("val engineReady = screenState.engine.isReady"),
        )
        val gate = lobby.indexOf("enabled = engineReady")
        val click = lobby.indexOf("GameUiEvent.StartConfiguredGame")
        assertTrue("버튼에 `enabled = engineReady`가 없다(#101).", gate >= 0)
        assertTrue("시작 디스패치를 찾지 못했다 — 이 계약의 전제가 무너졌다.", click >= 0)
        assertTrue("게이트가 시작 디스패치보다 뒤에 있다 — 같은 버튼이 아닐 수 있다(#101).", gate < click)
    }

    /** ⚠️ 잠긴 이유를 말해야 한다 — 이유 없이 안 눌리는 버튼은 고장으로 읽힌다. */
    @Test
    fun theDisabledButtonExplainsItself() {
        assertTrue(
            "버튼이 잠겼을 때 사유를 띄우지 않는다(#101).",
            lobby.contains("strings.engineNotReadyToStart"),
        )
    }
}
