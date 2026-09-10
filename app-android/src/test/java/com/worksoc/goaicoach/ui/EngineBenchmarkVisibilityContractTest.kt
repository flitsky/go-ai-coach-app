package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * '엔진 성능 측정'을 누르면 **무언가는 보여야 한다**(2026-09-10 사용자 신고: *"눌러도 아무 반응 안함"*).
 *
 * ## 무엇이 잘못돼 있었나
 * 벤치마크의 출력 채널은 둘(`engineMessage`·`candidateText)뿐인데 **둘 다 화면에 닿지 않는다** —
 * 진단 리포트와 런타임 로그만 읽는다. 그래서 "보이는 것"은 오로지 팝업인데, 그 팝업이
 * `GoCoachContent`, 즉 **`ScreenDestination.InGame` 가지 안에서만** 그려지고 있었다.
 * 버튼은 설정 화면(개발자 섹션)에 있으니 **막힘·진행·성공·실패 넷 다** 아무것도 안 보였다.
 *
 * ## ⚠️ 이 그물이 지키는 것은 "어디서 그리는가"다
 * 컴파일도 초록이고 기존 테스트도 전부 초록이었다 — 팝업 컴포저블은 멀쩡했고, 그것을 **부르는
 * 자리**만 틀렸기 때문이다. 그래서 여기서는 소스의 **위치**를 읽는다.
 */
class EngineBenchmarkVisibilityContractTest {

    private fun sourceOf(name: String): String =
        File("src/main/java/com/worksoc/goaicoach/ui/$name").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { line -> line.substringBefore("//") }

    private val shell = sourceOf("GoCoachApp.kt")
    private val inGameContent = sourceOf("GoCoachContent.kt")

    @Test
    fun theShellDrawsTheBenchmarkOverlayOutsideTheDestinationBranch() {
        val call = shell.indexOf("EngineBenchmarkOverlays(")
        assertTrue(
            "셸이 `EngineBenchmarkOverlays`를 부르지 않는다 — 설정 화면에서 벤치마크를 눌러도 " +
                "아무 팝업이 뜨지 않는 상태로 되돌아갔다.",
            call >= 0,
        )
        val destinationBranch = shell.indexOf("when (currentDestination)")
        assertTrue("`when (currentDestination)`을 찾지 못했다 — 이 계약의 전제가 무너졌다.", destinationBranch >= 0)
        assertTrue(
            "벤치마크 오버레이가 목적지 분기 **안**에서 그려진다 — 그러면 그 화면에서 누른 " +
                "사용자만 응답을 본다. 버튼이 어느 화면에 있든 응답은 보여야 한다.",
            call < destinationBranch,
        )
    }

    /**
     * ⚠️ **대국 화면이 다시 그리면 안 된다** — 같은 팝업이 두 번 선언되고, 무엇보다 "여기서
     * 그리면 된다"는 옛 구조가 되살아난다. 대국 화면은 벤치마크 상태를 **다른 팝업을 미루는
     * 신호로만** 쓴다.
     */
    @Test
    fun theInGameScreenNoLongerOwnsTheBenchmarkPopups() {
        listOf("EngineBenchmarkProgressDialog(", "EngineBenchmarkResultDialog(", "EngineBenchmarkOverlays(")
            .forEach { call ->
                assertTrue(
                    "GoCoachContent.kt가 `$call`을 다시 부르고 있다 — 벤치마크 팝업의 주인은 셸이다.",
                    !inGameContent.contains(call),
                )
            }
        assertTrue(
            "대국 화면이 벤치마크 상태를 아예 안 본다 — 벤치마크가 떠 있는 동안 이 화면의 다른 " +
                "팝업을 미루는 판단이 사라졌다.",
            inGameContent.contains("benchmarkProgress") && inGameContent.contains("benchmarkResult"),
        )
    }

    /** 막힘 사유는 **번역해서** 보여준다 — 게이트의 영어 진단 문장을 그대로 띄우지 않는다. */
    @Test
    fun theBlockedReasonIsTranslatedRatherThanEchoed() {
        val dialogs = sourceOf("EngineBenchmarkDialogs.kt")
        listOf(
            "EngineOperationBlockReason.EngineNotReady -> strings.benchmarkBlockedEngineNotReady",
            "EngineOperationBlockReason.BenchmarkUnsupported -> strings.benchmarkBlockedUnsupported",
            "EngineOperationBlockReason.EngineBusy -> strings.benchmarkBlockedEngineBusy",
        ).forEach { branch ->
            assertTrue("차단 사유 번역이 빠졌다: $branch", dialogs.contains(branch))
        }
        assertTrue(
            "게이트의 진단 문장(`block.message`)을 화면이 그대로 띄우고 있다 — 그건 영어이고 " +
                "디버그 리포트가 읽는 문장이다.",
            !dialogs.contains(".message"),
        )
    }

    /**
     * 세 사유가 **서로 다르게 읽혀야** 한다. 같은 문장이면 팝업이 떠도 사용자는 셋 중 무엇인지
     * 모르고, 그건 침묵과 크게 다르지 않다.
     *
     * ⚠️ 번역 누락 자체는 `UiStringsTest`의 리플렉션 그물이 잡는다(비한국어에 한글이 남으면 빨개진다).
     * 여기서 보는 것은 **뜻이 갈리는가** 다.
     */
    @Test
    fun theThreeBlockedReasonsReadDifferentlyInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            val messages = listOf(
                strings.benchmarkBlockedEngineNotReady,
                strings.benchmarkBlockedUnsupported,
                strings.benchmarkBlockedEngineBusy,
            )
            assertTrue("$language: 빈 차단 문구가 있다", messages.none { message -> message.isBlank() })
            assertTrue(
                "$language: 차단 사유 문구가 겹친다 — 셋 중 무엇 때문인지 화면으로 알 수 없다.",
                messages.toSet().size == 3,
            )
            assertTrue("$language: 차단 팝업 제목이 비었다", strings.benchmarkBlockedTitle.isNotBlank())
        }
    }
}
