package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEventApplicationTest {
    @Test
    fun runtimeLogSnippetNormalizesWhitespaceAndTruncates() {
        assertEquals("one two three", " one\n two\t three ".runtimeLogSnippet(50))
        assertEquals("abcd...", "abcdef".runtimeLogSnippet(4))
    }

    @Test
    fun appStartLogIncludesEngineAndDiagnostic() {
        val log = runtimeAppStartLog(
            engineName = "KataGo",
            engineDiagnostic = "ready\nwith local process",
        )

        assertEquals("app_start engine=KataGo diagnostic=ready with local process", log)
    }

    @Test
    fun gameResetLogIncludesSetupSearchAndFingerprint() {
        val reset = buildNewLocalGameSessionPlan(
            message = "new game\nstarted",
            ruleset = Ruleset.Japanese,
        )

        val log = runtimeGameResetLog(
            reset = reset,
            playerSetup = PlayerSetup(),
            engineName = "KataGo",
            autoPlayDelaySetting = AutoPlayDelaySetting.Normal,
            searchTimeSettings = SearchTimeSettings(b16Millis = 2_000L),
        )

        assertTrue(log.startsWith("game_reset moves=0 next=Black ruleset=Japanese"))
        assertTrue(log.contains("setup=Black: 플레이어 일반 / White: KataGo 빠른 초급 1단계"))
        assertTrue(log.contains("autoDelayMs=1000"))
        assertTrue(log.contains("search=B16 2000ms / B32 2000ms / B64 3000ms"))
        assertTrue(log.contains("message=new game started"))
        assertTrue(log.contains("fp="))
    }

    @Test
    fun aiTurnBeginLogIncludesLevelLimitAndSearchCachePolicy() {
        val playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)
        val log = runtimeAiTurnBeginLog(
            turnState = GameState.empty(),
            aiPlayer = StoneColor.Black,
            playLevel = playLevel,
            analysisLimit = AnalysisLimit(visits = 32, timeMillis = 2_000L, candidateCount = 16),
            delayMillis = 500L,
            isolateSearchCache = true,
        )

        assertTrue(log.startsWith("ai_turn_begin move=1 player=Black"))
        assertTrue(log.contains("level=초급 7단계"))
        assertTrue(log.contains("limit=visits=32,timeMs=2000,candidates=16"))
        assertTrue(log.contains("delayMs=500"))
        assertTrue(log.contains("searchCache=clear"))
    }
}
