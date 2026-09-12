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
        val wideBranch = content.between("if (layout.isWide) {", "} else {")
        assertFalse("넓은 배치에 스크롤이 생겼다 — 판 위 끌기가 착수라 스크롤할 자리가 없다(함정 44).", wideBranch.contains("verticalScroll"))

        wideArrangements().forEach { (name, source) ->
            assertFalse("$name 안에 스크롤이 생겼다(함정 44).", source.contains("verticalScroll"))
        }
    }

    @Test
    fun theBoardTakesTheSpaceThatIsLeftInBothWideLayouts() {
        wideArrangements().forEach { (name, source) ->
            val boardCall = source.between("board(\n", "if (showMoveQualityLegend)")
            assertTrue(
                "$name: 판이 남는 자리를 가져가지 않는다(`weight(1f)`) — 넘치면 판이 줄지 않고 조작부가 잘린다.",
                boardCall.contains(".weight(1f)"),
            )
        }
    }

    /**
     * ⚠️ 좌우 기둥(L1)에서 판은 **가운데 칸이 통째로** 가져야 한다 — 기둥이 고정 폭이므로 판이 남는
     * 폭을 전부 쓴다. 가운데 칸에 `weight`가 없으면 판이 제 크기를 못 찾는다.
     */
    @Test
    fun theColumnsAreFixedWidthSoTheBoardGetsTheRest() {
        val columns = code("GamePlaySection.kt").between("private fun WideColumnsArrangement(", "private val WideColumnWidth")
        assertTrue(
            "기둥이 고정 폭(`WideColumnWidth`)이 아니다 — 판과 폭을 다투게 된다.",
            columns.contains("Modifier.width(WideColumnWidth)"),
        )
        assertTrue(
            "판 칸이 남는 폭을 가져가지 않는다.",
            columns.contains("Box(modifier = Modifier.weight(1f).fillMaxHeight())"),
        )
        assertTrue(
            "좌석 카드가 기둥에서 세 줄로 접히지 않는다 — 좁은 폭에서 시계·사석이 잘린다.",
            columns.contains("stacked = true"),
        )
    }

    private fun wideArrangements(): List<Pair<String, String>> {
        val play = code("GamePlaySection.kt")
        return listOf(
            "P1 위아래" to play.between("private fun WidePlayArrangement(", "private val WideBoardInset"),
            "L1 좌우 기둥" to play.between("private fun WideColumnsArrangement(", "private val WideColumnWidth"),
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
        val stacked = code("GamePlaySection.kt").between("private fun WidePlayArrangement(", "private val WideBoardInset")
        assertTrue("위아래 배치가 착수 칸을 가로로 쓰지 않는다.", stacked.contains("horizontal = true"))
        val columns = code("GamePlaySection.kt").between("private fun WideColumnsArrangement(", "private val WideColumnWidth")
        assertTrue("좌우 기둥이 착수 칸을 세로로 쓰지 않는다 — 기둥 폭에서는 가로가 들어가지 않는다.", columns.contains("horizontal = false"))
        // ⚠️ #143이 착수 칸을 **플래그 뒤로** 보냈다(UX에서는 빠졌고 코드는 남았다). 두 배치 모두 그 뒤에 있어야
        //   한다 — 한쪽만 남으면 플래그를 켰을 때 배치마다 다른 화면이 된다.
        assertTrue("위아래 배치의 착수 칸이 플래그 뒤에 있지 않다(#143).", stacked.contains("playConfirmSlot {"))
        assertTrue("좌우 기둥의 착수 칸이 플래그 뒤에 있지 않다(#143).", columns.contains("if (FeatureFlags.isPlayConfirmModeEnabled)"))
    }

    /**
     * ⚠️ 형세·추천은 **칸(`GameActionSlots`)이 자기 자리를 알린다** — 배치마다 적으면 한쪽만 고쳐진다.
     * 두 배치 모두 그 칸을 쓰므로, 칸이 알리면 어디에 놓이든 동그라미가 따라온다.
     */
    @Test
    fun theActionSlotsCarryTheirOwnCoachMarkTargets() {
        val host = code("GamePlaySection.kt").between("private fun GameActionButtonHost(", "private fun GameActionButtons(")
        listOf("GuideTarget.Eval", "GuideTarget.TopMoves").forEach { target ->
            assertTrue("조작 버튼 칸이 `guideTarget($target)`을 알리지 않는다(#128).", host.contains("guideTarget($target)"))
        }
        wideArrangements().forEach { (name, source) ->
            assertFalse(
                "${name}이 형세·추천을 직접 그린다 — 게이팅이 두 벌이 되는 길이다. 칸(`slots`)을 쓸 것.",
                source.contains("ToggleActionButton("),
            )
        }
    }
}
