package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.preferences.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoCoachSessionFactoryTest {
    @Test
    fun buildsSavedHandicapAsReadyPreviewWithoutStartingTheGame() {
        val plan = buildInitialUserPreferencesPlan(
            preferences = UserPreferencesSnapshot(
                boardSize = BoardSize.Nineteen,
                ruleset = Ruleset.Japanese,
                handicapCount = 5,
            ),
            defaultPlayLevel = PlayLevelSetting(),
            currentProfile = EngineProfile(),
        )

        val state = buildInitialSessionState(
            initialPlan = plan,
            engineDiagnostic = "test",
            benchmarkStore = EmptyBenchmarkStore,
        )

        assertTrue(state.isGameEnded)
        assertEquals(5, state.settings.handicapCount)
        assertEquals(5, state.gameState.handicapCount)
        assertEquals(5, state.gameState.stones.size)
    }
}

private object EmptyBenchmarkStore : EngineBenchmarkStorePort {
    override fun exists(): Boolean = false

    override fun hasUsableProfile(
        samplesPerVisit: Int,
        timeCapMs: Long,
        measurementVersion: Int,
        visitsTargets: List<Int>,
    ): Boolean = false

    override fun save(profile: EngineBenchmarkProfile) = Unit

    override fun load(): EngineBenchmarkProfile? = null

    override fun loadText(): String = ""

    override fun path(): String = ""
}
