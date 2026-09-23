package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **AI가 앉은** 대국을 엔진이 준비되기 전에 시작할 수 없게 하는 게이트(백로그 #101 0단계).
 *
 * ⚠️ **게이트를 빼도 다른 테스트는 전부 초록이다.** 강등 자체는 `runStartConfiguredGame`의
 * 정상 동작이고(`!isEngineReady`면 로컬 2인으로), 그 결과가 나쁜 것은 **화면 쪽 사정**이다 —
 * `playerSetup`이 HumanVsAi 그대로라 `canAcceptBoardInput`이 false가 되어 **터치가 죽은 판**이
 * 된다. 순수 함수는 옳고 조합이 나쁜, #96과 같은 모양이다.
 *
 * ## ⚠️ 2026-09-23: 게이트를 **좌석 조건까지 포함해** 고정한다
 *
 * 처음에는 `enabled = engineReady` 한 항만 고정했는데, 그 잠금이 **사람끼리 두는 대국까지**
 * 막고 있었다(위 사유는 AI 좌석이 있을 때만 성립한다). 엔진이 끝내 못 뜨는 기기에서
 * `EngineUnavailableNoticeDialog`가 *"사람끼리 두는 대국은 그대로 쓸 수 있어요"* 라고 띄우는데
 * 실제로는 한 판도 시작할 수 없었다 — `NewGameBoardTapSmokeTest`가 그 구멍을 드러냈다.
 *
 * 그래서 이 계약은 이제 **두 방향을 함께** 못박는다: AI 좌석이면 잠기고, 사람끼리면 열린다.
 * 한쪽만 고정하면 반대쪽으로 다시 미끄러진다.
 */
class EngineReadyGateContractTest {

    private val lobby = RepoPaths.uiFile("GameSetupLobby.kt").readContractSource()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun theStartButtonIsDisabledUntilTheEngineIsReady() {
        assertTrue(
            "대국 시작 버튼이 엔진 준비 여부를 보지 않는다 — 준비 전에 누르면 AI 대국이 조용히 " +
                "로컬 2인으로 강등되고 사용자는 터치가 죽은 판 앞에 앉는다(#101).",
            lobby.contains("val engineReady = screenState.engine.isReady"),
        )
        val gate = lobby.indexOf("enabled = canStartMatch")
        val click = lobby.indexOf("GameUiEvent.StartConfiguredGame")
        assertTrue("버튼에 `enabled = canStartMatch`가 없다(#101).", gate >= 0)
        assertTrue("시작 디스패치를 찾지 못했다 — 이 계약의 전제가 무너졌다.", click >= 0)
        assertTrue("게이트가 시작 디스패치보다 뒤에 있다 — 같은 버튼이 아닐 수 있다(#101).", gate < click)
        assertTrue(
            "`canStartMatch`가 엔진 준비 여부에서 오지 않는다 — AI 좌석이 엔진 없이 시작된다(#101).",
            lobby.contains("val canStartMatch = engineReady || !needsEngine"),
        )
    }

    /**
     * ⚠️ 사람끼리 두는 대국은 엔진을 기다릴 이유가 없다 — 엔진이 끝내 못 뜨는 기기에서
     * `EngineUnavailableNoticeDialog`의 *"사람끼리 두는 대국은 그대로 쓸 수 있어요"* 를
     * **실제로 지키는** 조건이 이것 하나다(2026-09-23).
     */
    @Test
    fun localTwoPlayerStartsWithoutWaitingForTheEngine() {
        assertTrue(
            "게이트가 좌석을 보지 않는다 — 엔진이 못 뜨면 사람끼리도 한 판을 못 둔다.",
            lobby.contains("val needsEngine = screenState.matchMode != MatchMode.LocalTwoPlayer"),
        )
    }

    /** ⚠️ 잠긴 이유를 말해야 한다 — 이유 없이 안 눌리는 버튼은 고장으로 읽힌다. */
    @Test
    fun theDisabledButtonExplainsItself() {
        assertTrue(
            "버튼이 잠겼을 때 사유를 띄우지 않는다(#101).",
            lobby.contains("strings.engineNotReadyToStart"),
        )
        assertTrue(
            "사유 문구의 조건이 버튼과 다르다 — 눌리는 버튼 밑에 '잠겼다'는 안내가 붙는다.",
            lobby.contains("if (!canStartMatch) {"),
        )
    }
}
