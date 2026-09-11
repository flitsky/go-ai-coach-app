package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #141 — 넓은 배치(P1)의 **소스 계약**. 레이아웃은 계측 테스트가 없어 처방을 소스에 못박는다.
 *
 * 지키는 것 넷(2026-09-12 사용자 확정 배치):
 * ⓐ 넓은 배치에는 **스크롤이 없다** — 판 위 끌기는 착수라(함정 44) 스크롤 여지가 생기면 안 된다.
 * ⓑ 판이 **남는 높이를 전부** 가진다(`weight(1f)`) — 그래야 넘치는 것이 생겨도 판이 줄지 화면이 늘지 않는다.
 * ⓒ 착수 칸은 가로여도 **크기를 맞바꾼다** — 바로 착수면 스위치가 넓고 `착수`가 좁게, 확인이면 반대.
 * ⓓ 코치마크 대상(돋보기·판 크기)이 넓은 배치에서도 **자기 자리를 알린다** — 안 그러면 동그라미가 사라진다.
 */
class WideGameLayoutContractTest {

    private fun code(name: String): String =
        File("src/main/java/com/worksoc/goaicoach/ui/$name").readText()
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

    @Test
    fun theScreenPicksItsLayoutFromTheViewportAndTheWideOneNeverScrolls() {
        val content = code("GoCoachContent.kt")
        assertTrue(
            "배치 판정이 뷰포트 크기를 보지 않는다 — 그래프·범례에 따라 배치가 흔들린다.",
            content.contains("gameScreenLayoutFor(maxWidth.value, maxHeight.value)"),
        )
        val wideBranch = content.between("if (layout == GameScreenLayout.Wide) {", "} else {")
        assertFalse("넓은 배치에 스크롤이 생겼다 — 판 위 끌기가 착수라 스크롤할 자리가 없다(함정 44).", wideBranch.contains("verticalScroll"))

        val wide = code("GamePlaySection.kt").between("private fun WidePlayArrangement(", "private val WideBoardInset")
        assertFalse("넓은 배치 안에 스크롤이 생겼다(함정 44).", wide.contains("verticalScroll"))
    }

    @Test
    fun theBoardTakesTheHeightThatIsLeft() {
        val wide = code("GamePlaySection.kt").between("private fun WidePlayArrangement(", "private val WideBoardInset")
        val boardCall = wide.between("board(\n", "if (showMoveQualityLegend)")
        assertTrue(
            "판이 남는 높이를 가져가지 않는다(`weight(1f)`) — 넘치면 판이 줄지 않고 아래 줄이 잘린다.",
            boardCall.contains(".weight(1f)"),
        )
    }

    @Test
    fun thePlaySlotSwapsWidthsWithThePlayMode() {
        val slot = code("GameStatusPanel.kt").between("internal fun PlaySlot(", "private const val PlaySlotLeadWeight")
        assertTrue(
            "가로 착수 칸에서 스위치가 바로 착수일 때 넓지 않다(2026-09-12 사용자 확인).",
            slot.contains("switch(Modifier.weight(if (isDirectPlay) PlaySlotLeadWeight else PlaySlotRestWeight))"),
        )
        assertTrue(
            "가로 착수 칸에서 `착수`가 확인 모드일 때 넓지 않다 — 두 크기가 맞바뀌어야 한다.",
            slot.contains("playButton(Modifier.weight(if (isDirectPlay) PlaySlotRestWeight else PlaySlotLeadWeight))"),
        )
        val wide = code("GamePlaySection.kt").between("private fun WidePlayArrangement(", "private val WideBoardInset")
        assertTrue("넓은 배치가 착수 칸을 가로로 쓰지 않는다.", wide.contains("horizontal = true"))
    }

    @Test
    fun theWideTogglesStillTellTheCoachMarkWhereTheyAre() {
        val wide = code("GamePlaySection.kt").between("private fun WidePlayArrangement(", "private val WideBoardInset")
        listOf("GuideTarget.Magnifier", "GuideTarget.BoardSize").forEach { target ->
            assertTrue(
                "넓은 배치의 토글이 `guideTarget($target)`을 알리지 않는다 — 첫돌이 동그라미가 사라진다(#128).",
                wide.contains("guideTarget($target)"),
            )
        }
    }
}
