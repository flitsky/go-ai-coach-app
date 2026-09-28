package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * **판 정체성(판 크기·룰·접바둑·덤)을 값 객체 하나로 묶어도 저장 바이트는 한 글자도 안 바뀐다**(refactor backlog #22).
 *
 * 골든 문자열은 전부 **#22 이전 코드(`adf89db3`)의 출력을 그대로 뽑은 것**이다 — 기대값을 새 코드로 만들면
 * 새 코드와 옛 코드가 함께 틀려도 초록이 된다. ⚠️ 함정 69: 스키마 번호는 등호 검사라, 옛 저장분이 하나라도
 * 안 읽히면 이어하기·대국 기록이 통째로 사라진다. 그래서 두 방향을 다 본다 —
 * ① 같은 상태 → 옛 코드와 **같은 바이트**, ② 옛 형식의 저장분(덤·접바둑 키가 없던 시절) → 지금도 읽힌다.
 *
 * ⚠️ **여기는 JVM의 `org.json`이다** — 키 순서가 해시 순서라 기기(`LinkedHashMap`, 넣은 순서)의 바이트와 다르다.
 * 기기에서 쓰는 바로 그 바이트는 계기 테스트 `DeviceGameSetupStorageGoldenTest`가 따로 고정한다. 이 파일은 값의
 * 모양(키 이름·숫자 표기·빠진 키)을 잡는다.
 */
class GameSetupStorageGoldenTest {

    @Test
    fun aHandicapSessionEncodesToTheSameBytesAsBefore() {
        assertEquals(HandicapSessionGolden, SavedGameSessionCodec.encode(GameSetupGoldenFixtures.handicapSession()))
    }

    @Test
    fun anEndedSessionEncodesToTheSameBytesAsBefore() {
        assertEquals(EndedSessionGolden, SavedGameSessionCodec.encode(GameSetupGoldenFixtures.endedSession()))
    }

    @Test
    fun anAllDefaultsSessionEncodesToTheSameBytesAsBefore() {
        assertEquals(DefaultSessionGolden, SavedGameSessionCodec.encode(GameSetupGoldenFixtures.defaultSession()))
    }

    @Test
    fun theHistoryIndexEncodesToTheSameBytesAsBefore() {
        assertEquals(HistoryIndexGolden, GameHistoryIndexCodec.encodeAll(GameSetupGoldenFixtures.historyEntries()))
    }

    /** 골든 바이트가 원래 상태로 그대로 돌아온다 — 네 값이 전부 기본값이 아닌 판이 섞여 있다. */
    @Test
    fun theGoldenBytesDecodeBackToTheSameState() {
        listOf(
            HandicapSessionGolden to GameSetupGoldenFixtures.handicapSession(),
            EndedSessionGolden to GameSetupGoldenFixtures.endedSession(),
            DefaultSessionGolden to GameSetupGoldenFixtures.defaultSession(),
        ).forEach { (golden, expected) ->
            assertEquals(expected, SavedGameSessionCodec.decode(golden))
        }
        assertEquals(GameSetupGoldenFixtures.historyEntries(), GameHistoryIndexCodec.decodeAll(HistoryIndexGolden))
    }

    /**
     * **첫 형식**(2026-06-08, `25269f04`) — 접바둑·덤 키가 없고 기보·설정만 있던 이어하기. 덤은 그 뒤로 줄곧
     * 기본값 6.5로 복원돼 왔으므로 지금도 6.5여야 한다(`40c4975c`의 흡수 규칙).
     */
    @Test
    fun theFirstSessionFormatWithoutHandicapOrKomiStillDecodes() {
        val restored = SavedGameSessionCodec.decode(FirstFormatSession)

        assertNotNull("첫 형식 이어하기가 안 읽힌다 — 기기의 옛 저장분이 조용히 사라진다(함정 69)", restored)
        val state = restored!!.gameState
        assertEquals(BoardSize.Thirteen, state.boardSize)
        assertEquals(Ruleset.Chinese, state.ruleset)
        assertEquals(0, state.handicapCount)
        assertEquals(DefaultKomi, state.komi, 0.0)
        assertEquals(listOf(Move.Play(StoneColor.Black, BoardCoordinate(row = 9, column = 3))), state.moves)
        assertEquals(1000L, restored.savedAtMillis)
    }

    /** **덤 키가 없던 형식**(2026-07-16 `aace70da` ~ 2026-09-23 `40c4975c`) — 접바둑은 있고 덤만 없다. */
    @Test
    fun thePreKomiSessionFormatStillDecodesWithItsHandicap() {
        val restored = SavedGameSessionCodec.decode(PreKomiHandicapSession)

        assertNotNull("덤 키가 없던 이어하기가 안 읽힌다(함정 69)", restored)
        val state = restored!!.gameState
        assertEquals(BoardSize.Nine, state.boardSize)
        assertEquals(Ruleset.Japanese, state.ruleset)
        assertEquals(2, state.handicapCount)
        assertEquals(DefaultKomi, state.komi, 0.0)
        assertEquals(StoneColor.Black, state.nextPlayer)
        assertEquals(1, state.moves.size)
    }

    /** 이관 전 `SharedPreferences` 기록(승자 대신 `result`) — 판 정체성 네 값이 그대로 읽힌다. */
    @Test
    fun theLegacyPrefsHistoryStillDecodesItsSetup() {
        val entry = GameHistoryIndexCodec.decodeLegacyAll(LegacyPrefsHistory).single()

        assertEquals(13, entry.boardSize)
        assertEquals(Ruleset.Chinese, entry.ruleset)
        assertEquals(3, entry.handicapCount)
        assertEquals(0.5, entry.komi, 0.0)
        assertEquals(StoneColor.White, entry.winner)
    }

    internal companion object {
        // ── #22 이전 코드(adf89db3)의 JVM 출력, 손대지 말 것 ──────────────────────────────
        const val HandicapSessionGolden =
            """{"schema":1,"komi":0.5,"handicapCount":2,"boardSize":13,"playLevel":{"level":1,"group":"FastBeginner"},"moves":[{"coordinate":"D11","type":"play","player":"White"},{"coordinate":"K4","type":"play","player":"Black"},{"type":"pass","player":"White"}],"finalScoreJudgement":null,"ruleset":"Chinese","savedAtMillis":1727000000000,"playerSetup":{"white":{"controller":"Ai","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"},"black":{"controller":"Human","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"}},"scoreSnapshots":[{"moveNumber":1,"whiteWinRate":0.25,"whiteScoreLead":-4.5,"source":"EngineEstimate"},{"moveNumber":2,"whiteWinRate":null,"whiteScoreLead":null,"source":"LocalAreaEstimate"}],"topMovesEnabled":true}"""
        const val EndedSessionGolden =
            """{"schema":1,"komi":7.5,"handicapCount":0,"boardSize":19,"playLevel":{"level":1,"group":"FastBeginner"},"moves":[{"coordinate":"Q16","type":"play","player":"Black"},{"coordinate":"D4","type":"play","player":"White"},{"type":"pass","player":"Black"},{"type":"pass","player":"White"}],"finalScoreJudgement":{"capturedByWhite":0,"margin":7.5,"capturedByBlack":0,"ruleset":"Japanese","blackArea":0,"removedWhite":0,"komi":7.5,"winner":"White","handicapCount":0,"whiteHandicapBonus":0,"isEstimatedDisplay":false,"whiteAreaWithKomi":7.5,"removedBlack":0},"ruleset":"Japanese","savedAtMillis":42,"playerSetup":{"white":{"controller":"Ai","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"},"black":{"controller":"Human","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"}},"scoreSnapshots":[],"topMovesEnabled":false}"""
        const val DefaultSessionGolden =
            """{"schema":1,"komi":6.5,"handicapCount":0,"boardSize":9,"playLevel":{"level":1,"group":"FastBeginner"},"moves":[{"coordinate":"E5","type":"play","player":"Black"}],"finalScoreJudgement":null,"ruleset":"Japanese","savedAtMillis":0,"playerSetup":{"white":{"controller":"Ai","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"},"black":{"controller":"Human","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"}},"scoreSnapshots":[],"topMovesEnabled":false}"""
        const val HistoryIndexGolden =
            """{"schema":1,"entries":[{"isResign":false,"note":"좋은 판","margin":12.5,"humanColor":"Black","boardSize":13,"playedAtMillis":1727000000000,"ruleset":"Chinese","komi":0.5,"handicapCount":2,"winner":"Black","playerSetup":{"white":{"controller":"Ai","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"},"black":{"controller":"Human","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"}},"id":"1727000000000-123456","moveCount":3,"hasReplay":true},{"isResign":true,"note":null,"margin":null,"humanColor":null,"boardSize":19,"playedAtMillis":42,"ruleset":"Japanese","komi":7.5,"handicapCount":0,"winner":null,"playerSetup":{"white":{"controller":"Ai","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"},"black":{"controller":"Human","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"}},"id":"42-7","moveCount":4,"hasReplay":false},{"isResign":false,"note":null,"margin":null,"humanColor":"White","boardSize":9,"playedAtMillis":0,"ruleset":"Japanese","komi":6.5,"handicapCount":0,"winner":"White","playerSetup":{"white":{"controller":"Ai","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"},"black":{"controller":"Human","playLevel":{"level":1,"group":"FastBeginner"},"humanGameType":"Normal"}},"id":"0-0","moveCount":0,"hasReplay":false}]}"""

        // ── 옛 형식 — 그 시절 encode가 넣던 키만(그 순서로) 손으로 옮겼다 ────────────────────
        /** `25269f04`(2026-06-08)의 encode: schema·savedAtMillis·boardSize·ruleset·moves·playerSetup·playLevel·topMovesEnabled. */
        const val FirstFormatSession =
            """{"schema":1,"savedAtMillis":1000,"boardSize":13,"ruleset":"Chinese","moves":[{"type":"play","player":"Black","coordinate":"D4"}],"playerSetup":{"black":{"controller":"Human","humanGameType":"Normal","aiEngine":"KataGo","playLevel":{"group":"FastBeginner","level":1}},"white":{"controller":"Ai","humanGameType":"Normal","aiEngine":"KataGo","playLevel":{"group":"FastBeginner","level":1}}},"playLevel":{"group":"FastBeginner","level":1},"topMovesEnabled":false}"""

        /** `40c4975c^`(2026-09-23 직전)의 encode: 접바둑은 싣고 덤은 없다. 2점 접바둑은 백이 먼저 둔다. */
        const val PreKomiHandicapSession =
            """{"schema":1,"savedAtMillis":5,"boardSize":9,"ruleset":"Japanese","handicapCount":2,"moves":[{"type":"play","player":"White","coordinate":"E5"}],"playerSetup":{"black":{"controller":"Human"},"white":{"controller":"Ai"}},"playLevel":{"group":"FastBeginner","level":1},"topMovesEnabled":false,"scoreSnapshots":[],"finalScoreJudgement":null}"""

        /** #151 이관 전 `SharedPreferences`의 `entries` 덩어리 — 승자 대신 `result`. */
        const val LegacyPrefsHistory =
            """{"schema":1,"entries":[{"id":"legacy","playedAtMillis":1000,"boardSize":13,"ruleset":"Chinese","komi":0.5,"handicapCount":3,"playerSetup":{"black":{"controller":"Human"},"white":{"controller":"Ai"}},"moveCount":84,"humanColor":"Black","result":"Loss","margin":3.5}]}"""
    }
}
