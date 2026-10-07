package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.rankmeasure.RankMeasureState
import com.worksoc.goaicoach.application.rankmeasure.rankMeasureMatchup
import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePlayerSetup
import com.worksoc.goaicoach.match.isRankMeasure
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 기력 측정 대국(백로그 #219)이 기기에 남기는 것 — 제 저장소의 상태와, 좌석 설정에 실리는 표시·급수. */
class RankMeasureCodecTest {
    @Test
    fun theStateRoundTrips() {
        val state = RankMeasureState(
            rank = KgsRank.dan(2),
            hasStarted = true,
            peakRank = KgsRank.dan(3),
            consecutiveLosses = 1,
            recentDanResults = listOf(true, false, true),
            lastCountedGameId = "1791-42",
        )

        assertEquals(state, RankMeasureCodec.decode(RankMeasureCodec.encode(state)))
        assertEquals(RankMeasureState(), RankMeasureCodec.decode(RankMeasureCodec.encode(RankMeasureState())))
    }

    /**
     * 모든 필드를 기본값과 함께 읽는다(함정 69) — 새 필드가 없는 옛 저장분도, 값이 깨진 저장분도 그 필드만 기본으로 읽힌다.
     * 범위를 벗어난 급수는 끝 칸으로 당긴다(읽다가 죽지 않는다). 기록이 없으면 20급 · 최고 기력 없음 · 아직 시작 전이다.
     */
    @Test
    fun missingOrBrokenFieldsFallBackOneByOne() {
        val fresh = RankMeasureCodec.decode("{}")
        assertEquals(RankMeasureState(), fresh)
        assertEquals(KgsRank.kyu(20), fresh?.rank)
        assertNull(fresh?.peakRank)
        assertTrue(fresh?.canChooseStartingRank == true)

        assertEquals(RankMeasureState(rank = KgsRank.kyu(3)), RankMeasureCodec.decode("""{"schema":1,"rankStep":18}"""))
        assertEquals(KgsRank.Strongest, RankMeasureCodec.decode("""{"rankStep":99}""")?.rank)
        assertEquals(KgsRank.Weakest, RankMeasureCodec.decode("""{"rankStep":-4}""")?.rank)
        assertEquals(0, RankMeasureCodec.decode("""{"consecutiveLosses":-2}""")?.consecutiveLosses)
        assertNull(RankMeasureCodec.decode("""{"peakRankStep":null,"lastCountedGameId":null}""")?.peakRank)
        assertNull("a broken peak reads as no record, not as 20 kyu", RankMeasureCodec.decode("""{"peakRankStep":"??"}""")?.peakRank)
        // 단 구간의 최근 전적 — 모르는 글자는 버리고, 다섯 판을 넘으면 최근 다섯만 남긴다.
        assertEquals(listOf(true, false, true), RankMeasureCodec.decode("""{"recentDanResults":"W?LxW"}""")?.recentDanResults)
        assertEquals(listOf(false, true, true, false, true), RankMeasureCodec.decode("""{"recentDanResults":"WWLWWLW"}""")?.recentDanResults)
        // 2026-10-06의 랠리 규칙이 남긴 필드(연승 수·랠리 표시)는 읽지 않는다 — 규칙이 바뀌었다. 기력과 연패는 그대로 읽는다.
        assertEquals(
            RankMeasureState(rank = KgsRank.kyu(12), hasStarted = true, consecutiveLosses = 1),
            RankMeasureCodec.decode("""{"schema":1,"rankStep":9,"hasStarted":true,"consecutiveWins":2,"rally":true,"consecutiveLosses":1}"""),
        )
        assertNull("not JSON at all", RankMeasureCodec.decode("rank=5k"))
    }

    /**
     * 기력 측정 대국의 좌석은 좌석 설정 코덱을 그대로 지난다 — 이어하기·대국 기록이 함께 쓰는 코덱이다. 사람 좌석의 대국 종류
     * `RankMeasure`와 상대의 그룹 `CustomRank` + 단계 번호가 실려야, 다시 읽은 판이 여전히 기력 측정 대국이고 급수도 그대로다.
     * ⚠️ 두 이름이 곧 저장 형식이다(함정 1) — enum 이름을 바꾸면 저장된 판이 전부 일반 대국·캐릭터 상대로 읽힌다.
     */
    @Test
    fun aRankMeasureSetupSurvivesThePlayerSetupCodec() {
        val setup = rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(5))

        val encoded = PlayerSetupJsonCodec.encodePlayerSetup(setup)

        assertEquals("RankMeasure", encoded.getJSONObject("black").getString("humanGameType"))
        assertEquals("CustomRank", encoded.getJSONObject("white").getJSONObject("playLevel").getString("group"))
        assertEquals(16, encoded.getJSONObject("white").getJSONObject("playLevel").getInt("level"))
        val decoded = PlayerSetupJsonCodec.decodePlayerSetup(encoded)
        assertEquals(setup, decoded)
        assertTrue(decoded.isRankMeasure())
        assertEquals(KgsRank.kyu(5), decoded.rankMeasureMatchup()?.opponentRank)
    }
}
