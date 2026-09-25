package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * **GTP 수 토큰을 읽는 호출부마다, 못 읽는 토큰에서 무엇이 일어나는지를 고정한다**(refactor backlog #36).
 *
 * 토큰 읽기 규칙은 한 벌([toGtpMoveOrNull]·[toGtpMove], 좌표는 `BoardCoordinate.fromLabel`)이지만
 * **실패 의미는 호출부마다 다르고, 그 차이는 의도다.**
 *
 * | 호출부 | 못 읽는 토큰 |
 * |---|---|
 * | `KataGoProcessEngineAdapter.genMove` | `fromLabel`의 [IllegalArgumentException]을 그 문구 그대로 던진다 — KataGo가 둔 수를 모르면 대국을 이어갈 수 없다 |
 * | `KataGoAnalysisParser.parseCandidates` | 그 `info move` 줄만 버린다 |
 * | `KataGoJsonAnalysisParser.parseCandidates` | 그 `moveInfos[]` 항목만 버린다(`move`가 없는 항목도) |
 * | `KataGoAnalysisParser.parseFinalStatusList` | 그 토큰만 버린다(`=`·`pass`·설명 문구가 섞여 온다) |
 *
 * 아래 기대값은 세 벌의 private `String.toMove`를 한 벌로 합치기 **전** 코드 위에서 초록임을 확인했다.
 * ⚠️ `genMove` 쪽을 `null`·패스로 "관대하게" 바꾸면 KataGo의 잘못된 응답이 조용히 넘어간다.
 * 반대로 분석 파서 쪽을 던지게 바꾸면 이상한 줄 하나가 분석 전체를 날린다. 소문자·앞자리 0 같은
 * 관대 파싱은 두 쪽 모두 `fromLabel` 그대로다(`GtpMoveTokenGoldenTest`가 토큰 표로 고정한다).
 */
class GtpMoveTokenCallSiteFailureTest {
    @Test
    fun genMoveReadsKataGoRepliesIncludingLenientSpellings() {
        assertEquals(Move.Play(White, BoardCoordinate(3, 15)), genMoveReply("Q16", BoardSize.Nineteen).getOrThrow())
        assertEquals(Move.Play(White, BoardCoordinate(3, 15)), genMoveReply("q16", BoardSize.Nineteen).getOrThrow())
        assertEquals(Move.Play(White, BoardCoordinate(3, 9)), genMoveReply("K10", BoardSize.Thirteen).getOrThrow())
        assertEquals(Move.Play(White, BoardCoordinate(4, 4)), genMoveReply("E05", BoardSize.Nine).getOrThrow())
        assertEquals(Move.Pass(White), genMoveReply("pass", BoardSize.Nineteen).getOrThrow())
        assertEquals(Move.Pass(White), genMoveReply("PASS", BoardSize.Nineteen).getOrThrow())
        assertEquals(Move.Resign(White), genMoveReply("resign", BoardSize.Nineteen).getOrThrow())
    }

    /** 예외 타입은 정확히 [IllegalArgumentException](하위 타입 아님), 문구는 `fromLabel`이 쓰는 그대로다. */
    @Test
    fun genMoveThrowsTheCoordinateParsersExceptionUnchangedForUnreadableReplies() {
        val cases = listOf(
            Triple(BoardSize.Nineteen, "I5", "Invalid coordinate column: I5"),
            Triple(BoardSize.Nineteen, "U1", "Coordinate U1 is outside 19x19"),
            Triple(BoardSize.Nineteen, "T0", "Coordinate T0 is outside 19x19"),
            Triple(BoardSize.Nineteen, "T20", "row must be zero or greater"),
            Triple(BoardSize.Nineteen, "Q", "Invalid coordinate label: Q"),
            Triple(BoardSize.Nineteen, "Qx", "Invalid coordinate row: Qx"),
            Triple(BoardSize.Nineteen, "passs", "Invalid coordinate row: passs"),
            Triple(BoardSize.Nineteen, "", "Invalid coordinate label: "),
            Triple(BoardSize.Nine, "K1", "Coordinate K1 is outside 9x9"),
        )

        val failures = cases.mapNotNull { (boardSize, reply, expectedMessage) ->
            val thrown = genMoveReply(reply, boardSize).exceptionOrNull()
            when {
                thrown == null -> "\"$reply\"(${boardSize.value}x${boardSize.value}): 던지지 않았다"
                thrown::class != IllegalArgumentException::class -> "\"$reply\": ${thrown::class.qualifiedName}"
                thrown.message != expectedMessage -> "\"$reply\": \"${thrown.message}\" (기대 \"$expectedMessage\")"
                else -> null
            }
        }

        assertEquals(emptyList<String>(), failures, "genMove 응답의 실패 의미가 바뀌었다:\n" + failures.joinToString("\n"))
    }

    @Test
    fun gtpAnalysisParserDropsOnlyTheUnreadableInfoLines() {
        val response = """
            info move I5 visits 9 winrate 0.5 scoreLead 0 order 0 pv I5
            info move E5 visits 8 winrate 0.5 scoreLead 0 order 1 pv E5
            info move K1 visits 7 winrate 0.5 scoreLead 0 order 2 pv K1
            info move e4 visits 6 winrate 0.5 scoreLead 0 order 3 pv e4
            info move A10 visits 5 winrate 0.5 scoreLead 0 order 4 pv A10
            info move PASS visits 4 winrate 0.5 scoreLead 0 order 5 pv pass
            info move Dx visits 3 winrate 0.5 scoreLead 0 order 6 pv Dx
        """.trimIndent()

        val candidates = KataGoAnalysisParser.parseCandidates(
            response = response,
            player = White,
            boardSize = BoardSize.Nine,
            maxCandidates = 10,
        )

        assertEquals(
            listOf(
                Move.Play(White, BoardCoordinate(4, 4)),
                Move.Play(White, BoardCoordinate(5, 4)),
                Move.Pass(White),
            ),
            candidates.map { it.move },
        )
    }

    @Test
    fun jsonAnalysisParserDropsOnlyTheUnreadableMoveInfos() {
        val json = """
            {
              "id": "q1",
              "moveInfos": [
                {"move":"I5","order":0,"visits":9},
                {"move":"E5","order":1,"visits":8},
                {"order":2,"visits":7},
                {"move":"pass","order":3,"visits":6},
                {"move":"j9","order":4,"visits":5},
                {"move":"K1","order":5,"visits":4},
                {"move":"","order":6,"visits":3}
              ]
            }
        """.trimIndent()

        val candidates = KataGoJsonAnalysisParser.parseCandidates(
            response = json,
            player = White,
            boardSize = BoardSize.Nine,
            maxCandidates = 20,
        )

        assertEquals(
            listOf(
                Move.Play(White, BoardCoordinate(4, 4)),
                Move.Pass(White),
                Move.Play(White, BoardCoordinate(0, 8)),
            ),
            candidates.map { it.move },
        )
    }

    @Test
    fun finalStatusListDropsOnlyTheUnreadableTokens() {
        val deadStones = KataGoAnalysisParser.parseFinalStatusList(
            response = "= e5 C03\nI5 K1, A0 J9\npass Dx E5",
            boardSize = BoardSize.Nine,
        )

        assertEquals(
            listOf(BoardCoordinate(4, 4), BoardCoordinate(6, 2), BoardCoordinate(0, 8)),
            deadStones,
        )
    }

    /** 가짜 KataGo가 `genmove`에 [reply]로 답하게 하고 어댑터의 `genMove` 결과(또는 던진 것)를 돌려준다. */
    private fun genMoveReply(
        reply: String,
        boardSize: BoardSize,
    ): Result<Move> {
        val fake = FakeKataGoExecutable.create(genMoveReply = reply)
        val adapter = KataGoProcessEngineAdapter(fake.processConfig)
        return try {
            runBlocking {
                adapter.initialize(EngineProfile())
                adapter.newGame(boardSize, Ruleset.Japanese, handicapCount = 0, komi = DefaultKomi)
                runCatching { adapter.genMove(White).move }
            }
        } finally {
            runBlocking { adapter.stop() }
            fake.close()
        }
    }

    private companion object {
        private val White = StoneColor.White
    }
}
