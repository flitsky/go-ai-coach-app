package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.debugreport.DebugReportController
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.LocalEngineMoveResult
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.analysisFingerprint
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.EngineOperationBlockReason
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test

/**
 * 컨트롤러 12개를 **실제 배선**([wireGoCoachControllers])으로 만들어 돌린다(refactor backlog #43).
 *
 * ## 왜 있는가
 * 이 테스트 전에는 12개 중 11개가 어떤 단위 테스트에서도 **생성조차 되지 않았다**(가드 테스트에
 * 이름이 문자열로만 나왔다). 배선에서 나는 버그는 CI를 전부 통과한 뒤 **실기에서만** 드러났다.
 *
 * ## ⚠️ "인스턴스가 만들어진다"로는 모자란다 — 함정 67
 * 컨트롤러가 게터 람다 대신 **값**을 붙잡으면 컨트롤러는 문제없이 만들어지고, 그 뒤로 설정을 아무리
 * 바꿔도 **붙잡은 값으로 동작한다.** 붙잡는 자리는 셋이고, 그물도 셋이다.
 * 1. **`GoCoachApp`의 익명 컨텍스트 객체**(함정 67의 원래 자리) — [WiringContextFreezeContractTest]
 *    (소스 계약: `remember` 키·컨트롤러 재배선). 이 클래스의 페이크로는 닿지 않는다.
 * 2. **배선 시점**(`wire*Controller`가 게터를 불러 값을 넘김) — (a)가
 *    [FakeGoCoachAppWiringContext.getterReads]를 정확한 목록으로 단언한다.
 * 3. **첫 사용 시점**(`lazy`·첫 결과를 저장하는 람다·컨트롤러의 지연 초기화 필드) — (b)의 탐침.
 *    (b)의 탐침은 **부른다 → 옛 값의 결과를 단언한다 → 값을 바꾼다 → 같은 인스턴스를 다시 부른다 →
 *    새 값의 결과를 단언한다**의 모양이다([FreshnessProbes]에는 이 탐침과 함께 (c)의 협력자 테스트·특성
 *    테스트 하나도 올라 있다 — 아래 "컨트롤러별 탐침·협력자 테스트 목록"). 첫 호출이 없으면 첫 사용
 *    캡처는 바뀐 **뒤의** 값을 붙잡아 초록이 된다(#43 검토에서 12개 중 11개가 그렇게 샜다). 첫 호출의
 *    결과를 단언하는 것은 "컨트롤러가 그 게터를 아예 안 본다"도 가려내려는 것이다.
 *
 *    ⚠️ 탐침 하나가 잡는 것은 **그 탐침이 첫 호출에서 읽고 나서 바꾼 게터 하나**의 첫 사용 캡처와
 *    "안 봄"뿐이다. 같은 컨트롤러의 다른 게터는 그 탐침으로 지켜지지 않는다 — 잡는 쌍과 못 잡는 쌍은
 *    아래 두 목록이 **전부**다.
 *
 * 단언은 언제나 컨트롤러가 컨텍스트 세터로 쓴 기록·걸린 실행·로그에 한다 — 테스트가 넣은 값을
 * 테스트가 다시 읽는 동어반복은 쓰지 않는다([FakeGoCoachAppWiringContext]의 "기록과 조작").
 *
 * ## 첫 사용 캡처를 잡는 (컨트롤러, 게터) 쌍 — 정확히 이것뿐이다
 * 괄호는 같은 게터를 읽는 배선 람다가 여럿일 때 어느 람다인지다.
 * - topMoves: `playerSetup`, `sessionSnapshot`(currentControllerState — 켜며 쓰는 설정의 출처),
 *   `shouldShowResumePrompt`, `isEngineReady`
 * - undo: `playerSetup`, `matchMode`, `isEngineReady`
 * - autoAi: `sessionSnapshot`(currentControllerState), `isEngineReady`, `currentRuntimeLogContext`(예약 로그 → 취소 로그)
 * - humanMove: `playerSetup`, `isEngineBlockingBusy`, `isEngineReady`, `gameState`(앞 수가 쓴 판 위에 다음 수)
 * - newGame: `playerSetup`, `settingsState`(currentBoardSize), `sessionSnapshot`(currentSearchTimeSettings), `isEngineReady`
 * - savedSession: `isEngineBusy`, `isEngineReady`, `settingsState`(복원 람다)
 * - cacheOpt: `playerSetup`(accept), `gameState`(dismiss)
 * - scoreEstimate: `matchMode`, `isEngineReady`
 * - scoringRule: `matchMode`, `gameState`, `isEngineReady`
 * - settings: `playerSetup`, `isGameEnded`
 * - debugReport: `sessionSnapshot`(copy()마다의 읽기 횟수·currentControllerState), `androidContext`(copy()마다),
 *   `isEngineReady`, `isEngineBusy`, `savedSessionRawJson`, `undoAnalysisRestoreCache`(analysisCacheStatsText),
 *   `engineClient.positionAnalysisCacheStatsText`, `turnTimeState`(turnTimeText·turnTimeDebugText)
 * - benchmark: `isEngineReady`, `isEngineBusy`, `benchmarkUiState`
 *
 * ## ⚠️ 이 클래스가 못 잡는 것
 * - **위 목록 밖의 (컨트롤러, 게터) 쌍은 전부** 첫 사용 캡처에 대해 검증되지 않았다(배선 시점 캡처는 (a)가
 *   컨트롤러와 게터를 가리지 않고 잡는다). 눈에 띄는 것:
 *   - `isEngineBusy` — topMoves·undo·autoAi·newGame·cacheOpt·scoreEstimate·scoringRule·settings
 *     (바꿔 보는 곳은 savedSession·benchmark·debugReport뿐이고, humanMove는 `isEngineBlockingBusy`를 읽는다).
 *   - `searchTimeSettings`(`sessionSnapshot().settings`) — autoAi·cacheOpt·savedSession·settings(바꿔 보는 곳은 newGame뿐).
 *   - autoAi의 `shouldShowResumePrompt` — 유일한 읽기(취소 로그)가 값을 바꾼 **뒤**라 첫 읽기가 곧 새 값이다.
 *   - newGame의 `settingsState`(currentHandicapCount), 위 목록에 그 게터가 없는 컨트롤러의 `isGameEnded`·
 *     `runtimeState`(세대·엔진 프로필)·`analysisState`·`scoreState`·`moveReviewState`·`turnTimeState`·
 *     `isPendingUndoSync`·`showMoveReviewEnabled`·`positionCacheOptimizationState` 등(settings의 `isGameEnded`와
 *     debugReport의 `turnTimeState`는 위 목록에 있다).
 * - 무르기·AI 자동 착수의 후속 분석: 무르기는 조용한 구간 `delay` 뒤에서, AI 착수는 엔진 착수가 끝난
 *   뒤에서만 닿는다. 이 페이크의 `delay`는 멈춘 채로 있으므로 탐침을 두지 않았다. 그 뒤에서 다시 읽는
 *   게터(무르기의 `isEngineReady`·`isEngineBusy`·`gameState`, AI 착수 블록의 검증)도 같은 이유로 못 본다.
 * - 여러 컨트롤러가 **같은** `TopMovesController` 인스턴스를 쓰는지: 그 컨트롤러는 인스턴스 상태가
 *   없고(유예는 컨텍스트가 쥔다) 갈라져도 지금은 무해하다. 상태가 생기면 여기에 단언을 더할 것.
 * - 디버그 리포트의 `engineName`/`engineDiagnostic`은 **알려진 잠복 동결**이다 —
 *   [debugReportEngineNameAndDiagnosticAreTheKnownLatentFreeze]가 지금 모습을 못박는다.
 *
 * ## (a) 배선은 아무것도 읽거나 쓰지 않는다
 * 배선 중 게터 읽기는 [FakeGoCoachAppWiringContext.getterReads]로, 쓰기는
 * [FakeGoCoachAppWiringContext.recordCounts](기록 20종 — 값 목록을 따로 두지 않는 세터 다섯은
 * `otherSetterCalls`로 센다)와 세션 홀더·엔진 준비 여부가 그대로인지로 본다.
 *
 * ## (c) 협력자 배선
 * [wireGoCoachControllers]의 KDoc이 생성 순서의 이유로 드는 의존 둘을 실제로 돌린다.
 * - 설정 → **공유** 무르기 컨트롤러: 설정을 바꾸면 조용한 구간을 닫으면서 **같은 인스턴스**에 걸린
 *   무르기 재동기화를 취소해야 한다.
 * - 착수·새 대국·이어하기·채점 규칙 → 추천 수 컨트롤러: 엔진 동기화가 끝나면 후속 분석을 넘긴다.
 *   엔진 작업 블록을 끝까지 돌려(`Dispatchers.IO` 한 번 왕복) 후속 분석이 공유 유예 자리
 *   ([FakeGoCoachAppWiringContext.deferredTopMoveAnalysis])에 도착하는지 본다.
 *
 * ## 컨트롤러별 탐침·협력자 테스트 목록
 * 목록은 [FreshnessProbes]에 있다. (b)의 탐침과 함께 (c)의 협력자 테스트, 특성 테스트 하나
 * ([debugReportEngineNameAndDiagnosticAreTheKnownLatentFreeze])가 올라 있다 — 그 컨트롤러를 실제 배선으로
 * 부르는 테스트의 목록이지, 올린 것이 모두 "부른다 → 바꾼다 → 다시 부른다" 탐침이라는 뜻은 아니다.
 * [everyWiredControllerHasAFreshnessProbeInThisClass]는 [GoCoachControllers]의 필드와 목록의 **이름**이
 * 맞는지, 올린 테스트가 이 클래스에 `@Test`로 있고 꺼져 있지 않은지만 본다 — 그 테스트가 정말 그 컨트롤러를
 * 부르는지는 사람이 읽어 확인한다.
 */
class GoCoachControllerWiringTest {

    // ── (a) 12개가 만들어지고, 배선은 아무 상태도 읽거나 쓰지 않는다 ──

    @Test
    fun wiringBuildsAllTwelveControllersWithoutReadingOrWritingState() {
        val context = FakeGoCoachAppWiringContext()
        val sessionBeforeWiring = context.holder.current
        val engineReadyBeforeWiring = context.engineIsReady

        wireGoCoachControllers(context)

        assertEquals("배선이 내놓는 컨트롤러는 12개다(#43).", 12, controllerFields().size)
        // ⚠️ 배선 **도중**의 게터 읽기는 곧 값 붙잡기다. 지금 읽히는 것은 디버그 리포트의 엔진
        // 이름·진단 둘뿐이고, 그 둘은 알려진 잠복 동결이다 — [debugReportEngineNameAndDiagnosticAreTheKnownLatentFreeze].
        assertEquals(
            "배선 도중 상태 게터를 읽으면 그 값이 컨트롤러에 얼어붙는다(함정 67). 새로 읽힌 게터가 있다면 람다로 바꿀 것.",
            listOf("engineName", "engineDiagnostic"),
            context.getterReads,
        )
        // 값을 **바꾸는** 쓰기는 여기서, 같은 값을 다시 쓰는 쓰기(홀더가 흘려보낸다)는 아래 otherSetterCalls가 잡는다.
        assertSame(
            "배선만으로 세션 상태가 바뀌었다 — setGameState·setMoveReviewState·setSavedSessionUiState·setIsGameEnded 등을 불렀다(#43).",
            sessionBeforeWiring,
            context.holder.current,
        )
        assertEquals("배선만으로 엔진 준비 여부가 바뀌었다 — setEngineReady를 불렀다(#43).", engineReadyBeforeWiring, context.engineIsReady)
        assertEquals(
            "배선만으로 컨텍스트에 쓰거나, 코루틴을 걸거나, 로그를 찍거나, androidContext를 읽으면 안 된다(#43).",
            context.recordCounts().mapValues { 0 },
            context.recordCounts(),
        )
    }

    @Test
    fun everyWiredControllerHasAFreshnessProbeInThisClass() {
        assertEquals(
            "GoCoachControllers에 컨트롤러가 더해지거나 빠졌다 — 그 컨트롤러의 '부르고 → 값 변경 → 다시 부르기' " +
                "탐침을 이 클래스에 쓰고 FreshnessProbes에 올릴 것(#43, 함정 67).",
            controllerFields().map { it.name }.toSet(),
            FreshnessProbes.keys,
        )
        FreshnessProbes.forEach { (controller, probes) ->
            assertTrue("$controller 의 탐침이 비어 있다(#43).", probes.isNotEmpty())
            probes.forEach { probe ->
                val method = runCatching { GoCoachControllerWiringTest::class.java.getDeclaredMethod(probe) }.getOrNull()
                    ?: return@forEach fail("$controller 의 탐침 $probe 이 이 클래스에 없다(#43).")
                assertTrue("$controller 의 탐침 $probe 에 @Test가 없다(#43).", method.isAnnotationPresent(Test::class.java))
                assertFalse("$controller 의 탐침 $probe 이 꺼져 있다 — 돌지 않는 탐침은 아무것도 지키지 않는다(#43).", method.isAnnotationPresent(Ignore::class.java))
            }
        }
    }

    // ── (b) 컨트롤러별 탐침: 부르고 → 값을 바꾸고 → 같은 인스턴스를 다시 부른다 ──

    /** 추천 수 — `playerSetup()`(함정 67이 이름을 든 값). AI 차례에 한 번 막히고, 사람 차례로 바뀌면 켜진다. */
    @Test
    fun topMovesSeesPlayerSetupChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        context.engineIsReady = true
        val controllers = wireGoCoachControllers(context)

        controllers.topMovesController.showForCurrentState()
        assertEquals("흑이 AI인 동안 추천 수는 막힌다(첫 호출).", listOf(TopMovesHumanTurnOnly), context.engineMessages)
        assertEquals(0, context.dispatcher.queuedCount)

        context.changePlayerSetup(HumanBlackAiWhite) // 다음 차례(흑)가 사람이 됐다.
        controllers.topMovesController.showForCurrentState()

        assertEquals(
            "배선 뒤 바꾼 playerSetup을 추천 수가 못 봤다 — 첫 호출 때의 설정(흑=AI)으로 또 막았다(함정 67).",
            1,
            context.engineMessages.count { it == TopMovesHumanTurnOnly },
        )
        assertEquals(listOf(false, true), context.settingsWrites.map { it.topMovesEnabled })
        assertEquals(
            "추천 수를 켜며 쓴 설정이 낡은 스냅샷에서 나와 방금 바꾼 playerSetup을 되돌렸다(함정 67).",
            HumanBlackAiWhite,
            context.settingsWrites.last().playerSetup,
        )
        assertEquals("추천 수 분석이 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
    }

    /** 추천 수 — `shouldShowResumePrompt()`(함정 67이 이름을 든 값). 한 번 켜진 뒤 이어하기 안내가 떴다. */
    @Test
    fun topMovesSeesResumePromptRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        context.engineIsReady = true
        val controllers = wireGoCoachControllers(context)

        controllers.topMovesController.showForCurrentState()
        assertFalse("안내가 없을 때 사람 차례면 추천 수가 켜진다(첫 호출).", TopMovesHumanTurnOnly in context.engineMessages)
        assertEquals(1, context.dispatcher.queuedCount)

        context.changeSession { it.withSavedSession(it.savedSession.copy(shouldShowResumePrompt = true)) }
        controllers.topMovesController.showForCurrentState()

        assertEquals("배선 뒤 뜬 이어하기 안내를 추천 수가 못 봤다(함정 67).", TopMovesHumanTurnOnly, context.engineMessages.last())
        assertEquals(listOf(true, false), context.settingsWrites.map { it.topMovesEnabled })
        assertEquals("안내가 떠 있는 동안 분석을 더 걸면 안 된다(#43).", 1, context.dispatcher.queuedCount)
    }

    /** 추천 수 — `isEngineReady()`. 사람 차례여도 준비 전에는 막히고, 준비된 뒤에는 켜며 분석을 건다. */
    @Test
    fun topMovesSeesEngineReadyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        val controllers = wireGoCoachControllers(context)

        controllers.topMovesController.showForCurrentState()
        assertEquals("준비 전이면 추천 수는 막힌다(첫 호출).", listOf(TopMovesHumanTurnOnly), context.engineMessages)
        assertEquals(0, context.dispatcher.queuedCount)

        context.engineIsReady = true
        controllers.topMovesController.showForCurrentState()

        assertEquals(
            "배선 뒤 준비된 엔진을 추천 수가 못 봤다 — 첫 호출 때의 '준비 전'으로 또 막았다(함정 67).",
            listOf(false, true),
            context.settingsWrites.map { it.topMovesEnabled },
        )
        assertEquals("추천 수 분석이 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
    }

    /** 무르기 — `playerSetup()`. 흑 한 수 뒤, 흑이 AI이면 무를 사람 수가 없고, 흑이 사람으로 바뀌면 무른다. */
    @Test
    fun undoSeesPlayerSetupChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        val controllers = wireGoCoachControllers(context)

        controllers.undoController.undoLastTurn()
        assertEquals("흑이 AI면 무를 사람 수가 없다(첫 호출).", listOf("No human move to undo yet."), context.engineMessages)
        assertTrue(context.coreWrites.isEmpty())

        context.changePlayerSetup(HumanBlackAiWhite)
        controllers.undoController.undoLastTurn()

        assertEquals(
            "배선 뒤 바꾼 playerSetup을 무르기가 못 봤다 — 첫 호출 때의 설정으로 또 '무를 사람 수가 없다'고 했다(함정 67).",
            "Local undo completed without engine sync.",
            context.engineMessages.last(),
        )
        assertTrue("무르기는 조용한 구간을 연다(#43).", context.quietUntilWrites.single() > 0L)
        assertTrue("판이 한 수 앞으로 돌아가야 한다(#43).", context.coreWrites.single().gameState.moves.isEmpty())
    }

    /** 무르기 — `matchMode()`. AI끼리 두는 판에서 한 번 막히고, 2인 대국으로 바뀌면 무른다. */
    @Test
    fun undoSeesMatchModeChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = TwoAis))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        val controllers = wireGoCoachControllers(context)

        controllers.undoController.undoLastTurn()
        assertEquals(listOf("Undo is not available while AI controls both sides."), context.engineMessages)

        context.changePlayerSetup(TwoHumans)
        controllers.undoController.undoLastTurn()

        assertEquals(
            "배선 뒤 바꾼 matchMode를 무르기가 못 봤다 — 첫 호출 때의 값으로 또 'AI끼리 두는 중'이라 막았다(함정 67).",
            "Local undo completed without engine sync.",
            context.engineMessages.last(),
        )
        assertTrue(context.coreWrites.single().gameState.moves.isEmpty())
    }

    /**
     * 무르기 — `isEngineReady()`. 준비 전에는 엔진 없이 무르기만 하고, 사람이 다시 둔 뒤 엔진이 준비된
     * 상태에서 무르면 조용한 구간 뒤의 재동기화를 예약한다.
     */
    @Test
    fun undoSeesEngineReadyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        val controllers = wireGoCoachControllers(context)

        controllers.undoController.undoLastTurn()
        assertEquals("준비 전이면 엔진 없이 무른다(첫 호출).", listOf("Local undo completed without engine sync."), context.engineMessages)
        assertTrue(context.pendingUndoSyncWrites.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)

        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) } // 사람이 다시 뒀다.
        context.engineIsReady = true
        controllers.undoController.undoLastTurn()

        assertEquals(
            "배선 뒤 준비된 엔진을 무르기가 못 봤다 — 첫 호출 때의 '준비 전'으로 또 재동기화 없이 물렀다(함정 67).",
            listOf(true),
            context.pendingUndoSyncWrites,
        )
        assertEquals("재동기화 블록이 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
    }

    /**
     * AI 자동 착수 — `sessionSnapshot()`(여기서 playerSetup이 들어온다)·`currentRuntimeLogContext()`·
     * `shouldShowResumePrompt()`. 사람 차례에는 건너뛰고, 배선 뒤 AI 차례가 되면 예약한다.
     * 예약 로그가 런타임 문맥을 **처음** 읽은 뒤 엔진 이름을 다시 바꾸고, 예약된 블록이 돌기 전에 이어하기
     * 안내가 뜨면 취소 로그가 **그때의** 엔진 이름과 안내 여부를 적는다.
     *
     * ⚠️ `shouldShowResumePrompt()`는 첫 사용 캡처를 잡지 못한다 — 이 컨트롤러가 그 람다를 읽는 곳은 취소
     * 로그뿐이고, 그 첫 읽기가 안내를 띄운 **뒤**다. 여기서는 "그 순간의 값을 읽는다"만 본다.
     */
    @Test
    fun autoAiTurnSeesSessionAndResumePromptChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        context.engineIsReady = true
        context.currentEngineName = "engine-before"
        val controllers = wireGoCoachControllers(context)

        controllers.autoAiTurnController.requestAiTurn()
        assertTrue("사람 차례에는 AI 착수를 예약하지 않는다(#43).", context.autoAiTurnWrites.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)

        context.changePlayerSetup(AiBlackHumanWhite)
        context.currentEngineName = "engine-after"
        controllers.autoAiTurnController.requestAiTurn()

        assertEquals(
            "배선 뒤 AI 차례가 됐는데 자동 착수가 예약되지 않았다 — 세션 스냅샷이 얼었다(함정 67).",
            listOf(true),
            context.autoAiTurnWrites.map { it.isPending },
        )
        assertEquals("AI 착수 블록이 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
        val scheduleLog = context.runtimeLog.lines.single { it.contains("event=ai_turn_schedule phase=") }
        assertTrue("런타임 로그 문맥은 지금의 엔진 이름으로 찍는다(#43).", scheduleLog.contains("engine-after"))
        assertFalse(scheduleLog.contains("engine-before"))

        context.currentEngineName = "engine-latest" // 예약 로그가 문맥을 한 번 읽은 **뒤**에 바꾼다.
        context.changeSession { it.withSavedSession(it.savedSession.copy(shouldShowResumePrompt = true)) }
        assertTrue(context.dispatcher.runNext())

        val cancelLog = context.runtimeLog.lines.single { it.contains("event=ai_turn_schedule_cancelled") }
        assertTrue(
            "취소 로그의 런타임 문맥이 예약 로그 때 읽은 엔진 이름에 머물렀다 — 첫 읽기 값을 쥐었다(함정 67).",
            cancelLog.contains("engine-latest") && !cancelLog.contains("engine-after"),
        )
        assertTrue(
            "취소 로그의 이어하기 안내 여부는 배선 뒤 바뀐 값이어야 한다(함정 67).",
            cancelLog.substringAfter("detail=").contains("resumePrompt=true"),
        )
        assertFalse("취소되면 예약 표시가 풀린다(#43).", context.autoAiTurnWrites.last().isPending)
    }

    /** AI 자동 착수 — `isEngineReady()`. AI 차례여도 준비 전에는 건너뛰고, 준비된 뒤에는 예약한다. */
    @Test
    fun autoAiTurnSeesEngineReadyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        val controllers = wireGoCoachControllers(context)

        controllers.autoAiTurnController.requestAiTurn()
        assertTrue("준비 전이면 AI 착수를 예약하지 않는다(첫 호출).", context.autoAiTurnWrites.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)

        context.engineIsReady = true
        controllers.autoAiTurnController.requestAiTurn()

        assertEquals(
            "배선 뒤 준비된 엔진을 AI 자동 착수가 못 봤다 — 첫 호출 때의 '준비 전'으로 또 건너뛰었다(함정 67).",
            listOf(true),
            context.autoAiTurnWrites.map { it.isPending },
        )
        assertEquals("AI 착수 블록이 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
    }

    /** 사람 착수 — `playerSetup()`(착수의 첫 관문). AI 차례라 한 번 거절되고, 사람 차례로 바뀌면 받는다. */
    @Test
    fun humanMoveSeesPlayerSetupChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        val controllers = wireGoCoachControllers(context)

        controllers.humanMoveController.submitMove(BlackAtThreeThree)
        assertEquals("흑이 AI면 사람 착수를 받지 않는다(첫 호출).", listOf(NotHumanTurn), context.engineMessages)
        assertTrue(context.coreWrites.isEmpty())

        context.changePlayerSetup(HumanBlackAiWhite)
        controllers.humanMoveController.submitMove(BlackAtThreeThree)

        assertEquals(
            "배선 뒤 바꾼 playerSetup을 착수가 못 봤다 — 첫 호출 때의 설정(흑=AI)으로 또 거절했다(함정 67).",
            1,
            context.engineMessages.count { it == NotHumanTurn },
        )
        assertTrue(context.engineMessages.last().startsWith(LocalMoveAccepted))
        assertEquals("착수가 판에 올라가야 한다(#43).", listOf<Move>(BlackAtThreeThree), context.coreWrites.last().gameState.moves)
        assertEquals("착수는 무르기 조용한 구간을 닫는다(#43).", 1, context.quietWindowClears)
    }

    /** 사람 착수 — `isEngineBlockingBusy()`. 한 수는 받고, 엔진이 막는 일을 시작한 뒤의 다음 수는 막는다. */
    @Test
    fun humanMoveSeesEngineBlockingBusyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = TwoHumans))
        val controllers = wireGoCoachControllers(context)

        controllers.humanMoveController.submitMove(BlackAtThreeThree)
        assertTrue("엔진이 한가하면 받는다(첫 호출).", context.engineMessages.last().startsWith(LocalMoveAccepted))

        context.engineIsBlockingBusy = true
        controllers.humanMoveController.submitMove(WhiteAtFourFour)

        assertEquals(
            "배선 뒤 엔진이 바빠졌는데 착수가 못 봤다 — 첫 호출 때의 한가함으로 또 받았다(함정 67).",
            "Engine is busy. Wait for the current action.",
            context.engineMessages.last(),
        )
        assertEquals(listOf<Move>(BlackAtThreeThree), context.holder.current.gameState.moves)
    }

    /**
     * 사람 착수 — `isEngineReady()`(와 `gameState()`). 2인 대국에서 준비 전의 흑 한 수는 엔진 없이 받고,
     * 준비된 뒤의 백 한 수는 엔진 동기화를 건다. 백 수는 **첫 수가 쓴 판** 위에서만 차례가 맞는다.
     */
    @Test
    fun humanMoveSeesEngineReadyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = TwoHumans))
        val controllers = wireGoCoachControllers(context)

        controllers.humanMoveController.submitMove(BlackAtThreeThree)
        assertTrue("준비 전이면 엔진 없이 받는다(첫 호출).", context.engineMessages.single().startsWith(LocalMoveAccepted))
        assertEquals(0, context.dispatcher.queuedCount)

        context.engineIsReady = true
        controllers.humanMoveController.submitMove(WhiteAtFourFour)

        assertEquals(
            "배선 뒤 준비된 엔진을 착수가 못 봤다 — 첫 호출 때의 '준비 전'으로 또 엔진 없이 받았다(함정 67).",
            1,
            context.engineMessages.count { it.startsWith(LocalMoveAccepted) },
        )
        assertEquals(
            "두 번째 수는 첫 수가 놓인 판 위에 놓여야 한다 — 첫 호출 때의 판을 쥐면 차례가 어긋나 거절된다(함정 67).",
            listOf<Move>(BlackAtThreeThree, WhiteAtFourFour),
            context.holder.current.gameState.moves,
        )
        assertEquals("착수 뒤 엔진 동기화가 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
    }

    /**
     * 새 대국 — `playerSetup()`과 `settingsState()`(판 크기). 2인·13줄로 한 번 시작하고, AI 대국·19줄로 바꿔
     * 다시 시작한다. 리셋은 매번 무르기 조용한 구간과 무르기 복원 캐시를 비운다.
     */
    @Test
    fun newGameSeesPlayerSetupAndBoardSizeChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = TwoHumans))
        val controllers = wireGoCoachControllers(context)

        context.seedUndoRestoreCache()
        controllers.newGameController.startConfiguredGame()
        assertEquals(
            "2인 대국은 엔진 없이 로컬로 시작한다(첫 호출).",
            "Local two-player game. Engine analysis is not connected.",
            context.coreWrites.single().engineMessage,
        )
        assertEquals(BoardSize.Thirteen, context.coreWrites.single().gameState.boardSize)
        assertEquals("리셋은 무르기 조용한 구간을 닫는다(#43).", 1, context.quietWindowClears)
        assertEquals("리셋은 무르기 복원 캐시를 비운다(#43).", 0, context.undoRestoreEntries())

        context.changeSettings { it.applyPlayerSetup(HumanBlackAiWhite).applyBoardSize(BoardSize.Nineteen) }
        context.seedUndoRestoreCache()
        controllers.newGameController.startConfiguredGame()

        val reset = context.coreWrites.last()
        assertEquals(
            "배선 뒤 바꾼 playerSetup을 새 대국이 못 봤다 — 첫 호출 때의 값이면 또 '2인 대국'으로 시작한다(함정 67).",
            AiSetupEngineNotReady,
            reset.engineMessage,
        )
        assertEquals("배선 뒤 바꾼 판 크기로 시작해야 한다(#43).", BoardSize.Nineteen, reset.gameState.boardSize)
        assertEquals(2, context.coreWrites.size)
        assertEquals("리셋은 대국 시간도 새로 잰다(#43).", 2, context.turnTimeWrites.size)
        assertEquals(2, context.quietWindowClears)
        assertEquals(0, context.undoRestoreEntries())
        assertEquals(2, context.runtimeLog.lines.count { it.contains("event=game_reset") })
    }

    /**
     * 새 대국 — `sessionSnapshot().settings.searchTimeSettings`(함정 67이 이름을 든 값). 엔진 대국을 한 번
     * 시작해 엔진 작업을 끝까지 돌리고(→ 추천 수로 후속 분석을 넘긴다), 탐색 시간을 바꿔 다시 시작한다.
     */
    @Test
    fun newGameSeesSearchTimeChangedAfterWiringAndHandsItsFollowUpToTopMoves() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        context.engineIsReady = true
        context.changeSettings { it.applySearchTimeSettings(SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds)) }
        val controllers = wireGoCoachControllers(context)

        controllers.newGameController.startConfiguredGame()
        assertEquals(listOf<Long?>(3_000L), context.runtimeWrites.map { it.engineProfile.analysisLimit.timeMillis })
        context.dispatcher.runEngineOperationThroughIo()
        val followUp = context.deferredTopMoveAnalysis.takeWhenIdle(isEngineBusy = false)
        assertEquals(
            "엔진 새 대국이 끝나면 추천 수 컨트롤러에 후속 분석을 넘겨야 한다 — requestFollowUpAnalysis 배선이 끊겼다(#43).",
            context.holder.current.gameState,
            followUp?.targetState,
        )

        context.changeSettings { it.applySearchTimeSettings(SearchTimeSettings(SearchTimeLimit.WithinOneSecond)) }
        controllers.newGameController.startConfiguredGame()

        assertEquals(
            "배선 뒤 바꾼 탐색 시간을 새 대국이 못 봤다 — 첫 호출 때의 3초로 또 골랐다(함정 67).",
            listOf<Long?>(3_000L, 1_000L),
            context.runtimeWrites.map { it.engineProfile.analysisLimit.timeMillis },
        )
    }

    /** 새 대국 — `isEngineReady()`. AI 대국을 준비 전에 시작하면 로컬로 리셋하고, 준비된 뒤에는 엔진 새 대국을 건다. */
    @Test
    fun newGameSeesEngineReadyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        val controllers = wireGoCoachControllers(context)

        controllers.newGameController.startConfiguredGame()
        assertEquals("AI 대국인데 준비 전이면 로컬로 리셋한다(첫 호출).", AiSetupEngineNotReady, context.coreWrites.single().engineMessage)
        assertTrue(context.runtimeWrites.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)

        context.engineIsReady = true
        controllers.newGameController.startConfiguredGame()

        assertEquals(
            "배선 뒤 준비된 엔진을 새 대국이 못 봤다 — 첫 호출 때의 '준비 전'으로 또 로컬 리셋했다(함정 67).",
            1,
            context.coreWrites.count { it.engineMessage == AiSetupEngineNotReady },
        )
        assertEquals("엔진 새 대국은 런타임을 고른다(#43).", 1, context.runtimeWrites.size)
        assertEquals("엔진 새 대국 작업이 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
    }

    /**
     * 이어하기 — `isEngineBusy()`와 복원 람다 안의 `settingsState()`. 이 컨트롤러는 playerSetup을
     * **읽지 않는다**(복원할 설정은 저장본이 가져온다). 바쁠 때 한 번 거절되고, 풀린 뒤에는 그 순간의
     * 설정 위에 저장본을 덮는다 — 13줄 설정에서 한 번(설정의 첫 읽기), 19줄로 바꾼 뒤 한 번. 복원은 매번
     * 무르기 조용한 구간과 무르기 복원 캐시를 비운다.
     */
    @Test
    fun savedSessionSeesEngineBusyAndSettingsChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession())
        context.engineIsBusy = true
        val controllers = wireGoCoachControllers(context)

        controllers.savedSessionController.restore(SavedGameWithOneMove)
        assertEquals("엔진이 바쁘면 복원을 미룬다(첫 호출).", listOf(RestoreWhileBusy), context.engineMessages)
        assertTrue(context.settingsWrites.isEmpty())

        context.engineIsBusy = false
        context.seedUndoRestoreCache()
        controllers.savedSessionController.restore(SavedGameWithOneMove)

        assertEquals(
            "배선 뒤 풀린 엔진 바쁨을 이어하기가 못 봤다 — 첫 호출 때의 바쁨으로 또 미뤘다(함정 67).",
            1,
            context.engineMessages.count { it == RestoreWhileBusy },
        )
        val firstRestore = context.settingsWrites.single()
        assertEquals("복원은 그 순간의 설정 위에 저장본을 덮는다(#43).", BoardSize.Thirteen, firstRestore.boardSize)
        assertEquals(SavedGameWithOneMove.playerSetup, firstRestore.playerSetup)
        assertTrue(firstRestore.topMovesEnabled)
        assertEquals(listOf<Move>(BlackAtThreeThree), context.coreWrites.single().gameState.moves)
        assertEquals("복원은 무르기 조용한 구간을 닫는다(#43).", 1, context.quietWindowClears)
        assertEquals("복원은 무르기 복원 캐시를 비운다(#43).", 0, context.undoRestoreEntries())

        context.changeSettings { it.applyBoardSize(BoardSize.Nineteen) }
        context.seedUndoRestoreCache()
        controllers.savedSessionController.restore(SavedGameWithOneMove)

        assertEquals(
            "배선 뒤 바꾼 설정(판 크기)을 이어하기가 못 봤다 — 앞선 복원 때 읽은 설정 위에 또 덮었다(함정 67).",
            listOf(BoardSize.Thirteen, BoardSize.Nineteen),
            context.settingsWrites.map { it.boardSize },
        )
        assertEquals(2, context.quietWindowClears)
        assertEquals(0, context.undoRestoreEntries())
        assertEquals("엔진이 준비 전이면 동기화를 걸지 않는다(#43).", 0, context.dispatcher.queuedCount)
    }

    /** 이어하기 — `isEngineReady()`. 준비 전에는 로컬로만 복원하고, 준비된 뒤의 복원은 엔진과 맞춘다. */
    @Test
    fun savedSessionSeesEngineReadyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession())
        val controllers = wireGoCoachControllers(context)

        controllers.savedSessionController.restore(SavedGameWithOneMove)
        assertEquals("엔진이 준비 전이면 동기화를 걸지 않는다(첫 호출).", 0, context.dispatcher.queuedCount)

        context.engineIsReady = true
        controllers.savedSessionController.restore(SavedGameWithOneMove)

        assertEquals("배선 뒤 준비된 엔진을 이어하기가 못 봤다 — 동기화가 걸리지 않았다(함정 67).", 1, context.dispatcher.queuedCount)
    }

    /**
     * 대국 후 캐시 최적화 — `playerSetup()`. 백이 빠른 초급(GTP 빠른 탐색이라 대상 없음)일 때 한 번 받아도
     * 아무것도 시작하지 않고, 초급(JSON 국면 분석)으로 바뀐 뒤에 받으면 시작한다.
     */
    @Test
    fun cacheOptimizationSeesPlayerSetupChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree).play(WhiteAtFourFour)) }
        val controllers = wireGoCoachControllers(context)

        controllers.cacheOptController.accept()
        assertTrue("빠른 초급이면 최적화 대상이 없다(첫 호출).", context.engineMessages.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)

        context.changePlayerSetup(PlayerSetup(white = SidePlayerSetup(SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, 1))))
        controllers.cacheOptController.accept()

        assertEquals(
            "배선 뒤 바꾼 AI 세기를 캐시 최적화가 못 봤다 — 첫 호출 때의 값이면 또 대상이 없다(함정 67).",
            1,
            context.engineMessages.size,
        )
        assertTrue(context.engineMessages.single().startsWith("Post-game cache optimization started:"))
        assertTrue(context.cacheOptimizationWrites.last().isRunning)
        assertEquals("최적화 작업이 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
    }

    /** 대국 후 캐시 최적화 — `gameState()`. 안내를 한 번 닫고, 한 수가 더 놓인 뒤 다시 닫으면 새 판의 지문을 쓴다. */
    @Test
    fun cacheOptimizationDismissSeesGameStateChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession())
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        val controllers = wireGoCoachControllers(context)

        controllers.cacheOptController.dismiss()
        val firstFingerprint = context.holder.current.gameState.analysisFingerprint()
        assertEquals(listOf(firstFingerprint), context.cacheOptimizationWrites.map { it.dismissedGameFingerprint })

        context.changeCore { it.copy(gameState = it.gameState.play(WhiteAtFourFour)) }
        controllers.cacheOptController.dismiss()

        val secondFingerprint = context.holder.current.gameState.analysisFingerprint()
        assertNotEquals(firstFingerprint, secondFingerprint)
        assertEquals(
            "배선 뒤 바뀐 판을 캐시 최적화가 못 봤다 — 첫 호출 때의 판 지문으로 또 닫았다(함정 67).",
            listOf(firstFingerprint, secondFingerprint),
            context.cacheOptimizationWrites.map { it.dismissedGameFingerprint },
        )
    }

    /** 형세 추정 — `matchMode()`. AI 대국에서는 엔진 없이 막히고, 2인 대국으로 바뀌면 로컬로 센다. */
    @Test
    fun scoreEstimateSeesMatchModeChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        val controllers = wireGoCoachControllers(context)

        controllers.scoreEstimateController.request()
        assertEquals("AI 대국에서 엔진이 준비 전이면 막는다(첫 호출).", listOf(EngineNotReady), context.engineMessages)
        assertTrue(context.coreWrites.isEmpty())

        context.changePlayerSetup(TwoHumans)
        controllers.scoreEstimateController.request()

        assertEquals(
            "배선 뒤 바꾼 matchMode를 형세 추정이 못 봤다 — 첫 호출 때의 값으로 또 '엔진 준비 전'이라고만 했다(함정 67).",
            listOf(EngineNotReady),
            context.engineMessages,
        )
        assertEquals("Local Territory estimate refreshed.", context.coreWrites.single().engineMessage)
    }

    /** 형세 추정 — `isEngineReady()`. 준비 전에는 막히고, 준비된 뒤에는 엔진 추정을 건다. */
    @Test
    fun scoreEstimateSeesEngineReadyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        val controllers = wireGoCoachControllers(context)

        controllers.scoreEstimateController.request()
        assertEquals("준비 전이면 막는다(첫 호출).", listOf(EngineNotReady), context.engineMessages)
        assertEquals(0, context.dispatcher.queuedCount)

        context.engineIsReady = true
        controllers.scoreEstimateController.request()

        assertEquals("배선 뒤 준비된 엔진을 형세 추정이 못 봤다 — 또 막았다(함정 67).", listOf(EngineNotReady), context.engineMessages)
        assertEquals("엔진 형세 추정이 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)
    }

    /** 채점 규칙 — `matchMode()`. AI 대국에서는 점수가 '지금 아님'으로 남고, 2인 대국으로 바뀌면 로컬 점수를 보여 준다. */
    @Test
    fun scoringRuleSeesMatchModeChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite, ruleset = Ruleset.Chinese))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        val controllers = wireGoCoachControllers(context)

        controllers.scoringRuleController.change(Ruleset.Japanese)
        val first = context.coreWrites.single()
        assertEquals(Ruleset.Japanese, first.gameState.ruleset)
        assertEquals("AI 대국은 엔진 없이 점수를 확정하지 않는다(첫 호출).", ScoreNotCurrent, first.scoreState.scoreText)

        context.changePlayerSetup(TwoHumans)
        controllers.scoringRuleController.change(Ruleset.Chinese)

        val second = context.coreWrites.last()
        assertEquals(2, context.coreWrites.size)
        assertEquals(Ruleset.Chinese, second.gameState.ruleset)
        assertNotEquals(
            "배선 뒤 바꾼 matchMode를 채점 규칙이 못 봤다 — 첫 호출 때의 값이면 또 로컬 점수를 보여 주지 않는다(함정 67).",
            ScoreNotCurrent,
            second.scoreState.scoreText,
        )
        assertEquals("Scoring rule changed to Area. Local scoring is active.", second.engineMessage)
    }

    /** 채점 규칙 — `isEngineReady()`. AI 대국에서 준비 전의 변경은 로컬 채점으로 끝나고, 준비된 뒤의 변경은 엔진과 규칙을 맞춘다. */
    @Test
    fun scoringRuleSeesEngineReadyRaisedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite, ruleset = Ruleset.Chinese))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        val controllers = wireGoCoachControllers(context)

        controllers.scoringRuleController.change(Ruleset.Japanese)
        assertEquals(
            "준비 전이면 로컬 채점으로 끝난다(첫 호출).",
            "Scoring rule changed to ${Ruleset.Japanese.scoringLabel}. Local scoring is active.",
            context.coreWrites.single().engineMessage,
        )
        assertEquals(0, context.dispatcher.queuedCount)

        context.engineIsReady = true
        controllers.scoringRuleController.change(Ruleset.Chinese)

        assertEquals(listOf(Ruleset.Japanese, Ruleset.Chinese), context.coreWrites.map { it.gameState.ruleset })
        assertEquals(
            "배선 뒤 준비된 엔진을 채점 규칙이 못 봤다 — 첫 호출 때의 '준비 전'으로 또 엔진 동기화를 건너뛰었다(함정 67).",
            1,
            context.dispatcher.queuedCount,
        )
    }

    /** 채점 규칙 — `gameState()`. 판과 같은 규칙으로의 변경은 아무 일도 아니고, 판의 규칙이 바뀐 뒤에는 변경이다. */
    @Test
    fun scoringRuleSeesGameStateChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession(ruleset = Ruleset.Chinese))
        val controllers = wireGoCoachControllers(context)

        controllers.scoringRuleController.change(Ruleset.Chinese)
        assertTrue("판과 같은 규칙이면 아무 일도 없다(첫 호출).", context.coreWrites.isEmpty())

        context.changeCore { it.copy(gameState = it.gameState.copy(ruleset = Ruleset.Japanese)) }
        controllers.scoringRuleController.change(Ruleset.Chinese)

        assertEquals(
            "배선 뒤 바뀐 판의 규칙을 채점 규칙이 못 봤다 — 첫 호출 때의 판(중국식)이면 또 아무 일도 아니다(함정 67).",
            listOf(Ruleset.Chinese),
            context.coreWrites.map { it.gameState.ruleset },
        )
    }

    /**
     * 채점 규칙 — 엔진이 준비된 AI 대국. `isEngineReady()`와 `isEngineBusy()`를 가려낸다(둘이 바뀌어 배선되면
     * '바쁨'으로 막힌다). 엔진 동기화를 끝까지 돌리면 추천 수 컨트롤러에 후속 분석을 넘긴다.
     */
    @Test
    fun scoringRuleSyncsWithAReadyEngineAndHandsItsFollowUpToTopMoves() {
        val context = FakeGoCoachAppWiringContext(
            inGameSession(playerSetup = HumanBlackAiWhite, ruleset = Ruleset.Chinese),
            engineClient = SyncingEngineClient(),
        )
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        context.engineIsReady = true
        val controllers = wireGoCoachControllers(context)

        controllers.scoringRuleController.change(Ruleset.Japanese)

        assertTrue("준비된 한가한 엔진인데 규칙 변경이 막혔다 — isEngineReady/isEngineBusy 배선이 뒤섞였다(#43).", context.engineMessages.isEmpty())
        assertEquals(Ruleset.Japanese, context.coreWrites.single().gameState.ruleset)
        assertEquals("엔진 규칙 동기화가 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)

        context.dispatcher.runEngineOperationThroughIo()
        assertEquals(
            "채점 규칙 동기화가 끝나면 추천 수 컨트롤러에 후속 분석을 넘겨야 한다 — requestFollowUpAnalysis 배선이 끊겼다(#43).",
            context.holder.current.gameState,
            context.deferredTopMoveAnalysis.takeWhenIdle(isEngineBusy = false)?.targetState,
        )
    }

    /** 사람 착수 — 엔진이 준비됐으면 동기화를 걸고, 그것이 끝나면 추천 수 컨트롤러에 후속 분석을 넘긴다. */
    @Test
    fun humanMoveEngineSyncHandsItsFollowUpToTopMoves() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite), engineClient = SyncingEngineClient())
        context.engineIsReady = true
        val controllers = wireGoCoachControllers(context)

        controllers.humanMoveController.submitMove(BlackAtThreeThree)
        assertEquals("착수 뒤 엔진 동기화가 한 번 걸려야 한다(#43).", 1, context.dispatcher.queuedCount)

        context.dispatcher.runEngineOperationThroughIo()
        val followUp = context.deferredTopMoveAnalysis.takeWhenIdle(isEngineBusy = false)
        assertEquals(
            "착수 동기화가 끝나면 추천 수 컨트롤러에 후속 분석을 넘겨야 한다 — requestFollowUpAnalysis 배선이 끊겼다(#43).",
            listOf<Move>(BlackAtThreeThree),
            followUp?.targetState?.moves,
        )
    }

    /** 이어하기 — 준비된 엔진과 맞추는 복원이 끝나면 추천 수 컨트롤러에 후속 분석을 넘긴다. */
    @Test
    fun savedSessionEngineSyncHandsItsFollowUpToTopMoves() {
        val context = FakeGoCoachAppWiringContext(inGameSession(), engineClient = SyncingEngineClient())
        context.engineIsReady = true
        val controllers = wireGoCoachControllers(context)

        controllers.savedSessionController.restore(SavedGameWithOneMove)
        assertEquals(1, context.dispatcher.queuedCount)

        context.dispatcher.runEngineOperationThroughIo()
        assertEquals(
            "복원 동기화가 끝나면 추천 수 컨트롤러에 후속 분석을 넘겨야 한다 — requestFollowUpAnalysis 배선이 끊겼다(#43).",
            SavedGameWithOneMove.gameState.moves,
            context.deferredTopMoveAnalysis.takeWhenIdle(isEngineBusy = false)?.targetState?.moves,
        )
    }

    /**
     * 설정 — **백로그 #43이 이름을 든 단언**: *"설정 변경 → 컨트롤러가 새 `playerSetup`을 본다."*
     * 탐색 시간을 바꾸면 런타임 플레이 레벨을 **지금의** AI 좌석에서 다시 고른다. 백이 1단계일 때 한 번,
     * 설정 화면에서 5단계로 바꾼 뒤 한 번 — 두 번째는 5단계가 골라져야 한다.
     */
    @Test
    fun settingsSeesPlayerSetupChangedAfterWiringWhenItReselectsThePlayLevel() {
        val levelOne = PlayerSetup(white = SidePlayerSetup(SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, 1)))
        val levelFive = PlayerSetup(white = SidePlayerSetup(SeatController.Ai, playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, 5)))
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = levelOne))
        val controllers = wireGoCoachControllers(context)

        controllers.settingsController.changeSearchTimeSettings(SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds))
        assertEquals("1단계 백으로 런타임을 고른다(첫 호출).", listOf(PlayLevelSetting(PlayLevelGroup.FastBeginner, 1)), context.runtimeWrites.map { it.playLevel })

        context.changePlayerSetup(levelFive) // 설정 화면에서 AI 세기를 바꿨다.
        controllers.settingsController.changeSearchTimeSettings(SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds))

        assertEquals(
            "배선 뒤 바꾼 playerSetup을 설정 컨트롤러가 못 봤다 — 첫 호출 때의 세기(1단계)로 또 골랐다(함정 67).",
            listOf(PlayLevelSetting(PlayLevelGroup.FastBeginner, 1), PlayLevelSetting(PlayLevelGroup.FastBeginner, 5)),
            context.runtimeWrites.map { it.playLevel },
        )
        assertEquals("무르기 컨트롤러를 거쳐 조용한 구간을 닫는다(#43).", listOf(0L, 0L), context.quietUntilWrites)
        assertEquals(SearchTimeLimit.WithinFiveSeconds, context.settingsWrites.last().searchTimeSettings.limit)
        assertNull(context.analysisWrites.last().lastAnalysisKey)
    }

    /** 설정 — `isGameEnded()`. 판 크기는 대국 사이에만 바뀐다. 대국 중 한 번 거절되고, 대국이 끝난 뒤에는 받는다. */
    @Test
    fun settingsSeesGameEndedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(inGameSession())
        val controllers = wireGoCoachControllers(context)

        controllers.settingsController.changeBoardSize(BoardSize.Nineteen)
        assertTrue("대국 중에는 판 크기를 받지 않는다(첫 호출).", context.settingsWrites.isEmpty() && context.coreWrites.isEmpty())

        context.changeCore { it.copy(isGameEnded = true) }
        controllers.settingsController.changeBoardSize(BoardSize.Nineteen)

        assertEquals(
            "배선 뒤 끝난 대국을 설정 컨트롤러가 못 봤다 — 첫 호출 때의 '대국 중'으로 또 거절했다(함정 67).",
            listOf(BoardSize.Nineteen),
            context.settingsWrites.map { it.boardSize },
        )
        assertEquals("새 판 미리보기는 방금 쓴 설정으로 그린다(#43).", BoardSize.Nineteen, context.coreWrites.single().gameState.boardSize)
    }

    /**
     * 설정 → 다른 컨트롤러 — 백로그 #43의 문장을 **앱의 흐름 그대로**: 착수가 한 번 거절된 뒤, 배선된 설정
     * 컨트롤러가 컨텍스트 세터로 쓴 `playerSetup`을 같은 배선에서 나온 착수·무르기 컨트롤러가 곧바로 본다.
     */
    @Test
    fun settingsChangeThroughTheWiredSettingsControllerReachesOtherPlayerSetupConsumers() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        val controllers = wireGoCoachControllers(context)

        controllers.humanMoveController.submitMove(BlackAtThreeThree)
        assertEquals("흑이 AI면 착수를 받지 않는다(첫 호출).", listOf(NotHumanTurn), context.engineMessages)

        controllers.settingsController.changePlayerSetup(HumanBlackAiWhite)
        assertEquals(HumanBlackAiWhite, context.settingsWrites.single().playerSetup)

        controllers.humanMoveController.submitMove(BlackAtThreeThree)
        assertTrue(
            "설정 컨트롤러가 바꾼 playerSetup을 착수가 못 봤다(함정 67).",
            context.engineMessages.last().startsWith(LocalMoveAccepted),
        )

        controllers.undoController.undoLastTurn()
        assertEquals(
            "설정 컨트롤러가 바꾼 playerSetup을 무르기가 못 봤다(함정 67).",
            "Local undo completed without engine sync.",
            context.engineMessages.last(),
        )
        assertTrue(context.coreWrites.last().gameState.moves.isEmpty())
    }

    /**
     * 설정 → **공유** 무르기 컨트롤러(배선 KDoc이 든 생성 순서 제약). 엔진이 준비된 무르기는 조용한 구간 뒤의
     * 재동기화를 `controllers.undoController`에 예약한다. 그 뒤 설정을 바꾸면 설정 컨트롤러가 조용한 구간을
     * 닫으며 **그 인스턴스의** 대기 중 재동기화를 취소해야 한다 — 설정이 다른 `UndoController`에 묶이면
     * 조용한 구간만 0이 되고 재동기화는 살아남는다.
     */
    @Test
    fun settingsCancelsThePostUndoResyncScheduledOnTheSharedUndoController() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        context.engineIsReady = true
        val controllers = wireGoCoachControllers(context)

        controllers.undoController.undoLastTurn()
        assertEquals("엔진이 준비된 무르기는 재동기화를 예약한다(#43).", listOf(true), context.pendingUndoSyncWrites)
        assertEquals(1, context.dispatcher.queuedCount)

        controllers.settingsController.changeSearchTimeSettings(SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds))

        assertEquals(
            "설정 변경이 공유 무르기 컨트롤러의 대기 중 재동기화를 취소하지 않았다 — 설정 컨트롤러가 다른 " +
                "UndoController에 묶였다(#43, wireGoCoachControllers의 생성 순서).",
            listOf(true, false),
            context.pendingUndoSyncWrites,
        )
        assertTrue(context.dispatcher.runNext())
        assertEquals(
            "취소된 무르기 재동기화가 여전히 돌아 조용한 구간 delay까지 갔다(#43).",
            emptyList<Long>(),
            context.dispatcher.parkedDelayMillis,
        )
    }

    /**
     * 디버그 리포트 — `copy()`는 JVM에서 끝까지 돌 수 없다(진동 진단이 진짜 `Context`를, 빌드 스탬프가
     * `android.os.Build`를 읽는다). 그래서 공개 API로는 **읽는 시점**만 본다: 배선은 `androidContext`를
     * 건드리지 않고, `copy()`가 불릴 **때마다** 세션 스냅샷과 `androidContext`를 새로 읽는다.
     */
    @Test
    fun debugReportReadsSessionAndAndroidContextOnEveryCopy() {
        val context = FakeGoCoachAppWiringContext()
        val controllers = wireGoCoachControllers(context)
        assertEquals(0, context.androidContextReads)
        val snapshotReadsAfterWiring = context.reads("sessionSnapshot")

        repeat(2) { copyIndex ->
            val thrown = runCatching { controllers.debugReportController.copy() }.exceptionOrNull()
            assertTrue("copy()가 진동 진단을 위해 androidContext를 읽어야 한다(#36·#43).", thrown is AndroidContextTouched)
            assertEquals(copyIndex + 1, context.androidContextReads)
            assertEquals(
                "copy()가 불릴 때마다 세션 스냅샷을 새로 읽어야 한다 — 한 번 읽은 값을 쥐면 리포트가 거짓말한다(함정 67).",
                snapshotReadsAfterWiring + copyIndex + 1,
                context.reads("sessionSnapshot"),
            )
        }
    }

    /**
     * 디버그 리포트 — 나머지 게터 람다. `copy()`를 끝까지 못 돌리므로 **생성자에 넘어간 람다를 직접**
     * 꺼내(비공개 필드 리플렉션 — `UiStringsTest`와 같은 방식) 값을 바꾸기 전과 뒤에 한 번씩 부른다.
     */
    @Test
    fun debugReportLambdasReadTheContextOnEveryCall() {
        val engine = StatsEngineClient()
        val context = FakeGoCoachAppWiringContext(engineClient = engine)
        val controller = wireGoCoachControllers(context).debugReportController

        assertEquals(false, controller.lambdaField("isEngineReady").invoke())
        assertEquals(false, controller.lambdaField("isEngineBusy").invoke())
        assertNull(controller.lambdaField("currentSavedSessionJson").invoke())
        assertEquals(HumanBlackAiWhite, (controller.lambdaField("currentControllerState").invoke() as GameSessionControllerState).playerSetup)
        assertTrue(controller.lambdaField("analysisCacheStatsText").invoke().toString().endsWith("undoRestoreEntries=0"))
        assertEquals("stats-initial", controller.longLambdaField("positionAnalysisCacheStatsText").invoke(0L))
        assertEquals("Time B 0.0s / W 0.0s", controller.lambdaField("turnTimeText").invoke())
        assertTrue(controller.longLambdaField("turnTimeDebugText").invoke(0L).toString().startsWith("blackMillis=0,"))

        context.engineIsReady = true
        context.engineIsBusy = true
        context.savedSessionJson = "{\"schema\":1}"
        context.changePlayerSetup(TwoHumans)
        context.seedUndoRestoreCache()
        engine.statsText = "stats-after"
        context.changeCore { it.copy(turnTimeState = it.turnTimeState.copy(blackAccumulatedMillis = 12_300L)) }

        assertEquals("배선 뒤 준비된 엔진을 디버그 리포트가 못 본다(함정 67).", true, controller.lambdaField("isEngineReady").invoke())
        assertEquals(true, controller.lambdaField("isEngineBusy").invoke())
        assertEquals("{\"schema\":1}", controller.lambdaField("currentSavedSessionJson").invoke())
        assertEquals(
            "배선 뒤 바꾼 playerSetup이 디버그 리포트의 세션 스냅샷에 보여야 한다(함정 67).",
            TwoHumans,
            (controller.lambdaField("currentControllerState").invoke() as GameSessionControllerState).playerSetup,
        )
        assertTrue(
            "디버그 리포트의 캐시 통계가 첫 값에 얼었다 — 무르기 복원 캐시는 대국 중에 바뀐다(#43).",
            controller.lambdaField("analysisCacheStatsText").invoke().toString().endsWith("undoRestoreEntries=1"),
        )
        assertEquals(
            "디버그 리포트의 국면 분석 캐시 통계가 첫 값에 얼었다(#43).",
            "stats-after",
            controller.longLambdaField("positionAnalysisCacheStatsText").invoke(0L),
        )
        assertEquals("디버그 리포트의 대국 시간 요약이 첫 값에 얼었다(#43).", "Time B 12.3s / W 0.0s", controller.lambdaField("turnTimeText").invoke())
        assertTrue(
            "디버그 리포트의 대국 시간 상세가 첫 값에 얼었다(#43).",
            controller.longLambdaField("turnTimeDebugText").invoke(0L).toString().startsWith("blackMillis=12300,"),
        )
    }

    /**
     * ⚠️ **알려진 잠복 동결 — 지금 모습을 못박는다.** `wireDebugReportController`는
     * `engineName = context.engineName()`·`engineDiagnostic = context.engineDiagnostic()`을 **배선 시점에
     * 값으로** 넘긴다(`SettingsAndDiagnosticsControllerWiring.kt`). 함정 67이 "얼면 안 된다"고 이름을 든
     * 바로 그 두 값이고, (a)의 배선 중 읽기 목록에 남아 있는 둘이다.
     *
     * 지금 사용자에게 안 보이는 것은 `GoCoachApp`이 `wiringContext`를 새로 만들 때마다 컨트롤러를
     * 통째로 다시 배선하기 때문이다(`remember(wiringContext) { wireGoCoachControllers(…) }` —
     * [WiringContextFreezeContractTest]가 그 줄과, 엔진 이름을 새로 만들게 하는 `isEngineReady` 키를 지킨다).
     * 그 가림막은 **엔진 정체가 준비 완료와 같은 재구성에서 바뀔 때만** 통한다 — 정체는 키가 아니다.
     *
     * 이 테스트는 두 모양을 다 받는다. 지금처럼 `String`이면 배선 때 값에 머물러 있음을(=알려진 동결)
     * 확인하고, `() -> String`으로 고치면 부를 때마다 지금 값을 읽는지 확인한다. 고치는 커밋은 (a)의
     * 배선 중 읽기 목록을 비우고 이 KDoc을 줄인다.
     *
     * 같은 두 값의 **둘째 동결**은 프로덕션에 실제로 있다(#43 재검토, 고치지 않았다 — 프로덕션 0줄).
     * `GoCoachApp.kt`의 `val lifecycleController = remember { EngineOperationLifecycleController(
     * currentRuntimeLogContext = { currentRuntimeLogContext() }, …) }`는 키가 없어 **첫 컴포지션의** 지역 함수를
     * 붙잡는다 — 엔진 작업 생명주기의 런타임 로그가 프로세스 내내 첫 컴포지션의 엔진 이름·진단(엔진 준비
     * 전이라 대개 `Unresolved`)을 적는다. 진단 로그에만 보이고, 재배선이 가려 주지도 않는다(그 인스턴스는
     * wiringContext 밖에서 한 번 만들어진다).
     * [WiringContextFreezeContractTest.theLifecycleControllerRuntimeLogContextIsTheKnownFirstCompositionFreeze]가
     * 지금 모습을 못박아, 고치면 빨개져 이 문단을 줄이라고 알린다.
     */
    @Test
    fun debugReportEngineNameAndDiagnosticAreTheKnownLatentFreeze() {
        val context = FakeGoCoachAppWiringContext()
        val controller = wireGoCoachControllers(context).debugReportController

        context.currentEngineName = "engine-after"
        context.currentEngineDiagnostic = "diagnostic-after"

        listOf("engineName" to "engine", "engineDiagnostic" to "diagnostic").forEach { (field, prefix) ->
            when (val stored = controller.privateField(field)) {
                is Function0<*> -> assertEquals("$field 는 부를 때마다 지금 값을 읽어야 한다(함정 67).", "$prefix-after", stored.invoke())
                is String -> assertEquals(
                    "$field 가 문자열인데 배선 때 값이 아니다 — 모양이 바뀌었으면 이 테스트와 (a)의 배선 중 읽기 목록을 함께 고칠 것.",
                    "$prefix-initial",
                    stored,
                )
                else -> fail("$field 의 모양이 바뀌었다: ${stored?.javaClass}")
            }
        }
    }

    /**
     * 기기 벤치마크 — `isEngineReady()`·`isEngineBusy()`. 준비 전에 한 번 막히고(준비 전), 준비됐지만 바빠진
     * 뒤 다시 막힌다(바쁨). 결과가 저장돼 있지 않으면 `showResult()`가 실행을 거는데, 그 블록을 매번 돌린다.
     */
    @Test
    fun benchmarkSeesEngineReadyAndBusyChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(engineClient = BenchmarkCapableEngineClient())
        val controllers = wireGoCoachControllers(context)

        controllers.benchmarkController.showResult()
        assertEquals("저장된 결과가 없으면 벤치마크 실행을 건다(#43).", 1, context.dispatcher.queuedCount)
        assertTrue(context.dispatcher.runNext())
        assertEquals("준비 전이면 막는다(첫 호출).", listOf(EngineOperationBlockReason.EngineNotReady), context.benchmarkWrites.map { it.blockedReason })

        context.engineIsReady = true
        context.engineIsBusy = true
        controllers.benchmarkController.showResult()
        assertTrue(context.dispatcher.runNext())

        assertEquals(
            "배선 뒤 바뀐 엔진 준비·바쁨을 벤치마크가 못 봤다(함정 67).",
            listOf(EngineOperationBlockReason.EngineNotReady, EngineOperationBlockReason.EngineBusy),
            context.benchmarkWrites.map { it.blockedReason },
        )
        assertEquals("Engine is busy. Run benchmark after the current response.", context.engineMessages.last())
    }

    /** 기기 벤치마크 — `benchmarkUiState()`. 엔진이 바빠 한 번 막힌 뒤, 벤치마크가 이미 도는 화면 상태가 됐다. */
    @Test
    fun benchmarkSeesBenchmarkUiStateChangedAfterWiring() {
        val context = FakeGoCoachAppWiringContext(engineClient = BenchmarkCapableEngineClient())
        context.engineIsReady = true
        context.engineIsBusy = true
        val controllers = wireGoCoachControllers(context)

        controllers.benchmarkController.showResult()
        assertTrue(context.dispatcher.runNext())
        val firstText = context.benchmarkWrites.single().benchmarkText
        assertEquals(EngineOperationBlockReason.EngineBusy, context.benchmarkWrites.single().blockedReason)

        context.engineIsBusy = false
        context.changeSession { it.withBenchmark(it.benchmark.copy(benchmarkText = "set-after-wiring").startWaitingForEngineSettle()) }
        controllers.benchmarkController.showResult()
        assertTrue(context.dispatcher.runNext())

        assertEquals(
            "배선 뒤 돌기 시작한 벤치마크를 컨트롤러가 못 봤다 — 첫 호출 때의 화면 상태면 한 번 더 실행하려 든다(함정 67).",
            listOf(EngineOperationBlockReason.EngineBusy, EngineOperationBlockReason.EngineBusy),
            context.benchmarkWrites.map { it.blockedReason },
        )
        assertNotEquals(firstText, "set-after-wiring")
        assertEquals("막힘 표시는 지금의 화면 상태 위에 얹는다(함정 67).", "set-after-wiring", context.benchmarkWrites.last().benchmarkText)
    }

    private fun controllerFields() =
        GoCoachControllers::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }

    /**
     * 줄에 선 엔진 작업 하나를 끝까지 돌린다: 블록을 시작하고(`runEngineIo`에서 멈춘다), `Dispatchers.IO`에서
     * 돌아오는 복귀를 받아 나머지(적용·후속 분석·완료 표시)를 돌린다. 돌린 블록 수를 돌려준다.
     */
    private fun QueueOnlyDispatcher.runEngineOperationThroughIo(): Int {
        assertTrue("걸린 엔진 작업이 없다(#43).", runNext())
        assertTrue("엔진 작업이 Dispatchers.IO에서 돌아오지 않았다(#43).", runNextArrivingWithin())
        assertEquals("엔진 작업이 끝난 뒤 줄에 남은 것이 있다 — 이 헬퍼의 가정(IO 한 번 왕복)이 낡았다.", 0, queuedCount)
        return 2
    }

    private fun DebugReportController.lambdaField(name: String): Function0<*> = privateField(name) as Function0<*>

    @Suppress("UNCHECKED_CAST")
    private fun DebugReportController.longLambdaField(name: String): Function1<Long, *> = privateField(name) as Function1<Long, *>

    private fun DebugReportController.privateField(name: String): Any? =
        DebugReportController::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .get(this)

    private class BenchmarkCapableEngineClient : FakeEngineSessionClient() {
        override val capabilities: EngineSessionCapabilities = EngineSessionCapabilities(supportsDeviceBenchmark = true)
    }

    /** 디버그 리포트의 국면 분석 캐시 통계를 테스트가 바꿀 수 있는 엔진. */
    private class StatsEngineClient : FakeEngineSessionClient() {
        var statsText = "stats-initial"

        override fun positionAnalysisCacheStatsText(nowMillis: Long): String = statsText
    }

    /** 착수·복원·채점 규칙의 엔진 동기화가 성공하는 엔진. */
    private class SyncingEngineClient : FakeEngineSessionClient() {
        private val estimate = ScoreEstimate(
            status = EngineStatus.ready("synced"),
            whiteWinRate = 0.5,
            whiteScoreLead = 0.5,
            summary = "stub estimate",
        )

        override suspend fun syncAfterHumanMove(
            afterMove: GameState,
            profile: EngineProfile,
            move: Move,
            previousReviewCandidates: List<CandidateMove>,
        ): LocalEngineMoveResult = LocalEngineMoveResult(estimate = estimate)

        override suspend fun configureSyncAndEstimateGraphScore(state: GameState, profile: EngineProfile): ScoreEstimate = estimate

        override suspend fun syncAndEstimateGraphScore(state: GameState, profile: EngineProfile): ScoreEstimate = estimate
    }

    private companion object {
        /** [GoCoachControllers]의 필드 → 그 컨트롤러를 부르는 탐침·협력자 테스트(디버그 리포트는 특성 테스트 하나 포함). */
        val FreshnessProbes: Map<String, List<String>> = mapOf(
            "topMovesController" to listOf(
                "topMovesSeesPlayerSetupChangedAfterWiring",
                "topMovesSeesResumePromptRaisedAfterWiring",
                "topMovesSeesEngineReadyRaisedAfterWiring",
            ),
            "undoController" to listOf(
                "undoSeesPlayerSetupChangedAfterWiring",
                "undoSeesMatchModeChangedAfterWiring",
                "undoSeesEngineReadyRaisedAfterWiring",
                "settingsCancelsThePostUndoResyncScheduledOnTheSharedUndoController",
            ),
            "autoAiTurnController" to listOf("autoAiTurnSeesSessionAndResumePromptChangedAfterWiring", "autoAiTurnSeesEngineReadyRaisedAfterWiring"),
            "humanMoveController" to listOf(
                "humanMoveSeesPlayerSetupChangedAfterWiring",
                "humanMoveSeesEngineBlockingBusyRaisedAfterWiring",
                "humanMoveSeesEngineReadyRaisedAfterWiring",
                "humanMoveEngineSyncHandsItsFollowUpToTopMoves",
            ),
            "newGameController" to listOf(
                "newGameSeesPlayerSetupAndBoardSizeChangedAfterWiring",
                "newGameSeesSearchTimeChangedAfterWiringAndHandsItsFollowUpToTopMoves",
                "newGameSeesEngineReadyRaisedAfterWiring",
            ),
            "savedSessionController" to listOf(
                "savedSessionSeesEngineBusyAndSettingsChangedAfterWiring",
                "savedSessionSeesEngineReadyRaisedAfterWiring",
                "savedSessionEngineSyncHandsItsFollowUpToTopMoves",
            ),
            "cacheOptController" to listOf("cacheOptimizationSeesPlayerSetupChangedAfterWiring", "cacheOptimizationDismissSeesGameStateChangedAfterWiring"),
            "scoreEstimateController" to listOf("scoreEstimateSeesMatchModeChangedAfterWiring", "scoreEstimateSeesEngineReadyRaisedAfterWiring"),
            "scoringRuleController" to listOf(
                "scoringRuleSeesMatchModeChangedAfterWiring",
                "scoringRuleSeesGameStateChangedAfterWiring",
                "scoringRuleSeesEngineReadyRaisedAfterWiring",
                "scoringRuleSyncsWithAReadyEngineAndHandsItsFollowUpToTopMoves",
            ),
            "settingsController" to listOf(
                "settingsSeesPlayerSetupChangedAfterWiringWhenItReselectsThePlayLevel",
                "settingsSeesGameEndedAfterWiring",
                "settingsChangeThroughTheWiredSettingsControllerReachesOtherPlayerSetupConsumers",
                "settingsCancelsThePostUndoResyncScheduledOnTheSharedUndoController",
            ),
            "debugReportController" to listOf(
                "debugReportReadsSessionAndAndroidContextOnEveryCopy",
                "debugReportLambdasReadTheContextOnEveryCall",
                "debugReportEngineNameAndDiagnosticAreTheKnownLatentFreeze",
            ),
            "benchmarkController" to listOf("benchmarkSeesEngineReadyAndBusyChangedAfterWiring", "benchmarkSeesBenchmarkUiStateChangedAfterWiring"),
        )

        const val TopMovesHumanTurnOnly = "Top Moves is available only on human turns."
        const val NotHumanTurn = "It is not a human player's turn."
        const val LocalMoveAccepted = "Local move accepted without engine sync:"
        const val AiSetupEngineNotReady = "Player Setup includes AI, but engine is not ready."
        const val RestoreWhileBusy = "Engine is busy. Restore the saved game after the current action."
        const val EngineNotReady = "Engine is not ready."
        const val ScoreNotCurrent = "Score estimate not current."

        val HumanBlackAiWhite = PlayerSetup()
        val AiBlackHumanWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Human))
        val TwoHumans = PlayerSetup(black = SidePlayerSetup(SeatController.Human), white = SidePlayerSetup(SeatController.Human))
        val TwoAis = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Ai))

        val BlackAtThreeThree = Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 2))
        val WhiteAtFourFour = Move.Play(StoneColor.White, BoardCoordinate(row = 3, column = 3))

        val SavedGameWithOneMove = SavedGameSnapshot(
            gameState = GameState.empty(boardSize = BoardSize.Thirteen).play(BlackAtThreeThree),
            playerSetup = AiBlackHumanWhite,
            playLevel = PlayLevelSetting(),
            topMovesEnabled = true,
            savedAtMillis = 0L,
        )
    }
}
