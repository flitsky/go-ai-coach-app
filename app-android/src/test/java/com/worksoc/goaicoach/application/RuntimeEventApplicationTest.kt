package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
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
        val log = runtimeAppStartLog(runtimeContext(engineDiagnostic = "ready\nwith local process"))

        assertTrue(log.contains("event=app_start"))
        assertTrue(log.contains("phase=startup"))
        assertTrue(log.contains("app=\"Go AI Coach\""))
        assertTrue(log.contains("purpose=\"Android-first local AI Go coaching app"))
        assertTrue(log.contains("engine=\"KataGo\""))
        assertTrue(log.contains("diagnostic=ready with local process"))
        assertTrue(log.contains("turnTime=\"B=0.0s W=0.0s current=Black\""))
        assertTrue(log.contains("transition=\"engine_startup_then_saved_game_check\""))
    }

    @Test
    fun gameResetLogIncludesSetupSearchAndFingerprint() {
        val reset = buildNewLocalGameSessionPlan(
            message = "new game\nstarted",
            ruleset = Ruleset.Japanese,
        )

        val log = runtimeGameResetLog(
            context = runtimeContext(searchTimeSettings = SearchTimeSettings(b16Millis = 2_000L)),
            reset = reset,
        )

        assertTrue(log.startsWith("event=game_reset phase=game_setup"))
        assertTrue(log.contains("board=\"size=9 ruleset=Japanese moves=0 next=Black"))
        assertTrue(log.contains("setup=\"Black: 플레이어 일반 / White: KataGo 빠른 초급 1단계\""))
        assertTrue(log.contains("autoDelay=1초/1000ms"))
        assertTrue(log.contains("search=B16 2000ms / B32 2000ms / B64 3000ms"))
        assertTrue(log.contains("detail=\"New local board prepared. message=new game started\""))
        assertTrue(log.contains("fp="))
    }

    @Test
    fun aiTurnBeginLogIncludesLevelLimitAndSearchCachePolicy() {
        val playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)
        val log = runtimeAiTurnBeginLog(
            context = runtimeContext(),
            turnState = GameState.empty(),
            aiPlayer = StoneColor.Black,
            playLevel = playLevel,
            analysisLimit = AnalysisLimit(visits = 32, timeMillis = 2_000L, candidateCount = 16),
            delayMillis = 500L,
            isolateSearchCache = true,
        )

        assertTrue(log.startsWith("event=ai_turn_begin phase=ai_turn"))
        assertTrue(log.contains("level=초급 7단계"))
        assertTrue(log.contains("limit=visits=32,timeMs=2000,candidates=16"))
        assertTrue(log.contains("delayMs=500"))
        assertTrue(log.contains("searchCache=clear"))
        assertTrue(log.contains("transition=\"engine_select_move\""))
    }

    @Test
    fun humanMoveAcceptedLogExplainsMoveAndNextTransition() {
        val beforeMove = GameState.empty()
        val result = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        ).getOrThrow()

        val log = runtimeHumanMoveAcceptedLog(
            context = runtimeContext(gameState = beforeMove, isEngineReady = true),
            beforeMove = beforeMove,
            localMove = result,
            turnTimeUpdate = GameSessionTurnTimeState
                .reset(beforeMove, nowMillis = 1_000L)
                .recordMove(StoneColor.Black, nowMillis = 2_200L, nextPlayer = StoneColor.White),
        )

        assertTrue(log.startsWith("event=human_move_accepted phase=human_turn"))
        assertTrue(log.contains("move=Black E5"))
        assertTrue(log.contains("before=size=9 ruleset=Japanese moves=0 next=Black"))
        assertTrue(log.contains("after=size=9 ruleset=Japanese moves=1 next=White"))
        assertTrue(log.contains("transition=\"sync_engine_after_human_move\""))
        assertTrue(log.contains("review=Move review: no pre-move analysis cache was ready."))
        assertTrue(log.contains("turnTime=player=Black elapsed=1.2s total=B=1.2s W=0.0s current=White"))
    }

    private fun runtimeContext(
        engineDiagnostic: String = "ready",
        gameState: GameState = GameState.empty(),
        isEngineReady: Boolean = true,
        searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    ): RuntimeLogContext =
        RuntimeLogContext(
            engineName = "KataGo",
            engineDiagnostic = engineDiagnostic,
            playerSetup = PlayerSetup(),
            gameState = gameState,
            runtimeState = GameSessionRuntimeState(
                playLevel = PlayLevelSetting(),
                engineProfile = EngineProfile(),
                analysisPreset = AnalysisPreset.Lite,
            ),
            autoPlayDelaySetting = AutoPlayDelaySetting.Normal,
            searchTimeSettings = searchTimeSettings,
            topMovesEnabled = true,
            isEngineReady = isEngineReady,
            isEngineBusy = false,
            isGameEnded = false,
            isAutoAiTurnPending = false,
            shouldShowResumePrompt = false,
            analysisCacheStats = "entries=0, hits=0, misses=0",
            moveAnalysisCoverage = "Analysis coverage: legal 81, scored 0, policy-only 0, pending 81.",
            scoreText = "No score estimate yet.",
            turnTimeText = "B=0.0s W=0.0s current=${gameState.nextPlayer.label}",
        )
}
