package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMoveSelectionPolicyTest {
    @Test
    fun gtpFastBestOnlyLimitUsesSingleCandidateForFastBeginnerBestLevel() {
        val limit = AiMoveSelectionPolicy.analysisLimitFor(
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3),
            searchTimeSettings = SearchTimeSettings(),
            searchMode = EngineSearchMode.GtpStatefulFast,
        )

        assertEquals(16, limit.visits)
        assertEquals(1, limit.candidateCount)
        assertEquals(false, limit.includePolicy)
    }

    @Test
    fun jsonLevelingLimitKeepsCandidatePoolForBeginnerBestLevel() {
        val limit = AiMoveSelectionPolicy.analysisLimitFor(
            playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7),
            searchTimeSettings = SearchTimeSettings(),
            searchMode = EngineSearchMode.JsonPositionAnalysis,
        )

        assertEquals(32, limit.visits)
        assertEquals(16, limit.candidateCount)
        assertEquals(true, limit.includePolicy)
    }

    @Test
    fun passBestCandidateOverridesRandomLeveling() {
        val pass = Move.Pass(StoneColor.White)
        val selected = AiMoveSelectionPolicy.select(
            currentState = GameState.empty(),
            aiPlayer = StoneColor.White,
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 1),
            searchMode = EngineSearchMode.GtpStatefulFast,
            candidates = listOf(
                CandidateMove(move = pass, pointLoss = 0.0),
                CandidateMove(
                    move = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D4", BoardSize.Nine)),
                    pointLoss = 5.0,
                ),
            ),
            analysisSummary = "analysis",
            random = Random(0),
        )

        assertEquals(pass, selected?.move)
        assertTrue(selected?.summary?.contains("endgame pass override") == true)
        assertTrue(selected?.summary?.contains("AI candidates:") == true)
        assertTrue(selected?.summary?.contains("1. White pass loss=0.0 [selected]") == true)
    }

    @Test
    fun selectionIgnoresOpponentCandidatesAndUnscoredCandidates() {
        val blackMove = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))
        val selected = AiMoveSelectionPolicy.select(
            currentState = GameState.empty(),
            aiPlayer = StoneColor.Black,
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3),
            searchMode = EngineSearchMode.GtpStatefulFast,
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D4", BoardSize.Nine)),
                    pointLoss = 0.0,
                ),
                CandidateMove(move = blackMove, pointLoss = null),
                CandidateMove(move = blackMove, pointLoss = 0.2),
            ),
            analysisSummary = "analysis",
            random = Random(0),
        )

        assertEquals(blackMove, selected?.move)
        assertTrue(selected?.summary?.contains("rank 1/1") == true)
        assertTrue(selected?.summary?.contains("AI candidates:") == true)
        assertTrue(selected?.summary?.contains("1. Black E5 loss=0.2 [selected]") == true)
    }

    @Test
    fun returnsNullWhenNoScoredPlayCandidateFitsPolicy() {
        val selected = AiMoveSelectionPolicy.select(
            currentState = GameState.empty(),
            aiPlayer = StoneColor.Black,
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3),
            searchMode = EngineSearchMode.GtpStatefulFast,
            candidates = emptyList(),
            analysisSummary = "analysis",
            random = Random(0),
        )

        assertNull(selected)
    }
}
