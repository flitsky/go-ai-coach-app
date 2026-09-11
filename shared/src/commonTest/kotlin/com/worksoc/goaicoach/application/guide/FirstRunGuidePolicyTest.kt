package com.worksoc.goaicoach.application.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [autoPlayStep]의 경계를 전부 센다(백로그 #128).
 *
 * ⚠️ **이 판정이 틀리면 화면을 보고 있어야만 알아챈다** — 가이드가 조용히 소진되거나 조용히
 * 영원히 뜬다. 그것이 판정을 `shared`로 뺀 이유이고, 이 파일이 그 대가를 받는 자리다.
 */
class FirstRunGuidePolicyTest {

    private val armed = GuideProgress(armed = true)

    @Test
    fun nothingPlaysBeforeTheFirstRunArmsTheChain() {
        // 기존 사용자가 여기 해당한다 — 첫 실행을 이미 지났으므로 armed가 켜질 기회가 없었다.
        assertNull(autoPlayStep(GuideSurface.Home, GuideProgress(), blocked = false))
    }

    @Test
    fun armingPlaysTheFirstUnseenStepOfThatSurface() {
        assertEquals(GuideStep.HomeStartMatch, autoPlayStep(GuideSurface.Home, armed, blocked = false))
        assertEquals(GuideStep.MatchSetup, autoPlayStep(GuideSurface.MatchSetup, armed, blocked = false))
        assertEquals(GuideStep.InGameMagnifier, autoPlayStep(GuideSurface.InGame, armed, blocked = false))
    }

    @Test
    fun aSeenStepDoesNotPlayAgain() {
        val seen = armed.copy(seenSteps = setOf(GuideStep.HomeStartMatch.id))
        assertNull(autoPlayStep(GuideSurface.Home, seen, blocked = false))
        // 다른 표면은 영향받지 않는다.
        assertEquals(GuideStep.MatchSetup, autoPlayStep(GuideSurface.MatchSetup, seen, blocked = false))
    }

    /**
     * ⚠️ **가려진 채 그려지면 사용자는 못 봤는데 기록된다.** 그래서 `blocked`는 판정의 인자이고
     * 호출부의 재량이 아니다 — 이 단언이 그 계약이다.
     */
    @Test
    fun nothingPlaysWhileAnotherDialogCoversTheSurface() {
        assertNull(autoPlayStep(GuideSurface.Home, armed, blocked = true))
        assertNull(autoPlayStep(GuideSurface.InGame, armed, blocked = true))
    }

    /**
     * ⚠️ **오늘은 표면 하나에 단계 하나지만, 그 가정을 지금 풀어 둔다.** #18·#26이나 로그인이 붙으면
     * 대국 설정·마이페이지에 두 번째 단계가 생긴다 — 그때 판정식을 다시 쓰지 않도록
     * **선언 순서가 같은 표면 안에서도 권위를 갖는다**는 성질을 여기서 못박는다.
     */
    @Test
    fun declarationOrderDrainsTheChainInOrder() {
        val chain = GuideStep.entries
        var progress = armed
        chain.forEach { expected ->
            assertEquals(
                expected,
                autoPlayStep(expected.surface, progress, blocked = false),
                "선언 순서상 먼저인 미시청 단계가 나와야 한다",
            )
            progress = progress.copy(seenSteps = progress.seenSteps + expected.id)
        }
        // 다 보고 나면 어느 표면에서도 아무것도 뜨지 않는다.
        GuideSurface.entries.forEach { surface ->
            assertNull(autoPlayStep(surface, progress, blocked = false), "$surface 가 다 본 뒤에도 뜬다")
        }
    }

    /**
     * ⚠️ **대국 화면이 한 표면에 단계 넷을 갖는 첫 사례다**(2026-09-09, 버튼별로 쪼갬).
     * 사용자가 하나를 확인하면 **다음이 뜨는** 것이 그 요구였고, 그것은 곧 *"미시청 중 선언 순서상
     * 첫째"* 다 — 이 케이스가 그 순서를 못박는다.
     */
    @Test
    fun theInGameStepsAdvanceOneTapAtATimeInDeclarationOrder() {
        val expected = listOf(
            GuideStep.InGameMagnifier,
            GuideStep.InGameBoardSize,
            GuideStep.InGameEval,
            GuideStep.InGameTopMoves,
        )
        var progress = armed
        expected.forEach { step ->
            assertEquals(
                step,
                autoPlayStep(GuideSurface.InGame, progress, blocked = false),
                "확인할 때마다 다음 버튼 안내가 떠야 한다",
            )
            progress = progress.copy(seenSteps = progress.seenSteps + step.id)
        }
        assertNull(
            autoPlayStep(GuideSurface.InGame, progress, blocked = false),
            "넷을 다 본 뒤에는 대국 화면에서 아무것도 뜨지 않아야 한다",
        )
    }

    /** 네 단계가 각자 **다른 버튼**을 가리키는지 — 같은 대상을 두 번 가리키면 하나는 헛수고다. */
    @Test
    fun eachInGameStepPointsAtItsOwnButton() {
        val targets = GuideStep.entries
            .filter { it.surface == GuideSurface.InGame }
            .map { it.target }
        assertEquals(4, targets.size)
        assertEquals(targets.distinct().size, targets.size, "두 단계가 같은 버튼을 가리킨다")
        assertTrue(targets.none { it == null }, "대국 화면 단계는 가리킬 버튼이 있어야 한다")
    }

    /** 같은 표면에 단계가 둘 생기는 미래를 지금 시뮬레이션한다 — 순서가 권위를 갖는지 본다. */
    @Test
    fun theEarlierDeclarationWinsWhenOneSurfaceHasTwoSteps() {
        val onMatchSetup = GuideStep.entries.filter { it.surface == GuideSurface.MatchSetup }
        assertTrue(onMatchSetup.isNotEmpty(), "대국 설정 표면에 단계가 하나도 없다 — 전제가 무너졌다")
        // 표면이 같은 두 단계를 순서대로 늘어놓고, 앞선 것이 먼저 나오는지 확인한다.
        val ordered = GuideStep.entries
        val bySurface = ordered.groupBy { it.surface }
        bySurface.forEach { (surface, steps) ->
            val first = steps.first()
            assertEquals(
                first,
                autoPlayStep(surface, armed, blocked = false),
                "$surface 에서 선언 순서상 첫 단계가 나와야 한다",
            )
        }
    }

    /**
     * ⚠️ [GuideStep.id]는 **저장 포맷**이다(함정 1번과 같은 성질) — 바꾸면 이미 본 사용자에게
     * 가이드가 다시 뜬다. 값을 여기 못박아 무심한 개명을 잡는다.
     *
     * ⚠️ `landing`은 #140이 **지웠다**(개명이 아니다) — ①은 판정에 참여한 적이 없어 저장된 적도 없다.
     */
    @Test
    fun theStoredIdsAreFrozen() {
        assertEquals(
            listOf(
                "attendance_claim", "home_start_match", "match_setup",
                "in_game_magnifier", "in_game_board_size", "in_game_eval", "in_game_top_moves",
            ),
            GuideStep.entries.map { it.id },
        )
    }
}
