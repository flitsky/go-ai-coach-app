package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHistoryCodecTest {
    private fun sampleEntry(
        id: String = "1000-1",
        winner: StoneColor? = StoneColor.Black,
        isResign: Boolean = false,
        margin: Double? = 3.5,
        humanColor: StoneColor? = StoneColor.Black,
        hasReplay: Boolean = false,
    ) = GameHistoryEntry(
        id = id,
        playedAtMillis = 1_000L,
        boardSize = 9,
        ruleset = Ruleset.Chinese,
        komi = 6.5,
        handicapCount = 2,
        playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        ),
        moveCount = 84,
        humanColor = humanColor,
        winner = winner,
        isResign = isResign,
        margin = margin,
        hasReplay = hasReplay,
    )

    @Test
    fun roundTripRestoresAllFields() {
        val encoded = GameHistoryIndexCodec.encodeAll(listOf(sampleEntry(hasReplay = true)))

        val decoded = GameHistoryIndexCodec.decodeAll(encoded)

        assertEquals(listOf(sampleEntry(hasReplay = true)), decoded)
    }

    @Test
    fun roundTripPreservesResignWithNullMargin() {
        val resigned = sampleEntry(id = "resigned", winner = StoneColor.White, isResign = true, margin = null)

        val decoded = GameHistoryIndexCodec.decodeAll(GameHistoryIndexCodec.encodeAll(listOf(resigned)))

        assertEquals(listOf(resigned), decoded)
        assertNull(decoded.single().margin)
        assertTrue(decoded.single().isResign)
        assertEquals(StoneColor.White, decoded.single().winner)
    }

    /**
     * ⚠️ **사람이 없거나 둘인 대국**(AI:AI · 사람:사람)은 `humanColor`가 `null`이다(백로그 #151).
     * `enumOrDefault`로 읽으면 **흑으로 메워져** 없는 사실을 지어낸다 — 그래서 `enumOrNull`을 쓴다.
     */
    @Test
    fun aNullHumanColorSurvivesTheRoundTripInsteadOfDefaultingToBlack() {
        val noHuman = sampleEntry(id = "ai-vs-ai", humanColor = null)

        val decoded = GameHistoryIndexCodec.decodeAll(GameHistoryIndexCodec.encodeAll(listOf(noHuman)))

        assertNull("humanColor가 null로 남아야 한다 — 기본값으로 메우면 안 된다", decoded.single().humanColor)
    }

    @Test
    fun appendPreservesOrderAcrossMultipleEntries() {
        val entries = listOf(sampleEntry("a"), sampleEntry("b"), sampleEntry("c"))

        val decoded = GameHistoryIndexCodec.decodeAll(GameHistoryIndexCodec.encodeAll(entries))

        assertEquals(listOf("a", "b", "c"), decoded.map { it.id })
    }

    /**
     * 백로그 #23 — komi 키가 없는 항목은 `DefaultKomi`(6.5)로 채워야 한다,
     * `GameSessionStore`/`UserPreferencesStore`와 같은 기본값. `encodeEntry`가 이 파일이
     * 생긴 첫 커밋부터 계속 komi를 실어 왔으므로 실제 기록에는 도달 불가지만, 폴백 자체가
     * 다른 스토어와 어긋나면 읽는 사람이 헷갈린다.
     */
    @Test
    fun decodeFallsBackToDefaultKomiWhenTheKeyIsMissing() {
        val raw = """
            {"schema":1,"entries":[{"id":"no-komi","playedAtMillis":1000,"boardSize":9,
            "ruleset":"Chinese","handicapCount":0,
            "playerSetup":{"black":{"controller":"Human"},"white":{"controller":"Ai"}},
            "moveCount":10,"humanColor":"Black","winner":"Black","isResign":false,
            "margin":3.5,"hasReplay":false}]}
        """.trimIndent()

        val decoded = GameHistoryIndexCodec.decodeAll(raw)

        assertEquals(com.worksoc.goaicoach.shared.domain.DefaultKomi, decoded.single().komi, 0.0001)
    }

    @Test
    fun decodeAllReturnsEmptyListForUnknownSchema() {
        val raw = """{"schema":999,"entries":[]}"""

        assertTrue(GameHistoryIndexCodec.decodeAll(raw).isEmpty())
    }

    @Test
    fun decodeAllReturnsEmptyListForGarbageInput() {
        assertTrue(GameHistoryIndexCodec.decodeAll("not json").isEmpty())
    }

    // ── 옛 SharedPreferences 기록의 이관 ─────────────────────────────────────────

    private fun legacyJson(result: String, humanColor: String = "Black", margin: String = "3.5"): String =
        """
        {"schema":1,"entries":[{"id":"legacy","playedAtMillis":1000,"boardSize":9,
        "ruleset":"Chinese","komi":6.5,"handicapCount":2,
        "playerSetup":{"black":{"controller":"Human"},"white":{"controller":"Ai"}},
        "moveCount":84,"humanColor":"$humanColor","result":"$result","margin":$margin}]}
        """.trimIndent()

    /** 옛 기록은 `humanColor`+`result`만 있다 — 승자를 역산해야 새 화면이 진영을 말할 수 있다. */
    @Test
    fun legacyWinIsTranslatedIntoTheHumansColorAsWinner() {
        val decoded = GameHistoryIndexCodec.decodeLegacyAll(legacyJson(result = "Win"))

        assertEquals(StoneColor.Black, decoded.single().winner)
    }

    @Test
    fun legacyLossIsTranslatedIntoTheOpponentAsWinner() {
        val decoded = GameHistoryIndexCodec.decodeLegacyAll(legacyJson(result = "Loss"))

        assertEquals(StoneColor.White, decoded.single().winner)
    }

    /**
     * ⚠️ **옛 기권 기록의 승자는 복원할 수 없다** — *"어느 쪽이 기권했는지 구분하지 않는다"* 는
     * 옛 결정 때문에 저장된 적이 없다. 지어내지 말고 `null`로 두고, 화면이 "기권"으로만 쓴다.
     */
    @Test
    fun legacyResignKeepsAnUnknownWinnerInsteadOfGuessing() {
        val decoded = GameHistoryIndexCodec.decodeLegacyAll(legacyJson(result = "Resign", margin = "null"))

        assertNull("옛 기권 기록의 승자를 지어내면 안 된다", decoded.single().winner)
        assertTrue(decoded.single().isResign)
    }

    @Test
    fun legacyDrawHasNeitherAWinnerNorResignFlag() {
        val decoded = GameHistoryIndexCodec.decodeLegacyAll(legacyJson(result = "Draw", margin = "null"))

        assertNull(decoded.single().winner)
        assertTrue(!decoded.single().isResign)
    }

    // ── 리플레이 본문 ───────────────────────────────────────────────────────────

    private val replay = GameReplayData(
        moves = listOf(
            Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 3)),
            Move.Pass(StoneColor.White),
            Move.Resign(StoneColor.White),
        ),
        scoreSnapshots = listOf(
            ScoreSnapshot(moveNumber = 1, whiteScoreLead = -2.5, whiteWinRate = 0.4, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = null, whiteWinRate = null, source = ScoreSnapshotSource.LocalAreaEstimate),
        ),
        moveEvaluations = listOf(
            MoveReviewMarker(
                coordinate = BoardCoordinate(row = 2, column = 3),
                moveNumber = 1,
                tone = MoveReviewTone.Blunder,
                pointLoss = 12.5,
            ),
        ),
    )

    @Test
    fun replayRoundTripRestoresMovesTimelineAndEvaluations() {
        val encoded = GameReplayCodec.encode(replay, BoardSize.Nine)

        assertEquals(replay, GameReplayCodec.decode(encoded, BoardSize.Nine))
    }

    /**
     * ⚠️ **손실집수는 [MoveReviewTone]으로 되돌릴 수 없다** — 톤은 구간으로 뭉갠 값이다.
     * 저장에서 빠지면 다시보기가 *"10집 이상 잃은 수"* 를 수치로 고를 수 없다(U-35).
     */
    @Test
    fun replayKeepsTheNumericPointLossNotJustTheTone() {
        val decoded = GameReplayCodec.decode(GameReplayCodec.encode(replay, BoardSize.Nine), BoardSize.Nine)

        assertEquals(12.5, decoded!!.moveEvaluations.single().pointLoss!!, 0.0001)
    }

    @Test
    fun replayDecodeReturnsNullForUnknownSchema() {
        assertNull(GameReplayCodec.decode("""{"schema":999}""", BoardSize.Nine))
    }

    @Test
    fun replayDecodeReturnsNullForGarbageInput() {
        assertNull(GameReplayCodec.decode("not json", BoardSize.Nine))
    }

    /**
     * ⚠️ 형세나 평가가 깨져도 **수순은 살아남아야 한다** — 다시보기의 뼈대이기 때문이다.
     * 셋을 한 `runCatching`으로 묶으면 한 조각 때문에 판 전체가 사라진다.
     */
    @Test
    fun aBrokenTimelineDoesNotTakeTheMovesDownWithIt() {
        // JSON 자체는 멀쩡하고 **형세 배열 안의 값만** 깨뜨린다 — 문서를 통째로 깨면
        // 바깥 파싱에서 걸려 이 테스트가 지키려는 경계를 지나가 버린다.
        val broken = GameReplayCodec.encode(replay, BoardSize.Nine)
            .replace("\"whiteScoreLead\":-2.5", "\"whiteScoreLead\":\"broken\"")
        assertTrue("깨뜨릴 자리를 못 찾았다", broken.contains("\"broken\""))

        val decoded = GameReplayCodec.decode(broken, BoardSize.Nine)

        assertEquals(replay.moves, decoded?.moves)
        assertTrue(decoded!!.scoreSnapshots.isEmpty())
    }
}
