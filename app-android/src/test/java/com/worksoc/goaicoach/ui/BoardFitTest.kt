package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #139 1차 — 대국 화면이 한 화면에 다 들어오도록 판의 최대 높이를 정한다.
 *
 * 제보: 갤럭시 폴드에서 *"바둑판이 자꾸 움직인다."* 안쪽 화면(≈690×829dp)에서 판이 가로폭을 다 먹어
 * 세로 ~680dp가 됐고 조작부 전부가 화면 밖으로 밀려, 판 위 끌기가 화면을 굴렸다(함정 45).
 */
class BoardFitTest {

    // 420dpi(2.625배) 기준의 280dp.
    private val minBoard = 735

    @Test
    fun beforeTheFirstMeasurementThereIsNoCap() {
        assertNull(fittedBoardMaxHeightPx(viewportPx = 0, contentPx = 0, boardPx = 0, minBoardPx = minBoard))
        assertNull(fittedBoardMaxHeightPx(viewportPx = 2100, contentPx = 0, boardPx = 1000, minBoardPx = minBoard))
    }

    /**
     * ⚠️ **보통 폰에서는 아무것도 안 바뀌어야 한다** — 자리가 남으면 상한이 가로폭(=판)보다 커서
     * `GoBoard`의 `min(가로, 세로)`가 그대로 가로폭을 고른다. 이게 깨지면 멀쩡하던 폰의 판이 작아진다.
     */
    @Test
    fun onARoomyPhoneTheCapIsTallerThanTheBoard() {
        // Pixel 7 대국 화면 실측에 가까운 값: 뷰포트 2200, 내용 1900, 판 1080.
        val cap = fittedBoardMaxHeightPx(viewportPx = 2200, contentPx = 1900, boardPx = 1080, minBoardPx = minBoard)!!
        assertTrue("자리가 남는데 판을 줄이려 한다 (상한 $cap < 판 1080)", cap >= 1080)
    }

    /** 폴드 안쪽 화면 — 넘친 만큼 정확히 줄여 한 화면에 들어오게 한다. */
    @Test
    fun onAFoldInnerScreenTheBoardShrinksByExactlyTheOverflow() {
        val viewport = 2112
        val board = 1780
        val content = 2980 // 넘침 868
        val cap = fittedBoardMaxHeightPx(viewport, content, board, minBoard)!!
        assertEquals("판이 아닌 부분(1200)을 빼고 남는 높이여야 한다", viewport - (content - board), cap)
        assertEquals(912, cap)
    }

    /**
     * ⚠️ **한 번 재면 수렴한다** — 판이 줄면 내용도 같은 만큼 줄어 차가 그대로다. 수렴하지 않으면
     * 매 프레임 크기가 바뀌며 판이 떨린다.
     */
    @Test
    fun theCapIsStableOnceTheBoardHasShrunk() {
        val first = fittedBoardMaxHeightPx(viewportPx = 2112, contentPx = 2980, boardPx = 1780, minBoardPx = minBoard)!!
        assertTrue("바닥에 걸리면 수렴을 증명하지 못한다 — 바닥보다 큰 값으로 잡을 것", first > minBoard)
        val shrunkContent = 2980 - (1780 - first)
        val second = fittedBoardMaxHeightPx(viewportPx = 2112, contentPx = shrunkContent, boardPx = first, minBoardPx = minBoard)
        assertEquals("줄인 뒤 다시 재면 같은 값이어야 한다", first, second)
    }

    /** 세로가 아주 짧으면(분할 화면) 바닥에서 멈추고 예전처럼 스크롤한다 — 두기 어려운 판보다 낫다. */
    @Test
    fun aVeryShortViewportStopsAtTheFloorAndScrolls() {
        val cap = fittedBoardMaxHeightPx(viewportPx = 900, contentPx = 2000, boardPx = 1000, minBoardPx = minBoard)
        assertEquals(minBoard, cap)
    }

    /** 화면이 다시 커지면(접었다 펴기) 판도 다시 커질 수 있어야 한다 — 한 번 줄인 값에 갇히면 안 된다. */
    @Test
    fun theCapGrowsBackWhenTheViewportGrows() {
        val small = fittedBoardMaxHeightPx(viewportPx = 2112, contentPx = 2980, boardPx = 1780, minBoardPx = minBoard)!!
        val tall = fittedBoardMaxHeightPx(viewportPx = 3000, contentPx = 2980 - (1780 - small), boardPx = small, minBoardPx = minBoard)!!
        assertTrue("뷰포트가 커졌는데 상한이 그대로다 ($small → $tall)", tall > small)
    }

    /**
     * ⚠️ **어디서 재느냐가 이 수정의 전부다** — 뷰포트는 `verticalScroll` **앞**, 내용은 **뒤**에서
     * 재야 한다. 순서가 바뀌면 둘 다 같은 값이 되거나(뷰포트를 스크롤 안에서 재면 무한) 조용히
     * 예전처럼 넘친다. 제스처·레이아웃은 계측 테스트가 없어 소스로 잡는다.
     */
    @Test
    fun theViewportIsMeasuredOutsideTheScrollAndTheBoardIsCapped() {
        fun src(name: String) = File("src/main/java/com/worksoc/goaicoach/ui/$name").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

        val content = src("GoCoachContent.kt")
        val viewport = content.indexOf("onSizeChanged { size -> viewportHeightPx = size.height }")
        val scroll = content.indexOf(".verticalScroll(rememberScrollState())")
        val whole = content.indexOf("onSizeChanged { size -> contentHeightPx = size.height }")
        assertTrue("뷰포트·스크롤·내용을 재는 자리를 찾지 못했다", viewport >= 0 && scroll >= 0 && whole >= 0)
        assertTrue("뷰포트를 스크롤 **앞**에서 재지 않는다 — 스크롤 안에서는 세로가 무한이다(함정 45).", viewport < scroll)
        assertTrue("내용 전체를 스크롤 **뒤**에서 재지 않는다 — 넘친 만큼을 알 수 없다.", scroll < whole)
        // ⚠️ 스크롤은 fillMaxSize의 최소 높이를 넘긴다 — 풀지 않으면 짧은 내용이 뷰포트 높이로 재져
        //   상한이 지금 판 크기에 갇힌다(실측: 그래프를 접어도 판이 다시 커지지 않았다).
        val unclamp = content.indexOf(".wrapContentHeight(align = Alignment.Top)")
        assertTrue(
            "스크롤과 내용 측정 사이에 wrapContentHeight가 없다 — 내용이 짧으면 뷰포트 높이로 재져 판이 갇힌다.",
            unclamp in (scroll + 1) until whole,
        )

        val play = src("GamePlaySection.kt")
        val cap = play.indexOf("Modifier.heightIn(max = boardMaxHeight)")
        val expand = play.indexOf("Modifier.expandBeyondScreenPadding()")
        assertTrue("판에 높이 상한을 걸지 않는다 — 폴드 안쪽 화면에서 다시 넘친다(#139).", cap >= 0)
        assertTrue("상한이 확대보다 **뒤**에 있다 — 확대가 상한을 넘겨받지 못한다.", expand >= 0 && cap < expand)
    }
}
