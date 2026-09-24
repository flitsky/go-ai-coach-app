package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.operation.*
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.engineOperationRequest

import com.worksoc.goaicoach.application.contract.*
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

    /**
     * `capabilities.backend`가 **엔진 오퍼레이션의 `backendId`가 된다**(refactor backlog #60).
     *
     * ⚠️ 이 배선에는 **테스트가 하나도 없었다.** 네 군데의 `backendId = capabilities.backend.label`을
     * 전부 상수로 바꿔 놓고 `:shared:check`와 `:app-android:testDebugUnitTest`를 `--rerun-tasks`로
     * 돌려도 초록이었다. `backendId`는 진단 이벤트의 키라서, 원격 백엔드가 `local-engine`으로
     * 찍히면 **로그를 읽는 사람이 어느 엔진이 느렸는지 영영 모른다** — 조용히 틀리는 종류다.
     * (같은 사고를 `EngineMode` 기본값이 이미 한 번 냈다.)
     */
    @Test
    fun engineStartupOperationCarriesTheBackendIdFromCapabilities() = runBlocking {
        val backendIds = listOf(EngineSessionBackend.LocalEngine, EngineSessionBackend.RemoteServer)
            .map { backend -> backendIdOfStartupOperation(backend) }

        assertEquals(listOf("local-engine", "remote-server"), backendIds)
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

/**
 * [runEngineStartupApplication]이 만든 오퍼레이션을 라이프사이클 콜백으로 낚아채 `backendId`만 읽는다.
 * 이 경로를 고른 이유: 나머지 세 자리(`runEngineStartupEffect`/`runEngineBackedNewGameEffect`/
 * `runEngineUndoEffect`)는 요청을 함수 안에서만 만들고, 그 값이 밖으로 나오는 길이 **느림/타임아웃
 * 진단 이벤트뿐**이라 시계에 의존하지 않고는 결정적으로 관찰할 수 없다. 덮은 척하지 않고 적어 둔다.
 */
private suspend fun backendIdOfStartupOperation(backend: EngineSessionBackend): String {
    val started = mutableListOf<EngineOperationRequest>()
    val client = BackendReportingEngineSessionClient(backend)

    client.runEngineStartupApplication(
        EngineStartupRunRequest(
            state = GameState.empty(),
            profile = EngineProfile(name = "Startup"),
            sessionGeneration = 7,
            engineDiagnostic = { "diagnostic" },
            lifecycleCallbacks = EngineOperationLifecycleCallbacks(
                onStarted = { request -> started += request },
            ),
        ),
    )

    return started.single().backendId
}

private class BackendReportingEngineSessionClient(
    backend: EngineSessionBackend,
) : FakeEngineSessionClient() {
    override val capabilities: EngineSessionCapabilities = EngineSessionCapabilities(
        supportsDeviceBenchmark = false,
        backend = backend,
    )

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult = EngineStartupResult(message = "startup", scoreSnapshot = null)
}
