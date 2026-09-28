package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #109 ⓑ — 화면의 와치독이 **차례가 아니라 시도마다** 다시 걸리는지의 **소스 계약**.
 *
 * 수정은 `GamePlaySection`의 와치독 루프에 산다. 순수 규칙(`EngineTurnWatchdogAttempt`)은 공유 모듈 테스트가, 그 규칙으로
 * 도는 루프는 `EngineStallRecoveryWiringTest`의 복제본(`WatchdogLoop`)이 재지만, **화면 루프 자체**를 읽는 테스트가
 * 없었다 — 화면을 예전의 지역 `var watchdogReported`(차례마다 한 번)로 되돌려도 전부 초록인 채 ⓑ가 돌아온다.
 *
 * 지키는 것: 루프가 틱마다 **살아 있는** 완료 순번으로 시도를 갱신하고(`observe`), 그 **뒤에** 이 시도의 보고 여부를
 * 보며, 경과를 그 시도의 기준에서 잰다. 첫 시도는 지금의 순번으로 걸어 화면에 다시 들어와도 차례 시작부터 잰다.
 */
class EngineTurnWatchdogScreenContractTest {

    private fun code(name: String): String =
        RepoPaths.uiFile(name).readContractSource()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private fun String.between(from: String, to: String): String {
        val start = indexOf(from)
        assertTrue("`$from`를 찾지 못했다 — 계약이 보는 자리가 사라졌다.", start >= 0)
        val end = indexOf(to, start + from.length)
        assertTrue("`$to`를 찾지 못했다 — 계약이 보는 자리가 사라졌다.", end >= 0)
        return substring(start, end)
    }

    private fun String.indexOfOrFail(pattern: Regex, what: String): Int {
        val match = pattern.find(this)
        assertTrue(what, match != null)
        return match!!.range.first
    }

    private val screen by lazy { code("GamePlaySection.kt") }
    private val watchdogEffect by lazy { screen.between("LaunchedEffect(watchdogBaseMillis", "showEngineStuckDialog = true") }

    @Test
    fun theWatchdogLoopReArmsOnEveryAttemptFromTheLiveCompletionSeq() {
        assertFalse(
            "화면에 예전의 `watchdogReported`가 남아 있다 — 차례마다 한 번만 보고하면 실패 뒤 조용한 재시도가 멎어도 " +
                "팝업이 다시 뜨지 않는다(#109 ⓑ).",
            screen.contains("watchdogReported"),
        )
        assertTrue(
            "완료 순번이 `rememberUpdatedState`로 살아 있지 않다 — 효과 안에서 붙잡힌 값은 바뀌지 않아 시도가 다시 걸리지 않는다.",
            Regex("""val\s+liveEngineTurnWaitCompletionSeq\s*=\s*rememberUpdatedState\(\s*screenState\.engine\.engineTurnWaitCompletionSeq\s*\)""")
                .containsMatchIn(screen),
        )

        val loopStart = watchdogEffect.indexOfOrFail(
            Regex("""while\s*\("""),
            "와치독 효과에서 틱 루프(`while`)를 찾지 못했다 — 계약이 보는 자리가 사라졌다.",
        )
        val observe = watchdogEffect.indexOfOrFail(
            Regex(
                """watchdogAttempt\s*=\s*watchdogAttempt\.observe\(\s*nowMillis\s*=\s*now\s*,\s*""" +
                    """completionSeq\s*=\s*liveEngineTurnWaitCompletionSeq\.value\s*\)""",
            ),
            "루프가 틱마다 `watchdogAttempt.observe(…, completionSeq = liveEngineTurnWaitCompletionSeq.value)`로 시도를 " +
                "갱신하지 않는다 — 차례 대기 작업이 끝나도 다시 걸리지 않는다(#109 ⓑ).",
        )
        val reportedGate = watchdogEffect.indexOfOrFail(
            Regex("""if\s*\(\s*!\s*watchdogAttempt\.isReported\s*\)"""),
            "루프가 `watchdogAttempt.isReported`로 이 시도의 보고 여부를 보지 않는다(#109 ⓑ).",
        )
        assertTrue(
            "`observe`가 틱 루프 안에서 보고 여부 검사보다 **앞에** 있어야 한다 — 뒤에 있으면 이미 보고한 시도가 " +
                "다음 시도로 넘어가지 못한다(#109 ⓑ).",
            observe in (loopStart + 1) until reportedGate,
        )

        val gated = watchdogEffect.substring(reportedGate)
        assertTrue(
            "경과를 이 시도의 기준(`watchdogAttempt.elapsedMillis(now)`)에서 재지 않는다 — 차례 시작에서 재면 재시도가 곧바로 한도를 넘긴 것으로 보인다.",
            gated.contains("watchdogAttempt.elapsedMillis(now)"),
        )
        assertFalse(
            "경과를 여전히 `now - watchdogBaseMillis`로 잰다 — 시도가 다시 걸려도 기준이 차례 시작에 묶인다(#109 ⓑ).",
            watchdogEffect.contains("now - watchdogBaseMillis"),
        )
        assertTrue(
            "팝업을 띄운 시도를 `watchdogAttempt.reported()`로 표시하지 않는다 — 한 시도에 팝업이 틱마다 다시 보고된다.",
            Regex("""watchdogAttempt\s*=\s*watchdogAttempt\.reported\(\)""").containsMatchIn(gated),
        )
    }

    @Test
    fun theFirstAttemptStartsFromTheTurnBaseAndTheCurrentCompletionSeq() {
        val loopStart = watchdogEffect.indexOf("while")
        assertTrue("와치독 효과에서 틱 루프(`while`)를 찾지 못했다 — 계약이 보는 자리가 사라졌다.", loopStart >= 0)
        val setup = watchdogEffect.substring(0, loopStart)
        assertTrue(
            "첫 시도가 차례 기준 시각과 **지금의** 완료 순번으로 걸리지 않는다 — 다른 값이면 화면에 다시 들어오자마자 " +
                "다시 걸려 차례 시작이 아니라 그 순간부터 잰다.",
            Regex(
                """var\s+watchdogAttempt\s*=\s*EngineTurnWatchdogAttempt\(\s*baseMillis\s*=\s*watchdogBaseMillis\s*,\s*""" +
                    """completionSeq\s*=\s*liveEngineTurnWaitCompletionSeq\.value\s*,?\s*\)""",
            ).containsMatchIn(setup),
        )
    }
}
