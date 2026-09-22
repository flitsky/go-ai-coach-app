package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 학습 도해의 계약(백로그 #164 「바둑 규칙 배우기」 · #183 「바둑 기초 행마」).
 *
 * ⚠️ **틀린 도해는 컴파일도 되고 화면에도 뜬다** — 바둑을 모르는 눈에는 잘못 놓인 돌 하나가
 * 보이지 않는다. 그래서 *도해가 가르치려는 그 규칙*을 여기서 직접 물어본다. 문구가 "따낸다"고
 * 적혀 있는데 판에서 돌이 안 사라지는 사고가 이 파일이 막는 것이다.
 */
class StudyLessonsTest {

    /**
     * 모든 단원의 수순이 [BoardRules]를 그대로 지난다 — 차례가 어긋나거나 위법수가 섞이면
     * [StudyLesson.statesByStep]이 여기서 예외로 죽는다.
     */
    @Test
    fun everyLessonReplaysUnderTheRules() {
        studyLessons.forEach { lesson ->
            val states = lesson.statesByStep()
            assertEquals(lesson.steps.size, states.size, "${lesson.id} 단계 수가 국면 수와 다르다")
        }
    }

    /** 단원 하나에 단계가 없으면 빈 화면이 열린다. 목록에 줄만 늘리는 사고를 막는다. */
    @Test
    fun everyLessonHasSteps() {
        studyLessons.forEach { lesson ->
            assertTrue(lesson.steps.isNotEmpty(), "${lesson.id} 에 단계가 없다")
        }
    }

    /** 단원은 [GoRuleLessonId] 하나당 정확히 하나 — 목록이 같은 줄을 두 번 그리지 않게. */
    @Test
    fun lessonsCoverEveryIdExactlyOnce() {
        assertEquals(StudyLessonId.entries, studyLessons.map { it.id })
    }

    /** 문구 표의 키라서 겹치면 두 단계가 같은 설명을 쓴다 — 조용히 틀린다. */
    @Test
    fun stepIdsAreUnique() {
        val ids = studyLessons.flatMap { lesson -> lesson.steps.map { it.id } }
        assertEquals(ids.size, ids.distinct().size, "단계 키가 겹친다: $ids")
    }

    /**
     * ⚠️ **가늠돌은 「다음은 여기」라는 뜻 하나뿐이다**(`StudyLessonStep.marker`). 판이 그것을
     * *놓을 수 있는 자리*의 돌로 그리므로, 둘 수 없는 자리에 찍으면 그림과 글이 정반대를
     * 말한다 — 착수금지·패 단원이 정확히 그 유혹에 걸린다.
     */
    @Test
    fun everyMarkerPointsAtALegalMove() {
        studyLessons.forEach { lesson ->
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
        val lesson = studyLessons.single { it.id == StudyLessonId.RuleCapture }
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
        val lesson = studyLessons.single { it.id == StudyLessonId.RuleForbidden }
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
        val lesson = studyLessons.single { it.id == StudyLessonId.RuleKo }
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
        val lesson = studyLessons.single { it.id == StudyLessonId.RuleTerritory }
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

    /**
     * 갈래마다 단원이 있어야 한다 — 화면은 `studyLessonsFor(track)`만 받아 그리므로,
     * 비면 **빈 목록이 열린다**(고장으로 읽힌다).
     */
    @Test
    fun everyTrackHasLessons() {
        StudyLessonTrack.entries.forEach { track ->
            assertTrue(studyLessonsFor(track).isNotEmpty(), "$track 갈래에 단원이 없다")
        }
        assertEquals(
            studyLessons.size,
            StudyLessonTrack.entries.sumOf { studyLessonsFor(it).size },
            "갈래별 단원을 다 합치면 전체와 같아야 한다 — 어느 갈래에도 안 속한 단원이 있다.",
        )
    }

    /** 단계 키의 접두사가 갈래와 어긋나면 문구 표에서 엉뚱한 본문을 집는다. */
    @Test
    fun stepIdsCarryTheirTrackPrefix() {
        val prefixes = mapOf(StudyLessonTrack.Rules to "rule.", StudyLessonTrack.Shapes to "shape.")
        studyLessons.forEach { lesson ->
            val prefix = prefixes.getValue(lesson.track)
            lesson.steps.forEach { step ->
                assertTrue(
                    step.id.startsWith(prefix),
                    "${lesson.id}/${step.id} 의 키가 `$prefix`로 시작하지 않는다.",
                )
            }
        }
    }

    /** 3단원(호구)이 가르치는 것 — 뛰어든 돌은 **둘 수는 있지만** 곧바로 잡힌다. */
    @Test
    fun theTigerMouthLessonPunishesTheIntruderInsteadOfForbiddingIt() {
        val lesson = studyLessons.single { it.id == StudyLessonId.ShapeTigerMouth }
        val states = lesson.statesByStep()
        val mouth = BoardCoordinate.fromLabel("E5", BoardSize.Nine)

        assertTrue(
            LegalMoveGenerator.isLegalPlay(states[0], mouth),
            "호구 자리에 아예 둘 수 없다 — 그러면 착수금지 단원과 같은 이야기가 되어 버린다.",
        )
        assertEquals(
            null,
            states[2].stoneAt(mouth),
            "뛰어든 백 한 점이 그대로 살아 있다 — 호구가 아니다.",
        )
        assertEquals(1, states[2].capturedBy(StoneColor.Black), "흑이 뛰어든 돌을 따내지 못했다")
    }

    /** 2단원(뻗기)이 가르치는 것 — 뻗으면 활로가 늘고, 그래도 쫓기는 것은 계속된다. */
    @Test
    fun theExtendLessonActuallyGainsLibertiesAndThenLosesThemAgain() {
        val lesson = studyLessons.single { it.id == StudyLessonId.ShapeExtend }
        val states = lesson.statesByStep()
        val stone = BoardCoordinate.fromLabel("E5", BoardSize.Nine)

        fun libertiesOfBlack(state: GameState): Int =
            BoardSize.Nine.allCoordinates()
                .filter { state.stoneAt(it) == null }
                .count { empty ->
                    empty.neighborsForTest().any { state.stoneAt(it) == StoneColor.Black }
                }

        assertEquals(StoneColor.Black, states[0].stoneAt(stone))
        assertEquals(1, libertiesOfBlack(states[0]), "출발 국면이 단수가 아니다 — 뻗을 이유가 없다.")
        assertEquals(3, libertiesOfBlack(states[1]), "뻗었는데 활로가 셋으로 늘지 않았다.")
        assertEquals(2, libertiesOfBlack(states[2]), "백이 막았는데 활로가 둘로 줄지 않았다.")
    }

    /** 4단원(한 칸 뜀)이 가르치는 것 — 갈라 들어온 돌이 **먼저** 단수에 몰린다. */
    @Test
    fun theJumpLessonPutsTheCuttingStoneInAtari() {
        val lesson = studyLessons.single { it.id == StudyLessonId.ShapeJump }
        val states = lesson.statesByStep()
        val cut = BoardCoordinate.fromLabel("D5", BoardSize.Nine)
        val after = states[2]

        assertEquals(StoneColor.White, after.stoneAt(cut), "갈라 들어온 백 돌이 판에 없다")
        val liberties = cut.neighborsForTest().count { after.stoneAt(it) == null }
        assertEquals(
            1,
            liberties,
            "갈라 들어온 백 한 점이 단수가 아니다 — 「끊으면 그쪽이 먼저 쫓긴다」가 거짓이 된다.",
        )
    }
}

/** 활로를 세기 위한 이웃 — `BoardRules`의 것은 `internal`이라 테스트에서 다시 만든다. */
private fun BoardCoordinate.neighborsForTest(): List<BoardCoordinate> =
    buildList {
        if (row > 0) add(copy(row = row - 1))
        if (row < BoardSize.Nine.value - 1) add(copy(row = row + 1))
        if (column > 0) add(copy(column = column - 1))
        if (column < BoardSize.Nine.value - 1) add(copy(column = column + 1))
    }
