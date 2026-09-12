package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #144 — **지연 착수**의 소스 계약. 넷 다 깨져도 컴파일은 멀쩡하고 화면을 봐야만 드러난다.
 *
 * ⓐ 누를 때마다 **새 번호**를 발급한다 — 같은 자리를 다시 눌러도 처음부터 다시 세야 한다(사용자 결정).
 *   좌표만 들고 있으면 키가 안 바뀌어 **타이머가 조용히 재시작되지 않는다.**
 * ⓑ 판이 바뀌면 대기를 **버린다** — 무르기·기권·종국·AI 착수 뒤에 돌이 하나 더 떨어지면 안 된다.
 * ⓒ 진해지는 것과 놓이는 시점이 **하나의 애니메이션**이다 — 둘로 나누면 서로 어긋난다.
 * ⓓ 새 설정값이 **자동저장 조립부에 배선**돼 있다(함정 2번) — 빠지면 대국 설정을 한 번 만지는 순간
 *   사용자가 켠 옵션이 조용히 꺼진다.
 */
class DelayedPlayContractTest {

    private fun code(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val section = code("src/main/java/com/worksoc/goaicoach/ui/GamePlaySection.kt")

    @Test
    fun everyTapIssuesAFreshTokenSoTheSameSpotRestartsTheTimer() {
        assertTrue(
            "대기 상태가 번호를 들고 있지 않다 — 같은 자리를 다시 눌러도 타이머가 재시작되지 않는다(#144).",
            section.contains("private data class PendingPlay(val coordinate: BoardCoordinate, val token: Int)"),
        )
        assertTrue(
            "누를 때마다 번호를 새로 발급하지 않는다(#144).",
            section.contains("pendingPlaySeq += 1") &&
                section.contains("pendingPlay = PendingPlay(coordinate, pendingPlaySeq)"),
        )
        assertTrue(
            "대기 상태를 키로 타이머를 걸지 않는다 — 새 번호가 재시작을 일으키지 못한다(#144).",
            section.contains("LaunchedEffect(pendingPlay)"),
        )
    }

    @Test
    fun aChangedBoardThrowsThePendingPlayAway() {
        val onGameState = section.substring(
            section.indexOf("LaunchedEffect(screenState.gameState)"),
            section.indexOf("LaunchedEffect(pendingPlay)"),
        )
        assertTrue(
            "판이 바뀔 때 대기를 버리지 않는다 — 끝난 판이나 남의 차례에 돌이 하나 더 떨어진다(#144).",
            onGameState.contains("pendingPlay = null"),
        )
    }

    @Test
    fun oneAnimationIsBothTheDarkeningAndTheTimer() {
        val timer = section.substring(section.indexOf("LaunchedEffect(pendingPlay)"))
            .substringBefore("val boardPremium")
        assertTrue(
            "0.5초를 따로 재고 있다 — 진해짐과 착수 시점이 어긋난다(#144).",
            timer.contains("pendingPlayProgress.animateTo(") &&
                timer.contains("DelayedPlayWindowMillis.toInt()"),
        )
        assertTrue(
            "기다린 뒤 실제로 놓지 않는다(#144).",
            timer.contains("onEvent(GameUiEvent.PlayAt(waiting.coordinate))"),
        )
        assertFalse(
            "`delay(...)`로 따로 세고 있다 — 애니메이션과 둘로 갈리면 어긋난다(#144).",
            timer.contains("delay(DelayedPlayWindowMillis"),
        )

        val board = code("src/main/java/com/worksoc/goaicoach/ui/GoBoard.kt")
        assertTrue(
            "판이 진행도를 **람다로** 받지 않는다 — 값으로 받으면 0.5초 내내 화면 전체가 리컴포즈된다(#144).",
            board.contains("pendingPlayProgress: () -> Float"),
        )
        assertTrue("대기 중인 돌을 그리지 않는다(#144).", board.contains("if (pendingPlay != null)"))
    }

    /** ⚠️ 함정 2번 — 자동저장 조립부에 없는 필드는 다음 저장에서 조용히 기본값(꺼짐)으로 돌아간다. */
    @Test
    fun theNewOptionSurvivesAnAutosave() {
        val autosave = code(
            "../shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/preferences/UserPreferencesAutosaveApplication.kt",
        )
        assertTrue(
            "자동저장 요청에 지연 착수가 없다 — 설정을 한 번 만지면 사용자가 켠 옵션이 꺼진다(함정 2번).",
            autosave.contains("val isDelayedPlayEnabled"),
        )
        assertTrue(
            "자동저장 조립부가 지연 착수를 넘기지 않는다(함정 2번).",
            autosave.contains("isDelayedPlayEnabled = request.isDelayedPlayEnabled"),
        )
        val shell = code("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        assertTrue(
            "셸이 자동저장 요청에 지연 착수를 싣지 않는다(함정 2번).",
            shell.contains("isDelayedPlayEnabled = uxOptions.isDelayedPlayEnabled"),
        )
        val store = code("src/main/java/com/worksoc/goaicoach/persistence/UserPreferencesStore.kt")
        assertTrue("저장소가 지연 착수를 쓰지 않는다(#144).", store.contains(""""isDelayedPlayEnabled", snapshot.isDelayedPlayEnabled"""))
        assertTrue(
            "저장소가 지연 착수를 **꺼짐** 기본값으로 읽지 않는다 — 기본값은 꺼짐이다(사용자 결정).",
            store.contains("""json.optBoolean("isDelayedPlayEnabled", false)"""),
        )
    }

    /**
     * ⚠️ **실기 결함(2026-09-12 사용자)** — 대기 중 다른 곳을 눌렀는데도 **옛 자리가 확정**됐다.
     * 0.5초가 손가락이 **닿아 있는 동안** 끝나 버렸기 때문이다(손은 누르고 떼는 데 0.5초를 쉽게 넘긴다).
     *
     * 규칙: **카운트는 판에서 손이 떨어져 있을 때만 돈다.** 누르는 순간 대기를 버리고, 떼는 순간
     * `onCoordinateTap`이 새 번호로 다시 센다.
     *
     * ⚠️ 합성 탭(`adb input tap`)은 down→up이 몇 ms라 이 결함을 **못 잡는다** — 그래서 그때 실기가
     * 통과해 버렸다. 눈이 아니라 이 그물이 지켜야 하는 이유다.
     */
    @Test
    fun aNewPressStopsTheCountdownSoTheOldSpotIsNotConfirmed() {
        val board = code("src/main/java/com/worksoc/goaicoach/ui/GoBoard.kt")
        assertTrue(
            "판이 누르는 순간을 바깥에 알리지 않는다 — 새 자리를 누르고 있는 사이에 0.5초가 끝나 옛 자리가 확정된다(#144).",
            board.contains("onCoordinatePress: () -> Unit"),
        )
        assertTrue(
            "누르는 순간 알리지 않는다 — 통로만 있고 부르지 않으면 아무것도 멈추지 않는다(#144).",
            Regex("""down\.consume\(\)\s*onCoordinatePress\(\)""").containsMatchIn(board),
        )
        assertTrue(
            "지연 착수 설정이 제스처 루프의 키에 없다 — 대국 중 켜고 꺼도 즉시 반영되지 않는다(#144).",
            board.contains("uxOptions.isDelayedPlayEnabled,"),
        )
        assertTrue(
            "누르는 순간 대기를 버리지 않는다 — 타이머가 계속 돌아 옛 자리가 확정된다(#144).",
            section.contains("onCoordinatePress = { pendingPlay = null }"),
        )
    }
}
