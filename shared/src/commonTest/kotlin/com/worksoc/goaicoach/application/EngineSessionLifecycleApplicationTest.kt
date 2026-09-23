package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.operation.*
import com.worksoc.goaicoach.shared.engine.engineOperationRequest

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.endgame.*
import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient

class EngineSessionLifecycleApplicationTest {
    @Test
    fun startupRunnerDelegatesToEngineSessionClient() = runBlocking {
        val client = RecordingLifecycleEngineSessionClient()
        val state = GameState.empty()
        val profile = EngineProfile(name = "Startup")
        val request = engineOperationRequest(
            kind = EngineOperationKind.EngineStartup,
            state = state,
            sessionGeneration = 4,
        )

        val result = client.runEngineStartupEffect(
            effect = GameSessionEffect.StartEngineSession(
                state = state,
                profile = profile,
            ),
            operationRequest = request,
            diagnosticEventLog = NoopDiagnosticEventLog,
        )

        assertEquals("startup", result.message)
        assertSame(profile, client.startedProfile)
        assertSame(state, client.startedState)
    }

    @Test
    fun newGameRunnerDelegatesToEngineSessionClient() = runBlocking {
        val client = RecordingLifecycleEngineSessionClient()
        val currentState = GameState.empty()
        val profile = EngineProfile(name = "NewGame")

        val result = client.runEngineBackedNewGameEffect(
            effect = GameSessionEffect.StartEngineBackedGame(
                currentState = currentState,
                profile = profile,
                boardSize = BoardSize.Nine,
                ruleset = Ruleset.Chinese,
            ),
        )

        assertEquals("new-game", result.message)
        assertSame(profile, client.newGameProfile)
        assertEquals(BoardSize.Nine, client.newGameBoardSize)
        assertEquals(Ruleset.Chinese, client.newGameRuleset)
    }

    @Test
    fun startupAndNewGameWorkflowResultsWrapSuccessAndFailure() = runBlocking {
        val state = GameState.empty()
        val profile = EngineProfile(name = "Workflow")

        val startupSuccess = RecordingLifecycleEngineSessionClient()
            .runEngineStartupWorkflowResult(
                effect = GameSessionEffect.StartEngineSession(
                    state = state,
                    profile = profile,
                ),
            )
        val startupFailure = RecordingLifecycleEngineSessionClient(
            startupError = IllegalStateException("startup failed"),
        ).runEngineStartupWorkflowResult(
            effect = GameSessionEffect.StartEngineSession(
                state = state,
                profile = profile,
            ),
        )
        val newGameFailure = RecordingLifecycleEngineSessionClient(
            newGameError = IllegalStateException("new game failed"),
        ).runEngineBackedNewGameWorkflowResult(
            effect = GameSessionEffect.StartEngineBackedGame(
                currentState = state,
                profile = profile,
                boardSize = BoardSize.Nine,
                ruleset = Ruleset.Chinese,
            ),
        )

        assertTrue(startupSuccess is EngineStartupWorkflowResult.Success)
        assertEquals("startup", (startupSuccess as EngineStartupWorkflowResult.Success).result.message)
        assertTrue(startupFailure is EngineStartupWorkflowResult.Failure)
        assertEquals("startup failed", (startupFailure as EngineStartupWorkflowResult.Failure).error.message)
        assertTrue(newGameFailure is EngineStartupWorkflowResult.Failure)
        assertEquals("new game failed", (newGameFailure as EngineStartupWorkflowResult.Failure).error.message)
    }

    @Test
    fun undoRunnerRepeatsRequestedUndoCountAndReturnsLastStatus() = runBlocking {
        val client = RecordingLifecycleEngineSessionClient()

        val status = client.runEngineUndoEffect(
            effect = GameSessionEffect.UndoEngineMoves(
                state = GameState.empty(),
                undoCount = 3,
            ),
        )

        assertEquals(3, client.undoCalls)
        assertEquals("undo-3", status.message)
    }

    @Test
    fun undoWorkflowResultWrapsSuccessAndFailure() = runBlocking {
        val success = RecordingLifecycleEngineSessionClient()
            .runEngineUndoWorkflowResult(
                effect = GameSessionEffect.UndoEngineMoves(
                    state = GameState.empty(),
                    undoCount = 2,
                ),
            )
        val failure = RecordingLifecycleEngineSessionClient(
            undoError = IllegalStateException("undo failed"),
        ).runEngineUndoWorkflowResult(
            effect = GameSessionEffect.UndoEngineMoves(
                state = GameState.empty(),
                undoCount = 2,
            ),
        )

        assertTrue(success is EngineUndoWorkflowResult.Success)
        assertEquals("undo-2", (success as EngineUndoWorkflowResult.Success).status.message)
        assertTrue(failure is EngineUndoWorkflowResult.Failure)
        assertEquals("undo failed", (failure as EngineUndoWorkflowResult.Failure).error.message)
    }

    @Test
    fun scopedEngineOperationHelperCompletesLifecycleOnFailure() = runBlocking {
        val request = engineOperationRequest(
            kind = EngineOperationKind.EngineUndo,
            state = GameState.empty(),
            sessionGeneration = 1,
        )
        val events = mutableListOf<String>()

        runCatching {
            runEngineOperationInScope(
                request = request,
                callbacks = EngineOperationLifecycleCallbacks(
                    onStarted = { events += "started:${it.operationId}" },
                    onCompleted = { events += "completed:${it.operationId}" },
                ),
            ) {
                error("boom")
            }
        }

        assertEquals(
            listOf(
                "started:${request.operationId}",
                "completed:${request.operationId}",
            ),
            events,
        )
    }
}

private class RecordingLifecycleEngineSessionClient(
    private val startupError: Throwable? = null,
    private val newGameError: Throwable? = null,
    private val undoError: Throwable? = null,
) : FakeEngineSessionClient() {
    override val capabilities: EngineSessionCapabilities = EngineSessionCapabilities(
        supportsDeviceBenchmark = true,
    )
    var startedProfile: EngineProfile? = null
    var startedState: GameState? = null
    var newGameProfile: EngineProfile? = null
    var newGameBoardSize: BoardSize? = null
    var newGameRuleset: Ruleset? = null
    var undoCalls: Int = 0

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String =
        "entries=0"

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult {
        startedProfile = profile
        startedState = state
        startupError?.let { throw it }
        return EngineStartupResult(
            message = "startup",
            scoreSnapshot = localScoreSnapshot(state),
        )
    }

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStartupResult {
        newGameProfile = profile
        newGameBoardSize = boardSize
        newGameRuleset = ruleset
        newGameError?.let { throw it }
        return EngineStartupResult(
            message = "new-game",
            scoreSnapshot = localScoreSnapshot(GameState.empty(boardSize = boardSize, ruleset = ruleset)),
        )
    }

    override suspend fun undoMove(): EngineStatus {
        undoError?.let { throw it }
        undoCalls += 1
        return EngineStatus.ready("undo-$undoCalls")
    }
}
