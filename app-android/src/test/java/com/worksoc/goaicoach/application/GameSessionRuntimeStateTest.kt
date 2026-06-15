package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.score.*

import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class GameSessionRuntimeStateTest {
    @Test
    fun applySelectionUpdatesRuntimeTripleTogether() {
        val original = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 1),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
        )
        val nextLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 7)
        val nextProfile = nextLevel.toEngineProfile(EngineProfile(), SearchTimeSettings(b32Millis = 2_000L))
        val selection = RuntimePlayLevelSelection(
            playLevel = nextLevel,
            engineProfile = nextProfile,
            analysisPreset = nextLevel.analysisPreset,
            searchTimeSettings = SearchTimeSettings(b32Millis = 2_000L),
        )

        val next = original.applySelection(selection)

        assertEquals(nextLevel, next.playLevel)
        assertEquals(nextProfile, next.engineProfile)
        assertEquals(nextLevel.analysisPreset, next.analysisPreset)
        assertEquals(original.sessionGeneration, next.sessionGeneration)
    }

    @Test
    fun applyAutoAiTurnDisplayPlanUsesTurnRuntimeValues() {
        val original = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 1),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
        )
        val turnLevel = PlayLevelSetting(group = PlayLevelGroup.Intermediate, level = 3)
        val turnProfile = turnLevel.toEngineProfile(EngineProfile(), SearchTimeSettings(b64Millis = 3_000L))
        val display = AutoAiTurnDisplayPlan(
            playLevel = turnLevel,
            profile = turnProfile,
            analysisPreset = turnLevel.analysisPreset,
            gameState = GameState.empty(),
            turnEngineMessage = "AI moved",
            candidateText = "candidate",
            lastMoveText = "Black E5",
            scoreDisplay = ScoreEstimateDisplayPlan(
                scoreText = "Score estimate not current.",
                scoreEstimate = null,
                scoreSnapshots = emptyList(),
                engineMessage = "score",
            ),
            shouldResolveEndgame = false,
            endgamePrePassCandidates = emptyList(),
            nextAnalysisState = GameState.empty(),
        )

        val next = original.applyAutoAiTurnDisplayPlan(display)

        assertEquals(turnLevel, next.playLevel)
        assertEquals(turnProfile, next.engineProfile)
        assertEquals(turnLevel.analysisPreset, next.analysisPreset)
        assertEquals(original.sessionGeneration, next.sessionGeneration)
    }

    @Test
    fun nextSessionGenerationOnlyAdvancesGenerationCounter() {
        val original = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 3),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Balanced,
            sessionGeneration = 4L,
        )

        val next = original.nextSessionGeneration()

        assertEquals(original.playLevel, next.playLevel)
        assertEquals(original.engineProfile, next.engineProfile)
        assertEquals(original.analysisPreset, next.analysisPreset)
        assertEquals(5L, next.sessionGeneration)
    }
}
