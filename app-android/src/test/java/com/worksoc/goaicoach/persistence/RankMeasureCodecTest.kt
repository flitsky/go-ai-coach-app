package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.rankmeasure.RankMeasureState
import com.worksoc.goaicoach.application.rankmeasure.RankPromotionOffer
import com.worksoc.goaicoach.application.rankmeasure.rankMeasureMatchup
import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePlayerSetup
import com.worksoc.goaicoach.match.isRankMeasure
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 승급 대국(백로그 #219)이 기기에 남기는 것 — 제 저장소의 상태와, 좌석 설정에 실리는 표시·급수. */
class RankMeasureCodecTest {
    @Test
    fun theStateRoundTrips() {
        val state = RankMeasureState(
            rank = KgsRank.dan(2),
            hasStarted = true,
            peakRank = KgsRank.dan(3),
            consecutiveLosses = 1,
            lastCountedGameId = "1791-42",
        )

        assertEquals(state, RankMeasureCodec.decode(RankMeasureCodec.encode(state)))
        assertEquals(RankMeasureState(), RankMeasureCodec.decode(RankMeasureCodec.encode(RankMeasureState())))
        val pending = state.copy(rank = KgsRank.kyu(15), promotionOffer = RankPromotionOffer(steps = 2, margin = 41.0, byResignation = true))
        assertEquals(pending, RankMeasureCodec.decode(RankMeasureCodec.encode(pending)))
        val halfPoint = pending.copy(promotionOffer = RankPromotionOffer(steps = 1, margin = 15.5))
        assertEquals(halfPoint, RankMeasureCodec.decode(RankMeasureCodec.encode(halfPoint)))
    }

    /**
     * 고르지 않은 승급(#268)은 키 하나로 더해졌다(함정 69 — 스키마 번호는 그대로). 키가 없던 옛 저장분은 고를 것이 없고,
     * 깨진 값(폭 0 · 집 수 차이 없음)도 고를 것이 없는 것으로 읽는다. 폭은 한 판의 최대(3)로 당긴다.
     */
    @Test
    fun thePromotionOfferIsOneOptionalKey() {
        val old = RankMeasureCodec.decode("""{"schema":1,"rankStep":9,"hasStarted":true}""")
        assertNull(old?.promotionOffer)
        assertEquals(KgsRank.kyu(12), old?.rank)

        assertNull(RankMeasureCodec.decode("""{"promotionOffer":{"steps":0,"margin":40}}""")?.promotionOffer)
        assertNull(RankMeasureCodec.decode("""{"promotionOffer":{"steps":2}}""")?.promotionOffer)
        assertNull(RankMeasureCodec.decode("""{"promotionOffer":"??"}""")?.promotionOffer)
        assertEquals(
            RankPromotionOffer(steps = 3, margin = 120.0),
            RankMeasureCodec.decode("""{"promotionOffer":{"steps":9,"margin":120}}""")?.promotionOffer,
        )
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
        // 2026-10-07의 「최근 5판 중 3판」이 남긴 전적(`recentDanResults`)은 읽지 않는다 — #259가 「10집 이상 승리」로 갈아 끼웠다.
        // 그 키가 남은 옛 저장분도 기력·연패는 그대로 읽힌다(스키마 번호는 그대로, 함정 69).
        assertEquals(
            RankMeasureState(rank = KgsRank.dan(2), hasStarted = true, consecutiveLosses = 1),
            RankMeasureCodec.decode("""{"schema":1,"rankStep":${KgsRank.dan(2).step},"hasStarted":true,"recentDanResults":"WWL","consecutiveLosses":1}"""),
        )
        assertFalse("the retired record is no longer written", RankMeasureCodec.encode(RankMeasureState(rank = KgsRank.dan(2))).contains("recentDanResults"))
        // 2026-10-06의 랠리 규칙이 남긴 필드(연승 수·랠리 표시)는 읽지 않는다 — 규칙이 바뀌었다. 기력과 연패는 그대로 읽는다.
        assertEquals(
            RankMeasureState(rank = KgsRank.kyu(12), hasStarted = true, consecutiveLosses = 1),
            RankMeasureCodec.decode("""{"schema":1,"rankStep":9,"hasStarted":true,"consecutiveWins":2,"rally":true,"consecutiveLosses":1}"""),
        )
        assertNull("not JSON at all", RankMeasureCodec.decode("rank=5k"))
    }

    /**
     * 승급 대국의 좌석은 좌석 설정 코덱을 그대로 지난다 — 이어하기·대국 기록이 함께 쓰는 코덱이다. 사람 좌석의 대국 종류
     * `RankMeasure`와 상대의 그룹 `CustomRank` + 단계 번호가 실려야, 다시 읽은 판이 여전히 승급 대국이고 급수도 그대로다.
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
