package com.worksoc.goaicoach.ui

import java.io.File
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.worksoc.goaicoach.application.preferences.MagnifierSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #39 — 돋보기 말풍선의 자리와 배율.
 *
 * ⚠️ **이 항목에서 실제로 틀리기 쉬운 곳이 여기다.** 제스처는 실기에서 합성 이벤트로 밟아
 * 볼 수 있지만, "말풍선이 캔버스를 넘지 않는가 · 뒤집힘이 드래그 중에 튀지 않는가 · 작은 판에서
 * 배율이 1배 밑으로 내려가지 않는가"는 눈으로 봐서는 놓친다.
 */
class BoardMagnifierTest {

    // 330dp 보드를 3배 밀도로 그린 캔버스. 19줄이면 칸 간격이 52.1px 남짓이다.
    private val canvas = Size(990f, 990f)
    private val spacing19 = 990f / 19f
    private val spacing9 = 990f / 9f
    private val gap = 84f // 28dp @ 3x

    @Test
    fun theBubbleSitsAboveTheFingerWhenThereIsRoom() {
        val touch = Offset(500f, 700f)
        val placement = magnifierPlacement(touch, canvas, spacing19, gap, below = false)

        assertTrue("손가락 위에 떠야 한다", placement.center.y < touch.y)
        // 원 아래 끝이 손가락에서 적어도 간격만큼 떨어져 있어야 손끝이 가리지 않는다.
        assertTrue(
            "손가락과 원이 너무 가깝다",
            touch.y - (placement.center.y + placement.radius) >= gap - 0.01f,
        )
    }

    /** 상변을 누르면 위쪽에 원이 안 들어간다 — 그 줄이야말로 확대가 필요한 자리다. */
    @Test
    fun theBubbleFlipsBelowNearTheTopEdge() {
        assertTrue(magnifierPrefersBelow(Offset(500f, 60f), canvas, spacing19, gap))
        assertFalse(magnifierPrefersBelow(Offset(500f, 900f), canvas, spacing19, gap))

        val placement = magnifierPlacement(Offset(500f, 60f), canvas, spacing19, gap, below = true)
        assertTrue("아래로 뒤집혀야 한다", placement.center.y > 60f)
    }

    /**
     * ⚠️ **드래그 중에 방향이 바뀌면 안 된다.** 방향을 호출부가 넘기므로, 같은 값을 주면 손가락이
     * 경계선을 넘어가도 같은 쪽에 머문다 — 이 테스트가 그 계약을 고정한다.
     */
    @Test
    fun thePinnedSideIsHonouredEvenWhereTheOppositeWouldBeChosen() {
        // 위쪽으로는 들어갈 자리가 없는 지점인데도, 위로 고정하면 위에 (가둬져서) 머문다.
        val high = Offset(500f, 60f)
        assertTrue("이 지점은 원래 아래를 고른다", magnifierPrefersBelow(high, canvas, spacing19, gap))

        val pinnedAbove = magnifierPlacement(high, canvas, spacing19, gap, below = false)
        assertFalse(pinnedAbove.below)
        assertTrue("캔버스 밖으로 나가면 안 된다", pinnedAbove.center.y - pinnedAbove.radius >= -0.01f)

        // 반대로, 아래를 고르는 지점에서 아래로 고정해도 그대로다.
        val low = Offset(500f, 900f)
        val pinnedBelow = magnifierPlacement(low, canvas, spacing19, gap, below = true)
        assertTrue(pinnedBelow.below)
    }

    @Test
    fun theBubbleNeverLeavesTheCanvasSideways() {
        listOf(0f, 5f, 495f, 985f, 990f).forEach { x ->
            val placement = magnifierPlacement(Offset(x, 700f), canvas, spacing19, gap, below = false)
            assertTrue(
                "x=$x 에서 왼쪽으로 삐져나갔다",
                placement.center.x - placement.radius >= -0.01f,
            )
            assertTrue(
                "x=$x 에서 오른쪽으로 삐져나갔다",
                placement.center.x + placement.radius <= canvas.width + 0.01f,
            )
        }
    }

    /**
     * ⚠️ **배율이 1배 밑으로 내려가면 돋보기가 축소경이 된다.** "5×5 셀"을 글자대로 지키면
     * 9줄 판에서 정확히 그 일이 벌어진다(5칸이 판의 절반을 넘는다) — 그래서 배율을 지키고
     * 칸 수를 양보하기로 했다. 그 결정을 여기서 고정한다.
     */
    @Test
    fun theRequestedZoomIsHonouredOnEveryBoardSize() {
        // ⚠️ **#85로 배율이 상수에서 설정값이 됐다.** 지켜야 할 것은 *"2배"* 가 아니라
        // **"요청한 값이 판 크기에 흔들리지 않는다"** 이고, 그것이 이 테스트의 원래 뜻이었다
        // (작은 판에서 칸 수를 맞추려다 배율이 무너지는 것을 막는 계약).
        MagnifierSettings.zoomScales.forEach { zoom ->
            listOf(spacing9, spacing19).forEach { spacing ->
                val placement = magnifierPlacement(
                    Offset(500f, 700f), canvas, spacing, gap, below = false, zoom = zoom,
                )
                assertEquals("배율이 판 크기에 따라 흔들렸다", zoom, placement.scale, 0.001f)
            }
        }
    }

    /**
     * ⚠️ **#85의 목적 그 자체** — 기본 설정이 #39 당시보다 **넓게** 보여야 한다.
     * 실기 피드백이 *"너무 좁은 영역만 보여 준다"* 였고, 창과 배율을 따로 만지면 서로 상쇄될 수
     * 있어(칸 수 ≈ 창 ÷ 배율) 결과를 직접 잰다.
     */
    @Test
    fun theDefaultShowsMoreCellsThanTheOriginalTuning() {
        fun cells(sizeScale: Float, zoom: Float): Float {
            val placement = magnifierPlacement(
                Offset(500f, 700f), canvas, spacing19, gap, below = false,
                sizeScale = sizeScale, zoom = zoom,
            )
            return placement.radius * 2f / (spacing19 * placement.scale)
        }

        val before = cells(1.0f, 2.0f)
        val after = cells(MagnifierSettings.defaultSizeScale, MagnifierSettings.defaultZoom)
        assertTrue("기본값이 이전보다 넓게 보이지 않는다 (이전 ${before}칸, 지금 ${after}칸)", after > before)
    }

    /**
     * 큰 판일수록 확대창에 더 많은 칸이 들어간다 — 정밀도가 가장 필요한 19줄에서 이웃까지
     * 보이는지가 이 연출의 목적이다.
     */
    @Test
    fun aDenserBoardShowsMoreCellsInsideTheBubble() {
        fun visibleCells(spacing: Float): Float {
            val placement = magnifierPlacement(Offset(500f, 700f), canvas, spacing, gap, below = false)
            return placement.radius * 2f / (spacing * placement.scale)
        }

        val on19 = visibleCells(spacing19)
        val on9 = visibleCells(spacing9)
        assertTrue("19줄이 9줄보다 많이 보여야 한다 (19줄=$on19, 9줄=$on9)", on19 > on9)
        assertTrue("19줄에서 목표 교차점과 양옆이 보여야 한다 ($on19)", on19 >= 2.5f)
    }

    /** 말풍선이 판을 다 덮으면 무엇을 확대했는지 알 수 없다 — 지름 상한이 그것을 막는다. */
    @Test
    fun theBubbleStaysWellUnderTheFlippingThreshold() {
        // ⚠️ **#85가 창을 1.2배로 키우면서 이 상한도 함께 올라갔다** — 상한에 `sizeScale`을
        // 곱하지 않으면 19줄 판에서 창이 전혀 커지지 않기 때문이다(상한 쪽이 걸린다).
        //
        // ⚠️ **그래서 잃는 것이 있다.** 창이 커질수록 손가락 **위에 들어갈 자리가 없어져 아래로
        // 뒤집히는 영역이 넓어진다.** #39 당시 0.42로 잡았더니 **판 한가운데서도 뒤집혔다.**
        // 지금 최대는 0.32 × 1.2 = **0.384**로 그 선 아래이지만 여유가 줄었다 —
        // ⚠️ **창 크기 선택지를 더 키우려면 이 값을 먼저 볼 것.**
        MagnifierSettings.sizeScales.forEach { sizeScale ->
            val placement = magnifierPlacement(
                Offset(500f, 700f), canvas, spacing9, gap, below = false, sizeScale = sizeScale,
            )
            assertTrue(
                "지름이 뒤집힘 문턱(0.42)에 너무 가깝다 (창 ${sizeScale}배, 지름 ${placement.radius * 2})",
                placement.radius * 2f <= canvas.minDimension * 0.40f,
            )
        }
    }

    /**
     * ⚠️ **가늠돌은 누르는 순간 뜨고, 확대창은 꾹 누른 뒤에만 뜬다**(#138).
     *
     * ## 이 계약이 걸어온 길
     * - **#39**: 끌어서 두기 도입. 돋보기를 꺼 두면 꾹 눌러도 아무 일이 없었다.
     * - **2026-09-09 1차**: 토글의 뜻을 *"확대 창을 그릴지"* 로 좁혀 끌어서 두기를 언제나 되게 했다.
     * - **2026-09-09 2차(#131)**: 가늠돌까지 토글에서 풀었다 — 돋보기를 끈 사용자에게도 손을 따라오는 돌.
     *   그때 이 테스트는 *"`playDrag`를 채우는 자리가 정확히 둘"* 이라고 셌다.
     * - **2026-09-11(#138)**: 가늠돌을 **꾹 누름 임계 이전으로 앞당겼다.** 그전에는 임계(약 0.4초)를 넘겨야
     *   떠서, 빠른 탭은 **아무것도 안 보인 채** 처음 누른 자리에 놓였다. 사용자 요청: *"터치 다운해서
     *   '착수 확인'처럼 돌이 먼저 보여지게 하면서 드래그시 위치 변경도 따라오게."* 그래서 개수 대신
     *   **순서와 플래그**를 본다 — 개수는 루프 모양이 조금만 바뀌어도 틀리고, 지켜야 할 뜻을 말하지 않는다.
     *
     * ⚠️ 제스처는 계측 테스트가 없다 — 그래서 소스로 잡는다. 아래 하나하나가 **되돌리기 쉬운 곳**이다.
     */
    @Test
    fun theGhostAppearsOnTouchDownAndTheMagnifierOnlyAfterTheHold() {
        val source = goBoardSource()

        // ① 누르는 순간 — 임계를 기다리기 **전에** 가늠돌을 채운다.
        val firstGhost = source.indexOf("playDrag = PlayDrag(")
        val holdWait = source.indexOf("withTimeout(holdThresholdMillis)")
        assertTrue("가늠돌을 채우는 자리를 찾지 못했다", firstGhost >= 0)
        assertTrue("꾹 누름 임계를 기다리는 자리를 찾지 못했다", holdWait >= 0)
        assertTrue(
            "가늠돌이 임계 **뒤에** 처음 채워진다 — 빠른 탭은 다시 아무것도 안 보인 채 놓인다(#138).",
            firstGhost < holdWait,
        )
        // ② 그 순간에는 확대창을 **띄우지 않는다** — 모든 탭에 말풍선이 번쩍이면 안 된다.
        assertTrue(
            "누르는 순간의 가늠돌이 확대창까지 켠다 — 탭할 때마다 말풍선이 번쩍인다(#138).",
            source.substring(firstGhost, holdWait)
                .contains("playDrag = PlayDrag(follow.target, below = false, magnifier = false)"),
        )
        // ③ 확대창을 그리는 자리는 제스처가 정한 플래그 하나만 본다.
        assertTrue(
            "확대창을 그리는 자리가 `drag.magnifier`를 보지 않는다 — 임계 전 가늠돌에도 창이 뜨거나, " +
                "임계 후에도 안 뜬다(#138).",
            source.contains("if (drag.magnifier) drawMagnifier("),
        )
        // ④ 그 플래그는 여전히 토글에서 온다 — 토글이 뜻을 잃으면 안 된다.
        assertTrue(
            "확대창 플래그가 `isPlayMagnifierEnabled`에서 오지 않는다 — 돋보기를 꺼도 창이 뜬다.",
            source.contains("val magnifierSpacing = if (uxOptions.isPlayMagnifierEnabled)") &&
                source.contains("magnifier = showMagnifier"),
        )
        // 옛 조기 반환이 되살아나면 돋보기를 끌 때 끌어서 두기가 사라진다(2026-09-09).
        assertFalse(
            "제스처 루프가 `isPlayMagnifierEnabled`로 조기 반환한다 — 돋보기를 끄면 끌어서 두기가 " +
                "함께 사라진다(2026-09-09 사용자 지시로 없앤 갈래다).",
            source.contains("if (!uxOptions.isPlayMagnifierEnabled) {"),
        )
    }

    /**
     * 빠른 탭은 **가늠돌이 있던 자리**에 놓인다(#138) — 보이는 곳과 놓이는 곳이 같아야 한다.
     *
     * ⚠️ 이것은 #39의 결정을 **뒤집은** 것이다. 그때는 *"살짝 미끄러진 탭이 엉뚱한 곳에 놓인다"* 며 빠른
     * 탭을 **누른 자리**에 묶었다. 그 걱정은 이제 `followDrag`의 **터치 슬롭**이 맡는다 — 슬롭 안의
     * 떨림은 처음 자리를 유지한다(`BoardPlayDragTest`가 그 계약을 잡는다).
     * 옛 `coordinateAt(down.position)`으로 되돌리면 가늠돌을 끌어 옮겨 놓고도 처음 자리에 놓인다.
     */
    @Test
    fun aQuickReleasePlacesWhereTheGhostWas() {
        val source = goBoardSource()
        assertFalse(
            "빠른 탭이 누른 자리에 놓인다 — 가늠돌을 끌어 옮겨도 처음 자리에 놓이게 된다(#138).",
            source.contains("coordinateAt(down.position)?.let(onCoordinateTap)"),
        )
        assertTrue(
            "빠른 탭이 가늠돌 자리(`follow.target`)에 놓이지 않는다(#138).",
            source.contains("coordinateAt(follow.target)"),
        )
    }

    /**
     * ⚠️ **어떤 경로로 끝나도 가늠돌이 지워진다**(#138).
     *
     * 누르고 있는 동안 `inputEnabled`가 바뀌면(AI 차례가 끝나는 순간) `pointerInput`이 키 변경으로 다시
     * 시작되고 제스처 코루틴은 **취소**된다. `finally`가 아니면 그 경로에서 지울 곳이 없어, 손을 뗀 뒤에도
     * 판 위에 가늠돌이 남는다.
     */
    @Test
    fun theGhostIsClearedOnEveryExitIncludingCancellation() {
        assertTrue(
            "가늠돌을 `finally`에서 지우지 않는다 — 제스처가 취소되면 판 위에 남는다(#138).",
            Regex("""finally\s*\{\s*playDrag = null\s*\}""").containsMatchIn(goBoardSource()),
        )
    }

    /**
     * ⚠️ **판이 항상 우선이다**(#138, 2026-09-11 사용자 결정). 임계 전 추적기는 이동을 **소비**해서
     * 조상(`verticalScroll`)이 끼어들지 못하게 한다.
     *
     * 첫 구현은 반대였다 — 소비하지 않고 스크롤에 양보했더니 **0.4초 안의 빠른 세로 조정이 스크롤에
     * 빼앗겨 취소**됐다(가로는 따라왔다 — 방향에 따라 동작이 갈렸다). 사용자 결정: *"판이 항상 우선하면서
     * 즉시 터치 드래그하더라도 바로 끌려와야 한다."* 이 계약이 그 결정을 지킨다. 대가는 함정 44.
     *
     * ⚠️ **Final 단계의 소비 검사가 되살아나면 모든 끌기가 취소된다** — 이제 우리가 소비하므로, 그 검사는
     * 제 소비를 조상의 가로채기로 오인한다.
     */
    @Test
    fun theBoardAlwaysOwnsAGestureThatStartsOnIt() {
        val source = File("src/main/java/com/worksoc/goaicoach/ui/BoardPlayDrag.kt").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
        val start = source.indexOf("fun AwaitPointerEventScope.trackPressUntilUp(")
        assertTrue("`trackPressUntilUp`을 찾지 못했다 — 이 계약의 전제가 무너졌다.", start >= 0)
        val body = source.substring(start)
        val guard = body.indexOf("if (change.isConsumed) return false")
        val move = body.indexOf("onMove(")
        assertTrue("먼저 가져간 경우의 양보 갈래나 `onMove`를 찾지 못했다.", guard >= 0 && move > guard)
        assertTrue(
            "임계 전 추적기가 이동을 소비하지 않는다 — 판에서 시작한 빠른 세로 끌기를 스크롤이 빼앗아 " +
                "취소된다(#138, 사용자 결정: 판이 항상 우선).",
            body.substring(guard, move).contains("change.consume()"),
        )
        assertFalse(
            "임계 전 추적기가 Final 단계를 본다 — 제 소비를 조상의 가로채기로 오인해 모든 끌기가 취소된다(#138).",
            body.contains("PointerEventPass.Final"),
        )
    }

    // ⚠️ **주석과 `import`를 걷어낸 뒤 센다**(함정 10-2) — 코드를 지우고 사유만 주석으로 남기는(흔한)
    // 변경이 그물을 조용히 통과하지 않게.
    private fun goBoardSource(): String =
        File("src/main/java/com/worksoc/goaicoach/ui/GoBoard.kt").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }
}
