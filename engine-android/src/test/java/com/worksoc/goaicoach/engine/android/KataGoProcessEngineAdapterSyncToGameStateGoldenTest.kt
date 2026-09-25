package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.application.engine.syncToGameState
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.GameStateReplayer
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 오늘 앱이 실제로 타는 `syncToGameState` 경로에서 어댑터가 KataGo에 쓰는 **명령·쿼리 바이트**의 골든
 * (refactor backlog #91).
 *
 * ## 왜 있는가
 * #91은 정적 국면 **위에 수를 둔 뒤**의 시작 차례(`replayState`와 JSON 쿼리의 `initialPlayer`)를 고친다.
 * 그 상태에는 `syncToGameState`로는 닿지 않는다 — 매 동기화가 국면을 새로 세우고, 정적 국면은 수순이
 * 없을 때만 보낸다. 그러니 이 수정은 **사용자에게 보이는 동작을 바꾸지 않아야** 하고, 이 파일이 그것을
 * 고정한다. 아래 기대값은 수정 **전** 코드 위에서 초록임을 확인했고, 수정 뒤에도 한 바이트도 다르지 않다.
 *
 * ## 무엇을 비교하는가
 * [FakeKataGoExecutable]이 GTP 프로세스와 analysis 프로세스의 stdin에서 받은 줄을 **그대로** 비교한다.
 * 쿼리 `id`의 순번만 `#`으로 바꾼다 — 순번은 프로세스 전역 카운터라(`KataGoJsonAnalysisQueryFactory`)
 * 테스트 실행 순서에 따라 달라진다. JSON 키 순서는 테스트 JVM의 org.json이 정한다(기기의 org.json과는
 * 다를 수 있다 — 여기서 고정하는 것은 **어댑터가 넘긴 값**이다).
 *
 * ⚠️ 이 골든이 빨개지면 먼저 **의도한 변경인지** 확인한다. 의도했다면 그 커밋에서 기대값을 갱신하고,
 * 아니라면 그 변경이 오늘의 대국 경로를 건드린 것이다.
 */
class KataGoProcessEngineAdapterSyncToGameStateGoldenTest {
    private val fake = FakeKataGoExecutable.create()
    private val adapter = KataGoProcessEngineAdapter(fake.processConfig)

    @After
    fun tearDown() {
        runBlocking { adapter.stop() }
        fake.close()
    }

    /** 스캔한 정적 국면(백 차례) — 동기화 → JSON 분석 → GTP 분석 → AI 착수 → 형세. `runAutoAiTurn`의 순서다. */
    @Test
    fun staticPositionWhiteToMove() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncToGameState(StaticWhiteToMove)
        adapter.analyze(JsonPathLimit)
        adapter.analyze(GtpPathLimit)
        adapter.genMove(StoneColor.White)
        adapter.estimateScore(GtpPathLimit)

        assertGolden(
            gtp = InitializeCommands + listOf(
                // 정적 국면 동기화는 GTP에 아무것도 보내지 않는다(오늘의 동작 그대로 — 이 파일은 고치지 않고 고정만 한다).
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime",
                "kata-search_analyze W",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime 0.25",
                "genmove W",
                "kata-raw-nn 0",
            ),
            queries = listOf(
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"japanese","initialPlayer":"W","priority":0,"komi":6.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[["B","E5"],["W","D4"],["B","F6"]],"moves":[],"analyzeTurns":[0],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m0","overrideSettings":{}}""",
            ),
        )
    }

    /** 스캔한 정적 국면(흑 차례). */
    @Test
    fun staticPositionBlackToMove() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncToGameState(StaticBlackToMove)
        adapter.analyze(JsonPathLimit)
        adapter.analyze(GtpPathLimit)

        assertGolden(
            gtp = InitializeCommands + listOf(
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime",
                "kata-search_analyze B",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime 0.25",
            ),
            queries = listOf(
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"chinese","initialPlayer":"B","priority":0,"komi":7.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[["B","E5"],["W","D4"],["B","F6"]],"moves":[],"analyzeTurns":[0],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m0","overrideSettings":{}}""",
            ),
        )
    }

    /** 맞바둑 3수(홀수) 뒤 — `newGame` + `play` 셋으로 다시 세운다. */
    @Test
    fun evenGameAfterThreeMoves() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncToGameState(EvenGameAfterThreeMoves)
        adapter.analyze(JsonPathLimit)
        adapter.analyze(GtpPathLimit)

        assertGolden(
            gtp = InitializeCommands + listOf(
                "boardsize 9",
                "komi 6.5",
                "kata-set-rules japanese",
                "clear_board",
                "play B E5",
                "play W C3",
                "play B G7",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime",
                "kata-search_analyze W",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime 0.25",
            ),
            queries = listOf(
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"japanese","initialPlayer":"B","priority":0,"komi":6.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[],"moves":[["B","E5"],["W","C3"],["B","G7"]],"analyzeTurns":[3],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m3","overrideSettings":{}}""",
            ),
        )
    }

    /** 2점 접바둑 3수(홀수) 뒤 — `set_free_handicap`과 쿼리의 접바둑 돌(#65)까지. */
    @Test
    fun handicapGameAfterThreeMoves() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncToGameState(HandicapGameAfterThreeMoves)
        adapter.analyze(JsonPathLimit)
        adapter.analyze(GtpPathLimit)

        assertGolden(
            gtp = InitializeCommands + listOf(
                "boardsize 9",
                "komi 0.5",
                "kata-set-rules chinese",
                "clear_board",
                "set_free_handicap G7 C3",
                "play W E5",
                "play B E4",
                "play W D5",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime",
                "kata-search_analyze B",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime 0.25",
            ),
            queries = listOf(
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"chinese","initialPlayer":"W","priority":0,"komi":0.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[["B","G7"],["B","C3"]],"moves":[["W","E5"],["B","E4"],["W","D5"]],"analyzeTurns":[3],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m3","overrideSettings":{}}""",
            ),
        )
    }

    /** 맞바둑 5수(홀수) — 따낸 돌(A1)과 백의 패스가 수순 안에 있다. 수순을 그대로 다시 둔다. */
    @Test
    fun evenGameWithACaptureAndAPass() = runBlocking {
        assertNull("the fixture really captures A1", EvenGameWithACaptureAndAPass.stoneAt(BoardCoordinate.fromLabel("A1", BoardSize.Nine)))
        adapter.initialize(EngineProfile())
        adapter.syncToGameState(EvenGameWithACaptureAndAPass)
        adapter.analyze(JsonPathLimit)
        adapter.analyze(GtpPathLimit)

        assertGolden(
            gtp = InitializeCommands + listOf(
                "boardsize 9",
                "komi 6.5",
                "kata-set-rules japanese",
                "clear_board",
                "play B B1",
                "play W A1",
                "play B A2",
                "play W pass",
                "play B E5",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime",
                "kata-search_analyze W",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime 0.25",
            ),
            queries = listOf(
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"japanese","initialPlayer":"B","priority":0,"komi":6.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[],"moves":[["B","B1"],["W","A1"],["B","A2"],["W","pass"],["B","E5"]],"analyzeTurns":[5],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m5","overrideSettings":{}}""",
            ),
        )
    }

    /**
     * 무르기 — 앱은 엔진에 `undo`를 보내지 않고, 짧아진 국면으로 **다시 동기화**한다(#99가 3계층 `undoMove`
     * 경로를 지운 뒤 남은 유일한 길). 3수에서 2수로 돌아가도 `newGame`부터 다시 세운다.
     */
    @Test
    fun resyncAfterAnUndoRebuildsFromANewGame() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncToGameState(EvenGameAfterThreeMoves)
        adapter.analyze(JsonPathLimit)
        adapter.syncToGameState(EvenGameAfterTwoMoves)
        adapter.analyze(JsonPathLimit)

        assertGolden(
            gtp = InitializeCommands + listOf(
                "boardsize 9",
                "komi 6.5",
                "kata-set-rules japanese",
                "clear_board",
                "play B E5",
                "play W C3",
                "play B G7",
                "boardsize 9",
                "komi 6.5",
                "kata-set-rules japanese",
                "clear_board",
                "play B E5",
                "play W C3",
            ),
            queries = listOf(
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"japanese","initialPlayer":"B","priority":0,"komi":6.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[],"moves":[["B","E5"],["W","C3"],["B","G7"]],"analyzeTurns":[3],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m3","overrideSettings":{}}""",
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"japanese","initialPlayer":"B","priority":0,"komi":6.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[],"moves":[["B","E5"],["W","C3"]],"analyzeTurns":[2],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m2","overrideSettings":{}}""",
            ),
        )
    }

    /**
     * 스캔한 정적 국면(백 차례)을 분석한 뒤 대국으로 돌아온다 — `newGame`이 정적 국면의 돌을 비우므로
     * 두 번째 쿼리는 빈 시작판·흑 시작이다. 정적 국면이 남긴 시작 차례(백)가 대국으로 새지 않는다.
     */
    @Test
    fun staticPositionThenAGame() = runBlocking {
        adapter.initialize(EngineProfile())
        adapter.syncToGameState(StaticWhiteToMove)
        adapter.analyze(JsonPathLimit)
        adapter.syncToGameState(EvenGameAfterThreeMoves)
        adapter.analyze(JsonPathLimit)
        adapter.analyze(GtpPathLimit)

        assertGolden(
            gtp = InitializeCommands + listOf(
                "boardsize 9",
                "komi 6.5",
                "kata-set-rules japanese",
                "clear_board",
                "play B E5",
                "play W C3",
                "play B G7",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime",
                "kata-search_analyze W",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime 0.25",
            ),
            queries = listOf(
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"japanese","initialPlayer":"W","priority":0,"komi":6.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[["B","E5"],["W","D4"],["B","F6"]],"moves":[],"analyzeTurns":[0],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m0","overrideSettings":{}}""",
                """{"includeMovesOwnership":false,"boardYSize":9,"rules":"japanese","initialPlayer":"B","priority":0,"komi":6.5,"maxVisits":16,"includeOwnership":false,"boardXSize":9,"initialStones":[],"moves":[["B","E5"],["W","C3"],["B","G7"]],"analyzeTurns":[3],"includePolicy":true,"id":"go-ai-coach-analysis-#-9x9-m3","overrideSettings":{}}""",
            ),
        )
    }

    private fun assertGolden(gtp: List<String>, queries: List<String>) {
        assertEquals("GTP commands", gtp, fake.gtpCommands())
        assertEquals("analysis queries", queries, fake.rawQueryLines().map(::normalizeQueryId))
    }

    private companion object {
        val JsonPathLimit = AnalysisLimit(
            visits = 16,
            candidateCount = 5,
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        val GtpPathLimit = JsonPathLimit.copy(includePolicy = false)

        /** `initialize(EngineProfile())`가 보내는 명령 — `configure`가 기본 프로필의 탐색 한도 둘을 건다. */
        val InitializeCommands = listOf(
            "kata-set-param maxVisits 16",
            "kata-set-param maxTime 0.25",
        )

        val StaticWhiteToMove: GameState = GameState(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            stones = mapOf(
                BoardCoordinate.fromLabel("E5", BoardSize.Nine) to StoneColor.Black,
                BoardCoordinate.fromLabel("D4", BoardSize.Nine) to StoneColor.White,
                BoardCoordinate.fromLabel("F6", BoardSize.Nine) to StoneColor.Black,
            ),
            moves = emptyList(),
        )

        val StaticBlackToMove: GameState = StaticWhiteToMove.copy(
            ruleset = Ruleset.Chinese,
            nextPlayer = StoneColor.Black,
            komi = 7.5,
        )

        val EvenGameAfterThreeMoves: GameState = GameStateReplayer.replay(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            moves = listOf(
                play(StoneColor.Black, "E5"),
                play(StoneColor.White, "C3"),
                play(StoneColor.Black, "G7"),
            ),
        )

        val EvenGameAfterTwoMoves: GameState = GameStateReplayer.replay(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            moves = EvenGameAfterThreeMoves.moves.dropLast(1),
        )

        /** B1·A2가 A1의 백을 따낸다 — 판에서 A1은 비고 수순에는 남는다. */
        val EvenGameWithACaptureAndAPass: GameState = GameStateReplayer.replay(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            moves = listOf(
                play(StoneColor.Black, "B1"),
                play(StoneColor.White, "A1"),
                play(StoneColor.Black, "A2"),
                Move.Pass(StoneColor.White),
                play(StoneColor.Black, "E5"),
            ),
        )

        val HandicapGameAfterThreeMoves: GameState = GameStateReplayer.replay(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Chinese,
            moves = listOf(
                play(StoneColor.White, "E5"),
                play(StoneColor.Black, "E4"),
                play(StoneColor.White, "D5"),
            ),
            handicapCount = 2,
            komi = 0.5,
        )

        fun play(player: StoneColor, label: String): Move.Play =
            Move.Play(player, BoardCoordinate.fromLabel(label, BoardSize.Nine))

        private val QueryIdSequence = Regex("\"id\":\"go-ai-coach-analysis-\\d+-")

        fun normalizeQueryId(line: String): String =
            line.replace(QueryIdSequence, "\"id\":\"go-ai-coach-analysis-#-")
    }
}
