package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.home.HomePage
import com.worksoc.goaicoach.ui.l10n.StudyBasicsCategories
import com.worksoc.goaicoach.ui.l10n.StudyCategory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #280 — 홈은 세 페이지를 옆으로 넘긴다(사용자 2026-10-22 · 10-23): 내 정보 ‹ **대국** › 실험실.
 * 페이지 배치는 화면 코드라 단위 테스트가 없다 — 사용자가 정한 것을 소스로 못박는다(주석 · import는 걷어내고 본다).
 */
class HomePagesContractTest {

    /** 주석·import를 걷어낸 본문만 본다 — 이름이 주석에 남아 그물이 헐거워지는 것을 막는다(함정 10-2). */
    private fun codeOf(file: File): String =
        file.readContractSource()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val home = codeOf(RepoPaths.uiFile("GoCoachHomeScreen.kt"))
    private val lab = codeOf(RepoPaths.uiFile("HomeLabPage.kt"))
    private val shell = codeOf(RepoPaths.uiFile("GoCoachApp.kt"))

    /** 선언 순서가 왼쪽부터의 자리다 — 가운데가 대국, 셋째가 실험실(처음에는 「학습 하기」였다). */
    @Test
    fun pagesRunMyInfoPlayLabFromTheLeft() {
        assertEquals(listOf(HomePage.MyInfo, HomePage.Play, HomePage.Lab), HomePage.entries)
    }

    /** ① 켜면 늘 가운데 · 다른 화면에서 돌아오면 떠났던 페이지 — 그 기억은 프로세스 안에만 있고 처음 값이 대국이다. */
    @Test
    fun theHomeOpensOnThePlayPageAndComesBackWhereItLeft() {
        assertTrue("홈이 기억한 페이지로 열리지 않는다(#280 ①).", home.contains("rememberPagerState(initialPage = HomePageMemory.page.ordinal)"))
        assertTrue("페이지의 기억이 처음에 대국이 아니다 — 켜면 늘 가운데다(#280 ①).", home.contains("var page: HomePage = HomePage.Play"))
    }

    /**
     * ⑦ 옆 페이지의 뒤로가기는 가운데로 — 가운데에서는 홈이 잡지 않아 앱을 나간다(셸의 뒤로가기는 홈에서 꺼져 있다).
     * #281부터는 예측 뒤로가기다 — 끄는 동안 가운데가 들어오고, 놓으면 가운데로 넘어간다.
     */
    @Test
    fun backFromASidePageGoesToTheMiddle() {
        assertTrue(
            "옆 페이지에서만 뒤로가기를 잡지 않는다(#280 ⑦ · #281).",
            home.contains("PredictiveBackHandler(enabled = pagerState.currentPage != HomePage.Play.ordinal)"),
        )
        assertTrue(
            "뒤로가기를 놓았을 때 가운데로 넘어가지 않는다(#280 ⑦).",
            home.contains("val target = if (committed) HomePage.Play.ordinal else from"),
        )
        assertTrue("셸의 뒤로가기가 홈에서도 켜졌다 — 가운데에서 앱을 나가지 못한다.", shell.contains("currentDestination != ScreenDestination.Home"))
    }

    /** 내 정보는 따로 된 목적지가 아니라 홈의 왼쪽 페이지다 — 옛 목적지가 되살아나면 같은 화면으로 가는 길이 둘이 된다. */
    @Test
    fun myInfoIsAPageNotADestination() {
        assertFalse("셸에 「내 정보」 목적지가 되살아났다(#280).", shell.contains("ScreenDestination.MyPage"))
        assertTrue("셸이 홈에 내 정보 페이지를 넘기지 않는다.", shell.contains("myInfoPage = { MyPageScreen() }"))
    }

    /** 대국 페이지의 첫 카드는 「일반 대국」(옛 「캐릭터 대국」, #286)이다 — 「서 있는 답」(#239), 2026-10-23 사용자가 시안에서 다시 확인했다. */
    @Test
    fun characterMatchIsTheFirstCard() {
        val character = home.indexOf("title = strings.startMatch")
        val rank = home.indexOf("title = rankMeasureTitleFor(strings.language)")
        assertTrue("대국 카드를 찾지 못했다 — 이 계약의 전제가 바뀌었다.", character >= 0 && rank >= 0)
        assertTrue("「승급 대국」이 「일반 대국」보다 위에 있다(서 있는 답 #239).", character < rank)
    }

    /** 학습은 대국 페이지의 메뉴 줄이다(#288 — 처음은 타일) — 「사활 풀기」는 곧바로, 나머지 셋은 「기초 학습」 목록으로(사용자 2026-10-23). 분류는 빠짐없이 한 곳에 선다. */
    @Test
    fun everyStudyCategoryHasExactlyOnePlace() {
        assertEquals(
            "「기초 학습」 목록의 차례가 사용자가 정한 것과 다르다(#280).",
            listOf(StudyCategory.Rules, StudyCategory.Fundamentals, StudyCategory.YoutubeLessons),
            StudyBasicsCategories,
        )
        assertFalse("「사활 풀기」가 「기초 학습」 목록에도 있다 — 대국 페이지의 타일이 곧바로 연다.", StudyCategory.LifeAndDeath in StudyBasicsCategories)
        assertEquals(
            "자리가 없는 분류가 있다 — 새 분류를 열면 「기초 학습」 목록이나 대국 페이지의 타일에 자리를 줄 것.",
            StudyCategory.entries.toSet(),
            StudyBasicsCategories.toSet() + StudyCategory.LifeAndDeath,
        )
        listOf("onGameRecordsClick", "onLifeAndDeathClick", "onBasicsClick").forEach { row ->
            assertTrue("대국 페이지에 `$row` 메뉴 줄이 없다(#280 · #288).", home.contains("$row),"))
        }
    }

    /** 바둑판 사진 분석의 입구는 둘이다 — 대국 페이지 오른쪽 아래의 카메라 단추, 실험실 오른쪽 아래의 카드(사용자 2026-10-23). */
    @Test
    fun boardScanHasTwoQuietDoors() {
        assertTrue("대국 페이지에 카메라 단추가 없다(#280).", home.contains("imageVector = BoardScanCameraIcon"))
        assertTrue("실험실에 바둑판 사진 분석 카드가 없다(#280).", lab.contains("HomeMenuTile(strings.featureShortName(FeatureId.BoardScan), onBoardScanClick)"))
    }
}
