package com.worksoc.goaicoach.application.premium.state

import com.worksoc.goaicoach.application.botcharacter.isFreeUseMatch
import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePlayerSetup
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 대국 한 판의 무료 사용(백로그 #228). 사용자가 정한 것(2026-10-08): 캐릭터와 대국할 때 누구나, 매 대국 시작 시 형세 보기 3회 ·
 * 추천 수 3회. 다 쓰면 지금과 같이(1회권 · 광고 · 구독). 다시보기에서는 주지 않는다.
 */
class MatchFreeUsesTest {
    @Test
    fun everyGameStartsWithThreeFreeUsesOfEachFeature() {
        val fresh = MatchFreeUses()

        assertEquals(3, MatchFreeUses.PerMatch)
        assertEquals(3, fresh.remaining(FeatureId.Eval, matchGeneration = 1L))
        assertEquals(3, fresh.remaining(FeatureId.TopMoves, matchGeneration = 1L))
    }

    /** 형세 보기와 추천 수는 **따로** 센다 — 형세 보기를 다 써도 추천 수 3회는 그대로다. 다 쓰면 0에서 멈춘다. */
    @Test
    fun theTwoFeaturesAreCountedSeparatelyAndStopAtZero() {
        var uses = MatchFreeUses()
        repeat(3) { uses = uses.afterUsing(FeatureId.Eval, matchGeneration = 1L) }

        assertEquals(0, uses.remaining(FeatureId.Eval, matchGeneration = 1L))
        assertEquals(3, uses.remaining(FeatureId.TopMoves, matchGeneration = 1L))
        assertEquals(0, uses.afterUsing(FeatureId.Eval, matchGeneration = 1L).remaining(FeatureId.Eval, matchGeneration = 1L), "it never goes below zero")
    }

    /** 새 대국(대국 세대가 오른다)이면 다시 3회다 — 앞 판에서 센 것은 버린다. 무르기는 대국 세대를 올리지 않으므로 그대로다. */
    @Test
    fun aNewGameStartsOverButAnUndoDoesNot() {
        val usedTwice = MatchFreeUses().afterUsing(FeatureId.Eval, 1L).afterUsing(FeatureId.Eval, 1L)

        assertEquals(1, usedTwice.remaining(FeatureId.Eval, matchGeneration = 1L), "the same game — an undo keeps the match generation")
        assertEquals(3, usedTwice.remaining(FeatureId.Eval, matchGeneration = 2L))
        val nextGame = usedTwice.afterUsing(FeatureId.Eval, matchGeneration = 2L)
        assertEquals(2, nextGame.remaining(FeatureId.Eval, matchGeneration = 2L))
        assertEquals(3, nextGame.remaining(FeatureId.TopMoves, matchGeneration = 2L))
    }

    /**
     * 무료 사용은 형세 보기 · 추천 수 · **무르기** 셋이다(무르기는 백로그 #242, 사용자 2026-10-09 — 출석 3일차의 무제한 무르기를
     * 얻기 전에도 한 판에 세 번은 무를 수 있게). 착수 평가·사진 분석에는 없다. 기능마다 따로 센다.
     */
    @Test
    fun evalTopMovesAndUndoHaveFreeUsesAndEachIsCountedOnItsOwn() {
        assertEquals(setOf(FeatureId.Eval, FeatureId.TopMoves, FeatureId.Undo), MatchFreeUses.Features)
        val undoneTwice = MatchFreeUses().afterUsing(FeatureId.Undo, 1L).afterUsing(FeatureId.Undo, 1L)
        assertEquals(1, undoneTwice.remaining(FeatureId.Undo, matchGeneration = 1L))
        assertEquals(3, undoneTwice.remaining(FeatureId.Eval, matchGeneration = 1L), "an undo does not eat an evaluation")
        assertEquals(0, undoneTwice.afterUsing(FeatureId.Undo, 1L).afterUsing(FeatureId.Undo, 1L).remaining(FeatureId.Undo, 1L))
        FeatureId.entries.filterNot { it in MatchFreeUses.Features }.forEach { other ->
            assertEquals(0, MatchFreeUses().remaining(other, matchGeneration = 1L), "$other")
        }
    }

    /**
     * 무료 사용을 주는 판은 **사람이 캐릭터와 두는 판**이다(사용자: *"캐릭터와 대국할 때"*). 승급 대국에서는 형세 보기·추천 수가
     * 누구에게나 꺼져 있고(#219), AI끼리·사람끼리 두는 판은 캐릭터와 두는 판이 아니다.
     */
    @Test
    fun onlyAGameBetweenAPersonAndACharacterGetsTheFreeUses() {
        val humanVsCharacter = PlayerSetup()
        val characterVsHuman = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Human))
        val aiVsAi = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Ai))
        val humanVsHuman = PlayerSetup(black = SidePlayerSetup(SeatController.Human), white = SidePlayerSetup(SeatController.Human))

        assertTrue(isFreeUseMatch(humanVsCharacter))
        assertTrue(isFreeUseMatch(characterVsHuman))
        assertFalse(isFreeUseMatch(rankMeasurePlayerSetup(StoneColor.Black, KgsRank.kyu(10))))
        assertFalse(isFreeUseMatch(aiVsAi))
        assertFalse(isFreeUseMatch(humanVsHuman))
    }
}
