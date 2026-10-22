package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.home.HomePage
import com.worksoc.goaicoach.ui.l10n.StudyCategory
import com.worksoc.goaicoach.ui.study.StudyHubRow
import com.worksoc.goaicoach.ui.study.StudyHubRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #280 — 홈은 세 페이지를 옆으로 넘긴다(사용자 2026-10-22): 내 정보 ‹ **대국** › 학습 하기.
 * 페이지 배치는 화면 코드라 단위 테스트가 없다 — 사용자가 정한 것을 소스로 못박는다(주석은 걷어내고 본다).
 */
class HomePagesContractTest {

    private val home = RepoPaths.uiFile("GoCoachHomeScreen.kt").readContractSource()
    private val shell = RepoPaths.uiFile("GoCoachApp.kt").readContractSource()

    /** 선언 순서가 왼쪽부터의 자리다 — 가운데가 대국. */
    @Test
    fun pagesRunMyInfoPlayStudyFromTheLeft() {
        assertEquals(listOf(HomePage.MyInfo, HomePage.Play, HomePage.Study), HomePage.entries)
    }

    /** ① 켜면 늘 가운데 · 다른 화면에서 돌아오면 떠났던 페이지 — 그 기억은 프로세스 안에만 있고 처음 값이 대국이다. */
    @Test
    fun theHomeOpensOnThePlayPageAndComesBackWhereItLeft() {
        assertTrue("홈이 기억한 페이지로 열리지 않는다(#280 ①).", home.contains("rememberPagerState(initialPage = HomePageMemory.page.ordinal)"))
        assertTrue("페이지의 기억이 처음에 대국이 아니다 — 켜면 늘 가운데다(#280 ①).", home.contains("var page: HomePage = HomePage.Play"))
    }

    /** ⑦ 옆 페이지의 뒤로가기는 가운데로 — 가운데에서는 홈이 잡지 않아 앱을 나간다(셸의 뒤로가기는 홈에서 꺼져 있다). */
    @Test
    fun backFromASidePageGoesToTheMiddle() {
        assertTrue(
            "옆 페이지의 뒤로가기가 가운데로 가지 않는다(#280 ⑦).",
            home.contains("BackHandler(enabled = pagerState.currentPage != HomePage.Play.ordinal) { showPage(HomePage.Play) }"),
        )
        assertTrue("셸의 뒤로가기가 홈에서도 켜졌다 — 가운데에서 앱을 나가지 못한다.", shell.contains("currentDestination != ScreenDestination.Home"))
    }

    /** 내 정보는 따로 된 목적지가 아니라 홈의 왼쪽 페이지다 — 옛 목적지가 되살아나면 같은 화면으로 가는 길이 둘이 된다. */
    @Test
    fun myInfoIsAPageNotADestination() {
        assertFalse("셸에 「내 정보」 목적지가 되살아났다(#280).", shell.contains("ScreenDestination.MyPage"))
        assertTrue("셸이 홈에 내 정보 페이지를 넘기지 않는다.", shell.contains("myInfoPage = { MyPageScreen() }"))
        assertTrue("셸이 홈에 학습 허브 페이지를 넘기지 않는다.", shell.contains("studyPage = { StudyHubPage("))
    }

    /** 학습 하기 페이지의 줄 — 분류는 빠짐없이 한 번씩, 「기보 보기」 다음 맨 아래가 「유튜브 강좌」(사용자 2026-10-22). */
    @Test
    fun theStudyPageListsEveryCategoryOnceWithYoutubeLast() {
        val categories = StudyHubRows.filterIsInstance<StudyHubRow.Category>().map { it.category }
        assertEquals("학습 하기 페이지에 빠진 분류가 있다 — 새 분류를 열면 줄 표에도 더할 것.", StudyCategory.entries.toSet(), categories.toSet())
        assertEquals("학습 하기 페이지에 두 번 선 분류가 있다.", categories.size, categories.toSet().size)
        assertEquals(
            "학습 하기 페이지의 줄 차례가 사용자가 정한 것과 다르다(#280).",
            listOf(
                StudyHubRow.Category(StudyCategory.Rules),
                StudyHubRow.Category(StudyCategory.Fundamentals),
                StudyHubRow.Category(StudyCategory.LifeAndDeath),
                StudyHubRow.GameRecords,
                StudyHubRow.Category(StudyCategory.YoutubeLessons),
            ),
            StudyHubRows,
        )
    }
}
