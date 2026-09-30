package com.worksoc.goaicoach.ui

import androidx.compose.ui.geometry.Rect
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.gamehistory.ReviewRecommendationMistakeThreshold
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.ui.guide.FirstDolCharacterId
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.reviewGameActionFor
import com.worksoc.goaicoach.ui.l10n.reviewMistakeBadgeDescriptionFor
import com.worksoc.goaicoach.ui.l10n.reviewRecommendationMessageFor
import com.worksoc.goaicoach.ui.play.ReviewRecommendationPlacement.Side
import com.worksoc.goaicoach.ui.play.boardSquareWithin
import com.worksoc.goaicoach.ui.play.reviewMistakeBadgeLabel
import com.worksoc.goaicoach.ui.play.reviewRecommendationCharacterFor
import com.worksoc.goaicoach.ui.play.reviewRecommendationPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 대국 뒤 「복기 하기」 추천(백로그 #200)의 화면 쪽 — 배지 숫자, 네 언어 문구, 말풍선 캐릭터, 말풍선 자리.
 * 개수를 세는 규칙 자체는 `:shared`의 `ReviewRecommendationTest`가 지킨다.
 *
 * ⚠️ 문구가 **함수**라 `UiStringsTest`의 리플렉션 그물 밖이다(함정 10) — 여기가 손 그물이다.
 */
class ReviewRecommendationTest {

    @Test
    fun theBadgeShowsTheCountUpToEightThenNinePlus() {
        assertNull(reviewMistakeBadgeLabel(null))
        assertNull(reviewMistakeBadgeLabel(0))
        assertEquals("1", reviewMistakeBadgeLabel(1))
        assertEquals("8", reviewMistakeBadgeLabel(8))
        assertEquals("9+", reviewMistakeBadgeLabel(9))
        assertEquals("9+", reviewMistakeBadgeLabel(137))
    }

    /** 사용자가 준 한국어 문장 그대로(2026-09-30). */
    @Test
    fun theKoreanMessageIsTheUsersSentence() {
        assertEquals(
            "이번 대국에서 5점 이상 실착한 수가 3개 있어요. 복기하기를 추천드려요.",
            reviewRecommendationMessageFor(UiLanguage.Korean, mistakeCount = 3),
        )
    }

    @Test
    fun everyLanguageSaysTheCountAndTheThresholdWithoutKoreanLeaks() {
        fun mentionsNumber(text: String, number: Int): Boolean = Regex("(?<!\\d)$number(?!\\d)").containsMatchIn(text)

        UiLanguage.entries.forEach { language ->
            val message = reviewRecommendationMessageFor(language, mistakeCount = 12)
            val badge = reviewMistakeBadgeDescriptionFor(language, mistakeCount = 12)
            listOf(message, badge).forEach { text ->
                assertTrue("$language: 개수(12)를 말하지 않는다 — $text", mentionsNumber(text, 12))
                assertTrue("$language: 임계(5)를 말하지 않는다 — $text", mentionsNumber(text, 5))
                if (language != UiLanguage.Korean) {
                    assertFalse("$language: 한글이 남았다 — $text", text.containsHangul())
                }
            }
        }
    }

    /** ⚠️ **집수를 문구에 박지 않는다** — 임계 상수가 바뀌면 네 언어가 따라와야 한다. */
    @Test
    fun theThresholdInTheTextFollowsTheConstant() {
        assertEquals(5.0, ReviewRecommendationMistakeThreshold, 0.0)
        UiLanguage.entries.forEach { language ->
            val message = reviewRecommendationMessageFor(language, mistakeCount = 2, thresholdPoints = 7.0)
            assertTrue("$language: 바뀐 임계(7)를 따라오지 않는다 — $message", message.contains("7"))
            assertFalse("$language: 옛 임계(5)가 남았다 — $message", message.contains("5"))
        }
    }

    /** 영어는 한 수일 때 단수다 — `1 moves`는 틀린 말이다. */
    @Test
    fun englishUsesTheSingularForOneMove() {
        assertTrue(reviewRecommendationMessageFor(UiLanguage.English, 1).contains("1 move that"))
        assertTrue(reviewRecommendationMessageFor(UiLanguage.English, 2).contains("2 moves that"))
        assertTrue(reviewMistakeBadgeDescriptionFor(UiLanguage.English, 1).startsWith("1 move "))
    }

    /**
     * ⚠️ **실착은 누가 두었든 센다**(U-57) — 그래서 영어 문장이 사용자를 주어로 삼으면(`You made …`) AI의 실착까지
     * 사용자 탓이 된다. 주어는 "이번 대국"이다.
     */
    @Test
    fun theEnglishMessageDoesNotBlameTheUser() {
        val message = reviewRecommendationMessageFor(UiLanguage.English, 4)
        assertFalse(message, message.startsWith("You "))
        assertTrue(message, message.startsWith("This game had"))
    }

    /** 말풍선의 행동 줄은 **화면의 버튼 글자 그대로**를 인용한다(함정 39) — 같은 함수에서 온다. */
    @Test
    fun theBubbleActionQuotesTheOnScreenButtonLabel() {
        assertEquals("복기 하기", reviewGameActionFor(UiLanguage.Korean))
    }

    @Test
    fun theCharacterIsTheAiOpponentOrFirstDol() {
        val aiLevel4 = SidePlayerSetup(
            controller = SeatController.Ai,
            playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 4),
        )
        val human = SidePlayerSetup(controller = SeatController.Human)

        assertEquals("fast_beginner_4", reviewRecommendationCharacterFor(PlayerSetup(black = human, white = aiLevel4))?.id?.raw)
        assertEquals("fast_beginner_4", reviewRecommendationCharacterFor(PlayerSetup(black = aiLevel4, white = human))?.id?.raw)
        // ⚠️ 기본 배치(백 AI 1단계)는 **1단계 캐릭터**다 — 홈 카드처럼 "기본값이면 3단계"로 바꾸지 않는다(실제 상대가 1단계다).
        assertEquals("fast_beginner_1", reviewRecommendationCharacterFor(PlayerSetup())?.id?.raw)
        // 사람끼리 — 첫돌이.
        assertEquals(FirstDolCharacterId, reviewRecommendationCharacterFor(PlayerSetup(black = human, white = human))?.id)
        assertTrue(BotCharacterCatalog.byId(FirstDolCharacterId) != null)
    }

    // ── 자리 ─────────────────────────────────────────────────────────────

    /** 폰 배치 — 판 아래에 상태판·버튼이 있어 **판 아래**, 판 오른쪽 끝에 맞춘다. 판과 겹치지 않는다. */
    @Test
    fun onAPhoneTheBubbleSitsBelowTheBoardRightAligned() {
        val board = Rect(left = 0f, top = 150f, right = 1080f, bottom = 1230f)
        val placement = reviewRecommendationPlacement(
            containerWidth = 1080,
            containerHeight = 2200,
            board = board,
            bubbleWidth = 900,
            bubbleHeight = 300,
            gap = 20,
            edgeMargin = 0,
        )

        assertEquals(Side.BelowBoard, placement.side)
        assertEquals(1080 - 900, placement.x)
        assertEquals(1250, placement.y)
        assertTrue("판과 겹친다", placement.y >= board.bottom)
    }

    /** 판이 세로를 다 쓰고 옆이 남으면(가로 배치) **판 오른쪽**, 판 아래 끝에 맞춘다. */
    @Test
    fun withNoRoomBelowButRoomBesideTheBubbleSitsRightOfTheBoard() {
        val board = Rect(left = 600f, top = 0f, right = 1800f, bottom = 1200f)
        val placement = reviewRecommendationPlacement(
            containerWidth = 2600,
            containerHeight = 1260,
            board = board,
            bubbleWidth = 700,
            bubbleHeight = 300,
            gap = 20,
            edgeMargin = 0,
        )

        assertEquals(Side.RightOfBoard, placement.side)
        assertEquals(1820, placement.x)
        assertEquals(900, placement.y)
        assertTrue("판과 겹친다", placement.x >= board.right)
    }

    /**
     * 판 밖 어디에도 통째로 들어갈 자리가 없을 때(세로로 펼친 폴드 — 판 아래 버튼 한 줄뿐) — **화면 오른쪽 아래 끝**.
     * 판 모서리에 맞출 때보다 판을 덜 가린다(1812×2176 실측 비율).
     */
    @Test
    fun withNoRoomOutsideTheBubbleGoesToTheScreenCornerToCoverTheLeastBoard() {
        val board = Rect(left = 84f, top = 217f, right = 1728f, bottom = 1868f)
        val placement = reviewRecommendationPlacement(
            containerWidth = 1812,
            containerHeight = 2050,
            board = board,
            bubbleWidth = 860,
            bubbleHeight = 315,
            gap = 21,
            edgeMargin = 42,
        )

        assertEquals(Side.OverlapsBoard, placement.side)
        assertEquals(1812 - 860 - 42, placement.x)
        assertEquals(2050 - 315, placement.y)
        val overlapHeight = board.bottom - placement.y
        assertTrue("판 모서리에 맞춘 것(315)보다 덜 가려야 한다: $overlapHeight", overlapHeight < 315)
    }

    /** 화면이 말풍선보다 작아도 화면 밖으로 나가지 않는다. */
    @Test
    fun theBubbleNeverLeavesTheScreen() {
        val placement = reviewRecommendationPlacement(
            containerWidth = 1000,
            containerHeight = 1000,
            board = Rect(left = 0f, top = 0f, right = 1000f, bottom = 1000f),
            bubbleWidth = 1200,
            bubbleHeight = 300,
            gap = 20,
            edgeMargin = 0,
        )

        assertEquals(Side.OverlapsBoard, placement.side)
        assertEquals(0, placement.x)
        assertEquals(700, placement.y)
    }

    /** 판이 화면 끝까지 가도(「바둑판 최대」) 말풍선·캐릭터는 화면 끝에서 [edgeMargin]만큼 떨어진다. 폭이 모자라면 여백을 포기한다. */
    @Test
    fun theBubbleKeepsAnEdgeMarginWhenThereIsRoom() {
        val board = Rect(left = 0f, top = 150f, right = 1080f, bottom = 1230f)
        fun placeWidth(width: Int) = reviewRecommendationPlacement(
            containerWidth = 1080,
            containerHeight = 2200,
            board = board,
            bubbleWidth = width,
            bubbleHeight = 300,
            gap = 20,
            edgeMargin = 42,
        ).x

        assertEquals(1080 - 900 - 42, placeWidth(900))
        assertEquals(1080 - 1060, placeWidth(1060))
    }

    /** 넓은 배치의 판 자리는 판보다 크다 — 판은 그 **가운데 정사각형**이다(`GoBoard`의 `Alignment.Center`). */
    @Test
    fun theDrawnBoardIsTheCenteredSquareOfItsSlot() {
        assertEquals(Rect(300f, 0f, 1300f, 1000f), boardSquareWithin(Rect(0f, 0f, 1600f, 1000f)))
        assertEquals(Rect(0f, 100f, 800f, 900f), boardSquareWithin(Rect(0f, 0f, 800f, 1000f)))
    }
}
