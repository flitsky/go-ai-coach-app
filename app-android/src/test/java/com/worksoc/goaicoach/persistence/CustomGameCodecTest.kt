package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.customgame.CustomGameState
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.customRank
import com.worksoc.goaicoach.shared.policy.toPlayLevelSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 커스텀 대국(백로그 #217)이 기기에 남기는 것 — 제 저장소의 상태와, 좌석 설정에 실리는 급수. */
class CustomGameCodecTest {
    @Test
    fun theStateRoundTrips() {
        val state = CustomGameState(rank = KgsRank.dan(2), autoAdjustEnabled = false, consecutiveWins = 3, isRally = true, lastCountedGameId = "1791-42")

        assertEquals(state, CustomGameCodec.decode(CustomGameCodec.encode(state)))
        assertEquals(CustomGameState(), CustomGameCodec.decode(CustomGameCodec.encode(CustomGameState())))
    }

    /**
     * 모든 필드를 기본값과 함께 읽는다(함정 69) — 새 필드가 없는 옛 저장분도, 값이 깨진 저장분도 그 필드만 기본으로 읽힌다.
     * 범위를 벗어난 급수는 끝 칸으로 당긴다(읽다가 죽지 않는다).
     */
    @Test
    fun missingOrBrokenFieldsFallBackOneByOne() {
        assertEquals(CustomGameState(), CustomGameCodec.decode("{}"))
        assertEquals(CustomGameState(rank = KgsRank.kyu(3)), CustomGameCodec.decode("""{"schema":1,"rankStep":18}"""))
        assertEquals(KgsRank.Strongest, CustomGameCodec.decode("""{"rankStep":99}""")?.rank)
        assertEquals(KgsRank.Weakest, CustomGameCodec.decode("""{"rankStep":-4}""")?.rank)
        assertEquals(0, CustomGameCodec.decode("""{"consecutiveWins":-2}""")?.consecutiveWins)
        assertNull(CustomGameCodec.decode("""{"lastCountedGameId":null}""")?.lastCountedGameId)
        assertNull("not JSON at all", CustomGameCodec.decode("rank=5k"))
    }

    /**
     * 급수를 직접 고른 상대는 좌석 설정에 **그룹 이름 `CustomRank` + 단계 번호**로 실린다 — 이어하기·대국 기록·설정이 함께 쓰는 코덱이다.
     * ⚠️ 그 이름이 곧 저장 형식이다(함정 1) — enum 이름을 바꾸면 저장된 판이 전부 캐릭터 상대로 읽힌다.
     */
    @Test
    fun aCustomRankSeatSurvivesThePlayerSetupCodec() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(SeatController.Human),
            white = SidePlayerSetup(SeatController.Ai, playLevel = KgsRank.kyu(5).toPlayLevelSetting()),
        )

        val encoded = PlayerSetupJsonCodec.encodePlayerSetup(setup)

        assertEquals("CustomRank", encoded.getJSONObject("white").getJSONObject("playLevel").getString("group"))
        assertEquals(16, encoded.getJSONObject("white").getJSONObject("playLevel").getInt("level"))
        assertEquals(setup, PlayerSetupJsonCodec.decodePlayerSetup(encoded))
        assertEquals(KgsRank.kyu(5), PlayerSetupJsonCodec.decodePlayerSetup(encoded).white.playLevel.customRank())
    }
}
