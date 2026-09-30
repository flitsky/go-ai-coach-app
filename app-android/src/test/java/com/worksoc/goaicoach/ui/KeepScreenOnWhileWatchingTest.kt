package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.ui.play.shouldKeepScreenOnWhileWatching
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #205 — 화면을 켜 두는 것은 **AI 대 AI 관전 중(종국 전)** 뿐이다(U-58, 2026-10-01). */
class KeepScreenOnWhileWatchingTest {

    @Test
    fun onlyAnAiVsAiGameInProgressKeepsTheScreenOn() {
        assertTrue(shouldKeepScreenOnWhileWatching(MatchMode.AiVsAi, isGameEnded = false))
        assertFalse("끝난 판은 풀어야 한다 — 켜 둔 채 자리를 비우면 배터리만 쓴다", shouldKeepScreenOnWhileWatching(MatchMode.AiVsAi, isGameEnded = true))
        listOf(MatchMode.HumanVsAi, MatchMode.AiVsHuman, MatchMode.LocalTwoPlayer).forEach { mode ->
            assertFalse("사람이 두는 판($mode)은 폰 설정을 따른다", shouldKeepScreenOnWhileWatching(mode, isGameEnded = false))
        }
    }

    @Test
    fun theGameScreenAppliesIt() {
        val content = RepoPaths.uiFile("GoCoachContent.kt").readContractSource()
        assertTrue(
            "대국 화면이 관전 중 화면 켜 두기를 걸지 않는다(#205).",
            content.contains("KeepScreenOnWhile(shouldKeepScreenOnWhileWatching(screenState.matchMode, screenState.isGameEnded))"),
        )
    }
}
