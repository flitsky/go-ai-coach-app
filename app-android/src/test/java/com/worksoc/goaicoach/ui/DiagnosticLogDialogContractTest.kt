package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 진단 로그 뷰어(백로그 #79)의 소스 계약.
 *
 * ⚠️ **여기 걸린 것들은 전부 "잘못돼도 조용한" 종류다** — 로그를 통째로 실으면 화면이 멈추거나
 * 클립보드가 **예외 없이** 실패하고, 파일을 매 컴포지션마다 읽으면 스크롤할 때 디스크를 때리는데
 * 둘 다 테스트로는 초록이다. 그래서 처방을 소스에 못박는다.
 */
class DiagnosticLogDialogContractTest {

    private val dialog = codeOnly(sourceOf("DiagnosticLogDialog.kt"))
    private val settings = codeOnly(sourceOf("SettingsScreen.kt"))

    /**
     * ⚠️ **로그 전체를 싣지 않는다.** `TrimmedAppendOnlyLog`가 1MB까지 허용하는데, 그만한 문자열은
     * ⓐ Compose `Text` 측정에서 화면을 멈추고 ⓑ **클립보드에 실으면 바인더 한도(1MB 근처)에서
     * `TransactionTooLargeException`으로 조용히 실패한다.** 진단에 필요한 것은 언제나 최근이다.
     */
    @Test
    fun onlyTheTailOfTheLogIsLoaded() {
        assertTrue(
            "로그 뒤쪽만 잘라 오지 않는다 — 1MB를 통째로 실으면 화면이 멈추고 복사가 조용히 실패한다(#79).",
            dialog.contains("takeLast(MaxTailLines)"),
        )
        assertTrue(
            "생략한 줄 수를 알리지 않는다 — 앞부분이 없다는 것을 모르면 로그를 오독한다.",
            dialog.contains("omitted"),
        )
    }

    /** ⚠️ 파일 읽기는 한 번만 — 컴포지션마다 읽으면 스크롤할 때마다 디스크를 때린다. */
    @Test
    fun theLogFileIsReadOnceRatherThanEveryComposition() {
        assertTrue(
            "로그를 `remember`로 감싸 읽지 않는다 — 스크롤마다 파일을 다시 읽는다(#79).",
            dialog.contains("remember(context) { readDiagnosticLogTail(context) }"),
        )
    }

    /**
     * ⚠️ **읽기 전용이어야 1차에 둘 수 있다**(#77의 분류 기준). 로그를 지우거나 상태를 쓰면
     * 그 순간 2차로 가야 하는 것이 된다.
     */
    @Test
    fun theViewerNeverWritesAnything() {
        assertFalse(
            "뷰어가 로그를 지운다 — 읽기 전용이라는 1차 배치의 전제가 깨진다(#79).",
            dialog.contains(".clear()"),
        )
        assertFalse(
            "뷰어가 진단 이벤트를 남긴다 — 로그를 보는 행위가 로그를 오염시킨다.",
            dialog.contains(".append("),
        )
    }

    /**
     * ⚠️ **줄바꿈을 끄고 가로 스크롤을 둔다.** 로그는 JSON 한 줄이 하나의 이벤트라, 접히면
     * 이벤트 경계가 사라져 읽을 수 없게 된다.
     */
    @Test
    fun eachEventStaysOnOneLine() {
        assertTrue("줄바꿈을 끄지 않았다 — 이벤트 경계가 사라진다(#79).", dialog.contains("softWrap = false"))
        assertTrue("가로 스크롤이 없다 — 줄바꿈을 끄면 오른쪽이 잘려 못 읽는다.", dialog.contains("horizontalScroll("))
        assertTrue("고정폭 글꼴이 아니다 — JSON이 어긋나 보인다.", dialog.contains("FontFamily.Monospace"))
    }

    /**
     * ⚠️ **스크롤 영역의 높이는 상한(`heightIn(max=)`)이어야 한다** — 고정 높이는 글자를 자르지만
     * (함정 9번) 상한은 넘칠 때 스크롤로 넘긴다. 둘의 구분이 이 항목의 유일한 레이아웃 판단이다.
     */
    @Test
    fun theScrollAreaIsCappedRatherThanFixed() {
        assertTrue("스크롤 영역에 높이 상한이 없다 — 스크롤 컨테이너가 무한히 자란다.", dialog.contains("heightIn(max = LogViewMaxHeight)"))
        assertFalse(
            "스크롤 영역에 고정 높이(`.height(`)를 줬다 — 배율에서 잘린다(함정 9번).",
            dialog.contains(".height(LogViewMaxHeight)"),
        )
    }

    /**
     * ⚠️ **다이얼로그는 스크롤 Column 밖에서 emit한다.** 별도 윈도우인데도 스크롤 안에 두면
     * 그 자리에 레이아웃 슬롯을 차지하고, 설정 화면에 빈 공간이 생긴다.
     */
    @Test
    fun theDialogIsHostedOutsideTheScrollingColumn() {
        // 위치를 **들여쓰기로** 고정한다 — 스크롤 Column 안이면 12칸 이상이고, 화면 함수의
        // 최상위 형제(다른 다이얼로그들과 같은 자리)면 4칸이다. 오프셋 비교보다 정확하다.
        assertTrue(
            "다이얼로그가 화면 함수의 최상위 형제 위치에 없다 — 스크롤 Column 안에 두면 별도 " +
                "윈도우인데도 그 자리에 레이아웃 슬롯을 차지한다(#79).",
            settings.contains("\n    if (showDiagnosticLog) {\n        DiagnosticLogDialog("),
        )
        // 그리고 실제로 다른 다이얼로그들과 같은 자리인지 — 형제 하나를 기준으로 확인한다.
        assertTrue(
            "기존 다이얼로그 형제(`showEmailDialog`)를 찾지 못했다 — 이 계약의 기준이 사라졌다.",
            settings.contains("\n    if (showEmailDialog) {"),
        )
    }

    private fun sourceOf(fileName: String): String =
        File("src/main/java/com/worksoc/goaicoach/ui/$fileName").readText()

    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .joinToString("\n") { it.substringBefore("//") }
}
