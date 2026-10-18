package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.attendance.buildAttendanceBoard
import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.monetization.attendanceFoldedRowIsWeekly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #256 — 설정과 「내 정보」의 배치(사용자 2026-10-13: 설정 「A 정리형」 · 좌석·판 설정 빼기 · 내 정보 이름 · 출석 판 접기 · 내 캐릭터).
 * 배치는 화면 코드라 단위 테스트가 없다 — 순서와 빠진 것을 소스로 못박는다(주석은 걷어내고 본다).
 */
class MyInfoAndSettingsLayoutContractTest {

    private val settings = RepoPaths.uiFile("SettingsScreen.kt").readContractSource()
    private val myPage = RepoPaths.uiFile("MyPageScreen.kt").readContractSource()
    private val myCharacters = RepoPaths.uiFile("MyCharactersRow.kt").readContractSource()

    @Test
    fun settingsSectionsComeInTheChosenOrder() {
        val order = listOf(
            "settingsDisplaySectionFor(",
            "LanguageSettingsPanel(",
            "FontScaleSettingsPanel()",
            // 「소리」 절 — 첫 절 바로 아래(#244, 사용자 2026-10-18).
            "settingsSoundSectionFor(",
            "BackgroundMusicSettingsPanel()",
            "KaTrainUxMenuPanel(",
            "settingsGameSectionFor(",
            "SearchTimeSettingsPanel(",
            "settingsHelpSectionFor(",
            "SettingsHelpLinks()",
            "settingsAboutSectionFor(",
            "AppUpdateRow(",
            "if (isDeveloperModeEnabled) {",
        )
        val positions = order.map { settings.indexOf(it) }
        order.zip(positions).forEach { (call, at) -> assertTrue("설정에 `$call`이 없다(#256).", at >= 0) }
        assertEquals("설정의 절 순서가 #256의 「A 정리형」과 다르다: $order", positions.sorted(), positions)
    }

    /**
     * 대국 쪽은 절 셋으로 펼친다(#261, 사용자 2026-10-13) — 같은 패널에 절 제목을 넘기고, 최대 탐색 시간은 AI 코치 절 끝이다.
     * ☰ 대국 메뉴는 절 제목을 넘기지 않아 예전 모양(카드 하나 + 소제목)이다 — 토글과 차례는 한 곳(`KaTrainUxPanels.kt`).
     */
    @Test
    fun gameSettingsAreThreeSectionsFromTheSharedPanel() {
        assertTrue("설정이 패널에 절 제목을 넘기지 않는다 — 대국 토글이 한 덩어리로 보인다(#261).", settings.contains("settingsSectionHeader = {"))
        val after = settings.indexOf("afterAiCoach = {")
        assertTrue("최대 탐색 시간이 AI 코치 절 끝에 있지 않다(#261).", after >= 0 && settings.indexOf("SearchTimeSettingsPanel(") > after)
        assertFalse("설정이 대국 토글을 다시 접는다 — #261에서 펼친 채로 정했다.", settings.contains("CollapsibleSettingsGroup("))
        val gameMenu = RepoPaths.uiFile("GameMenuSection.kt").readContractSource()
        assertFalse("대국 메뉴가 설정의 절 모양을 쓴다 — 메뉴는 카드 하나 + 소제목 그대로다.", gameMenu.contains("settingsSectionHeader"))
    }

    /**
     * 빈 구분선 두 줄(#256에서 발견) — 계정 절이 꺼져 있는데 그 앞뒤 구분선만 그어져 화면 가운데 남았다.
     * 설정의 구분선은 전부 로그인 플래그 안에 있어야 한다.
     */
    @Test
    fun dividersAroundTheHiddenAccountSectionOnlyDrawWhenLoginIsOn() {
        val lines = settings.lines()
        val dividerLines = lines.indices.filter { lines[it].trim() == "HorizontalDivider()" }
        assertTrue("구분선을 찾지 못했다 — 이 계약의 전제가 바뀌었다.", dividerLines.isNotEmpty())
        dividerLines.forEach { at ->
            val guard = lines.subList(maxOf(0, at - 3), at).joinToString("\n")
            assertTrue(
                "${at + 1}번째 줄의 구분선이 로그인 플래그 밖에 있다 — 로그인이 꺼진 지금 빈 줄로 남는다(#256).",
                guard.contains("if (FeatureFlags.isLoginEnabled) {"),
            )
        }
    }

    /**
     * 내 정보의 순서(사용자 2026-10-13, #260): 머리말 → **내 캐릭터** → 구독 카드 → 출석 → 1회권 → 고지 한 줄.
     * 내 캐릭터가 구독 카드 바로 위에 있어야 *"구독하면 모든 캐릭터가 열린다"* 로 이어 읽힌다.
     */
    @Test
    fun myInfoKeepsTheDecidedOrder() {
        val order = listOf(
            "MyPageProfileHeader()",
            "MyCharactersRow()",
            "PremiumSubscriptionCard()",
            "AttendanceBoardSection(",
            "strings.myPageInventoryTitle",
            "LocalOnlyDataNoticePointer()",
        )
        val positions = order.map { myPage.indexOf(it) }
        order.zip(positions).forEach { (call, at) -> assertTrue("내 정보에 `$call`이 없다.", at >= 0) }
        assertEquals("내 정보의 순서가 달라졌다: $order", positions.sorted(), positions)
    }

    /** 내 캐릭터는 **보기 + 광고로 조각 모으기**다 — 고르면 이어 두던 대국의 상대까지 바뀐다(사용자 2026-10-13). */
    @Test
    fun myCharactersOpensTheSharedRosterWithoutSelection() {
        assertTrue("내 캐릭터가 공유 캐러셀(`BotCharacterRosterHost`)을 쓰지 않는다.", myCharacters.contains("BotCharacterRosterHost("))
        assertTrue("내 캐릭터에서 상대를 고를 수 있다 — 보기 전용이어야 한다.", myCharacters.contains("onSelect = null"))
        assertFalse("내 캐릭터가 좌석을 바꾼다.", myCharacters.contains("onSideChange"))
    }

    @Test
    fun theFoldedAttendanceBoardShowsTheRowWhereTheUserIs() {
        assertFalse("처음에는 날마다의 행이다.", attendanceFoldedRowIsWeekly(buildAttendanceBoard(AttendanceState())))
        val midWeek = AttendanceState(attendanceCount = 4, claimedTiers = setOf(1, 2, 3))
        assertFalse("1~6일차를 다 찍기 전에는 날마다의 행이다.", attendanceFoldedRowIsWeekly(buildAttendanceBoard(midWeek)))
        val pastDaily = AttendanceState(attendanceCount = 10, claimedTiers = (1..7).toSet())
        assertTrue("1~6일차를 다 찍었으면 주 단위 행이다.", attendanceFoldedRowIsWeekly(buildAttendanceBoard(pastDaily)))
    }
}
