package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 「바둑 규칙 배우기」 도해의 계약(백로그 #164).
 *
 * ⚠️ **틀린 도해는 컴파일도 되고 화면에도 뜬다** — 바둑을 모르는 눈에는 잘못 놓인 돌 하나가
 * 보이지 않는다. 그래서 *도해가 가르치려는 그 규칙*을 여기서 직접 물어본다. 문구가 "따낸다"고
 * 적혀 있는데 판에서 돌이 안 사라지는 사고가 이 파일이 막는 것이다.
 */
class GoRuleLessonsTest {

    /**
     * 모든 단원의 수순이 [BoardRules]를 그대로 지난다 — 차례가 어긋나거나 위법수가 섞이면
     * [GoRuleLesson.statesByStep]이 여기서 예외로 죽는다.
     */
    @Test
    fun everyLessonReplaysUnderTheRules() {
        goRuleLessons.forEach { lesson ->
            val states = lesson.statesByStep()
            assertEquals(lesson.steps.size, states.size, "${lesson.id} 단계 수가 국면 수와 다르다")
        }
    }

    /** 단원 하나에 단계가 없으면 빈 화면이 열린다. 목록에 줄만 늘리는 사고를 막는다. */
    @Test
    fun everyLessonHasSteps() {
        goRuleLessons.forEach { lesson ->
            assertTrue(lesson.steps.isNotEmpty(), "${lesson.id} 에 단계가 없다")
        }
    }

    /** 단원은 [GoRuleLessonId] 하나당 정확히 하나 — 목록이 같은 줄을 두 번 그리지 않게. */
    @Test
    fun lessonsCoverEveryIdExactlyOnce() {
        assertEquals(GoRuleLessonId.entries, goRuleLessons.map { it.id })
    }

    /** 문구 표의 키라서 겹치면 두 단계가 같은 설명을 쓴다 — 조용히 틀린다. */
    @Test
    fun stepIdsAreUnique() {
        val ids = goRuleLessons.flatMap { lesson -> lesson.steps.map { it.id } }
        assertEquals(ids.size, ids.distinct().size, "단계 키가 겹친다: $ids")
    }

    /**
     * ⚠️ **가늠돌은 「다음은 여기」라는 뜻 하나뿐이다**(`GoRuleStep.marker`). 판이 그것을
     * *놓을 수 있는 자리*의 돌로 그리므로, 둘 수 없는 자리에 찍으면 그림과 글이 정반대를
     * 말한다 — 착수금지·패 단원이 정확히 그 유혹에 걸린다.
     */
    @Test
    fun everyMarkerPointsAtALegalMove() {
        goRuleLessons.forEach { lesson ->
            val states = lesson.statesByStep()
            lesson.steps.forEachIndexed { index, step ->
                val marker = step.marker ?: return@forEachIndexed
                // 가늠돌은 **그 단계를 보고 있는 동안** 다음에 둘 자리다 — 그 단계의 국면에서 묻는다.
                assertTrue(
                    LegalMoveGenerator.isLegalPlay(states[index], marker),
                    "${lesson.id}/${step.id} 의 가늠돌 ${marker.label(lesson.boardSize)} 이 둘 수 없는 자리다",
                )
            }
        }
    }

    /** 3단원이 가르치는 것 — 마지막 활로를 메우면 돌이 판에서 사라진다. */
    @Test
    fun theCaptureLessonActuallyCapturesAStone() {
        val lesson = goRuleLessons.single { it.id == GoRuleLessonId.Capture }
        val states = lesson.statesByStep()
        val beforeTake = states[0]
        val afterTake = states[1]

        assertEquals(
            StoneColor.White,
            beforeTake.stoneAt(BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            "따내기 전 국면에 백 한 점이 없다 — 도해가 무엇을 따내는지 알 수 없다",
        )
        assertEquals(
            null,
            afterTake.stoneAt(BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            "흑이 마지막 활로를 메웠는데 백 돌이 판에 남아 있다",
        )
        assertEquals(1, afterTake.capturedBy(StoneColor.Black), "흑의 사석이 1이 아니다")
    }

    /** 4단원이 가르치는 것 — 가운데는 못 두고, 따내는 수는 둘 수 있다. */
    @Test
    fun theForbiddenLessonShowsBothTheRuleAndItsException() {
        val lesson = goRuleLessons.single { it.id == GoRuleLessonId.Forbidden }
        val states = lesson.statesByStep()
        val nine = BoardSize.Nine

        assertFalse(
            LegalMoveGenerator.isLegalPlay(states[0], BoardCoordinate.fromLabel("E5", nine)),
            "착수금지를 가르치는 자리(E5)에 실제로는 둘 수 있다 — 도해가 규칙을 못 만든다",
        )
        assertTrue(
            LegalMoveGenerator.isLegalPlay(states[1], BoardCoordinate.fromLabel("A9", nine)),
            "예외를 가르치는 자리(A9)에 둘 수 없다 — 따내는 수라 둘 수 있어야 한다",
        )
        assertEquals(
            3,
            states[2].capturedBy(StoneColor.White),
            "A9에 두었는데 흑 세 점이 들려 나가지 않았다",
        )
    }

    /** 5단원이 가르치는 것 — 따낸 **바로 다음 수**로 되따낼 수 없다. */
    @Test
    fun theKoLessonForbidsTheImmediateRecapture() {
        val lesson = goRuleLessons.single { it.id == GoRuleLessonId.Ko }
        val states = lesson.statesByStep()
        val koPoint = BoardCoordinate.fromLabel("E5", BoardSize.Nine)

        val afterBlackTakes = states[1]
        assertEquals(koPoint, afterBlackTakes.koPoint, "흑이 따낸 자리에 패가 서지 않았다")
        assertEquals(StoneColor.White, afterBlackTakes.koForbiddenFor)
        assertFalse(
            LegalMoveGenerator.isLegalPlay(afterBlackTakes, koPoint),
            "백이 그 자리를 바로 되따낼 수 있다 — 패가 아니다",
        )
        // 팻감(B8)에 흑이 응수(C8)한 뒤에는 되따낼 수 있다 — 그래야 패가 이어진다.
        assertTrue(
            LegalMoveGenerator.isLegalPlay(states[4], koPoint),
            "팻감을 주고받은 뒤에도 되따낼 수 없다 — 패가 한 번 만에 끝나 버린다",
        )
        assertEquals(1, states[5].capturedBy(StoneColor.White), "백이 되따내지 못했다")
    }

    /**
     * 6단원이 가르치는 것 — 연달아 거르면 대국이 끝나고, 그때 집이 27 대 36이다.
     * ⚠️ **숫자가 문구에 박혀 있다**(`UiStringsStudyRules.kt`의 `territory.count`) —
     * 도해를 고치면 여기서 먼저 깨지게 둔다.
     */
    @Test
    fun theTerritoryLessonEndsWithTwoPassesAndACountableBoard() {
        val lesson = goRuleLessons.single { it.id == GoRuleLessonId.Territory }
        val states = lesson.statesByStep()
        val finished = states.last()

        assertTrue(finished.hasConsecutivePasses(), "두 번 거르고도 대국이 끝나지 않았다")

        val empties = BoardSize.Nine.allCoordinates().filter { finished.stoneAt(it) == null }
        assertEquals(27, empties.count { it.column < 3 }, "흑 집이 27이 아니다")
        assertEquals(36, empties.count { it.column > 4 }, "백 집이 36이 아니다")
        assertEquals(
            0,
            empties.count { it.column == 3 || it.column == 4 },
            "두 벽 사이에 공배가 남아 있다 — 집만 세면 되는 도해가 아니게 된다",
        )
        assertEquals(6.5, finished.komi, "덤이 6집반이 아니다 — 문구의 계산이 어긋난다")
    }
}
