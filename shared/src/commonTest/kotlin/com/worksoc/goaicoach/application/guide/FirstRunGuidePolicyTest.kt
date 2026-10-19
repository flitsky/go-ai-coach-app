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

    /** 첫 실행 사슬 — 업데이트 안내(#270)를 뺀 단계들. 사슬의 성질을 보는 단언은 이것만 센다. */
    private val firstRunChain = GuideStep.entries.filter { it.shownInVersions == null }

    @Test
    fun nothingPlaysBeforeTheFirstRunArmsTheChain() {
        // 기존 사용자가 여기 해당한다 — 첫 실행을 이미 지났으므로 armed가 켜질 기회가 없었다.
        assertNull(autoPlayStep(GuideSurface.Home, GuideProgress(), blocked = false, versionCode = V160))
    }

    @Test
    fun armingPlaysTheFirstUnseenStepOfThatSurface() {
        assertEquals(GuideStep.HomeStartMatch, autoPlayStep(GuideSurface.Home, armed, blocked = false, versionCode = V160))
        assertEquals(GuideStep.MatchSetup, autoPlayStep(GuideSurface.MatchSetup, armed, blocked = false, versionCode = V160))
        assertEquals(GuideStep.InGameEval, autoPlayStep(GuideSurface.InGame, armed, blocked = false, versionCode = V160))
    }

    @Test
    fun aSeenStepDoesNotPlayAgain() {
        val seen = armed.copy(seenSteps = setOf(GuideStep.HomeStartMatch.id))
        assertNull(autoPlayStep(GuideSurface.Home, seen, blocked = false, versionCode = V160))
        // 다른 표면은 영향받지 않는다.
        assertEquals(GuideStep.MatchSetup, autoPlayStep(GuideSurface.MatchSetup, seen, blocked = false, versionCode = V160))
    }

    /**
     * ⚠️ **가려진 채 그려지면 사용자는 못 봤는데 기록된다.** 그래서 `blocked`는 판정의 인자이고
     * 호출부의 재량이 아니다 — 이 단언이 그 계약이다.
     */
    @Test
    fun nothingPlaysWhileAnotherDialogCoversTheSurface() {
        assertNull(autoPlayStep(GuideSurface.Home, armed, blocked = true, versionCode = V160))
        assertNull(autoPlayStep(GuideSurface.InGame, armed, blocked = true, versionCode = V160))
    }

    /**
     * ⚠️ **오늘은 표면 하나에 단계 하나지만, 그 가정을 지금 풀어 둔다.** #18·#26이나 로그인이 붙으면
     * 대국 설정·마이페이지에 두 번째 단계가 생긴다 — 그때 판정식을 다시 쓰지 않도록
     * **선언 순서가 같은 표면 안에서도 권위를 갖는다**는 성질을 여기서 못박는다.
     */
    @Test
    fun declarationOrderDrainsTheChainInOrder() {
        val chain = firstRunChain
        var progress = armed
        chain.forEach { expected ->
            assertEquals(
                expected,
                autoPlayStep(expected.surface, progress, blocked = false, versionCode = V160),
                "선언 순서상 먼저인 미시청 단계가 나와야 한다",
            )
            progress = progress.copy(seenSteps = progress.seenSteps + expected.id)
        }
        // 다 보고 나면 어느 표면에서도 사슬의 단계는 뜨지 않는다 — 1.6.x 밖이면 업데이트 안내도 없으니 아무것도 뜨지 않는다.
        GuideSurface.entries.forEach { surface ->
            assertNull(autoPlayStep(surface, progress, blocked = false, versionCode = V170), "$surface 가 다 본 뒤에도 뜬다")
        }
    }

    /**
     * ⚠️ **대국 화면이 한 표면에 단계 여럿을 갖는 첫 사례다**(2026-09-09, 버튼별로 쪼갬).
     * 사용자가 하나를 확인하면 **다음이 뜨는** 것이 그 요구였고, 그것은 곧 *"미시청 중 선언 순서상
     * 첫째"* 다 — 이 케이스가 그 순서를 못박는다.
     * (넷이었다가 #143이 돋보기·판 크기 버튼을 메뉴로 옮기며 **둘**이 됐다.)
     */
    @Test
    fun theInGameStepsAdvanceOneTapAtATimeInDeclarationOrder() {
        val expected = listOf(
            GuideStep.InGameEval,
            GuideStep.InGameTopMoves,
        )
        var progress = armed
        expected.forEach { step ->
            assertEquals(
                step,
                autoPlayStep(GuideSurface.InGame, progress, blocked = false, versionCode = V160),
                "확인할 때마다 다음 버튼 안내가 떠야 한다",
            )
            progress = progress.copy(seenSteps = progress.seenSteps + step.id)
        }
        assertNull(
            autoPlayStep(GuideSurface.InGame, progress, blocked = false, versionCode = V160),
            "넷을 다 본 뒤에는 대국 화면에서 아무것도 뜨지 않아야 한다",
        )
    }

    /** 네 단계가 각자 **다른 버튼**을 가리키는지 — 같은 대상을 두 번 가리키면 하나는 헛수고다. */
    @Test
    fun eachInGameStepPointsAtItsOwnButton() {
        val targets = GuideStep.entries
            .filter { it.surface == GuideSurface.InGame }
            .map { it.target }
        // ⚠️ 자기검증 — 필터가 조용히 0개를 집으면 아래 둘은 아무것도 보지 않으면서 통과한다.
        //   넷이었다가 #143이 돋보기·판 크기 버튼을 메뉴로 옮기며 **둘**이 됐다.
        assertEquals(2, targets.size)
        assertEquals(targets.distinct().size, targets.size, "두 단계가 같은 버튼을 가리킨다")
        assertTrue(targets.none { it == null }, "대국 화면 단계는 가리킬 버튼이 있어야 한다")
    }

    /** 같은 표면에 단계가 둘 생기는 미래를 지금 시뮬레이션한다 — 순서가 권위를 갖는지 본다. */
    @Test
    fun theEarlierDeclarationWinsWhenOneSurfaceHasTwoSteps() {
        val onMatchSetup = GuideStep.entries.filter { it.surface == GuideSurface.MatchSetup }
        assertTrue(onMatchSetup.isNotEmpty(), "대국 설정 표면에 단계가 하나도 없다 — 전제가 무너졌다")
        // 표면이 같은 두 단계를 순서대로 늘어놓고, 앞선 것이 먼저 나오는지 확인한다.
        val bySurface = firstRunChain.groupBy { it.surface }
        bySurface.forEach { (surface, steps) ->
            val first = steps.first()
            assertEquals(
                first,
                autoPlayStep(surface, armed, blocked = false, versionCode = V160),
                "$surface 에서 선언 순서상 첫 단계가 나와야 한다",
            )
        }
    }

    /**
     * 업데이트 안내(백로그 #270 — 홈 윗줄의 배경음악 버튼): **기존 사용자**에게 뜬다. 그들은 첫 실행 처리를 지나 무장되지 않았거나,
     * 무장됐어도 사슬을 다 봤다 — 둘 다 1.6.x에서 한 번.
     */
    @Test
    fun theMusicToggleNoticeReachesExistingUsersInTheOneSixLine() {
        val oldInstall = GuideProgress()
        val chainSeen = armed.copy(seenSteps = firstRunChain.map { it.id }.toSet())
        listOf(oldInstall, chainSeen).forEach { progress ->
            assertEquals(GuideStep.HomeMusicToggle, autoPlayStep(GuideSurface.HomeQuickActions, progress, blocked = false, versionCode = V160))
            assertEquals(GuideStep.HomeMusicToggle, autoPlayStep(GuideSurface.HomeQuickActions, progress, blocked = false, versionCode = 10601))
        }
    }

    /** 사용자 2026-10-19: *"이번 신규 업데이트 버전에서만"* — 1.5.1 이전에도, 1.7.0 이후에도 뜨지 않는다. */
    @Test
    fun theMusicToggleNoticeStaysInsideItsVersions() {
        val oldInstall = GuideProgress()
        assertNull(autoPlayStep(GuideSurface.HomeQuickActions, oldInstall, blocked = false, versionCode = 10501))
        assertNull(autoPlayStep(GuideSurface.HomeQuickActions, oldInstall, blocked = false, versionCode = V170))
    }

    /** 새로 깐 사람의 첫 홈에는 ③ 말풍선이 있다 — 사슬이 남은 동안은 미루고, 사슬을 다 보면 뜬다. */
    @Test
    fun theMusicToggleNoticeWaitsForTheFirstRunChain() {
        assertNull(autoPlayStep(GuideSurface.HomeQuickActions, armed, blocked = false, versionCode = V160))
        val allButLast = armed.copy(seenSteps = firstRunChain.dropLast(1).map { it.id }.toSet())
        assertNull(autoPlayStep(GuideSurface.HomeQuickActions, allButLast, blocked = false, versionCode = V160))
        val chainSeen = armed.copy(seenSteps = firstRunChain.map { it.id }.toSet())
        assertEquals(GuideStep.HomeMusicToggle, autoPlayStep(GuideSurface.HomeQuickActions, chainSeen, blocked = false, versionCode = V160))
    }

    /** 한 번 보면 끝이고, 팝업(「새로워진 점」 등)이 덮은 동안은 뜨지 않는다. */
    @Test
    fun theMusicToggleNoticeShowsOnceAndNeverUnderADialog() {
        val oldInstall = GuideProgress()
        assertNull(autoPlayStep(GuideSurface.HomeQuickActions, oldInstall, blocked = true, versionCode = V160))
        val seen = oldInstall.copy(seenSteps = setOf(GuideStep.HomeMusicToggle.id))
        assertNull(autoPlayStep(GuideSurface.HomeQuickActions, seen, blocked = false, versionCode = V160))
        // 기존 사용자에게 첫 실행 사슬이 뜨지 않는 것은 그대로다.
        assertNull(autoPlayStep(GuideSurface.Home, oldInstall, blocked = false, versionCode = V160))
    }

    /**
     * ⚠️ [GuideStep.id]는 **저장 포맷**이다(함정 1번과 같은 성질) — 바꾸면 이미 본 사용자에게
     * 가이드가 다시 뜬다. 값을 여기 못박아 무심한 개명을 잡는다.
     *
     * ⚠️ `landing`은 #140이, `in_game_magnifier`·`in_game_board_size`는 #143이 **지웠다**(개명이 아니다).
     *   지운 id가 남의 `seen_steps`에 남아 있어도 그냥 무시된다 — 그래서 지우는 것은 안전하고, **바꾸는 것**만 위험하다.
     */
    @Test
    fun theStoredIdsAreFrozen() {
        assertEquals(
            listOf(
                "attendance_claim", "home_start_match", "match_setup",
                "in_game_eval", "in_game_top_moves",
                "home_music_toggle",
            ),
            GuideStep.entries.map { it.id },
        )
    }
}

private const val V160 = 10600
private const val V170 = 10700
