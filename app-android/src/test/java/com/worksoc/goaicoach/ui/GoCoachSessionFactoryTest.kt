package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.preferences.buildInitialUserPreferencesPlan
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.domain.Ruleset
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
            storedBenchmarkText = "No engine benchmark file recorded.",
        )

        assertTrue(state.isGameEnded)
        assertEquals(5, state.settings.handicapCount)
        assertEquals(5, state.gameState.handicapCount)
        assertEquals(5, state.gameState.stones.size)
        // 디버그 리포트용 원문은 받은 그대로 싣는다 — 포트가 아니라 어댑터가 낸다(refactor backlog #86).
        assertEquals("No engine benchmark file recorded.", state.benchmark.benchmarkText)
    }
}

private object EmptyBenchmarkStore : EngineBenchmarkStorePort {
    override fun exists(): Boolean = false

    override fun save(profile: EngineBenchmarkProfile) = Unit

    override fun load(): EngineBenchmarkProfile? = null
}
