package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * 빌려 간 판(backlog #218) — 엔진은 하나라, 대국 밖의 화면(다시보기·바둑판 사진)이 다른 국면을 분석하면 **대국 엔진의 판이
 * 그 국면으로 바뀐다.** 돌아온 대국이 `syncFirst = false`로 형세를 물으면(사람:AI·AI:AI 대국의 형세 보기) 엔진은
 * 빌려 간 국면의 형세를 답했다. [LocalEngineSessionClient]가 그 가정을 락 안에서 확인하고, 깨졌으면 판부터 맞춘다.
 *
 * ⚠️ **대국 흐름이 느려지지 않는 것도 여기서 지킨다** — 대국 안에서는 예전처럼 맞추지 않아야 한다. 맞추기는
 * `newGame` + 전 수순 재생이라, 매번 맞추면 긴 판의 형세 보기가 눈에 띄게 느려진다.
 */
class LocalEngineSessionClientBorrowedBoardTest {
    private val engine = ParkingCoreApi()
    private val client = LocalEngineSessionClient(coreApi = engine, currentSessionGeneration = { 0L })

    /** 다시보기가 다른 국면의 추천 수를 본 뒤 — 대국의 형세 보기는 판부터 맞춘다. 예전: 빌려 간 국면의 형세를 답했다. */
    @Test
    fun aScoreEstimateResyncsAfterAnAnalysisLeftTheBoardAtAnotherPosition() = runBlocking {
        client.syncAndEstimateGraphScore(LiveGame, Profile)
        client.analyzePosition(ReplayedPosition, SmallLimit)
        val before = engine.calls.size

        client.estimateScoreForState(LiveGame, Profile, syncFirst = false)

        assertEquals(LiveGameResync, engine.calls.drop(before))
    }

    /** 다시보기가 다른 국면의 형세를 본 뒤에도 같다 — 형세 추정끼리도 판을 빌린다. */
    @Test
    fun aScoreEstimateResyncsAfterASyncedEstimateLeftTheBoardAtAnotherPosition() = runBlocking {
        client.syncAndEstimateGraphScore(LiveGame, Profile)
        client.estimateScoreForState(ReplayedPosition, Profile, syncFirst = true)
        val before = engine.calls.size

        client.estimateScoreForState(LiveGame, Profile, syncFirst = false)

        assertEquals(LiveGameResync, engine.calls.drop(before))
    }

    /** 한 번 맞췄으면 다음 형세 보기는 다시 맞추지 않는다 — 판은 이미 그 국면이다. */
    @Test
    fun theResyncHappensOnceNotOnEveryEstimate() = runBlocking {
        client.analyzePosition(ReplayedPosition, SmallLimit)
        client.estimateScoreForState(LiveGame, Profile, syncFirst = false)
        val before = engine.calls.size

        client.estimateScoreForState(LiveGame, Profile, syncFirst = false)

        assertEquals(listOf("estimate"), engine.calls.drop(before))
    }

    /** 대국 안 — 추천 수와 형세 보기가 **같은 국면**을 본다. 예전처럼 맞추지 않는다(대국이 느려지지 않는다). */
    @Test
    fun anEstimateAtTheSamePositionAsTheAnalysisDoesNotResync() = runBlocking {
        client.analyzePosition(LiveGame, SmallLimit)
        val before = engine.calls.size

        client.estimateScoreForState(LiveGame, Profile, syncFirst = false)

        assertEquals(listOf("estimate"), engine.calls.drop(before))
    }

    /** 대국 안 — 분석이 한 번도 없었다. 예전처럼 맞추지 않는다. */
    @Test
    fun anEstimateWithNoAnalysisBeforeItDoesNotResync() = runBlocking {
        client.syncAfterHumanMove(LiveGame, Profile, LiveGame.moves.last(), previousReviewCandidates = emptyList())
        val before = engine.calls.size

        client.estimateScoreForState(LiveGame, Profile, syncFirst = false)

        assertEquals(listOf("estimate"), engine.calls.drop(before))
    }

    /** 빌려 간 뒤에 대국 계열 오퍼레이션이 판을 맞췄다 — 판은 다시 대국의 것이라 형세 보기는 맞추지 않는다. */
    @Test
    fun aGameOperationAfterTheAnalysisGivesTheBoardBackToTheGame() = runBlocking {
        client.analyzePosition(ReplayedPosition, SmallLimit)
        client.syncAndEstimateGraphScore(LiveGame, Profile)
        val before = engine.calls.size

        client.estimateScoreForState(LiveGame, Profile, syncFirst = false)

        assertEquals(listOf("estimate"), engine.calls.drop(before))
    }

    /** AI 차례는 안에서 분석을 돌지만 대국 계열이다 — 차례가 끝난 뒤의 형세 보기는 맞추지 않는다. */
    @Test
    fun anAiTurnWithItsInnerAnalysisLeavesTheBoardWithTheGame() = runBlocking {
        val turn = client.runAutoAiTurn(GameState.empty(), PlayLevelSetting(), Profile, SearchTimeSettings(), EngineSearchMode.GtpStatefulFast, isolateSearchCache = false)
        val before = engine.calls.size

        client.estimateScoreForState(turn.turnOutcome.gameState, Profile, syncFirst = false)

        assertEquals(listOf("estimate"), engine.calls.drop(before))
    }

    /**
     * 수순을 다시 두다 **끊긴** 분석(다시보기에서 수순을 넘기면 앞선 분석을 취소한다) — 판은 어느 국면도 아니다.
     * 끊긴 분석이 향하던 바로 그 국면을 물어도 맞춘다: 표시가 "그 국면"으로 남아 있으면 반쯤 둔 판의 형세를 답한다.
     */
    @Test
    fun anAnalysisCancelledWhileReplayingMovesLeavesTheBoardUnknown() = runBlocking {
        engine.parkAt("play Black E5")
        val interrupted = async { client.analyzePosition(LiveGame, SmallLimit) }
        engine.awaitParked()
        interrupted.cancel()
        interrupted.join()
        val before = engine.calls.size

        client.estimateScoreForState(LiveGame, Profile, syncFirst = false)

        assertEquals(LiveGameResync, engine.calls.drop(before))
    }

    /**
     * 엔진을 **띄우기 전에** 판 크기를 알린다(백로그 #215) — 새 대국은 프로세스를 새로 띄우는데, 모르고 띄우면 19줄로 떠서
     * 13줄 판의 첫 `boardsize`에 약 1.2초를 더 쓴다(S23). 알림이 `initialize` 뒤로 가면 이미 뜬 뒤라 소용이 없다.
     */
    @Test
    fun aNewGameTellsTheEngineItsBoardSizeBeforeStartingIt() = runBlocking {
        client.startNewGame(Profile, BoardSize.Thirteen, Ruleset.Japanese, 0, 6.5)

        assertEquals(listOf(13 to 1), engine.boardSizeHints, "the hint must come after `stop` and before `initialize`")
        assertEquals(listOf("stop", "initialize"), engine.calls.take(2))
    }

    @Test
    fun startingTheSessionTellsTheEngineTheBoardSizeFirst() = runBlocking {
        client.startSession(Profile, LiveGame)

        assertEquals(listOf(LiveGame.boardSize.value to 0), engine.boardSizeHints)
    }

    private companion object {
        val Profile = EngineProfile()
        val SmallLimit = AnalysisLimit(visits = 16, timeMillis = 500L, candidateCount = 3)

        /** 대국 화면에 떠 있는 판. */
        val LiveGame: GameState = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("C3", BoardSize.Nine)))

        /** 다시보기에서 들여다본 다른 국면. */
        val ReplayedPosition: GameState = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("D4", BoardSize.Nine)))

        val LiveGameResync = listOf("newGame", "play Black E5", "play White C3", "estimate")
    }
}
