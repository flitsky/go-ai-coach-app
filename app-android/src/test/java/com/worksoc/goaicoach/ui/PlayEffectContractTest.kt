package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #145 — **착수 이펙트**의 소스 계약. 다섯 다 깨져도 컴파일은 멀쩡하고 화면을 봐야만 드러난다.
 *
 * ⓐ **새 수에만 붙는다**(착수 전 결정: 사람·AI 착수만). 가르는 조건이 **둘 다** 있어야 한다 —
 *   수가 **정확히 하나** 늘었는가, 마지막이 `Move.Play`인가. 하나라도 빠지면 무르기나 저장 대국
 *   이어받기에서 돌이 뛴다.
 * ⓑ 배율은 **그리기 람다 안에서만** 읽는다 — 컴포지션 본문에서 읽으면 이펙트가 도는 내내 화면
 *   전체가 리컴포즈된다(#144의 `pendingPlayProgress`가 같은 이유로 람다다).
 * ⓒ **방금 놓인 돌에만** 붙는다 — 좌표를 대조하지 않으면 판의 모든 돌이 함께 뛴다.
 * ⓓ 새 설정값이 **자동저장 조립부에 배선**돼 있다(함정 2번) — 빠지면 대국 설정을 한 번 만지는 순간
 *   사용자가 켠 값이 조용히 되돌아간다.
 * ⓔ **기본값은 켜짐**이다(착수 전 결정) — 없던 것이 생기는 쪽이라 #144(기본 꺼짐)와 반대다.
 */
class PlayEffectContractTest {

    private fun code(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val board = code("src/main/java/com/worksoc/goaicoach/ui/GoBoard.kt")

    @Test
    fun onlyAFreshMoveGetsTheEffect() {
        assertTrue(
            "수가 늘어난 것을 보고 있지 않다 — 무엇에 반응해 튀는지가 없다(#145).",
            board.contains("LaunchedEffect(gameState.moves.size)"),
        )
        assertTrue(
            "수가 **정확히 하나** 는 경우만 가르지 않는다 — 무르기·이어받기에서도 돌이 뛴다(#145).",
            board.contains("if (current != previous + 1) return@LaunchedEffect"),
        )
        assertTrue(
            "마지막 수가 `Move.Play`인지 보지 않는다 — 통과(좌표 없음)에서 엉뚱한 곳이 뛴다(#145).",
            board.contains("gameState.moves.lastOrNull() as? Move.Play ?: return@LaunchedEffect"),
        )
        assertTrue(
            "설정이 꺼져 있어도 튄다 — 끌 방법이 사라진다(#145).",
            board.contains("if (!uxOptions.isPlayEffectEnabled) return@LaunchedEffect"),
        )
    }

    /** ⚠️ 첫 컴포지션에서 튀면 **대국을 이어받을 때마다** 마지막 돌이 한 번 뛴다. */
    @Test
    fun resumingAGameDoesNotPop() {
        assertTrue(
            "이미 본 수를 **현재 수로** 시작하지 않는다 — 첫 컴포지션에서 이전 값이 0이면 이어받기마다 돌이 뛴다(#145).",
            board.contains("var seenMoveCount by remember { mutableIntStateOf(gameState.moves.size) }"),
        )
    }

    @Test
    fun theScaleIsReadInsideTheDrawLambdaAndOnlyForThePlacedStone() {
        assertTrue(
            "방금 놓인 돌인지 대조하지 않는다 — 판의 돌이 전부 함께 뛴다(#145).",
            board.contains("if (coordinate == playEffectAt) playEffectScale.value else 1f"),
        )
        assertTrue(
            "그린 반지름에 배율이 곱해지지 않는다 — 값만 돌고 화면은 그대로다(#145).",
            board.contains("geometry.spacing * 0.42f * effectScale"),
        )
        // ⚠️ 배율을 **컴포지션 본문**에서 값으로 꺼내 두면(`val x = playEffectScale.value` 같은 꼴)
        //   이펙트가 도는 내내 화면 전체가 리컴포즈된다. 읽는 자리는 그리기 람다 한 곳이어야 한다.
        assertFalse(
            "배율을 컴포지션 본문에서 값으로 꺼냈다 — 이펙트 내내 화면 전체가 리컴포즈된다(#145).",
            Regex("""val\s+\w+\s*=\s*playEffectScale\.value""").containsMatchIn(board),
        )
    }

    /**
     * ⚠️ **착수 표시 테두리도 돌과 같은 배율로 커진다**(2026-09-12 사용자 요청).
     *
     * 돌만 부풀면 테두리가 **돌 안으로 파고들어** 커지는 내내 표시가 어긋나 보인다. 둘은 같은
     * 순간에 같은 배율이어야 한다 — 한쪽만 고치면 컴파일도 테스트도 멀쩡하고 화면에서만 드러난다.
     */
    @Test
    fun theLastMoveRingGrowsWithTheStone() {
        assertTrue(
            "착수 표시 테두리가 이펙트 배율을 쓰지 않는다 — 돌만 커지고 테두리는 제자리다(#145).",
            board.contains("val ringScale = if (lastMove.coordinate == playEffectAt) playEffectScale.value else 1f"),
        )
        assertTrue(
            "테두리 반지름에 배율이 곱해지지 않는다 — 값만 돌고 테두리는 그대로다(#145).",
            board.contains("geometry.spacing * 0.48f * ringScale"),
        )
    }

    /** ⚠️ 함정 2번 — 자동저장 조립부에 없는 필드는 다음 저장에서 조용히 기본값으로 돌아간다. */
    @Test
    fun theNewOptionSurvivesAnAutosave() {
        val autosave = code(
            "../shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/preferences/UserPreferencesAutosaveApplication.kt",
        )
        assertTrue(
            "자동저장 요청에 착수 이펙트가 없다 — 설정을 한 번 만지면 사용자가 끈 값이 되살아난다(함정 2번).",
            autosave.contains("val isPlayEffectEnabled"),
        )
        assertTrue(
            "자동저장 조립부가 착수 이펙트를 넘기지 않는다(함정 2번).",
            autosave.contains("isPlayEffectEnabled = request.isPlayEffectEnabled"),
        )
        val shell = code("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        assertTrue(
            "셸이 자동저장 요청에 착수 이펙트를 싣지 않는다(함정 2번).",
            shell.contains("isPlayEffectEnabled = uxOptions.isPlayEffectEnabled"),
        )
    }

    @Test
    fun theDefaultIsOnAndTheMenuCanTurnItOff() {
        val snapshot = code(
            "../shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/preferences/UserPreferencesSnapshot.kt",
        )
        assertTrue(
            "착수 이펙트 기본값이 켜짐이 아니다 — 착수 전 결정은 **켜짐**이다(#145).",
            snapshot.contains("val isPlayEffectEnabled: Boolean = true"),
        )
        val store = code("src/main/java/com/worksoc/goaicoach/persistence/UserPreferencesStore.kt")
        assertTrue("저장소가 착수 이펙트를 쓰지 않는다(#145).", store.contains(""""isPlayEffectEnabled", snapshot.isPlayEffectEnabled"""))
        assertTrue(
            "저장소가 착수 이펙트를 **켜짐** 기본값으로 읽지 않는다 — 키가 없던 기존 사용자에게 꺼진 채로 남는다(#145).",
            store.contains("""json.optBoolean("isPlayEffectEnabled", true)"""),
        )
        val menu = code("src/main/java/com/worksoc/goaicoach/ui/KaTrainUxPanels.kt")
        assertTrue(
            "메뉴에 착수 이펙트 스위치가 없다 — 기본이 켜짐인데 끌 방법이 없다(#145).",
            menu.contains("options.isPlayEffectEnabled") && menu.contains("strings.playEffect"),
        )
    }
}
