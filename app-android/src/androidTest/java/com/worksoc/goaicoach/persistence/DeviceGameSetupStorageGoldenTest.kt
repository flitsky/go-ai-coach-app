package com.worksoc.goaicoach.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **기기에 실제로 쓰이는 바이트**의 골든 — 판 정체성을 값 객체 하나로 묶어도 한 글자도 안 바뀐다(refactor backlog #22).
 *
 * JVM 단위 테스트의 `org.json`은 키를 해시 순서로 쓰지만 안드로이드의 `JSONObject`는 **넣은 순서**(`LinkedHashMap`)로
 * 쓴다. 그래서 코덱이 키를 넣는 순서가 바뀌면 JVM 골든(`GameSetupStorageGoldenTest`)은 초록인 채 기기의 저장 바이트만
 * 달라진다 — 이 테스트가 그 차이를 잡는다. 골든은 **#22 이전 코드(`adf89db3`)를 에뮬레이터에서 돌려 뽑은 출력**이다.
 * ⚠️ 함정 69: 옛 형식(덤·접바둑 키가 없던 저장분)도 기기의 파서로 다시 읽어 본다.
 */
@RunWith(AndroidJUnit4::class)
class DeviceGameSetupStorageGoldenTest {

    @Test
    fun aHandicapSessionWritesTheSameBytesAsBefore() {
        assertEquals(HandicapSessionGolden, SavedGameSessionCodec.encode(DeviceGameSetupGoldenFixtures.handicapSession()))
    }

    @Test
    fun anEndedSessionWritesTheSameBytesAsBefore() {
        assertEquals(EndedSessionGolden, SavedGameSessionCodec.encode(DeviceGameSetupGoldenFixtures.endedSession()))
    }

    @Test
    fun anAllDefaultsSessionWritesTheSameBytesAsBefore() {
        assertEquals(DefaultSessionGolden, SavedGameSessionCodec.encode(DeviceGameSetupGoldenFixtures.defaultSession()))
    }

    @Test
    fun theHistoryIndexWritesTheSameBytesAsBefore() {
        assertEquals(HistoryIndexGolden, GameHistoryIndexCodec.encodeAll(DeviceGameSetupGoldenFixtures.historyEntries()))
    }

    @Test
    fun theGoldenBytesReadBackToTheSameState() {
        assertEquals(DeviceGameSetupGoldenFixtures.handicapSession(), SavedGameSessionCodec.decode(HandicapSessionGolden))
        assertEquals(DeviceGameSetupGoldenFixtures.endedSession(), SavedGameSessionCodec.decode(EndedSessionGolden))
        assertEquals(DeviceGameSetupGoldenFixtures.defaultSession(), SavedGameSessionCodec.decode(DefaultSessionGolden))
        assertEquals(DeviceGameSetupGoldenFixtures.historyEntries(), GameHistoryIndexCodec.decodeAll(HistoryIndexGolden))
    }

    /** `40c4975c^`(덤 키가 없던 시절)의 이어하기 — 기기의 파서로도 접바둑은 그대로, 덤은 6.5로 읽힌다. */
    @Test
    fun thePreKomiSessionFormatStillReadsOnTheDevice() {
        val restored = SavedGameSessionCodec.decode(PreKomiHandicapSession)

        assertNotNull("덤 키가 없던 이어하기가 기기에서 안 읽힌다(함정 69)", restored)
        val state = restored!!.gameState
        assertEquals(BoardSize.Nine, state.boardSize)
        assertEquals(Ruleset.Japanese, state.ruleset)
        assertEquals(2, state.handicapCount)
        assertEquals(DefaultKomi, state.komi, 0.0)
        assertEquals(StoneColor.Black, state.nextPlayer)
    }

    private companion object {
        // ── #22 이전 코드(adf89db3)를 에뮬레이터(emulator-5554)에서 돌린 출력, 손대지 말 것 ─────────────
        const val HandicapSessionGolden =
            """{"schema":1,"savedAtMillis":1727000000000,"boardSize":13,"ruleset":"Chinese","handicapCount":2,"komi":0.5,"moves":[{"type":"play","player":"White","coordinate":"D11"},{"type":"play","player":"Black","coordinate":"K4"},{"type":"pass","player":"White"}],"playerSetup":{"black":{"controller":"Human","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}}},"playLevel":{"group":"FastBeginner","level":1},"topMovesEnabled":true,"scoreSnapshots":[{"moveNumber":1,"whiteScoreLead":-4.5,"whiteWinRate":0.25,"source":"EngineEstimate"},{"moveNumber":2,"whiteScoreLead":null,"whiteWinRate":null,"source":"LocalAreaEstimate"}],"finalScoreJudgement":null}"""
        const val EndedSessionGolden =
            """{"schema":1,"savedAtMillis":42,"boardSize":19,"ruleset":"Japanese","handicapCount":0,"komi":7.5,"moves":[{"type":"play","player":"Black","coordinate":"Q16"},{"type":"play","player":"White","coordinate":"D4"},{"type":"pass","player":"Black"},{"type":"pass","player":"White"}],"playerSetup":{"black":{"controller":"Human","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}}},"playLevel":{"group":"FastBeginner","level":1},"topMovesEnabled":false,"scoreSnapshots":[],"finalScoreJudgement":{"winner":"White","margin":7.5,"ruleset":"Japanese","isEstimatedDisplay":false,"removedBlack":0,"removedWhite":0,"blackArea":0,"whiteAreaWithKomi":7.5,"capturedByBlack":0,"capturedByWhite":0,"komi":7.5,"handicapCount":0,"whiteHandicapBonus":0}}"""
        const val DefaultSessionGolden =
            """{"schema":1,"savedAtMillis":0,"boardSize":9,"ruleset":"Japanese","handicapCount":0,"komi":6.5,"moves":[{"type":"play","player":"Black","coordinate":"E5"}],"playerSetup":{"black":{"controller":"Human","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}}},"playLevel":{"group":"FastBeginner","level":1},"topMovesEnabled":false,"scoreSnapshots":[],"finalScoreJudgement":null}"""
        const val HistoryIndexGolden =
            """{"schema":1,"entries":[{"id":"1727000000000-123456","playedAtMillis":1727000000000,"boardSize":13,"ruleset":"Chinese","komi":0.5,"handicapCount":2,"playerSetup":{"black":{"controller":"Human","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}}},"moveCount":3,"humanColor":"Black","winner":"Black","isResign":false,"margin":12.5,"hasReplay":true,"note":"좋은 판"},{"id":"42-7","playedAtMillis":42,"boardSize":19,"ruleset":"Japanese","komi":7.5,"handicapCount":0,"playerSetup":{"black":{"controller":"Human","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}}},"moveCount":4,"humanColor":null,"winner":null,"isResign":true,"margin":null,"hasReplay":false,"note":null},{"id":"0-0","playedAtMillis":0,"boardSize":9,"ruleset":"Japanese","komi":6.5,"handicapCount":0,"playerSetup":{"black":{"controller":"Human","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal","playLevel":{"group":"FastBeginner","level":1}}},"moveCount":0,"humanColor":"White","winner":"White","isResign":false,"margin":null,"hasReplay":false,"note":null}]}"""

        /** `40c4975c^`(2026-09-23 직전)의 encode 순서 그대로 — 접바둑은 싣고 덤은 없다. */
        const val PreKomiHandicapSession =
            """{"schema":1,"savedAtMillis":5,"boardSize":9,"ruleset":"Japanese","handicapCount":2,"moves":[{"type":"play","player":"White","coordinate":"E5"}],"playerSetup":{"black":{"controller":"Human"},"white":{"controller":"Ai"}},"playLevel":{"group":"FastBeginner","level":1},"topMovesEnabled":false,"scoreSnapshots":[],"finalScoreJudgement":null}"""
    }
}
