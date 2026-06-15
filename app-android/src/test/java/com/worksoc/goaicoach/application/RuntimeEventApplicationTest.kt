package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.operation.*

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.humanmove.*

import com.worksoc.goaicoach.application.startgame.*

import com.worksoc.goaicoach.application.savedgame.*

import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.application.session.*
import com.worksoc.goaicoach.application.runtime.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
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
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
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
        assertTrue(log.contains("searchMode=GtpStatefulFast"))
        assertTrue(log.contains("searchCache=clear"))
        assertTrue(log.contains("transition=\"engine_select_move\""))
    }

    @Test
    fun engineOperationDiscardedLogExplainsStaleResultReason() {
        val currentState = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val log = runtimeEngineOperationDiscardedLog(
            context = runtimeContext(gameState = currentState),
            discard = EngineOperationResultGuard.Discard(
                reason = "top_moves_analysis result is stale: requested move=0, current move=1.",
                operation = "top_moves",
                operationId = "top_moves:g2:m0:abc",
                sessionGeneration = 2L,
            ),
        )

        assertTrue(log.startsWith("event=engine_operation_discarded phase=engine_operation"))
        assertTrue(log.contains("operation=top_moves"))
        assertTrue(log.contains("operationId=top_moves:g2:m0:abc"))
        assertTrue(log.contains("sessionGeneration=2"))
        assertTrue(log.contains("discardReason=top_moves_analysis result is stale"))
        assertTrue(log.contains("requested move=0"))
        assertTrue(log.contains("current move=1"))
        assertTrue(log.contains("current=size=9 ruleset=Japanese moves=1 next=White"))
        assertTrue(log.contains("transition="))
    }

    @Test
    fun engineOperationDiscardLogPlanIncludesRuntimeAndDiagnosticEntries() {
        val currentState = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val plan = buildEngineOperationDiscardLogPlan(
            context = runtimeContext(gameState = currentState),
            currentState = currentState,
            discard = EngineOperationResultGuard.Discard(
                reason = "score_estimate result is stale",
                operation = "score_estimate",
                operationId = "score_estimate:g3:m0:def",
                sessionGeneration = 3L,
            ),
        )

        assertTrue(plan.runtimeLog.startsWith("event=engine_operation_discarded phase=engine_operation"))
        assertTrue(plan.runtimeLog.contains("operation=score_estimate"))
        assertEquals(DiagnosticSeverity.Info, plan.diagnosticEvent.severity)
        assertEquals("engine.operation.discarded", plan.diagnosticEvent.code)
        assertEquals("score_estimate result is stale", plan.diagnosticEvent.context["reason"])
        assertEquals("score_estimate", plan.diagnosticEvent.context["operation"])
        assertEquals("score_estimate:g3:m0:def", plan.diagnosticEvent.context["operationId"])
        assertEquals("3", plan.diagnosticEvent.context["sessionGeneration"])
        assertEquals("1", plan.diagnosticEvent.context["currentMoveCount"])
    }

    @Test
    fun recordEngineOperationDiscardLogWritesRuntimeAndDiagnosticPorts() {
        val currentState = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val runtimeLog = RecordingRuntimeEventLog()
        val diagnosticLog = RecordingDiagnosticEventLog()

        recordEngineOperationDiscardLog(
            context = runtimeContext(gameState = currentState),
            currentState = currentState,
            discard = EngineOperationResultGuard.Discard(
                reason = "late restored sync",
                operation = "restored_game_sync",
                operationId = "restored_game_sync:g5:m1:abc",
                sessionGeneration = 5L,
            ),
            runtimeEventLog = runtimeLog,
            diagnosticEventLog = diagnosticLog,
            nowMillis = 123L,
        )

        assertEquals(1, runtimeLog.events.size)
        assertTrue(runtimeLog.events.single().first.contains("event=engine_operation_discarded"))
        assertEquals(123L, runtimeLog.events.single().second)
        assertEquals(1, diagnosticLog.events.size)
        assertEquals("engine.operation.discarded", diagnosticLog.events.single().first.code)
        assertEquals(123L, diagnosticLog.events.single().second)
    }

    @Test
    fun engineOperationStartedAndCompletedLogsIncludeLifecycleCounts() {
        val context = runtimeContext(isEngineReady = true)

        val started = runtimeEngineOperationStartedLog(
            context = context.copy(isEngineBusy = true),
            operationId = "position_analysis:g1:m0:abc",
            activeOperationCount = 1,
        )
        val completed = runtimeEngineOperationCompletedLog(
            context = context.copy(isEngineBusy = false),
            operationId = "position_analysis:g1:m0:abc",
            activeOperationCount = 0,
        )

        assertTrue(started.startsWith("event=engine_operation_started phase=engine_operation"))
        assertTrue(started.contains("operationId=position_analysis:g1:m0:abc"))
        assertTrue(started.contains("activeOperationCount=1"))
        assertTrue(started.contains("transition=\"engine_busy_keep_current_state\""))
        assertTrue(completed.startsWith("event=engine_operation_completed phase=engine_operation"))
        assertTrue(completed.contains("operationId=position_analysis:g1:m0:abc"))
        assertTrue(completed.contains("activeOperationCount=0"))
        assertTrue(completed.contains("transition=\"await_human_move\""))
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

    @Test
    fun controllerStateBuildsRuntimeLogContext() {
        val gameState = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )
        val controller = GameSessionControllerState(
            core = GameSessionCoreState(
                gameState = gameState,
                isGameEnded = false,
                analysisState = GameSessionAnalysisState.empty(gameState),
                scoreState = GameSessionScoreState.reset(
                    scoreText = "Score estimate not current.",
                    scoreSnapshots = emptyList(),
                    endgameLog = "No endgame result recorded.",
                ),
                runtimeState = GameSessionRuntimeState(
                    playLevel = PlayLevelSetting(),
                    engineProfile = EngineProfile(),
                    analysisPreset = AnalysisPreset.Lite,
                ),
                moveReviewState = GameSessionMoveReviewState.reset(
                    moveReviewText = "none",
                    lastMoveText = "Black pass",
                ),
                engineMessage = "ready",
            ),
            settings = GameSessionSettingsState(
                playerSetup = playerSetup,
                autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
                searchTimeSettings = SearchTimeSettings(b16Millis = 1_500L),
                topMovesEnabled = true,
            ),
            benchmark = EngineBenchmarkUiState.initial("none", null),
            savedSession = SavedSessionUiState(shouldShowResumePrompt = true),
            autoAiTurn = AutoAiTurnUiState(isPending = true),
            positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
        )

        val context = controller.toRuntimeLogContext(
            engineName = "KataGo",
            engineDiagnostic = "ready",
            isEngineReady = true,
            isEngineBusy = false,
            analysisCacheStats = "entries=1",
            turnTimeText = "B=1.0s W=0.0s current=White",
        )

        assertEquals(playerSetup, context.playerSetup)
        assertEquals(gameState, context.gameState)
        assertEquals(true, context.topMovesEnabled)
        assertEquals(true, context.isAutoAiTurnPending)
        assertEquals(true, context.shouldShowResumePrompt)
        assertEquals("Score estimate not current.", context.scoreText)
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

    private class RecordingRuntimeEventLog : RuntimeEventLogPort {
        val events = mutableListOf<Pair<String, Long>>()

        override fun append(
            event: String,
            nowMillis: Long,
        ) {
            events += event to nowMillis
        }

        override fun readText(): String =
            events.joinToString("\n") { it.first }

        override fun clear() {
            events.clear()
        }
    }

    private class RecordingDiagnosticEventLog : DiagnosticEventLogPort {
        val events = mutableListOf<Pair<DiagnosticEvent, Long>>()

        override fun append(
            event: DiagnosticEvent,
            nowMillis: Long,
        ) {
            events += event to nowMillis
        }

        override fun readText(): String =
            events.joinToString("\n") { it.first.summary() }

        override fun clear() {
            events.clear()
        }
    }
}
