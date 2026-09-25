package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.score.buildScoringRuleChangePlan
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoringRuleApplicationTest {
    @Test
    fun localTwoPlayerWithoutEngineShowsLocalScoreAndSkipsEngineSync() {
        val state = playableState()

        val plan = buildScoringRuleChangePlan(
            currentState = state,
            nextRuleset = Ruleset.Chinese,
            isGameEnded = false,
            matchMode = MatchMode.LocalTwoPlayer,
            isEngineReady = false,
            previousSnapshots = emptyList(),
        )

        assertEquals(Ruleset.Chinese, plan.gameState.ruleset)
        assertEquals("Scoring rule changed.", plan.candidateText)
        assertTrue(plan.scoreText.contains("Local Chinese area estimate"))
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreSnapshots.single().source)
        assertEquals("Scoring rule changed to Area. Local scoring is active.", plan.engineMessage)
        assertFalse(plan.requiresEngineSync)
    }

    @Test
    fun engineReadyPlanDefersScoreTextToEngineSync() {
        val previous = listOf(
            ScoreSnapshot(moveNumber = 1, source = ScoreSnapshotSource.EngineEstimate),
        )

        val plan = buildScoringRuleChangePlan(
            currentState = playableState(),
            nextRuleset = Ruleset.Chinese,
            isGameEnded = false,
            matchMode = MatchMode.HumanVsAi,
            isEngineReady = true,
            previousSnapshots = previous,
        )

        assertEquals("Score estimate not current.", plan.scoreText)
        assertEquals(1, plan.scoreSnapshots.single().moveNumber)
        assertNull(plan.engineMessage)
        assertTrue(plan.requiresEngineSync)
    }

    private fun playableState(): GameState =
        GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
}
